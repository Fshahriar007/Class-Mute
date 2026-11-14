@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.classmute

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

enum class Screen { HOME, ADD }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { ClassMuteTheme { ClassMuteApp() } }
    }
}

@Composable
fun ClassMuteApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var schedules by remember { mutableStateOf(ScheduleRepository.getSchedules(context)) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dndGranted by remember { mutableStateOf(AudioVolumeController.hasDndPermission(context)) }
    var alarmGranted by remember { mutableStateOf(AudioVolumeController.hasExactAlarmPermission(context)) }
    var showSheet by remember { mutableStateOf(!dndGranted || !alarmGranted) }

    // Re-check permissions every time the user comes back from Settings
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dndGranted = AudioVolumeController.hasDndPermission(context)
                alarmGranted = AudioVolumeController.hasExactAlarmPermission(context)
                AlarmScheduler.rescheduleAll(context)
                ClassMuteEngine.sync(context)
                schedules = ScheduleRepository.getSchedules(context)
                if (dndGranted && alarmGranted) showSheet = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Heartbeat: keeps the status card fresh and acts as a backup to the alarms
    LaunchedEffect(Unit) {
        while (true) {
            ClassMuteEngine.sync(context)
            nowMillis = System.currentTimeMillis()
            delay(15_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                schedules = schedules,
                nowMillis = nowMillis,
                onToggleSkip = { id, skip -> schedules = ScheduleRepository.setSkipped(context, id, skip) },
                onDelete = { id -> schedules = ScheduleRepository.deleteSchedule(context, id) },
                onAddClick = { screen = Screen.ADD },
                onBellClick = { showSheet = true }
            )
            Screen.ADD -> {
                BackHandler { screen = Screen.HOME }
                AddScheduleScreen(
                    onBack = { screen = Screen.HOME },
                    onSave = { items ->
                        schedules = ScheduleRepository.addSchedules(context, items)
                        screen = Screen.HOME
                    }
                )
            }
        }

        if (showSheet) {
            PermissionSheet(
                dndGranted = dndGranted,
                alarmGranted = alarmGranted,
                onGrantDnd = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                },
                onGrantAlarm = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                },
                onDismiss = { showSheet = false }
            )
        }
    }
}

@Composable
fun PermissionSheet(
    dndGranted: Boolean,
    alarmGranted: Boolean,
    onGrantDnd: () -> Unit,
    onGrantAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ClassMuteColors.SurfaceCard,
        contentColor = ClassMuteColors.TextPrimary
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("Two quick permissions", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "ClassMute needs these to silence everything on time, even when your phone is locked.",
                color = ClassMuteColors.TextSecondary
            )
            Spacer(Modifier.height(20.dp))
            PermissionRow("Do Not Disturb access", "Silences calls, notifications, media and alarms in class.", dndGranted, onGrantDnd)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Spacer(Modifier.height(16.dp))
                PermissionRow("Exact alarms", "Starts and ends muting at the exact class minute.", alarmGranted, onGrantAlarm)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Maybe later", color = ClassMuteColors.TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun PermissionRow(title: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 13.sp, color = ClassMuteColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Text("Granted ✓", color = ClassMuteColors.Sage, fontWeight = FontWeight.Medium)
        } else {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(ClassMuteColors.Lavender)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Text("Grant", color = ClassMuteColors.DarkInk, fontWeight = FontWeight.SemiBold) }
        }
    }
}
