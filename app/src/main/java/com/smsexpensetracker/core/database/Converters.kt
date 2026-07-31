package com.smsexpensetracker.core.database

import androidx.room.TypeConverter
import com.smsexpensetracker.core.database.entity.ParseMethod
import com.smsexpensetracker.core.database.entity.ParseStatus
import com.smsexpensetracker.core.database.entity.TransactionType
import java.time.LocalDateTime
import java.time.ZoneOffset

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromParseStatus(value: ParseStatus): String = value.name

    @TypeConverter
    fun toParseStatus(value: String): ParseStatus = ParseStatus.valueOf(value)

    @TypeConverter
    fun fromParseMethod(value: ParseMethod): String = value.name

    @TypeConverter
    fun toParseMethod(value: String): ParseMethod = ParseMethod.valueOf(value)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? = value?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
}