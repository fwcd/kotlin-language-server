package org.javacs.kt

import com.google.gson.*
import org.eclipse.lsp4j.InitializeParams
import org.javacs.kt.storage.Storage
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

public data class SnippetsConfiguration(
    /** Whether code completion should return VSCode-style snippets. */
    var enabled: Boolean = true
)

public data class CompletionConfiguration(
    val snippets: SnippetsConfiguration = SnippetsConfiguration()
)

public data class LintingConfiguration(
    /** The time interval between subsequent lints in ms. */
    var debounceTime: Long = 250L
)

public data class JVMConfiguration(
    /** Which JVM target the Kotlin compiler uses. See Compiler.jvmTargetFrom for possible values. */
    var target: String = "default"
)

public data class CompilerConfiguration(
    val jvm: JVMConfiguration = JVMConfiguration()
)

public data class IndexingConfiguration(
    /** Whether an index of global symbols should be built in the background. */
    var enabled: Boolean = true
)

public data class ExternalSourcesConfiguration(
    /** Whether kls-URIs should be sent to the client to describe classes in JARs. */
    var useKlsScheme: Boolean = false,
    /** Whether external classes classes should be automatically converted to Kotlin. */
    var autoConvertToKotlin: Boolean = true
)


fun parseServerConfiguration(params: InitializeParams): ServerConfiguration? {
    val gson = GsonBuilder().registerTypeHierarchyAdapter(Path::class.java, GsonPathConverter()).create()

    var storage: Storage? = null

    params.initializationOptions?.let { initializationOptions ->
        val options = gson.fromJson(initializationOptions as JsonElement, InitializationOptions::class.java)

        options.storagePath?.let { storagePath ->
            if (Files.isDirectory(storagePath)) {
                storage = Storage(storagePath)
            }
        }
    }

    return ServerConfiguration(storage)
}

data class InitializationOptions(val storagePath: Path?)

data class ServerConfiguration(val storage: Storage?)

class GsonPathConverter : JsonDeserializer<Path?> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, type: Type?, context: JsonDeserializationContext?): Path? {
        return try {
            Paths.get(json.asString)
        } catch (ex: Exception) {
            LOG.printStackTrace(ex)
            null
        }
    }
}

public data class Configuration(
    val compiler: CompilerConfiguration = CompilerConfiguration(),
    val completion: CompletionConfiguration = CompletionConfiguration(),
    val linting: LintingConfiguration = LintingConfiguration(),
    var indexing: IndexingConfiguration = IndexingConfiguration(),
    val externalSources: ExternalSourcesConfiguration = ExternalSourcesConfiguration(),
    var server: ServerConfiguration? = null
)
