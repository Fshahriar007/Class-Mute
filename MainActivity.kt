@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.classmute

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// THEME: exact palette from the prompt + mint/lavender look from the UI image
// ============================================================================
object ClassMuteColors {
    val Background = Color(0xFF18181B)    // Soft Matte Charcoal
    val SurfaceCard = Color(0xFF27272A)   // Elevated Soft Gray
    val CardBorder = Color(0xFF3F3F46)    // Subtle card border
    val InputBorder = Color(0xFF524B6E)   // Pill input outline (from mockup)
    val Sage = Color(0xFFBCE3D4)          // Primary accent (Chill state, buttons)
    val Lavender = Color(0xFFC4B5FD)      // Secondary accent (In-class state)
    val TextPrimary = Color(0xFFF4F4F5)   // Warm off-white
    val TextSecondary = Color(0xFFA1A1AA) // Muted gray
    val DarkInk = Color(0xFF18181B)       // Text on pastel surfaces
}

private val ClassMuteColorScheme = darkColorScheme(
    primary = ClassMuteColors.Sage,
    onPrimary = ClassMuteColors.DarkInk,
    secondary = ClassMuteColors.Lavender,
    onSecondary = ClassMuteColors.DarkInk,
    background = ClassMuteColors.Background,
    onBackground = ClassMuteColors.TextPrimary,
    surface = ClassMuteColors.SurfaceCard,
    onSurface = ClassMuteColors.TextPrimary,
    surfaceVariant = ClassMuteColors.SurfaceCard,
    onSurfaceVariant = ClassMuteColors.TextSecondary,
    outline = ClassMuteColors.InputBorder
)

private val ClassMuteShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(50)
)

// Swap FontFamily.SansSerif for Inter / Plus Jakarta Sans if you add the font to res/font.
private val ClassMuteTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.1.sp
    )
)

@Composable
fun ClassMuteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClassMuteColorScheme,
        typography = ClassMuteTypography,
        shapes = ClassMuteShapes
    ) {
        // Surface sets the default text colour (otherwise Text would be black on charcoal)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ClassMuteColors.Background,
            contentColor = ClassMuteColors.TextPrimary
        ) { content() }
    }
}

// ============================================================================
// HELPERS (Locale.US everywhere so Bangla-locale phones don't produce Bangla digits)
// ============================================================================
val DAY_SHORT = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
val DAY_FULL = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

fun dateKey(cal: Calendar): String = String.format(
    Locale.US, "%04d-%02d-%02d",
    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
)

fun formatClock(totalMinutes: Int): String =
    String.format(Locale.US, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)

fun formatDuration(totalMinutes: Long): String {
    val m = totalMinutes.coerceAtLeast(1)
    return when {
        m < 60 -> "${m}m"
        m < 1440 -> "${m / 60}h ${m % 60}m"
        else -> "${m / 1440}d ${(m % 1440) / 60}h"
    }
}

// ============================================================================
// DATA MODELS
// ============================================================================
data class ClassSchedule(
    val id: String = UUID.randomUUID().toString(),
    val subject: String,
    val dayOfWeek: Int, // Calendar.SUNDAY (1) .. Calendar.SATURDAY (7)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    // Date (yyyy-MM-dd) of the ONE occurrence the user skipped. It expires by itself,
    // so a cancelled class this week doesn't stay un-muted forever.
    val skippedDate: String? = null
) {
    val startMinutes: Int get() = startHour * 60 + startMinute
    val endMinutes: Int get() = endHour * 60 + endMinute

    fun formattedTimeRange(): String = "${formatClock(startMinutes)} - ${formatClock(endMinutes)}"

    fun isCurrentlyRunning(now: Calendar = Calendar.getInstance()): Boolean {
        if (now.get(Calendar.DAY_OF_WEEK) != dayOfWeek) return false
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return nowMin in startMinutes until endMinutes
    }

    /** Date of the occurrence that is running now or is the next one to come. */
    fun nextOccurrenceKey(now: Calendar = Calendar.getInstance()): String {
        val cal = now.clone() as Calendar
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var add = (dayOfWeek - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (add == 0 && nowMin >= endMinutes) add = 7
        cal.add(Calendar.DAY_OF_YEAR, add)
        return dateKey(cal)
    }

    fun isSkipped(now: Calendar = Calendar.getInstance()): Boolean =
        skippedDate != null && skippedDate == nextOccurrenceKey(now)
}

data class VolumeSnapshot(
    val music: Int,
    val ring: Int,
    val notification: Int,
    val alarm: Int,
    val system: Int,
    val ringerMode: Int,
    val interruptionFilter: Int
)

// ============================================================================
// REPOSITORY (SharedPreferences)
// ============================================================================
object ScheduleRepository {
    private const val PREFS_NAME = "classmute_prefs"
    private const val KEY_SCHEDULES = "schedules_json"
    private const val KEY_MUTED = "muted"
    private const val KEY_ACTIVE_SUBJECT = "active_subject"
    private const val KEY_VOL_MUSIC = "vol_music"
    private const val KEY_VOL_RING = "vol_ring"
    private const val KEY_VOL_NOTIF = "vol_notif"
    private const val KEY_VOL_ALARM = "vol_alarm"
    private const val KEY_VOL_SYSTEM = "vol_system"
    private const val KEY_RINGER = "ringer_mode"
    private const val KEY_DND = "dnd_filter"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------- schedules ----------
    fun getSchedules(context: Context): List<ClassSchedule> {
        val raw = prefs(context).getString(KEY_SCHEDULES, null)
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ClassSchedule(
                    id = o.getString("id"),
                    subject = o.getString("subject"),
                    dayOfWeek = o.getInt("dayOfWeek"),
                    startHour = o.getInt("startHour"),
                    startMinute = o.getInt("startMinute"),
                    endHour = o.getInt("endHour"),
                    endMinute = o.getInt("endMinute"),
                    skippedDate = if (o.isNull("skippedDate")) null else o.getString("skippedDate")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveSchedules(context: Context, schedules: List<ClassSchedule>) {
        val array = JSONArray()
        schedules.forEach { s ->
            array.put(JSONObject().apply {
                put("id", s.id)
                put("subject", s.subject)
                put("dayOfWeek", s.dayOfWeek)
                put("startHour", s.startHour)
                put("startMinute", s.startMinute)
                put("endHour", s.endHour)
                put("endMinute", s.endMinute)
                put("skippedDate", s.skippedDate ?: JSONObject.NULL)
            })
        }
        prefs(context).edit().putString(KEY_SCHEDULES, array.toString()).apply()
    }

    fun addSchedules(context: Context, items: List<ClassSchedule>): List<ClassSchedule> {
        val updated = getSchedules(context) + items
        saveSchedules(context, updated)
        items.forEach { AlarmScheduler.scheduleClassAlarms(context, it) }
        ClassMuteEngine.sync(context)
        return updated
    }

    fun deleteSchedule(context: Context, id: String): List<ClassSchedule> {
        AlarmScheduler.cancelClassAlarms(context, id)
        val updated = getSchedules(context).filter { it.id != id }
        saveSchedules(context, updated)
        ClassMuteEngine.sync(context) // un-mutes if the deleted class was running
        return updated
    }

    fun setSkipped(context: Context, id: String, skip: Boolean): List<ClassSchedule> {
        val now = Calendar.getInstance()
        val updated = getSchedules(context).map {
            if (it.id == id) it.copy(skippedDate = if (skip) it.nextOccurrenceKey(now) else null) else it
        }
        saveSchedules(context, updated)
        ClassMuteEngine.sync(context)
        return updated
    }

    // ---------- mute state & pre-class volumes ----------
    fun isMuted(context: Context): Boolean = prefs(context).getBoolean(KEY_MUTED, false)

    fun setActiveSubject(context: Context, subject: String) {
        prefs(context).edit().putString(KEY_ACTIVE_SUBJECT, subject).apply()
    }

    fun saveSnapshot(context: Context, s: VolumeSnapshot, subject: String) {
        prefs(context).edit()
            .putInt(KEY_VOL_MUSIC, s.music)
            .putInt(KEY_VOL_RING, s.ring)
            .putInt(KEY_VOL_NOTIF, s.notification)
            .putInt(KEY_VOL_ALARM, s.alarm)
            .putInt(KEY_VOL_SYSTEM, s.system)
            .putInt(KEY_RINGER, s.ringerMode)
            .putInt(KEY_DND, s.interruptionFilter)
            .putString(KEY_ACTIVE_SUBJECT, subject)
            .putBoolean(KEY_MUTED, true)
            .apply()
    }

    fun consumeSnapshot(context: Context): VolumeSnapshot? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_MUTED, false)) return null
        val s = VolumeSnapshot(
            music = p.getInt(KEY_VOL_MUSIC, 5),
            ring = p.getInt(KEY_VOL_RING, 5),
            notification = p.getInt(KEY_VOL_NOTIF, 5),
            alarm = p.getInt(KEY_VOL_ALARM, 5),
            system = p.getInt(KEY_VOL_SYSTEM, 5),
            ringerMode = p.getInt(KEY_RINGER, android.media.AudioManager.RINGER_MODE_NORMAL),
            interruptionFilter = p.getInt(KEY_DND, android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
        )
        p.edit().putBoolean(KEY_MUTED, false).remove(KEY_ACTIVE_SUBJECT).apply()
        return s
    }

    // ---------- home screen subtitle ----------
    fun getNextClassSubtitle(schedules: List<ClassSchedule>, now: Calendar = Calendar.getInstance()): String {
        var bestDiff = Long.MAX_VALUE
        var best: ClassSchedule? = null
        for (item in schedules) {
            val cand = AlarmScheduler.computeNextTrigger(item.dayOfWeek, item.startHour, item.startMinute, now)
            if (item.skippedDate == dateKey(cand)) cand.add(Calendar.DAY_OF_YEAR, 7)
            val diff = cand.timeInMillis - now.timeInMillis
            if (diff in 1 until bestDiff) {
                bestDiff = diff
                best = item
            }
        }
        if (best == null) return "No upcoming classes"
        return "Next class in ${formatDuration(bestDiff / 60_000L)} (${best.subject})"
    }
}

// ============================================================================
// SOUND ENGINE: mutes music, alarm, ring, notification, system + Total Silence DND
// ============================================================================
object AudioVolumeController {

    fun hasDndPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun muteForClass(context: Context, schedule: ClassSchedule) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Remember how the phone was before class
        ScheduleRepository.saveSnapshot(
            context,
            VolumeSnapshot(
                music = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                ring = audio.getStreamVolume(AudioManager.STREAM_RING),
                notification = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
                alarm = audio.getStreamVolume(AudioManager.STREAM_ALARM),
                system = audio.getStreamVolume(AudioManager.STREAM_SYSTEM),
                ringerMode = audio.ringerMode,
                interruptionFilter = nm.currentInterruptionFilter
            ),
            schedule.subject
        )

        // 2. Media + alarm volume can always be changed
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0) }

        // 3. Ring / notification / system + DND need Do Not Disturb access
        if (hasDndPermission(context)) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, 0, 0) }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0) }
            runCatching { audio.ringerMode = AudioManager.RINGER_MODE_SILENT
