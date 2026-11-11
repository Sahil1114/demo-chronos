package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Kotlin Symbol Processing (KSP) provider that instantiates and initializes [WorkerProcessor].
 *
 * Registered as a service provider via standard Java `ServiceLoader` discovery in:
 * `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
 *
 * When KSP runs during Gradle compilation, this provider is instantiated by the compiler plugin to create
 * the [WorkerProcessor] instance that generates compile-time reflection-free worker registration code.
 *
 * @see WorkerProcessor
 */
public class WorkerProcessorProvider : SymbolProcessorProvider {

    /**
     * Creates a new [SymbolProcessor] instance wired with the provided [environment].
     *
     * @param environment KSP processing environment containing compiler code generator, options, and logger.
     * @return A configured [WorkerProcessor] ready to inspect `@RegisterWorker` annotations.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return WorkerProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}
