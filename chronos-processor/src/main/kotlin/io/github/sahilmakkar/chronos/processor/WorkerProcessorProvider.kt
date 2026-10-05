package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class WorkerProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return WorkerProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}
