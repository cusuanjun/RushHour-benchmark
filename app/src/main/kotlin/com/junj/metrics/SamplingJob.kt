package com.junj.metrics

import com.junj.domain.ExperimentResults
import com.junj.output.logging.LogType
import com.junj.output.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SamplingJob(
    private val collector: MetricsCollector,
    private val results: ExperimentResults,
    private val logger: Logger,
    private val intervalMs: Long,
) {
    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        try {
            println("Starting sampling job")
            while (isActive) {
                val item = collector.collect(results.samples.size)
                results.samples += item
                logger.log(LogType.SAMPLE, item)
//                delay(intervalMs.milliseconds)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
