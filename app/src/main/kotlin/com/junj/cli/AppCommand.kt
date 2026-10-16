package com.junj.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import com.junj.config.DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB
import com.junj.config.ExperimentConfig
import com.junj.domain.app.AppSetType
import com.junj.output.logging.getTimeStamp
import java.io.File

enum class ArgType {
    NO_SWAP,
    ONLY_ZRAM,
    ONLY_FLASH_SWAP,
    ZSWAP;

    val code: Int
        get() = ordinal
}

class AppCommand : CliktCommand(name = "android-app-launch-test") {
    val type: Int by option(
        "-t",
        "--type",
        help = "Swap type: 0=no swap, 1=zRAM, 2=flash swap, 3=zSwap",
    )
        .int()
        .required()
        .validate {
            require(it in ArgType.NO_SWAP.code..ArgType.ZSWAP.code) {
                "type must be between ${ArgType.NO_SWAP.code} and ${ArgType.ZSWAP.code}"
            }
        }

    val appSetNumber: Int by option(
        "-a",
        "--apps",
        help = "app set number: 0=light, 1=mid, 2=heavy"
    )
        .int()
        .required()
        .validate {
            require(it in AppSetType.LIGHT.code..AppSetType.HEAVY.code) {
                "app-set must be between ${AppSetType.LIGHT.code} and ${AppSetType.HEAVY.code}"
            }
        }

    val logPath: String? by option(
        "-p",
        "--prefix",
        help = "Optional log output path",
    )

    val randomSeed: Int by option(
        "-s",
        "--seed",
        help = "Random seed number for app launch"
    )
        .int()
        .default(0)

    val flashSize: Int? by option(
        "-f",
        "--flash_size",
        help = "Flash swap device size in MB; only used by the flash swap types " +
            "(-t ${ArgType.ONLY_FLASH_SWAP.code} and -t ${ArgType.ZSWAP.code}). " +
            "Optional, defaults to $DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB MB.",
    )
        .int()

    override fun run() = Unit
}

fun parseArgs(args: Array<String>): ExperimentConfig {
    val command = AppCommand().also { it.main(args) }
    val flashSize = command.flashSize
    if (flashSize != null) {
        require(
            command.type == ArgType.ONLY_FLASH_SWAP.code || command.type == ArgType.ZSWAP.code
        ) {
            "-f/--flash_size is only applicable to the flash swap types: " +
                "-t ${ArgType.ONLY_FLASH_SWAP.code} (flash swap) or -t ${ArgType.ZSWAP.code} (zSwap)"
        }
        require(flashSize > 0) { "flash_size must be greater than 0, got $flashSize" }
    }
    val flashSwapDeviceSizeMB = flashSize ?: DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB

    val suffix = buildString {
        when (command.appSetNumber) {
            AppSetType.LIGHT.code -> append("_light")
            AppSetType.MIDDLE.code -> append("_middle")
            AppSetType.HEAVY.code -> append("_heavy")
        }
        when(command.type) {
            0 -> append("_noSwap")
            1 -> append("_onlyZram")
            2 -> append("_flashSwap$flashSwapDeviceSizeMB")
            3 -> append("_zswap$flashSwapDeviceSizeMB")
        }
        command.logPath?.let { append("_$it") }
    }
    val timestamp = getTimeStamp() + suffix

    return ExperimentConfig(
        swapType = command.type,
        appSetNumber = command.appSetNumber,
        randomSeed = command.randomSeed,
        timestamp = timestamp,
        outputDirectory = File("../logs/$timestamp"),
        flashSwapDeviceSizeMB = flashSwapDeviceSizeMB,
    )
}
