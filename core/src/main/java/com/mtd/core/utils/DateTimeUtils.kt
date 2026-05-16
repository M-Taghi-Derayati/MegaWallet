package com.mtd.core.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utility for formatting dates in the application.
 */
object DateTimeUtils {

    fun getDateHeader(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) return "Unknown"

        val timestampMillis = timestampSeconds * 1000
        val txCalendar = Calendar.getInstance().apply {
            timeInMillis = timestampMillis
        }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        val gregorianPart = when {
            isSameDay(txCalendar, today) -> "امروز"
            isSameDay(txCalendar, yesterday) -> "دیروز"
            else -> {
                val format = if (txCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                    "MMMM d"
                } else {
                    "MMMM d, yyyy"
                }
                SimpleDateFormat(format, Locale.ENGLISH).format(Date(timestampMillis))
            }
        }

        val jalaliDate = JalaliCalendar.fromTimestamp(timestampMillis)
        val jalaliPart = jalaliDate.formatPersian()

        // Combining Gregorian and Jalali: e.g. "Today (15 Ordibehesht)" or "May 5 (15 Ordibehesht)"
        // Since the user wants both for distinction:
        return "$gregorianPart ($jalaliPart)"
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
