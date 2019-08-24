package org.javacs.kt.util

import java.nio.charset.StandardCharsets
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Parse a possibly-percent-encoded URI string.
 * Decoding is necessary since some language clients
 * (including VSCode) invalidly percent-encode colons.
 */
fun parseURI(uri: String): URI = URI.create(runCatching { URLDecoder.decode(uri, StandardCharsets.UTF_8.toString()) }.getOrDefault(uri))

val URI.filePath: Path? get() = runCatching { Paths.get(this) }.getOrNull()

fun describeURIs(uris: Collection<URI>): String =
    if (uris.isEmpty()) "0 files"
    else if (uris.size > 5) "${uris.size} files"
    else uris.map(::describeURI).joinToString(", ")

fun describeURI(uri: String): String = describeURI(parseURI(uri))

fun describeURI(uri: URI): String {
    val (parent, fileName) = uri.path.partitionAroundLast("/")
    return ".../" + parent.substringAfterLast("/") + fileName
}
