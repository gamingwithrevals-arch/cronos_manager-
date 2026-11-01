package com.cronoszip.app.data

import com.cronoszip.app.engine.NativeBridge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class CompressSummary(val entries: Int, val bytes: Long)

/** Builds a ZIP from files and folders using the engine. Output is written beside the inputs' destination. */
object Compressor {
    fun compress(output: String, inputs: List<String>, store: Boolean): Result<CompressSummary> = runCatching {
        if (!NativeBridge.loaded) {
            throw IllegalStateException("The compression engine is not available in this build.")
        }
        val config = buildJsonObject {
            put("output", output)
            putJsonArray("inputs") {
                inputs.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            put("store", store)
        }.toString()
        val envelope = Json { ignoreUnknownKeys = true }
            .decodeFromString<ApiEnvelope>(NativeBridge.nativeCompress(config))
        if (!envelope.ok) {
            throw IllegalStateException(envelope.message.ifBlank { "Compression failed." })
        }
        val data = envelope.data?.jsonObject
        CompressSummary(
            entries = data?.get("entries")?.jsonPrimitive?.int ?: 0,
            bytes = data?.get("bytes")?.jsonPrimitive?.long ?: 0L,
        )
    }
}
