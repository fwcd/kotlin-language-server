package org.javacs.kt.storage

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.javacs.kt.LOG
import java.lang.Exception
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Storage(val path: Path) {
    fun getSlice(slicePath: String): Storage? {
        val fullSlicePath = Paths.get(path.toString(), slicePath)

        try {
            Files.createDirectories(fullSlicePath)
        } catch (ex: FileAlreadyExistsException) {
            return null
        }

        return Storage(fullSlicePath)
    }

    inline fun <reified T> getObject(objectPath: String, deserializer: KSerializer<T> = Json.serializersModule.serializer()): T? {
        val fullObjectPath = Paths.get(path.toString(), objectPath)

        if (Files.exists(fullObjectPath)) {
            val content = fullObjectPath.toFile().readText()
            try {
                return Json.decodeFromString(deserializer, content)
            } catch (ex: Exception) {
                // We catch any exception just in case something unexpected happens. We return null in that case and delete the file.
                LOG.printStackTrace(ex)
                Files.deleteIfExists(fullObjectPath)
            }
        }

        return null
    }

    inline fun <reified T> setObject(objectPath: String, value: T, serializer: KSerializer<T> = Json.serializersModule.serializer()) {
        val fullObjectPath = Paths.get(path.toString(), objectPath)

        if (!Files.exists(fullObjectPath)) {
            Files.createFile(fullObjectPath)
        }

        try {
            val content = Json.encodeToString(serializer, value)
            fullObjectPath.toFile().writeText(content)
        } catch (ex: Exception) {
            // We catch any exception just in case something unexpected happens.
            LOG.printStackTrace(ex)
            Files.deleteIfExists(fullObjectPath)
        }
    }
}

object PathAsStringSerializer : KSerializer<Path> {
    override val descriptor = PrimitiveSerialDescriptor("Path", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Path) = encoder.encodeString(value.toAbsolutePath().toString())

    override fun deserialize(decoder: Decoder): Path = Path.of(decoder.decodeString())
}

object SetOfPathsAsStringSerializer : KSerializer<Set<Path>> by SetSerializer(PathAsStringSerializer)
