package com.junj.output.csv

import com.junj.domain.ExperimentResults
import com.junj.domain.launch.LaunchApplicationItem
import com.junj.domain.metrics.SamplingItem
import com.junj.domain.survival.AppSurvivalResult
import com.junj.output.ExperimentOutputPaths
import java.io.File

fun saveCsv(results: ExperimentResults, output: ExperimentOutputPaths, sampleIntervalMs: Long) {
    saveLaunchResultsToCsv(
        results.launches,
        output.launchCsv,
    )
    saveSampleResultsToCsv(
        results.samples,
        output.sampleCsv,
        sampleIntervalMs,
    )
    saveSurvivalResultsToCsv(
        results.survivals,
        output.survivalCsv,
    )
}

fun saveSurvivalResultsToCsv(
    results: List<AppSurvivalResult>,
    outputFile: File,
) {
    outputFile.parentFile?.mkdirs()

    outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.appendLine(
            listOf(
                "round",
                "launchPosition",
                "appName",
                "packageName",
                "launchedPid",
                "pidAtRoundEnd",
                "originalProcessAlive",
                "appCurrentlyRunning",
            ).joinToString(",")
        )

        results.forEach { item ->
            writer.appendLine(
                listOf(
                    item.round,
                    item.launchPosition,
                    item.appName,
                    item.packageName,
                    item.launchedPid ?: "",
                    item.pidAtRoundEnd ?: "",
                    item.originalProcessAlive,
                    item.appCurrentlyRunning,
                ).joinToString(",") { value ->
                    escapeCsv(value.toString())
                }
            )
        }
    }
}

fun saveLaunchResultsToCsv(results: List<LaunchApplicationItem>, outputFile: File, ) {
    outputFile.parentFile?.mkdirs()

    outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.appendLine(
            listOf(
                "launchStamp(ms)",
                "amStartRound",
                "amStartAppName",
                "amStartLaunchState",
                "amStartTotalTime(ms)",
                "amStartWaitTime(ms)",
                "amStartStatus",
                "jankyFrames(%)",
                "p50RenderLat(ms)",
                "p90RenderLat(ms)",
                "p95RenderLat(ms)",
                "p99RenderLat(ms)",
                "totalPss(KB)",
            ).joinToString(",")
        )

        results.forEach { item ->
            writer.appendLine(
                listOf(
                    item.launchStamp,
                    item.amStartRound,
                    item.amStartAppName,
                    item.amStartLaunchState,
                    item.amStartTotalTime,
                    item.amStartWaitTime,
                    item.amStartStatus,
                    item.dumpsysGfxInfoJankyFrames,
                    item.dumpsysGfxInfoP50RenderLat,
                    item.dumpsysGfxInfoP90RenderLat,
                    item.dumpsysGfxInfoP95RenderLat,
                    item.dumpsysGfxInfoP99RenderLat,
                    item.dumpsysMemInfoTotalPss,
                ).joinToString(",") { value ->
                    escapeCsv(value.toString())
                }
            )
        }
    }
}

fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (
        value.contains(',') ||
        value.contains('"') ||
        value.contains('\n') ||
        value.contains('\r')
    ) {
        "\"$escaped\""
    } else {
        escaped
    }
}

fun saveSampleResultsToCsv(
    results: List<SamplingItem>,
    outputFile: File,
    sampleIntervalMs: Long,
) {
    require(sampleIntervalMs > 0) { "sampleIntervalMs must be positive" }
    outputFile.parentFile?.mkdirs()

    outputFile.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.appendLine(
            listOf(
                "sampleStamp(ms)",
                "memTotal(KB)",
                "memFree(KB)",
                "memAvailable(KB)",
                "swapTotal(KB)",
                "swapFree(KB)",
                "swapCached(KB)",
                "psiSome(us)",
                "psiFull(us)",
                "vmstatR/runnableProcesses",
                "vmstatB/blockedProcesses",
                "vmstatSwpd/swapUsed(KB)",
                "vmstatFree/freeMemory(KB)",
                "vmstatBuff/bufferMemory(KB)",
                "vmstatCache/cacheMemory(KB)",
                "vmstatSi/swapInBw(KB/s)",
                "vmstatSo/swapOutBw(KB/s)",
                "vmstatBi/blockInput(blocks/s)",
                "vmstatBo/blockOutput(blocks/s)",
                "vmstatIn/Interrupts(time/s)",
                "vmstatCs/contextSwitches(time/s)",
                "vmstatUs/userCpuTime(%)",
                "vmstatSy/kernelCpuTime(%)",
                "vmstatId/idleCpuTime(%)",
                "vmstatWa/waitIoCpuTime(%)",
                "bigTemp(°C)",
                "midTemp(°C)",
                "littleTemp(°C)",
                "socThermTemp(°C)",
                "virtualSkinTemp(°C)",
                "zramOrigDataSize(KB)",
                "zramComprDataSize(KB)",
                "zramMemUsedTotal(KB)",
                "zramMemUsedMax(KB)",
                "zramBdCount(KB)",
                "zramBdReads(KB)",
                "zramBdWrites(KB)",
                "zramBdReadBw(MB/s)",
                "zramBdWriteBw(MB/s)",
                "zswapStoredPages",
                "zswapStoredSize(KB)",
                "zswapPoolTotalSize(KB)",
                "zswapPoolLimitHit",
                "zswapWrittenBackPages",
                "zswapWrittenBackSize(KB)",
                "zswapWriteBw(MB/s)",
            ).joinToString(",")
        )

        results.forEachIndexed { index, item ->
            val previous = results.getOrNull(index - 1)
            val elapsedMs = previous?.let { item.sampleStamp - it.sampleStamp } ?: 0L
            writer.appendLine(
                listOf(
                    item.sampleStamp,
                    item.meminfoMemTotal,
                    item.meminfoMemFree,
                    item.meminfoMemAvailable,
                    item.meminfoSwapTotal,
                    item.meminfoSwapFree,
                    item.meminfoSwapCached,
                    item.psiSome,
                    item.psiFull,
                    item.vmstatR,
                    item.vmstatB,
                    item.vmstatSwpd,
                    item.vmstatFree,
                    item.vmstatBuff,
                    item.vmstatCache,
                    item.vmstatSi,
                    item.vmstatSo,
                    item.vmstatBi,
                    item.vmstatBo,
                    item.vmstatIn,
                    item.vmstatCs,
                    item.vmstatUs,
                    item.vmstatSy,
                    item.vmstatId,
                    item.vmstatWa,
                    item.bigTemp,
                    item.midTemp,
                    item.littleTemp,
                    item.socThermTemp,
                    item.virtualSkinTemp,
                    item.zramOrigDataSize,
                    item.zramComprDataSize,
                    item.zramMemUsedTotal,
                    item.zramMemUsedMax,
                    item.zramBdCount,
                    item.zramBdReads,
                    item.zramBdWrites,
                    counterRateMbPerSecond(item.zramBdReads, previous?.zramBdReads, elapsedMs, 1L),
                    counterRateMbPerSecond(item.zramBdWrites, previous?.zramBdWrites, elapsedMs, 1L),
                    item.zswapStoredPages,
                    item.zswapStoredSizeKb,
                    item.zswapPoolTotalSize,
                    item.zswapPoolLimitHit,
                    item.zswapWrittenBackPages,
                    item.zswapWrittenBackSizeKb,
                    // 池满拒绝计数不能算进写回吞吐。首样本无运行前基线，速率记 0。
                    if (previous != null && item.pageSizeKb == previous.pageSizeKb) {
                        counterRateMbPerSecond(item.zswapWrittenBackPages, previous.zswapWrittenBackPages, elapsedMs, item.pageSizeKb)
                    } else 0.0
                ).joinToString(",") { value->
                    escapeCsv(value.toString())
                }
            )
        }
    }
}

/** 累计计数 -> MB/s；使用实际采样时间，计数 reset/时间倒退时不产生负带宽。
 * MB 按 1024 KB 换算；浮点计算保留低于 1 MB/s 的流量，避免整数截断。
 */
internal fun counterRateMbPerSecond(current: Long, previous: Long?, elapsedMs: Long, kbPerUnit: Long): Double {
    if (previous == null || elapsedMs <= 0L || current < previous) return 0.0
    return (current - previous).toDouble() * kbPerUnit * 1000.0 / elapsedMs / 1024.0
}
