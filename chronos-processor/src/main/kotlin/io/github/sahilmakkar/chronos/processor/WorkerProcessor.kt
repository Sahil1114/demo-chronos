package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * A Kotlin Symbol Processor (KSP) that scans for classes annotated with
 * `io.github.sahilmakkar.chronos.RegisterWorker`.
 *
 * For each discovered worker class that does not declare a custom factory,
 * it generates code to automatically register the worker constructor in `KmpWorkerRegistry`.
 * This avoids the need for manual boilerplate registry calls for default workers.
 *
 * @property codeGenerator The KSP code generator used to create the registration source file.
 * @property logger The KSP logger for outputting warnings or error messages during processing.
 */
class WorkerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    /**
     * Processes the compiler symbols in the current round to find worker declarations annotated with `RegisterWorker`.
     * Generates a Kotlin source file (`GeneratedWorkers.kt`) containing an extension function
     * `KmpWorkerRegistry.registerGeneratedWorkers()` to register all discovered workers.
     *
     * @param resolver The resolver used to query symbols in the current compilation round.
     * @return A list of symbols that need to be deferred to the next round (always empty for this processor).
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("io.github.sahilmakkar.chronos.RegisterWorker")
        val workers = symbols.filterIsInstance<KSClassDeclaration>().toList()

        if (workers.isEmpty()) return emptyList()

        val file = codeGenerator.createNewFile(
            Dependencies(false, *workers.mapNotNull { it.containingFile }.toTypedArray()),
            "io.github.sahilmakkar.chronos",
            "GeneratedWorkers"
        )

        file.writer().use { writer ->
            writer.write("package io.github.sahilmakkar.chronos\n\n")
            writer.write("fun KmpWorkerRegistry.registerGeneratedWorkers() {\n")
            for (worker in workers) {
                val qualifiedName = worker.qualifiedName?.asString() ?: continue
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
                    writer.write("    // $qualifiedName uses a custom factory, register manually\n")
                } else {
                    writer.write("    register(\"$qualifiedName\") { $qualifiedName(it) }\n")
                }
            }
            writer.write("}\n")
        }

        return emptyList()
    }
}
