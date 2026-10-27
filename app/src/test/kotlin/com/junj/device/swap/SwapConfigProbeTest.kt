package com.junj.device.swap

import com.junj.cli.ArgType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 钉住 displaySwapConfigsFromPhone 的"按类型选节点"逻辑（纯函数，不需要连设备）。 */
class SwapConfigProbeTest {
    private fun labels(swapType: Int) = swapConfigProbes(swapType).map { it.label }

    @Test
    fun `every type reads the swap list and the selinux state`() {
        ArgType.entries.forEach { type ->
            val labels = labels(type.code)
            assertTrue(labels.any { it.contains("proc/swaps") }, "${type.name}: $labels")
            assertTrue(labels.any { it.contains("selinux") }, "${type.name}: $labels")
            assertTrue(labels.any { it.contains("backing_dev") } == (type == ArgType.ZRAM_WRITEBACK), "${type.name}: $labels")
        }
    }

    @Test
    fun `no swap and only zram do not read backends they never create`() {
        for (type in listOf(ArgType.NO_SWAP.code, ArgType.ONLY_ZRAM.code)) {
            val labels = labels(type)
            assertTrue(labels.none { it.contains("flash swap file") }, labels.toString())
            assertTrue(labels.none { it.contains("zswap") }, labels.toString())
            assertTrue(labels.none { it.contains("bd_stat") }, labels.toString())
        }
    }

    @Test
    fun `flash swap variants read the flash file and zswap adds its own nodes`() {
        assertTrue(labels(ArgType.ONLY_FLASH_SWAP.code).any { it.contains("flash swap file") })
        assertTrue(labels(ArgType.ONLY_FLASH_SWAP.code).none { it.contains("zswap") })

        val zswap = labels(ArgType.ZSWAP.code)
        assertTrue(zswap.any { it.contains("flash swap file") }, zswap.toString())
        assertTrue(zswap.any { it.contains("zswap parameters") }, zswap.toString())
        assertTrue(zswap.any { it.contains("zswap counters") }, zswap.toString())
    }

    @Test
    fun `writeback reads backing device bd_stat quota and per_boot files`() {
        val labels = labels(ArgType.ZRAM_WRITEBACK.code)
        assertTrue(labels.any { it.contains("backing_dev") }, labels.toString())
        assertTrue(labels.any { it.contains("bd_stat") }, labels.toString())
        assertTrue(labels.any { it.contains("writeback limit") }, labels.toString())
        assertTrue(labels.any { it.contains("per_boot") }, labels.toString())
        assertTrue(labels.none { it.contains("zswap parameters") }, labels.toString())
    }

    @Test
    fun `loop probes cover the backing file direct io and autoclear`() {
        val labels = backingLoopProbes("/dev/block/loop46").map { it.label }
        assertTrue(labels.any { it.contains("backing_file") }, labels.toString())
        assertTrue(labels.any { it.contains("dio/blocksize") }, labels.toString())
        assertTrue(labels.any { it.contains("autoclear/size") }, labels.toString())
    }

    @Test
    fun `raw partitions and unset backends produce no loop probes`() {
        assertEquals(emptyList(), backingLoopProbes("/dev/block/sda12"))
        assertEquals(emptyList(), backingLoopProbes(""))
        assertEquals(emptyList(), backingLoopProbes("none"))
    }
}
