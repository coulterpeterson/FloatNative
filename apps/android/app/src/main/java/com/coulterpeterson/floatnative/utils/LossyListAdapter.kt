package com.coulterpeterson.floatnative.utils

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Element-tolerant list deserializer for Moshi. When applied (via [Lossy]),
 * a single malformed element is logged and skipped instead of failing the
 * whole array. Mirrors LossyArray on iOS — the home feed uses both so a
 * single bad post can't blank the screen.
 *
 * Usage:
 * ```kotlin
 * val response = ContentCreatorListV3ResponseTolerant(
 *     blogPosts = ..., // decoded with Lossy<BlogPostModelV3>
 *     lastElements = ...,
 * )
 * ```
 *
 * Or annotate the Moshi field with [Lossy]:
 * ```kotlin
 * @Lossy val blogPosts: List<BlogPostModelV3>
 * ```
 *
 * Or annotate a Retrofit method that returns a list, as its Moshi converter
 * passes the method's qualifiers along.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@com.squareup.moshi.JsonQualifier
annotation class Lossy

class LossyListAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        val lossy = annotations.firstOrNull { it.annotationClass == Lossy::class }
            ?: return null
        if (type !is ParameterizedType || Types.getRawType(type) != List::class.java) {
            return null
        }
        val elementType = type.actualTypeArguments[0]
        val elementAdapter: JsonAdapter<Any?> =
            moshi.nextAdapter(this, elementType, annotations.minus(lossy))
        return LossyListAdapter(elementAdapter)
    }
}

private class LossyListAdapter<T>(private val element: JsonAdapter<T?>) : JsonAdapter<List<T>>() {
    override fun fromJson(reader: JsonReader): List<T> {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Any>()
            return emptyList()
        }
        val result = mutableListOf<T>()
        reader.beginArray()
        var index = 0
        while (reader.hasNext()) {
            val peek = reader.peekJson()
            try {
                val value = element.fromJson(peek)
                if (value != null) result.add(value)
                reader.skipValue() // advance the real reader past this element
            } catch (e: Exception) {
                reader.skipValue()
                val msg = "${e.javaClass.simpleName}: ${e.message}"
                Log.w("LossyList", "skipped element at index $index: $msg")
                DebugLogManager.decode("LossyList: skipped element at index $index", msg)
            } finally {
                peek.close()
            }
            index += 1
        }
        reader.endArray()
        return result
    }

    override fun toJson(writer: JsonWriter, value: List<T>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        for (item in value) {
            element.toJson(writer, item)
        }
        writer.endArray()
    }
}
