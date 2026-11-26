package com.infinicada.focuspocus

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Shared [Json] configuration for all persisted Focus Pocus data and backups.
 *
 * Configured for full backward compatibility with payloads previously written by Gson:
 * - [ignoreUnknownKeys]: skips retired fields (e.g. `websites`, `reflexOpens`).
 * - [encodeDefaults]: preserves explicit default values in emitted JSON.
 * - [coerceInputValues]: falls back to Kotlin defaults on null/unknown enum values where defaulted.
 * - [explicitNulls] = false: omits null fields on encode and treats absent nullable fields as null.
 */
object AppJson {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Decodes a JSON array element-by-element, silently dropping individual null
     * or corrupt elements (e.g. records damaged by an earlier build) while keeping
     * all valid elements intact. Throws if [raw] is not a valid JSON array.
     */
    inline fun <reified T> decodeListLenient(raw: String): List<T> {
        val element = json.parseToJsonElement(raw)
        if (element is JsonNull) return emptyList()
        require(element is JsonArray) { "Expected JSON array" }
        val result = ArrayList<T>(element.size)
        for (item in element) {
            if (item is JsonNull) continue
            runCatching { json.decodeFromJsonElement<T>(item) }.getOrNull()?.let { result.add(it) }
        }
        return result
    }
}
