package dev.deiv.dear.ui.common

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import dev.deiv.dear.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
private val fullFormat = DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm", Locale.getDefault())

fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fullFormat)

/** "Oggi", "Ieri", "giovedì 16 ottobre", or with the year when it is not this year. */
fun formatDay(context: Context, day: LocalDate): String {
    val today = LocalDate.now()
    return when {
        day == today -> context.getString(R.string.day_today)
        day == today.minusDays(1) -> context.getString(R.string.day_yesterday)
        day.year == today.year -> day.format(dayFormat)
        else -> day.format(dayYearFormat)
    }
}

/** "1:05" or "1:02:05". */
fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "it.wikipedia.org" from a URL, without the www. */
fun domainOf(url: String): String =
    Uri.parse(url).host?.removePrefix("www.") ?: url

/** "Chrome" instead of "com.android.chrome" when the app is installed. */
fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    packageName
}
