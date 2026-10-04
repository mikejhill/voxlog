package com.mikejhill.voxlog.engine.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Converts kotlinx JSON trees into plain Kotlin maps, lists and primitives for SDKs that take `Any?`. */
internal object JsonValueConverter {
    /** Recursively unwraps [element]. */
    fun toPlain(element: JsonElement): Any? = when (element) {
        is JsonNull -> null

        is JsonObject -> element.mapValues { (_, value) -> toPlain(value) }

        is JsonArray -> element.map { toPlain(it) }

        is JsonPrimitive -> if (element.isString) {
            element.content
        } else {
            element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
        }
    }
}
