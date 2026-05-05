package com.thapsus.cargo.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `dimensions_json` straddles two server-side wire shapes:
 *
 * 1. Legacy: a JSON-encoded **string** like `"{\"length_cm\":30,\"width_cm\":10}"`.
 *    Older code paths and direct `SELECT dimensions_json` reads from PostgREST
 *    surface this form because the column type is `text`.
 *
 * 2. Current: an **object** like `{"length_cm":30,"width_cm":10,"height_cm":12}`.
 *    Every Express handler in `routes/{orders,admin,tracking}.js` calls
 *    `JSON.parse(o.dimensions_json)` before responding, so the modern wire form
 *    is an object — and the strict-string DTO crashed every admin order detail
 *    with `Illegal input: Expected beginning of the string, but got {`.
 *
 * This serializer accepts either form and projects it down to a stable
 * [OrderDimensionsDto] so callers don't have to care which path the row took.
 * Encoding always emits an object — we never round-trip the legacy quoted form
 * back to the server.
 */
object DimensionsObjectOrStringSerializer : KSerializer<OrderDimensionsDto?> {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DimensionsObjectOrString")

    override fun deserialize(decoder: Decoder): OrderDimensionsDto? {
        val jd = decoder as? JsonDecoder ?: return null
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> null
            is JsonObject -> decodeObject(el)
            is JsonPrimitive -> {
                if (!el.isString) return null
                val raw = el.content
                if (raw.isBlank()) return null
                runCatching {
                    val parsed = json.parseToJsonElement(raw)
                    if (parsed is JsonObject) decodeObject(parsed) else null
                }.getOrNull()
            }
            else -> null
        }
    }

    private fun decodeObject(obj: JsonObject): OrderDimensionsDto? {
        val l = obj.numericField("length_cm")
        val w = obj.numericField("width_cm")
        val h = obj.numericField("height_cm")
        if (l == null && w == null && h == null) return null
        return OrderDimensionsDto(
            lengthCm = l ?: 0.0,
            widthCm = w ?: 0.0,
            heightCm = h ?: 0.0
        )
    }

    private fun JsonObject.numericField(name: String): Double? {
        val el = this[name] as? JsonPrimitive ?: return null
        if (el is JsonNull) return null
        return el.content.toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: OrderDimensionsDto?) {
        val je = encoder as? JsonEncoder ?: error("DimensionsObjectOrStringSerializer requires JSON")
        if (value == null) {
            je.encodeJsonElement(JsonNull); return
        }
        val obj: JsonElement = buildJsonObject {
            put("length_cm", value.lengthCm)
            put("width_cm", value.widthCm)
            put("height_cm", value.heightCm)
        }
        je.encodeJsonElement(obj)
    }
}
