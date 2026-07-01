package com.obrynex.studyguard.data

import androidx.room.TypeConverter
import java.util.Date

/**
 * Room type converter for Date <-> Long (epoch milliseconds).
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
