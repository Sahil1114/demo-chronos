package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Provider class that instantiates [WorkerProcessor] for the Kotlin Symbol Processing (KSP) environment.
 * Registered as a service via META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider.
 */
class WorkerProcessorProvider : SymbolProcessorProvider {
    /**
     * Factory method to construct the [WorkerProcessor] with the given KSP environment parameters.
     *
     * @param environment Configuration and utility tools (like codeGenerator and logger) provided by the KSP engine.
     * @return A new instance of [WorkerProcessor].
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return WorkerProcessor(environment.codeGenerator, environment.logger)
    }
}
