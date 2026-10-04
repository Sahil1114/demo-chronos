package io.github.sahilmakkar.chronos.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

class WorkerProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {


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