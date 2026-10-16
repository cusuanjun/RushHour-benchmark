package com.junj.cli

import com.junj.config.DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppCommandTest {
    @Test
    fun `flash size falls back to the default when -f is omitted`() {
        val config = parseArgs(arrayOf("-t", "2", "-a", "0"))

        assertEquals(DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB, config.flashSwapDeviceSizeMB)
    }

    @Test
    fun `flash size is taken from -f`() {
        val config = parseArgs(arrayOf("-t", "2", "-a", "0", "-f", "2048"))

        assertEquals(2048, config.flashSwapDeviceSizeMB)
    }

    @Test
    fun `flash size shows up right after the flash swap suffix`() {
        assertTrue(
            parseArgs(arrayOf("-t", "2", "-a", "0", "-f", "2048"))
                .timestamp.endsWith("_light_flashSwap2048")
        )
        assertTrue(
            parseArgs(arrayOf("-t", "3", "-a", "1"))
                .timestamp.endsWith("_middle_zswap4096")
        )
    }

    @Test
    fun `swap types without flash swap keep the suffix without a size`() {
        assertTrue(parseArgs(arrayOf("-t", "0", "-a", "0")).timestamp.endsWith("_light_noSwap"))
        assertTrue(parseArgs(arrayOf("-t", "1", "-a", "0")).timestamp.endsWith("_light_onlyZram"))
    }

    @Test
    fun `flash size is rejected for swap types that cannot use flash swap`() {
        assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("-t", "0", "-a", "0", "-f", "2048"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("-t", "1", "-a", "0", "-f", "2048"))
        }
    }

    @Test
    fun `flash size must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("-t", "2", "-a", "0", "-f", "0"))
        }
        assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("-t", "2", "-a", "0", "-f", "-1"))
        }
    }
}
