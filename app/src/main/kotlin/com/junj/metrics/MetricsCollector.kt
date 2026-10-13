package com.junj.metrics

import com.junj.device.adb.AdbExecutor
import com.junj.device.adb.ProcessAdbExecutor
import com.junj.domain.metrics.SamplingItem
import com.junj.metrics.parser.CommandType
import com.junj.metrics.parser.ResultField
import com.junj.metrics.parser.parseVmstat
import com.junj.metrics.parser.parseZramBdStatResult
import com.junj.metrics.parser.parseZramMmStatResult
import com.junj.metrics.parser.regexFindField

/** 分段标记行前缀：把一次 adb 调用的输出切成若干段。 */
internal const val SAMPLE_MARKER = "@@DSH@@"
internal const val SECTION_VMSTAT = "VMSTAT"
internal const val SECTION_MEMINFO = "MEMINFO"
internal const val SECTION_PRESSURE = "PRESSURE"
internal const val SECTION_THERMAL = "THERMAL"
internal const val SECTION_ZRAM_MM = "ZRAM_MM"
internal const val SECTION_ZRAM_BD = "ZRAM_BD"
internal const val SECTION_ZSWAP = "ZSWAP"

/**
 * 一整轮采样只发一次 adb 调用的脚本。
 *
 * runAdbRootShellCommand 是把脚本 pipe 给设备上的 `su -c sh`，sh 会从 stdin 读完整段脚本，
 * 所以多行命令只付一次 bash/adb.exe 进程启动与一次 ADB 往返的代价（原来是 10 次）。
 * 每段数据前打印一行 [SAMPLE_MARKER] 标记，取回后在宿主端切分，再交给原有解析器。
 *
 * 注意：脚本里不能出现会读取 stdin 的命令（例如无参数的 `cat`），否则会吃掉后面的脚本行。
 */
internal val SAMPLE_SCRIPT: String = listOf(
    "echo $SAMPLE_MARKER $SECTION_VMSTAT",
    "vmstat 1 2",
    "echo $SAMPLE_MARKER $SECTION_MEMINFO",
    "cat /proc/meminfo",
    "echo $SAMPLE_MARKER $SECTION_PRESSURE",
    "cat /proc/pressure/memory",
    "echo $SAMPLE_MARKER $SECTION_THERMAL",
    "dumpsys thermalservice",
    "echo $SAMPLE_MARKER $SECTION_ZRAM_MM",
    "cat /sys/block/zram0/mm_stat",
    "echo $SAMPLE_MARKER $SECTION_ZRAM_BD",
    "cat /sys/block/zram0/bd_stat",
    "echo $SAMPLE_MARKER $SECTION_ZSWAP",
    "echo \"stored_pages=\$(cat /sys/kernel/debug/zswap/stored_pages 2>/dev/null)\"",
    "echo \"pool_total_size=\$(cat /sys/kernel/debug/zswap/pool_total_size 2>/dev/null)\"",
    "echo \"pool_limit_hit=\$(cat /sys/kernel/debug/zswap/pool_limit_hit 2>/dev/null)\"",
    "echo \"written_back_pages=\$(cat /sys/kernel/debug/zswap/written_back_pages 2>/dev/null)\"",
).joinToString("\n")

class MetricsCollector(
    private val adb: AdbExecutor = ProcessAdbExecutor,
) {
    fun collect(sampleStamp: Long): SamplingItem =
        parseSamplingItem(adb.rootShell(SAMPLE_SCRIPT).output, sampleStamp)
}

/** 按标记行把整段输出切成 段名 -> 段内容；每段内容与原来单独执行该命令得到的输出一致。 */
internal fun splitSampleSections(rawOutput: String): Map<String, String> {
    val sections = LinkedHashMap<String, String>()
    val buffer = StringBuilder()
    var current: String? = null

    rawOutput.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith(SAMPLE_MARKER)) {
            current?.let { sections[it] = buffer.toString() }
            buffer.setLength(0)
            current = trimmed.removePrefix(SAMPLE_MARKER).trim()
        } else if (current != null) {
            buffer.append(line).append('\n')
        }
    }
    current?.let { sections[it] = buffer.toString() }

    return sections
}

internal fun parseSamplingItem(rawOutput: String, sampleStamp: Long): SamplingItem {
    val sections = splitSampleSections(rawOutput)

    val vmstat = parseVmstat(sections[SECTION_VMSTAT].orEmpty())
    val memInfo = sections[SECTION_MEMINFO].orEmpty()
    val pressureMemory = sections[SECTION_PRESSURE].orEmpty()
    val thermal = sections[SECTION_THERMAL].orEmpty()
    val zramMm = parseZramMmStatResult(sections[SECTION_ZRAM_MM].orEmpty())
    val zramBd = parseZramBdStatResult(sections[SECTION_ZRAM_BD].orEmpty())
    val zswap = sections[SECTION_ZSWAP].orEmpty()

    return SamplingItem(
        sampleStamp = sampleStamp,

        meminfoMemTotal = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_TOTAL, memInfo)?.toLongOrNull() ?: 0,
        meminfoMemFree = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_FREE, memInfo)?.toLongOrNull() ?: 0,
        meminfoMemAvailable = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_AVAILABLE, memInfo)?.toLongOrNull() ?: 0,
        meminfoSwapTotal = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_TOTAL, memInfo)?.toLongOrNull() ?: 0,
        meminfoSwapFree = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_FREE, memInfo)?.toLongOrNull() ?: 0,
        meminfoSwapCached = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_CACHED, memInfo)?.toLongOrNull() ?: 0,

        psiSome = regexFindField(CommandType.CAT_PROC_PRESSURE_MEMORY, ResultField.PSI_SOME_TOTAL, pressureMemory)?.toLongOrNull() ?: 0,
        psiFull = regexFindField(CommandType.CAT_PROC_PRESSURE_MEMORY, ResultField.PSI_FULL_TOTAL, pressureMemory)?.toLongOrNull() ?: 0,

        vmstatR = vmstat?.vmstatR ?: 0,
        vmstatB = vmstat?.vmstatB ?: 0,
        vmstatSwpd = vmstat?.vmstatSwpd ?: 0L,
        vmstatFree = vmstat?.vmstatFree ?: 0L,
        vmstatBuff = vmstat?.vmstatBuff ?: 0L,
        vmstatCache = vmstat?.vmstatCache ?: 0L,
        vmstatSi = vmstat?.vmstatSi ?: 0L,
        vmstatSo = vmstat?.vmstatSo ?: 0L,
        vmstatBi = vmstat?.vmstatBi ?: 0L,
        vmstatBo = vmstat?.vmstatBo ?: 0L,
        vmstatIn = vmstat?.vmstatIn ?: 0L,
        vmstatCs = vmstat?.vmstatCs ?: 0L,
        vmstatUs = vmstat?.vmstatUs ?: 0,
        vmstatSy = vmstat?.vmstatSy ?: 0,
        vmstatId = vmstat?.vmstatId ?: 0,
        vmstatWa = vmstat?.vmstatWa ?: 0,

        bigTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.BIG_TEMP, thermal)?.toDoubleOrNull() ?: 0.0,
        midTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.MID_TEMP, thermal)?.toDoubleOrNull() ?: 0.0,
        littleTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.LITTLE_TEMP, thermal)?.toDoubleOrNull() ?: 0.0,
        socThermTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.SOC_THERM_TEMP, thermal)?.toDoubleOrNull() ?: 0.0,
        virtualSkinTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.VIRTUAL_SKIN_TEMP, thermal)?.toDoubleOrNull() ?: 0.0,

        zramOrigDataSize = (zramMm?.origDataSize?.div(1024)) ?: 0,
        zramComprDataSize = (zramMm?.comprDataSize?.div(1024)) ?: 0,
        zramMemUsedTotal = (zramMm?.memUsedTotal?.div(1024)) ?: 0,
        zramMemUsedMax = (zramMm?.memUsedMax?.div(1024)) ?: 0,
        zramBdCount = (zramBd?.bdCount?.div(1024)) ?: 0,
        zramBdReads = (zramBd?.bdReads?.div(1024)) ?: 0,
        zramBdWrites = (zramBd?.bdWrites?.div(1024)) ?: 0,

        zswapStoredPages = labeledLong(zswap, "stored_pages"),
        zswapPoolTotalSize = labeledLong(zswap, "pool_total_size"),
        zswapPoolLimitHit = labeledLong(zswap, "pool_limit_hit"),
        zswapWrittenBackPages = labeledLong(zswap, "written_back_pages"),
    )
}

/** 解析 `key=value` 形式的段内容；文件不存在时 value 为空，回退为 0。 */
private fun labeledLong(section: String, key: String): Long =
    section.lineSequence()
        .firstOrNull { it.trim().startsWith("$key=") }
        ?.substringAfter('=')
        ?.trim()
        ?.toLongOrNull()
        ?: 0L
