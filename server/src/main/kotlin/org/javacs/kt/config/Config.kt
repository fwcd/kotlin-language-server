package org.javacs.kt.config

import com.google.gson.*
import org.eclipse.lsp4j.InitializeParams
import org.javacs.kt.LOG
import org.javacs.kt.storage.Storage
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

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
