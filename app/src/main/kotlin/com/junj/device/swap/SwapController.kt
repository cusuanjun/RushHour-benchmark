package com.junj.device.swap

import com.junj.cli.ArgType
import com.junj.config.ExperimentConfig
import com.junj.device.adb.AdbExecutor
import com.junj.device.adb.runAdbRootShellCommand
import com.junj.output.logging.Logger
import com.junj.output.logging.LogType

class SwapController(
    val adb: AdbExecutor,
    val logger: Logger,
    val config: ExperimentConfig,
) {
    fun disableZram() {
        runAdbRootShellCommand("swapoff /dev/block/zram0")
    }

    fun enableFlashSwap(flashSwapDeviceSizeMb: Int) {
        adb.rootShell("dd if=/dev/zero of=/data/per_boot/flash.swap bs=1M count=$flashSwapDeviceSizeMb")
        adb.rootShell("chmod 600 /data/per_boot/flash.swap")
        adb.rootShell("mkswap /data/per_boot/flash.swap")
        adb.rootShell("swapon /data/per_boot/flash.swap")
        println(adb.rootShell("cat /proc/swaps").output)
    }

    fun enableZswap() {
        adb.rootShell("echo lz4 > /sys/module/zswap/parameters/compressor")
        adb.rootShell("echo zsmalloc > /sys/module/zswap/parameters/zpool")
        adb.rootShell("echo Y > /sys/module/zswap/parameters/enabled")
        println("zSwap enabled: " + runAdbRootShellCommand("cat /sys/module/zswap/parameters/enabled").output)
        adb.rootShell("mount -t debugfs debugfs /sys/kernel/debug")
    }

    fun initSwapByType() {
        when (config.swapType) {
            ArgType.NO_SWAP.code -> {
                logger.log(LogType.SUMMARY, "config.swapLabel=No Swap")
                disableZram()
            }
            ArgType.ONLY_ZRAM.code -> logger.log(LogType.SUMMARY, "config.swapLabel=Only zRAM")
            ArgType.ONLY_FLASH_SWAP.code -> {
                logger.log(LogType.SUMMARY, "config.swapLabel=Only Flash Swap")
                disableZram()
                enableFlashSwap(config.flashSwapDeviceSizeMB)
            }
            ArgType.ZSWAP.code -> {
                logger.log(LogType.SUMMARY, "config.swapLabel=ZSWAP")
                disableZram()
                enableFlashSwap(config.flashSwapDeviceSizeMB)
                enableZswap()
            }
        }
        adb.rootShell("""echo 1 > /sys/kernel/mm/swapin_latency/reset""")
        if (config.swapType != ArgType.NO_SWAP.code) {
            adb.rootShell("""echo 1 > /sys/kernel/mm/swapin_latency/enable""")
            adb.rootShell("""echo ${config.swapInLatSampleRate} > /sys/kernel/mm/swapin_latency/sample_rate""")
        }
    }
}