package com.junj.domain.metrics

data class SamplingItem(
    // Milliseconds elapsed since the start of the test run.
    val sampleStamp: Long,

    val meminfoMemTotal: Long,
    val meminfoMemFree: Long,
    val meminfoMemAvailable: Long,
    val meminfoSwapTotal: Long,
    val meminfoSwapFree: Long,
    val meminfoSwapCached: Long,

    val psiSome: Long,
    val psiFull: Long,

    val vmstatR: Int,
    val vmstatB: Int,
    val vmstatSwpd: Long,
    val vmstatFree: Long,
    val vmstatBuff: Long,
    val vmstatCache: Long,
    // 已从 vmstat 的 KB/s 换算成 MB/s。
    val vmstatSi: Double,
    val vmstatSo: Double,
    val vmstatBi: Double,
    val vmstatBo: Double,
    // /proc/vmstat 累计换入/换出页数（pswpin/pswpout），做差得到本轮读写次数。
    val vmstatPswpin: Long = 0L,
    val vmstatPswpout: Long = 0L,
    /** 该拍是否真的采到了 pswpin/pswpout（老数据的默认 0 不能当成"真的 0"）。 */
    val vmstatPswpSampled: Boolean = false,
    val vmstatIn: Long,
    val vmstatCs: Long,
    val vmstatUs: Int,
    val vmstatSy: Int,
    val vmstatId: Int,
    val vmstatWa: Int,

    val bigTemp: Double,
    val midTemp: Double,
    val littleTemp: Double,
    val socThermTemp: Double,
    val virtualSkinTemp: Double,

    val zramOrigDataSize: Long,
    val zramComprDataSize: Long,
    val zramMemUsedTotal: Long,
    val zramMemUsedMax: Long,

    // zram bd_stat 原始 4 KB 计数：Count 当前占用，Reads/Writes 累计流量。
    val zramBdCount: Long,
    val zramBdReads: Long,
    val zramBdWrites: Long,

    val zswapStoredPages: Long,
    // KB，采集时从 pool_total_size 的字节数转换。
    val zswapPoolTotalSize: Long,
    val zswapPoolLimitHit: Long,
    val zswapWrittenBackPages: Long,
    // zswap 的页大小依赖内核；与 bd_stat 固定的 4 KB 单位不同。
    val pageSizeKb: Long = 4L,
    // 映射设备的累计读写量，单位 KB（1024 字节）；缺样为 null。
    val flashDeviceReadKb: Double? = null,
    val flashDeviceWriteKb: Double? = null,
) {
    val zswapStoredSizeKb: Long get() = zswapStoredPages * pageSizeKb
    val zswapWrittenBackSizeKb: Long get() = zswapWrittenBackPages * pageSizeKb
}

internal fun flashKbDelta(current: Double?, previous: Double?): Double =
    if (current == null || previous == null) 0.0 else (current - previous).coerceAtLeast(0.0)
