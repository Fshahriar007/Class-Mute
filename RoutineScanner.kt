package com.example.classmute

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Calendar
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// ============================================================================
// ROUTINE AUTO-SCAN: image/PDF -> ML Kit OCR text -> best-effort class extraction.
// OCR on tables is never perfect, so the UI always shows a preview before saving.
// ============================================================================
object RoutineScanner {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val DAY_REGEX = Regex(
        "\\b(sun(day)?|mon(day)?|tue(s|sday)?|wed(nesday)?|thu(r|rs|rsday)?|fri(day)?|sat(urday)?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val TIME_REGEX = Regex(
        "(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?\\s*(?:-|–|—|to)\\s*(\\d{1,2})[:.](\\d{2})\\s*([ap]\\.?m\\.?)?",
        RegexOption.IGNORE_CASE
    )
    private val SUBJECT_REGEX = Regex("\\b([A-Za-z]{2,4})[\\s-]?(\\d{3,4})\\b")
    private val NOT_SUBJECTS = setOf("ROOM", "SEC", "SECT", "ROLL", "FLOOR")

    // ---------------- OCR ----------------
    suspend fun extractText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val isPdf = context.contentResolver.getType(uri) == "application/pdf"
        val parts = mutableListOf<String>()
        if (isPdf) {
            for (bmp in renderPdf(context, uri)) {
                parts += recognize(InputImage.fromBitmap(bmp, 0))
                bmp.recycle()
            }
        } else {
            parts += recognize(InputImage.fromFilePath(context, uri))
        }
        parts.joinToString("\n")
    }

    private suspend fun recognize(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resume("") }
        }

    private fun renderPdf(context: Context, uri: Uri, maxPages: Int = 3): List<Bitmap> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val result = mutableListOf<Bitmap>()
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until minOf(renderer.pageCount, maxPages)) {
                val page = renderer.openPage(i)
                try {
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE) // PDFs render on transparent
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result.add(bmp)
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }
        return result
    }

    // ---------------- Parsing ----------------
    fun parse(text: String, fallbackDay: Int): List<ClassSchedule> {
        val out = mutableListOf<ClassSchedule>()
        var currentDay: Int? = null
        var pendingSubject: String? = null

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // A line with exactly one day name sets the day for the lines below it
            val days = DAY_REGEX.findAll(line).toList()
            if (days.size == 1) currentDay = dayNumber(days[0].value)

            val subject = findSubject(line)
            val tm = TIME_REGEX.find(line)

            if (tm != null) {
                val g = tm.groupValues
                val range = resolveRange(
                    g[1].toInt(), g[2].toInt(), g[3].ifEmpty { null },
                    g[4].toInt(), g[5].toInt(), g[6].ifEmpty { null }
                )
                if (range != null) {
                    out += ClassSchedule(
                        subject = subject ?: pendingSubject ?: "Class",
                        dayOfWeek = currentDay ?: fallbackDay,
                        startHour = range[0] / 60, startMinute = range[0] % 60,
                        endHour = range[1] / 60, endMinute = range[1] % 60
                    )
                    pendingSubject = null
                }
            } else if (subject != null) {
                pendingSubject = subject
            }
        }
        return out.distinctBy { listOf(it.subject, it.dayOfWeek, it.startMinutes, it.endMinutes) }
    }

    private fun findSubject(line: String): String? {
        for (m in SUBJECT_REGEX.findAll(line)) {
            val letters = m.groupValues[1].uppercase()
            if (letters !in NOT_SUBJECTS) return "$letters-${m.groupValues[2]}"
        }
        return null
    }

    private fun dayNumber(word: String): Int = when (word.lowercase().take(3)) {
        "sun" -> Calendar.SUNDAY
        "mon" -> Calendar.MONDAY
        "tue" -> Calendar.TUESDAY
        "wed" -> Calendar.WEDNESDAY
        "thu" -> Calendar.THURSDAY
        "fri" -> Calendar.FRIDAY
        else -> Calendar.SATURDAY
    }

    private fun to24(h: Int, pm: Boolean): Int = when {
        pm -> if (h == 12) 12 else h + 12
        else -> if (h == 12) 0 else h
    }

    /** Returns [startMinutes, endMinutes] or null if the numbers don't make sense. */
    private fun resolveRange(h1: Int, m1: Int, mer1: String?, h2: Int, m2: Int, mer2: String?): IntArray? {
        if (m1 > 59 || m2 > 59 || h1 > 24 || h2 > 24) return null
        val a = mer1?.lowercase()?.firstOrNull()
        val b = mer2?.lowercase()?.firstOrNull()
        var s: Int
        var e: Int
        if (a != null && b != null) {
            s = to24(h1, a == 'p'); e = to24(h2, b == 'p')
        } else if (b != null) {                       // "11:00 - 1:00 PM"
            e = to24(h2, b == 'p'); s = to24(h1, b == 'p')
            if (s > e) s = to24(h1, false)
        } else if (a != null) {                       // "11:00 AM - 1:00"
            s = to24(h1, a == 'p'); e = to24(h2, a == 'p')
            if (e <= s) e = to24(h2, true)
        } else {                                      // no AM/PM at all: assume class hours
            fun adjust(h: Int) = if (h in 1..7) h + 12 else h
            s = adjust(h1); e = adjust(h2)
            if (e <= s && e < 12) e += 12
        }
        val start = s * 60 + m1
        val end = e * 60 + m2
        return if (end > start && end <= 24 * 60 - 1) intArrayOf(start, end) else null
    }
}
