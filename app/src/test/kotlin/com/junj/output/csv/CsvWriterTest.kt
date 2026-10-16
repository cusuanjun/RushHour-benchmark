package com.junj.output.csv

import com.junj.domain.metrics.SamplingItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvWriterTest {
    @Test
    fun `sample csv header and every data row have the same column count`() {
        val file = File.createTempFile("sample", ".csv")
        try {
            saveSampleResultsToCsv(
                listOf(sample(stamp = 0, writtenBack = 0), sample(stamp = 2_000, writtenBack = 4)),
                file,
                2_000,
            )

            val lines = file.readLines().filter { it.isNotBlank() }
            assertEquals(3, lines.size, "expected one header line plus two data rows")

            val headerColumns = lines.first().split(",").size
            lines.drop(1).forEach { row ->
                assertEquals(headerColumns, row.split(",").size, "column count mismatch in row: $row")
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `zswap write back rate only counts pages written back from the pool`() {
        val file = File.createTempFile("sample", ".csv")
        try {
            // pool_limit_hit 说的是「被池拒绝、直接写进 swap 设备」的页，与回写无关，
            // 这里给它一个会变化的假值：旧实现把它一起算进速率，本用例会失败。
            saveSampleResultsToCsv(
                listOf(
                    sample(stamp = 0, writtenBack = 0, limitHit = 5),
                    sample(stamp = 2_000, writtenBack = 4, limitHit = 1_000),
                ),
                file,
                2_000,
            )

            val rows = file.readLines().filter { it.isNotBlank() }.drop(1)
            assertEquals("0", rows[0].substringAfterLast(","))
            // 4 页 x 4KB = 16KB，采样间隔 2s => 8 KB/s
            assertEquals("8", rows[1].substringAfterLast(","))
        } finally {
            file.delete()
        }
    }

    private fun sample(
        stamp: Long,
        writtenBack: Long = 0,
        limitHit: Long = 0,
    ) = SamplingItem(
        sampleStamp = stamp,
        meminfoMemTotal = 7_737_752,
        meminfoMemFree = 400_000,
        meminfoMemAvailable = 2_000_000,
        meminfoSwapTotal = 4_194_300,
        meminfoSwapFree = 1_000_000,
        meminfoSwapCached = 4_000,
        psiSome = 1_000_000,
        psiFull = 500_000,
        vmstatR = 1,
        vmstatB = 0,
        vmstatSwpd = 3_000_000,
        vmstatFree = 400_000,
        vmstatBuff = 1_000,
        vmstatCache = 2_000_000,
        vmstatSi = 0,
        vmstatSo = 0,
        vmstatBi = 0,
        vmstatBo = 0,
        vmstatIn = 1_000,
        vmstatCs = 2_000,
        vmstatUs = 30,
        vmstatSy = 40,
        vmstatId = 30,
        vmstatWa = 0,
        bigTemp = 55.0,
        midTemp = 56.0,
        littleTemp = 57.0,
        socThermTemp = 50.0,
        virtualSkinTemp = 42.0,
        zramOrigDataSize = 0,
        zramComprDataSize = 0,
        zramMemUsedTotal = 0,
        zramMemUsedMax = 0,
        zramBdCount = 0,
        zramBdReads = 0,
        zramBdWrites = 0,
        zswapStoredPages = 685_822,
        zswapPoolTotalSize = 1_376_096_256,
        zswapPoolLimitHit = limitHit,
        zswapWrittenBackPages = writtenBack,
    )
}
