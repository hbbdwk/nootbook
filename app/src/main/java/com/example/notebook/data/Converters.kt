package com.example.notebook.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(separator = "|||")
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.split("|||")?.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromSummaryStatus(status: SummaryStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSummaryStatus(value: String): SummaryStatus {
        return try {
            SummaryStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            SummaryStatus.PENDING
        }
    }
}
