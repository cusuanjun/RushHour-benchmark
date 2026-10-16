package com.junj.output.reporting

import com.junj.config.ExperimentConfig
import com.junj.domain.launch.LaunchApplicationItem
import com.junj.domain.metrics.SamplingItem
import com.junj.domain.survival.AppSurvivalResult
import com.junj.metrics.parser.CommandType
import com.junj.metrics.parser.ResultField
import com.junj.metrics.parser.regexFindField
import com.junj.output.logging.LogType
import com.junj.output.logging.Logger
import java.io.File
import java.util.Locale
import kotlin.math.ceil

/**
 * summary.txt 的结构版本号。
 *
 * v2 起改为「一行一个 key=value、单位写在 key 里、空行分节」的结构：
 * - 值里不再出现单位后缀，可直接解析/绘图；同一物理量在整个文件里单位一致
 * - 比率一律带分母，例如 `50/70(71.43%)`
 * - 所有浮点格式化显式使用 [Locale.ROOT]，避免逗号小数点（如 de_DE 的 71,43）破坏“逗号即分隔符”的解析习惯
 * 新增/改名 key 或改单位时递增此版本号，下游可据此选择解析方式。
 */
const val SUMMARY_SCHEMA_VERSION = 2

// ---------------------------------------------------------------------------
// 数据结构：按 summary.txt 的分节分组，类之间顺序 = 文件里分节的顺序。
// 只保存原始计数/数值，比率、百分比、单位换算都放在渲染阶段做，避免同一事实存两份。
// ---------------------------------------------------------------------------

/** `[result]` 节。 */
data class RunStats(
    val durationMs: Long,
    val sampleCount: Int,
)

/** `[launch]` 节里 `launch.*` 部分。 */
data class LaunchStats(
    val total: Int,
    val valid: Int,
    val failed: Int,
    val cold: Int,
    val warm: Int,
    val hot: Int,
    val unknown: Int,
    /** 以 UNKNOWN 开头的状态原文，用 `|` 连接；没有时为空串。 */
    val unknownRawStates: String,
    /** 既非 COLD/WARM/HOT 也不以 UNKNOWN 开头的状态原文，用 `|` 连接；正常时为空串。 */
    val otherStates: String,
)

/** `[launch]` 节里 `latency.*` 部分，单位统一为毫秒。 */
data class LatencyStats(
    /** 旧口径：TotalTime 总和 / (测试轮数 × 应用数)；失败启动以 0 计入分子。为与历史 summary 可比而保留。 */
    val plannedAvgMs: Long,
    /** 只统计 status=ok 且 TotalTime 可解析的启动。 */
    val validCount: Int,
    val validAvgMs: Long,
    val p50Ms: Long,
    val p95Ms: Long,
    val maxMs: Long,
    val coldAvgMs: Long,
    val warmAvgMs: Long,
    val hotAvgMs: Long,
)

/** `[survival]` 节；比率由计数在渲染时算出。 */
data class SurvivalStats(
    val total: Int,
    val originalAlive: Int,
    val currentlyRunning: Int,
)

/** `[memory]` 节。 */
data class MemoryPressureStats(
    val lmkdKills: Int,
    val psiSomeDeltaMs: Long,
    val psiFullDeltaMs: Long,
)

/** `[swap]` 节；延迟沿用内核模块上报的纳秒，渲染时换算为微秒。 */
data class SwapInStats(
    val totalSwapIns: Long,
    val sampledSwapIns: Long,
    val avgLatNs: Long,
    val p90LatNs: Long,
    val p99LatNs: Long,
    val maxLatNs: Long,
)

/** 本次运行启用了哪种内存压缩（由 config.type 决定）。 */
enum class CompressionEngine {
    /** 未启用任何压缩（NO_SWAP / ONLY_FLASH_SWAP）。 */
    NONE,
    ZRAM,
    ZSWAP,
}

/**
 * `[compression]` 节。
 *
 * 取自压缩率最有代表性的那一拍（压缩内容最多的样本），避免 t≈0 的空池和运行末尾池被释放导致读数失真。
 * 池里从未有数据时 [ratio] 为 null，渲染成 `-`（否则会输出一个看起来很像结论的 0）。
 */
data class CompressionStats(
    val engine: CompressionEngine,
    val ratio: Double?,
    val uncompressedMiB: Long,
    val compressedMiB: Long,
)

/** 温度轨迹，单位 °C：起跑（首样本）/ 结束（末样本）/ 全程峰值。 */
data class ThermalTrajectory(
    val startC: Double,
    val endC: Double,
    val maxC: Double,
)

/** `[thermal]` 节。 */
data class ThermalStats(
    val bigAvgC: Double,
    val midAvgC: Double,
    val littleAvgC: Double,
    val socAvgC: Double,
    val skinAvgC: Double,
    val soc: ThermalTrajectory,
    val skin: ThermalTrajectory,
)

/** 一次运行的完整结果；字段顺序即 summary.txt 的分节顺序。 */
data class ExperimentSummary(
    val run: RunStats,
    val launch: LaunchStats,
    val latency: LatencyStats,
    val survival: SurvivalStats,
    val memory: MemoryPressureStats,
    val swap: SwapInStats,
    val compression: CompressionStats,
    val thermal: ThermalStats,
)

/**
 * 纯计算：不读写文件、不访问设备（swap-in 统计由调用方读好后以文本传入）。
 *
 * 参数按三组排列：**数据源 → 实验定义 → 本次运行的标量输入**。
 */
fun calculateSummary(
    // 数据源
    launches: List<LaunchApplicationItem>,
    samples: List<SamplingItem>,
    survivals: List<AppSurvivalResult>,
    // 实验定义
    appCount: Int,
    testRoundCount: Int,
    compressionEngine: CompressionEngine,
    // 本次运行的标量输入
    runtimeMs: Long,
    lmkdCount: Int,
    swapInLatStats: String,
): ExperimentSummary {
    val unknownRawStates = launches.map { it.amStartLaunchState }
        .filter { it.startsWith("UNKNOWN") }
        .distinct()
        .sorted()
        .joinToString("|")
    val otherStates = launches.map { it.amStartLaunchState }
        .filter { it.isNotEmpty() && it != "COLD" && it != "WARM" && it != "HOT" && !it.startsWith("UNKNOWN") }
        .distinct()
        .sorted()
        .joinToString("|")

    val validLaunches = launches.filter { it.amStartStatus == "ok" && it.amStartTotalTime > 0 }
    val validTimes = validLaunches.map { it.amStartTotalTime }.sorted()
    fun stateAverage(state: String): Long {
        val times = validLaunches.filter { it.amStartLaunchState == state }.map { it.amStartTotalTime }
        return if (times.isEmpty()) 0L else times.sum() / times.size
    }

    val sampleCount = samples.size.coerceAtLeast(1)
    val firstSample = samples.firstOrNull()
    val lastSample = samples.lastOrNull()

    return ExperimentSummary(
        run = RunStats(
            durationMs = runtimeMs,
            sampleCount = samples.size,
        ),
        launch = LaunchStats(
            total = launches.size,
            valid = validLaunches.size,
            failed = launches.count { it.amStartStatus != "ok" },
            cold = launches.count { it.amStartLaunchState == "COLD" },
            warm = launches.count { it.amStartLaunchState == "WARM" },
            hot = launches.count { it.amStartLaunchState == "HOT" },
            unknown = launches.count { it.amStartLaunchState.startsWith("UNKNOWN") },
            unknownRawStates = unknownRawStates,
            otherStates = otherStates,
        ),
        latency = LatencyStats(
            plannedAvgMs = launches.sumOf { it.amStartTotalTime } / (testRoundCount * appCount).coerceAtLeast(1),
            validCount = validLaunches.size,
            validAvgMs = if (validTimes.isEmpty()) 0L else validTimes.sum() / validTimes.size,
            p50Ms = percentile(validTimes, 50),
            p95Ms = percentile(validTimes, 95),
            maxMs = validTimes.lastOrNull() ?: 0L,
            coldAvgMs = stateAverage("COLD"),
            warmAvgMs = stateAverage("WARM"),
            hotAvgMs = stateAverage("HOT"),
        ),
        survival = SurvivalStats(
            total = survivals.size,
            originalAlive = survivals.count { it.originalProcessAlive },
            currentlyRunning = survivals.count { it.appCurrentlyRunning },
        ),
        memory = MemoryPressureStats(
            lmkdKills = lmkdCount,
            psiSomeDeltaMs = ((lastSample?.psiSome ?: 0L) - (firstSample?.psiSome ?: 0L)) / 1000,
            psiFullDeltaMs = ((lastSample?.psiFull ?: 0L) - (firstSample?.psiFull ?: 0L)) / 1000,
        ),
        swap = SwapInStats(
            totalSwapIns = swapInField(swapInLatStats, ResultField.TOTAL_SWAP_INS),
            sampledSwapIns = swapInField(swapInLatStats, ResultField.SAMPLED_SWAP_INS),
            avgLatNs = swapInField(swapInLatStats, ResultField.AVG_SWAP_IN_LAT),
            p90LatNs = swapInField(swapInLatStats, ResultField.P90_SWAP_IN_LAT),
            p99LatNs = swapInField(swapInLatStats, ResultField.P99_SWAP_IN_LAT),
            maxLatNs = swapInField(swapInLatStats, ResultField.MAX_SWAP_IN_LAT),
        ),
        compression = compressionStats(compressionEngine, samples),
        thermal = ThermalStats(
            bigAvgC = samples.sumOf { it.bigTemp } / sampleCount,
            midAvgC = samples.sumOf { it.midTemp } / sampleCount,
            littleAvgC = samples.sumOf { it.littleTemp } / sampleCount,
            socAvgC = samples.sumOf { it.socThermTemp } / sampleCount,
            skinAvgC = samples.sumOf { it.virtualSkinTemp } / sampleCount,
            soc = ThermalTrajectory(
                startC = firstSample?.socThermTemp ?: 0.0,
                endC = lastSample?.socThermTemp ?: 0.0,
                maxC = samples.maxOfOrNull { it.socThermTemp } ?: 0.0,
            ),
            skin = ThermalTrajectory(
                startC = firstSample?.virtualSkinTemp ?: 0.0,
                endC = lastSample?.virtualSkinTemp ?: 0.0,
                maxC = samples.maxOfOrNull { it.virtualSkinTemp } ?: 0.0,
            ),
        ),
    )
}

/**
 * 结果入口：算好 summary、写入 summary.txt，并把 summary 返回给调用方
 * （后续要落 summary.csv / runs.csv 时直接用返回值，不必再解析文本）。
 *
 * 纯函数 [calculateSummary] 与 [renderSummary] 保持可单独单测，这里只做组合。
 */
fun reportSummary(
    logger: Logger,
    launches: List<LaunchApplicationItem>,
    samples: List<SamplingItem>,
    survivals: List<AppSurvivalResult>,
    appCount: Int,
    testRoundCount: Int,
    compressionEngine: CompressionEngine,
    runtimeMs: Long,
    lmkdCount: Int,
    swapInLatStats: String,
): ExperimentSummary {
    val summary = calculateSummary(
        launches = launches,
        samples = samples,
        survivals = survivals,
        appCount = appCount,
        testRoundCount = testRoundCount,
        compressionEngine = compressionEngine,
        runtimeMs = runtimeMs,
        lmkdCount = lmkdCount,
        swapInLatStats = swapInLatStats,
    )
    logger.log(LogType.SUMMARY, renderSummary(summary))
    return summary
}

/**
 * 头部入口：写入 summary.txt 的 `# schema` + `[run]` + `[config]` 三段。
 *
 * 刻意在测试开始**之前**调用：即使后面中途崩溃或设备掉线，文件里也留着"这轮跑的是什么条件"。
 *
 * 与 [reportSummary] 一样只接收纯数据（[ExperimentConfig] + 应用名 + 输出目录），不掺进 CLI 解析流程——
 * `parseArgs` 保持为无副作用的解析函数（它被多个单测直接调用，不该因此写文件）。
 */
fun logRunHeader(
    logger: Logger,
    config: ExperimentConfig,
    appNames: List<String>,
    outputDirectory: File,
) {
    logger.log(
        LogType.SUMMARY,
        buildString {
            appendLine("# schema=$SUMMARY_SCHEMA_VERSION")
            appendLine("[run]")
            appendLine("run.id=${config.timestamp}")
            appendLine("run.logsPath=${outputDirectory.path}")
            appendLine("[config]")
            appendLine("config.seed=${config.randomSeed}")
            appendLine("config.appSet=${config.appSetNumber}")
            appendLine("config.appOrder=${appNames.joinToString(",")}")
            appendLine("config.swapType=${config.swapType}")
            appendLine("config.flashSwapMiB=${config.flashSwapDeviceSizeMB}")
            appendLine("config.testRounds=${config.testRoundCount}")
            appendLine("config.sampleIntervalMs=${config.sampleIntervalMs}")
            appendLine("config.launchIntervalMs=${config.launchIntervalMs}")
            appendLine("config.collectDelayMs=${config.collectDelayMs}")
        }.trimEnd(),
    )
}

/**
 * 把 summary 渲染成 summary.txt 的正文（纯函数，便于单测）。
 *
 * 不变量：非空行要么是 `[节名]`，要么是 `key=value`；key 不重复；值里不含逗号与换行。
 */
fun renderSummary(summary: ExperimentSummary): String = buildString {
    fun put(key: String, value: Any?) = appendLine("$key=$value")
    fun section(name: String, body: () -> Unit) {
        appendLine()
        appendLine("[$name]")
        body()
    }

    section("result") {
        put("run.durationMs", summary.run.durationMs)
        put("run.sampleCount", summary.run.sampleCount)
    }

    section("launch") {
        with(summary.launch) {
            put("launch.total", total)
            put("launch.valid", valid)
            put("launch.failed", failed)
            // 计数后面直接带本轮占比，分母就是上面那行 launch.total
            put("launch.cold", countWithShare(cold, total))
            put("launch.warm", countWithShare(warm, total))
            put("launch.hot", countWithShare(hot, total))
            put("launch.unknown", countWithShare(unknown, total))
            put("launch.unknownRaw", unknownRawStates.ifEmpty { "-" })
            put("launch.otherStates", otherStates.ifEmpty { "-" })
        }
        with(summary.latency) {
            put("latency.avgMs", plannedAvgMs)
            put("latency.avgValidMs", validAvgMs)
            put("latency.p50Ms", p50Ms)
            put("latency.p95Ms", p95Ms)
            put("latency.maxMs", maxMs)
            put("latency.coldAvgMs", coldAvgMs)
            put("latency.warmAvgMs", warmAvgMs)
            put("latency.hotAvgMs", hotAvgMs)
        }
    }

    section("survival") {
        with(summary.survival) {
            put("survival.originalAlive", ratio(originalAlive, total))
            put("survival.currentlyRunning", ratio(currentlyRunning, total))
        }
    }

    section("memory") {
        with(summary.memory) {
            put("mem.lmkdKills", lmkdKills)
            put("mem.lmkdKillsPerMin", fixed2(if (summary.run.durationMs <= 0L) 0.0 else lmkdKills * 60_000.0 / summary.run.durationMs))
            put("mem.psiSomeMs", psiSomeDeltaMs)
            put("mem.psiSomePctOfRuntime", fixed2(percentOfRuntime(psiSomeDeltaMs, summary.run.durationMs)))
            put("mem.psiFullMs", psiFullDeltaMs)
            put("mem.psiFullPctOfRuntime", fixed2(percentOfRuntime(psiFullDeltaMs, summary.run.durationMs)))
        }
    }

    section("swap") {
        with(summary.swap) {
            put("swap.totalSwapIns", totalSwapIns)
            put("swap.sampledSwapIns", sampledSwapIns)
            // 内核模块上报 ns，这里统一换算成 µs（÷1000）
            put("swap.avgInLatUs", avgLatNs / 1000)
            put("swap.p90InLatUs", p90LatNs / 1000)
            put("swap.p99InLatUs", p99LatNs / 1000)
            put("swap.maxInLatUs", maxLatNs / 1000)
        }
    }

    section("compression") {
        with(summary.compression) {
            put("compr.engine", engine.name.lowercase(Locale.ROOT))
            // 压缩后 / 未压缩；池里没有数据时为 "-"，避免把"没测到"读成"压缩率 0"
            put("compr.ratio", ratio?.let { fixed4(it) } ?: "-")
            put("compr.uncompressedMiB", uncompressedMiB)
            put("compr.compressedMiB", compressedMiB)
        }
    }

    section("thermal") {
        with(summary.thermal) {
            put("thermal.bigAvgC", fixed2(bigAvgC))
            put("thermal.midAvgC", fixed2(midAvgC))
            put("thermal.littleAvgC", fixed2(littleAvgC))
            put("thermal.socAvgC", fixed2(socAvgC))
            put("thermal.skinAvgC", fixed2(skinAvgC))
            put("thermal.socStartC", fixed2(soc.startC))
            put("thermal.socEndC", fixed2(soc.endC))
            put("thermal.socMaxC", fixed2(soc.maxC))
            put("thermal.skinStartC", fixed2(skin.startC))
            put("thermal.skinEndC", fixed2(skin.endC))
        }
    }
}.trimEnd()

/**
 * 取「压缩内容最多」的那一拍计算压缩率：
 * - zRAM 看 `/sys/block/zram0/mm_stat` 的 orig/compr_data_size（KB）
 * - zswap 看 `stored_pages × 4KB` 与 `pool_total_size`（B）
 * 未启用压缩、没有样本、或池里从未有数据时，比率留空。
 */
internal fun compressionStats(engine: CompressionEngine, samples: List<SamplingItem>): CompressionStats {
    if (engine == CompressionEngine.NONE || samples.isEmpty()) {
        return CompressionStats(engine, ratio = null, uncompressedMiB = 0, compressedMiB = 0)
    }

    val peak = samples.maxByOrNull { uncompressedKb(engine, it) }!!
    val uncompressedKb = uncompressedKb(engine, peak)
    val compressedKb = compressedKb(engine, peak)

    return CompressionStats(
        engine = engine,
        ratio = if (uncompressedKb <= 0L) null else compressedKb.toDouble() / uncompressedKb,
        uncompressedMiB = uncompressedKb / 1024,
        compressedMiB = compressedKb / 1024,
    )
}

/** 压缩前的数据量，单位 KB。 */
private fun uncompressedKb(engine: CompressionEngine, sample: SamplingItem): Long = when (engine) {
    CompressionEngine.NONE -> 0L
    CompressionEngine.ZRAM -> sample.zramOrigDataSize
    // stored_pages 是页数，1 页 = 4KB
    CompressionEngine.ZSWAP -> sample.zswapStoredPages * 4L
}

/** 压缩后的数据量，单位 KB。 */
private fun compressedKb(engine: CompressionEngine, sample: SamplingItem): Long = when (engine) {
    CompressionEngine.NONE -> 0L
    CompressionEngine.ZRAM -> sample.zramComprDataSize
    // pool_total_size 的单位是字节
    CompressionEngine.ZSWAP -> sample.zswapPoolTotalSize / 1024
}

private fun swapInField(stats: String, field: ResultField): Long =
    regexFindField(CommandType.SWAP_IN_STATS, field, stats)?.toLongOrNull() ?: 0L

/** 最近秩（nearest-rank）百分位；升序输入，空输入返回 0。 */
private fun percentile(ascending: List<Long>, percent: Int): Long {
    if (ascending.isEmpty()) return 0L
    val rank = ceil(percent / 100.0 * ascending.size).toInt().coerceIn(1, ascending.size)
    return ascending[rank - 1]
}

private fun fixed2(value: Double): String = "%.2f".format(Locale.ROOT, value)

private fun fixed4(value: Double): String = "%.4f".format(Locale.ROOT, value)

private fun percentOfRuntime(value: Long, runtimeMs: Long): Double =
    if (runtimeMs <= 0L) 0.0 else value * 100.0 / runtimeMs

private fun percent(count: Int, total: Int): String =
    fixed2(if (total <= 0) 0.0 else count * 100.0 / total) + "%"

/** `50/70(71.43%)`：带分母的比率，用于存活率这种分母不显而易见的地方。 */
private fun ratio(count: Int, total: Int): String = "$count/$total(${percent(count, total)})"

/** `27(38.03%)`：紧跟在计数后面的占比，分母取同一节里的 total。 */
private fun countWithShare(count: Int, total: Int): String = "$count(${percent(count, total)})"
