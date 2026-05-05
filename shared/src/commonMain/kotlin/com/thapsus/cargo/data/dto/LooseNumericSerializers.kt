package com.thapsus.cargo.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Decode an Int from any of: JSON number, JSON string ("0"), or JSON null.
 *
 * Why: PostgreSQL `COUNT(*)` / `SUM(…)` return BIGINT, which the pg-node
 * driver serialises as a STRING (e.g. `"total_orders":"0"`). The same
 * aggregations return JSON `null` on empty tables. Either form would
 * crash a strict `Int` deserialiser with:
 *
 *   "Unexpected JSON token at offset N: Unexpected symbol 'n' in
 *    numeric literal at path: $.stats.orders.delivered"
 *
 * Encoding always emits a plain JSON number — we never round-trip the
 * loose form back to the server.
 */
object LooseIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LooseInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeInt()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> 0
            is JsonPrimitive -> el.content.toIntOrNull()
                ?: el.content.toDoubleOrNull()?.toInt()
                ?: 0
            else -> 0
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/** Same shape as [LooseIntSerializer] for Long-typed fields. */
object LooseLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LooseLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeLong()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> 0L
            is JsonPrimitive -> el.content.toLongOrNull()
                ?: el.content.toDoubleOrNull()?.toLong()
                ?: 0L
            else -> 0L
        }
    }

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }
}

/**
 * Decode a Double from JSON number, JSON string ("123.45"), or JSON null.
 * `SUM(amount)` returns NUMERIC which pg-node also stringifies, and `null`
 * on empty aggregates.
 */
object LooseDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LooseDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> 0.0
            is JsonPrimitive -> el.content.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

/**
 * Same as [LooseDoubleSerializer] but preserves `null`. Use this where the
 * server intentionally returns nullable optionals (e.g. `weight_kg` before a
 * parcel is weighed) and the iOS UI needs to distinguish "missing" from "0".
 */
object NullableLooseDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableLooseDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val jd = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val el = jd.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> el.content.toDoubleOrNull()
            else -> null
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}
