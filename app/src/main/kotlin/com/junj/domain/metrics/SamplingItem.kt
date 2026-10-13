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
    val vmstatSi: Long,
    val vmstatSo: Long,
    val vmstatBi: Long,
    val vmstatBo: Long,
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

    val zramBdCount: Long,
    val zramBdReads: Long,
    val zramBdWrites: Long,

    val zswapStoredPages: Long,
    val zswapPoolTotalSize: Long,
    val zswapPoolLimitHit: Long,
    val zswapWrittenBackPages: Long,
)
