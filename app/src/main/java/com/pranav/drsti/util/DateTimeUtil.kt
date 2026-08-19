package com.pranav.drsti.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Central "current date/time" resolution — the AI is never allowed to assume today's date (spec §23). */
object DateTimeUtil {

    data class CurrentTimeContext(
        val localDate: String,
        val localTime: String,
        val localTimestamp: String,
        val utcTimestamp: String,
        val zoneId: String,
        val utcOffset: String
    )

    fun currentContext(zoneId: ZoneId = ZoneId.systemDefault()): CurrentTimeContext {
        val now = ZonedDateTime.now(zoneId)
        val utc = now.withZoneSameInstant(ZoneId.of("UTC"))
        return CurrentTimeContext(
            localDate = now.format(DateTimeFormatter.ISO_LOCAL_DATE),
            localTime = now.format(DateTimeFormatter.ISO_LOCAL_TIME),
            localTimestamp = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            utcTimestamp = utc.format(DateTimeFormatter.ISO_INSTANT),
            zoneId = zoneId.id,
            utcOffset = now.offset.id
        )
    }

    fun nowIso(): String = Instant.now().toString()
}
