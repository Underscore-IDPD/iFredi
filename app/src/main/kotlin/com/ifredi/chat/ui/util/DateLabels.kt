package com.ifredi.chat.ui.util

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import com.ifredi.chat.R
import java.util.Date
import java.util.Locale

object DateLabels {
    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun dayLabel(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val yesterday = now - 24L * 60 * 60 * 1000
        val locale = Locale.forLanguageTag("es-DO")
        return when {
            sameDay(timestamp, now) -> context.getString(R.string.today)
            sameDay(timestamp, yesterday) -> context.getString(R.string.yesterday)
            Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR) ==
                    Calendar.getInstance().get(Calendar.YEAR) ->
                SimpleDateFormat("d 'de' MMMM", locale).format(Date(timestamp))
            else -> SimpleDateFormat("d 'de' MMMM 'de' yyyy", locale).format(Date(timestamp))
        }
    }
}