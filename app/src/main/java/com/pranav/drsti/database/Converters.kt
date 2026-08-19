package com.pranav.drsti.database

import androidx.room.TypeConverter

/**
 * Room converters. All complex objects (KundaliData, PanchangData, etc.)
 * are stored as JSON text columns (dataJson) and (de)serialized at the
 * repository boundary with kotlinx.serialization, so Room itself only
 * ever needs to know about primitives here.
 */
class Converters {
    @TypeConverter
    fun fromBoolean(value: Boolean?): Int? = value?.let { if (it) 1 else 0 }

    @TypeConverter
    fun toBoolean(value: Int?): Boolean? = value?.let { it != 0 }
}
