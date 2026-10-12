package com.junj.config

import java.io.File

const val DEFAULT_TEST_ROUND_COUNT = 5
const val DEFAULT_SAMPLE_INTERVAL_MS = 1_000L
const val DEFAULT_LAUNCH_INTERVAL_MS = 20_000L
const val DEFAULT_COLLECT_DELAY_MS = 18_000L
const val DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB = 1024 * 4

data class ExperimentConfig(
    val swapType: Int,
    val appSetNumber: Int,
    val randomSeed: Int = 0,
    val timestamp: String,
    val outputDirectory: File,
    val testRoundCount: Int = DEFAULT_TEST_ROUND_COUNT,
    val sampleIntervalMs: Long = DEFAULT_SAMPLE_INTERVAL_MS,
    val launchIntervalMs: Long = DEFAULT_LAUNCH_INTERVAL_MS,
    val collectDelayMs: Long = DEFAULT_COLLECT_DELAY_MS,
    val flashSwapDeviceSizeMb: Int = DEFAULT_FLASH_SWAP_DEVICE_SIZE_MB,
)
