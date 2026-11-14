@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.classmute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun HomeScreen(
    schedules: List<ClassSchedule>,
    nowMillis: Long,
    onToggleSkip: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit,
    onBellClick: () -> Unit
) {
    val nowCal = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis } }
    val today = nowCal.get(Calendar.DAY_OF_WEEK)
    var selectedDay by remember { mutableIntStateOf(today) }
    var toDelete by remember { mutableStateOf<ClassSchedule?>(null) }

    val running = schedules.firstOrNull { it.isCurrentlyRunning(nowCal) && !it.isSkipped(nowCal) }
    val (title, subtitle) = if (running != null) {
        val nowMin = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        "IN CLASS • MUTED" to "Back to normal in ${formatDuration((running.endMinutes - nowMin).toLong())} (${running.subject})"
    } else {
        "CHILL MODE" to ScheduleRepository.getNextClassSubtitle(schedules, nowCal)
    }

    val dayClasses = schedules.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMinutes }
    val listLabel = if (selectedDay == today) "TODAY'S CLASSES" else "${DAY_FULL[selectedDay - 1].uppercase()} CLASSES"

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ClassMute", style = MaterialTheme.typography.headlineLarge)
                IconButton(onClick = onBellClick) {
                    Icon(Icons.Outlined.NotificationsOff, contentDescription = "Permissions", tint = ClassMuteColors.TextSecondary)
                }
            }

            StatusCard(inClass = running != null, title = title, subtitle = subtitle)

            Spacer(Modifier.height(24.dp))
            Text(listLabel, style = MaterialTheme.typography.labelSmall, color = ClassMuteColors.TextSecondary)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in 1..7) {
                    val selected = d == selectedDay
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (selected) ClassMuteColors.Lavender else ClassMuteColors.SurfaceCard)
                            .clickable { selectedDay = d },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            DAY_SHORT[d - 1],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) ClassMuteColors.DarkInk else ClassMuteColors.TextSecondary
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (dayClasses.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No classes on this day.\nTap the button below to add one.",
                        color = ClassMuteColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    items(dayClasses, key = { it.id }) { item ->
                        ClassCard(
                            item = item,
                            skipped = item.isSkipped(nowCal),
                            onToggle = { onToggleSkip(item.id, it) },
                            onLongPress = { toDelete = item }
                        )
                    }
                }
            }
        }

        PillButton(
            text = "Upload or Add Schedule",
            container = ClassMuteColors.Sage,
            icon = Icons.Filled.Add,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth(),
            onClick = onAddClick
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = ClassMuteColors.SurfaceCard,
            title = { Text("Delete ${item.subject}?") },
            text = { Text("This removes the class from every week.", color = ClassMuteColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { onDelete(item.id); toDelete = null }) {
                    Text("Delete", color = ClassMuteColors.Lavender)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel", color = ClassMuteColors.TextSecondary) }
            }
        )
    }
}

@Composable
fun StatusCard(inClass: Boolean, title: String, subtitle: String) {
    val bg = if (inClass) ClassMuteColors.Lavender else ClassMuteColors.Sage
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = bg, spotColor = bg)
            .clip(shape)
            .background(bg)
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (inClass) Icons.Outlined.NotificationsOff else Icons.Filled.Bolt,
                contentDescription = null,
                tint = ClassMuteColors.DarkInk,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ClassMuteColors.DarkInk)
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = ClassMuteColors.DarkInk.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    }
}

@Composable
fun ClassCard(item: ClassSchedule, skipped: Boolean, onToggle: (Boolean) -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val alpha = if (skipped) 0.45f else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClassMuteColors.SurfaceCard)
            .border(1.dp, ClassMuteColors.CardBorder, shape)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(16.dp)
    ) {
        Text(item.subject, style = MaterialTheme.typography.titleLarge, color = ClassMuteColors.TextPrimary.copy(alpha = alpha))
        Spacer(Modifier.height(4.dp))
        Text(item.formattedTimeRange(), style = MaterialTheme.typography.bodyMedium, color = ClassMuteColors.TextSecondary)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Skip This", fontSize = 11.sp, color = ClassMuteColors.TextSecondary)
            Switch(
                checked = skipped,
                onCheckedChange = onToggle,
                modifier = Modifier.scale(0.8f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ClassMuteColors.TextPrimary,
                    checkedTrackColor = ClassMuteColors.Sage,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = ClassMuteColors.TextPrimary,
                    uncheckedTrackColor = ClassMuteColors.CardBorder,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
