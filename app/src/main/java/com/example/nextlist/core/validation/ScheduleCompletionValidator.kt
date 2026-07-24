package com.example.nextlist.core.validation

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object ScheduleCompletionValidator {
    fun dateError(value: String): String? = when {
        value.isBlank() -> "请选择日期"
        parseDate(value) == null -> "日期格式应为 YYYY-MM-DD"
        else -> null
    }

    fun timeError(value: String): String? = when {
        value.isBlank() -> "请选择时间"
        parseTime(value) == null -> "时间格式应为 HH:mm"
        else -> null
    }

    fun timezoneError(value: String): String? = when {
        value.isBlank() -> "请选择时区"
        zoneId(value) == null -> "时区无效，请使用 IANA 时区"
        else -> null
    }

    fun meetingPointError(value: String): String? =
        if (TextValidator.unicodeLength(value.trim()) > 200) {
            "集合地点最多 200 个字符"
        } else {
            null
        }

    fun scheduleNoteError(value: String): String? =
        optionalTextError(value, "备注")

    fun reviewError(value: String): String? =
        optionalTextError(value, "评价")

    fun ratingError(value: Int?): String? =
        if (value != null && value !in 1..5) "评分只能是 1～5 星" else null

    fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.trim())
    } catch (_: DateTimeException) {
        null
    }

    fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim())
    } catch (_: DateTimeException) {
        null
    }

    fun zoneId(value: String): ZoneId? = try {
        ZoneId.of(value.trim())
    } catch (_: DateTimeException) {
        null
    }

    private fun optionalTextError(value: String, label: String): String? =
        if (TextValidator.unicodeLength(value.trim()) > 500) {
            "$label 最多 500 个字符"
        } else {
            null
        }
}
