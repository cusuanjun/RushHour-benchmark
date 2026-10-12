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

class MetricsCollector(
    private val adb: AdbExecutor = ProcessAdbExecutor,
) {
    fun collect(sampleStamp: Int): SamplingItem {
        val vmstat = parseVmstat(adb.rootShell("vmstat 1 2").output)
        val memInfo = adb.shell("cat /proc/meminfo")
        val pressureMemory = adb.rootShell("cat /proc/pressure/memory")
        val thermal = adb.rootShell("dumpsys thermalservice")
        val zramMm = parseZramMmStatResult(adb.rootShell("cat /sys/block/zram0/mm_stat").output)
        val zramBd = parseZramBdStatResult(adb.rootShell("cat /sys/block/zram0/bd_stat").output)

        return SamplingItem(
            sampleStamp = sampleStamp,

            meminfoMemTotal = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_TOTAL, memInfo.output)?.toLongOrNull() ?: 0,
            meminfoMemFree = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_FREE, memInfo.output)?.toLongOrNull() ?: 0,
            meminfoMemAvailable = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.MEM_AVAILABLE, memInfo.output)?.toLongOrNull() ?: 0,
            meminfoSwapTotal = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_TOTAL, memInfo.output)?.toLongOrNull() ?: 0,
            meminfoSwapFree = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_FREE, memInfo.output)?.toLongOrNull() ?: 0,
            meminfoSwapCached = regexFindField(CommandType.CAT_PROC_MEMINFO, ResultField.SWAP_CACHED, memInfo.output)?.toLongOrNull() ?: 0,

            psiSome = regexFindField(CommandType.CAT_PROC_PRESSURE_MEMORY, ResultField.PSI_SOME_TOTAL, pressureMemory.output)?.toLongOrNull() ?: 0,
            psiFull = regexFindField(CommandType.CAT_PROC_PRESSURE_MEMORY, ResultField.PSI_FULL_TOTAL, pressureMemory.output)?.toLongOrNull() ?: 0,

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

            bigTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.BIG_TEMP, thermal.output)?.toDoubleOrNull() ?: 0.0,
            midTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.MID_TEMP, thermal.output)?.toDoubleOrNull() ?: 0.0,
            littleTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.LITTLE_TEMP, thermal.output)?.toDoubleOrNull() ?: 0.0,
            socThermTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.SOC_THERM_TEMP, thermal.output)?.toDoubleOrNull() ?: 0.0,
            virtualSkinTemp = regexFindField(CommandType.DUMPSYS_THERMALSERVICE, ResultField.VIRTUAL_SKIN_TEMP, thermal.output)?.toDoubleOrNull() ?: 0.0,

            zramOrigDataSize = (zramMm?.origDataSize?.div(1024)) ?: 0,
            zramComprDataSize = (zramMm?.comprDataSize?.div(1024)) ?: 0,
            zramMemUsedTotal = (zramMm?.memUsedTotal?.div(1024)) ?: 0,
            zramMemUsedMax = (zramMm?.memUsedMax?.div(1024)) ?: 0,
            zramBdCount = (zramBd?.bdCount?.div(1024)) ?: 0,
            zramBdReads = (zramBd?.bdReads?.div(1024)) ?: 0,
            zramBdWrites = (zramBd?.bdWrites?.div(1024)) ?: 0,

            zswapStoredPages = readRootLong("cat /sys/kernel/debug/zswap/stored_pages"),
            zswapPoolTotalSize = readRootLong("cat /sys/kernel/debug/zswap/pool_total_size"),
            zswapPoolLimitHit = readRootLong("cat /sys/kernel/debug/zswap/pool_limit_hit"),
            zswapWrittenBackPages = readRootLong("cat /sys/kernel/debug/zswap/written_back_pages"),
        )
    }

    private fun readRootLong(command: String): Long =
        adb.rootShell(command).output.trim().toLongOrNull() ?: 0L
}
