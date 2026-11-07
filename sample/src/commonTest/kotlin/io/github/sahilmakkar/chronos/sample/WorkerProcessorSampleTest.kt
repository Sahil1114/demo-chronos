package io.github.sahilmakkar.chronos.sample

import io.github.sahilmakkar.chronos.task.model.taskDataOf
import kotlin.test.Test
import kotlin.test.assertNotNull

class WorkerProcessorSampleTest {

    @Test
    fun testGeneratedWorkersContainsAllAnnotatedWorkersAcrossFiles() {
        val registry = createSampleRegistry()
        assertNotNull(registry.createWorker("io.github.sahilmakkar.chronos.sample.SampleSuccessWorker", taskDataOf()))
        assertNotNull(registry.createWorker("io.github.sahilmakkar.chronos.sample.SampleFailureWorker", taskDataOf()))
        assertNotNull(registry.createWorker("io.github.sahilmakkar.chronos.sample.SampleRetryWorker", taskDataOf()))
        assertNotNull(registry.createWorker("io.github.sahilmakkar.chronos.sample.SampleAdditionalWorker", taskDataOf()))
    }
}
