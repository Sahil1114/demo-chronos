package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * KSP [SymbolProcessor] that discovers [@RegisterWorker][io.github.sahilmakkar.chronos.RegisterWorker]
 * annotations and generates compile-time reflection-free worker registration code.
 *
 * ### Motivation & Multiplatform Compatibility
 * Kotlin Multiplatform targets—especially iOS Darwin (Kotlin/Native)—do not support standard JVM runtime
 * reflection (`Class.forName()`, `newInstance()`). To enable seamless multiplatform background task execution,
 * Chronos uses this processor to inspect worker classes at compile time and generate a strongly-typed
 * extension function:
 * ```kotlin
 * package io.github.sahilmakkar.chronos
 *
 * import io.github.sahilmakkar.chronos.worker.WorkerRegistry
 *
 * fun WorkerRegistry.registerGeneratedWorkers() {
 *     register("com.example.ImageUploadWorker") { ImageUploadWorker(it) }
 * }
 * ```
 *
 * ### Custom Factory Handling
 * If a worker specifies a custom factory via `@RegisterWorker(factory = CustomWorkerFactory::class)`,
 * the processor emits a comment indicating that the worker requires manual registration via dependency
 * injection (e.g. Koin, Dagger, or manual registry wiring).
 *
 * @param codeGenerator KSP code generator used to create the generated Kotlin source file.
 * @param logger KSP logger for reporting build warnings, errors, and informational diagnostics.
 */
internal class WorkerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    /**
     * Inspects the compilation symbols for `@RegisterWorker` annotations and emits `GeneratedWorkers.kt`.
     *
     * Processes declarations:
     * 1. Filters all symbols annotated with `io.github.sahilmakkar.chronos.RegisterWorker`.
     * 2. Resolves each class's fully qualified name.
     * 3. Checks if a custom factory type other than `Any` was supplied in the annotation argument.
     * 4. Generates `io.github.sahilmakkar.chronos.GeneratedWorkers.kt` with the `registerGeneratedWorkers()` extension.
     *
     * @param resolver KSP symbol resolver providing access to compiler symbol tables and annotations.
     * @return Empty list since all discovered symbols are processed in a single pass without deferred symbols.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("io.github.sahilmakkar.chronos.RegisterWorker")
        val workers = symbols.filterIsInstance<KSClassDeclaration>().toList()

        if (workers.isEmpty()) return emptyList()

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = false,
                sources = workers.mapNotNull { it.containingFile }.toTypedArray()
            ),
            packageName = "io.github.sahilmakkar.chronos",
            fileName = "GeneratedWorkers"
        )

        file.writer().use { writer ->
            writer.write("package io.github.sahilmakkar.chronos\n\n")
            writer.write("import io.github.sahilmakkar.chronos.worker.WorkerRegistry\n\n")
            writer.write("fun WorkerRegistry.registerGeneratedWorkers() {\n")
            for (worker in workers) {
                val qualifiedName = worker.qualifiedName?.asString()
                if (qualifiedName == null) {
                    logger.warn("Skipping @RegisterWorker class with no qualified name", worker)
                    continue
                }
                val annotation = worker.annotations.firstOrNull {
                    it.shortName.asString() == "RegisterWorker"
                }

                var hasCustomFactory = false
                val factoryArg = annotation?.arguments?.firstOrNull { it.name?.asString() == "factory" }
                val factoryValue = factoryArg?.value
                if (factoryValue is KSType) {
                    val typeName = factoryValue.declaration.qualifiedName?.asString()
                    if (typeName != null && typeName != "kotlin.Any") {
                        hasCustomFactory = true
                    }
                }

                if (hasCustomFactory) {
                    logger.info("$qualifiedName uses a custom factory, register manually")
                    writer.write("    // $qualifiedName uses a custom factory, register manually\n")
                } else {
                    writer.write("    register(\"$qualifiedName\") { $qualifiedName(it) }\n")
                }
            }
            writer.write("}\n")
        }

        logger.info("Generated registrations for ${workers.size} worker(s) -> GeneratedWorkers.kt")

        return emptyList()
    }
}