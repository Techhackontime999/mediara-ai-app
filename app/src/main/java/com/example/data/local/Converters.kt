package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list == null) return "[]"
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).toJson(list)
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        return moshi.adapter<List<String>>(type).fromJson(json) ?: emptyList()
    }

    @TypeConverter
    fun fromStringMap(map: Map<String, String>?): String {
        if (map == null) return "{}"
        val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        return moshi.adapter<Map<String, String>>(type).toJson(map)
    }

    @TypeConverter
    fun toStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val type = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
        return moshi.adapter<Map<String, String>>(type).fromJson(json) ?: emptyMap()
    }
}
