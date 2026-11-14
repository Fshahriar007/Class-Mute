package com.example.classmute

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import java.util.Calendar

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
            runCatching { audio.ringerMode = AudioManager.RINGER_MODE_SILENT }
            // Total Silence: blocks every sound, including alarms and media
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) }
        }
    }

    fun restoreAfterClass(context: Context) {
        val snap = ScheduleRepository.consumeSnapshot(context) ?: return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (hasDndPermission(context)) {
            // DND first, then ringer mode, so the volume streams unlock before we set them
            if (snap.interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                runCatching { nm.setInterruptionFilter(snap.interruptionFilter) }
            }
            runCatching { audio.ringerMode = snap.ringerMode }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_RING, snap.ring, 0) }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION, snap.notification, 0) }
            runCatching { audio.setStreamVolume(AudioManager.STREAM_SYSTEM, snap.system, 0) }
        }
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, snap.alarm, 0) }
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, snap.music, 0) }
    }
}

// ============================================================================
// ALARM ENGINE: one exact alarm at class start, one at class end, re-armed weekly
// ============================================================================
object AlarmScheduler {
    const val ACTION_CLASS_START = "com.example.classmute.ACTION_CLASS_START"
    const val ACTION_CLASS_END = "com.example.classmute.ACTION_CLASS_END"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"

    fun computeNextTrigger(dayOfWeek: Int, hour: Int, minute: Int, now: Calendar = Calendar.getInstance()): Calendar {
        val cal = now.clone() as Calendar
        val diff = (dayOfWeek - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, diff)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now.timeInMillis) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal
    }

    private fun pendingIntent(context: Context, scheduleId: String, isStart: Boolean): PendingIntent {
        val intent = Intent(context, ClassMuteReceiver::class.java).apply {
            action = if (isStart) ACTION_CLASS_START else ACTION_CLASS_END
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }
        return PendingIntent.getBroadcast(
            context,
            (scheduleId + if (isStart) "_START" else "_END").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setAlarm(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (AudioVolumeController.hasExactAlarmPermission(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // No exact-alarm permission yet: still schedule, just not to-the-second
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun scheduleClassAlarms(context: Context, s: ClassSchedule) {
        val start = computeNextTrigger(s.dayOfWeek, s.startHour, s.startMinute)
        val end = computeNextTrigger(s.dayOfWeek, s.endHour, s.endMinute)
        setAlarm(context, start.timeInMillis, pendingIntent(context, s.id, true))
        setAlarm(context, end.timeInMillis, pendingIntent(context, s.id, false))
    }

    fun cancelClassAlarms(context: Context, scheduleId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, scheduleId, true))
        am.cancel(pendingIntent(context, scheduleId, false))
    }

    fun rescheduleAll(context: Context) {
        ScheduleRepository.getSchedules(context).forEach { scheduleClassAlarms(context, it) }
    }
}

// ============================================================================
// SYNC: one function decides "should the phone be muted right now?"
// Called from alarms, boot, app open, skip toggles. Safe to call any time.
// ============================================================================
object ClassMuteEngine {
    @Synchronized
    fun sync(context: Context) {
        val now = Calendar.getInstance()
        val running = ScheduleRepository.getSchedules(context)
            .firstOrNull { it.isCurrentlyRunning(now) && !it.isSkipped(now) }
        val muted = ScheduleRepository.isMuted(context)
        when {
            running != null && !muted -> AudioVolumeController.muteForClass(context, running)
            running != null && muted -> ScheduleRepository.setActiveSubject(context, running.subject)
            running == null && muted -> AudioVolumeController.restoreAfterClass(context)
        }
    }
}

// ============================================================================
// RECEIVER: fires at class start / end, after reboot, and when exact-alarm access changes
// ============================================================================
class ClassMuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            AlarmScheduler.ACTION_CLASS_START, AlarmScheduler.ACTION_CLASS_END -> {
                ClassMuteEngine.sync(app)
                // Re-arm this class for next week
                val id = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULE_ID)
                ScheduleRepository.getSchedules(app).firstOrNull { it.id == id }
                    ?.let { AlarmScheduler.scheduleClassAlarms(app, it) }
            }
            Intent.ACTION_BOOT_COMPLETED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                AlarmScheduler.rescheduleAll(app)
                ClassMuteEngine.sync(app)
            }
        }
    }
}
