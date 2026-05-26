package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Project
import com.example.data.TimesheetEntry
import com.example.ui.TimesheetStats
import com.example.ui.TimesheetViewModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

sealed class AppTab(val title: String) {
    object Timer : AppTab("Track")
    object Dashboard : AppTab("Trends")
    object Reports : AppTab("Reports")
    object Settings : AppTab("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(viewModel: TimesheetViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf<AppTab>(AppTab.Timer) }

    // Observe flows
    val allEntries by viewModel.allEntries.collectAsState()
    val allProjects by viewModel.allProjects.collectAsState()
    val activeEntry by viewModel.activeEntry.collectAsState()
    val stats by viewModel.statsState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Timesheet Tracker",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                listOf(AppTab.Timer, AppTab.Dashboard, AppTab.Reports, AppTab.Settings).forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.Timer -> if (isSelected) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
                                    AppTab.Dashboard -> if (isSelected) Icons.Filled.BarChart else Icons.Outlined.BarChart
                                    AppTab.Reports -> if (isSelected) Icons.Filled.Assessment else Icons.Outlined.Assessment
                                    AppTab.Settings -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                                },
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            unselectedTextColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.Timer -> TimerTab(viewModel, activeEntry, allEntries, stats, allProjects)
                AppTab.Dashboard -> DashboardTab(stats, allEntries)
                AppTab.Reports -> ReportsTab(viewModel, allEntries)
                AppTab.Settings -> SettingsTab(viewModel, allProjects)
            }
        }
    }
}

@Composable
fun TimerTab(
    viewModel: TimesheetViewModel,
    activeEntry: TimesheetEntry?,
    allEntries: List<TimesheetEntry>,
    stats: TimesheetStats,
    allProjects: List<Project>
) {
    val context = LocalContext.current
    var inputNotes by remember { mutableStateOf("") }
    var inputProject by remember { mutableStateOf("") }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }

    // If projects are loaded and inputProject is blank, default to the first project name
    LaunchedEffect(allProjects) {
        if (inputProject.isEmpty() && allProjects.isNotEmpty()) {
            inputProject = allProjects.first().name
        }
    }

    // Manual Form Expansion States
    var showManualAddDialog by remember { mutableStateOf(false) }
    var viewAllLogs by remember { mutableStateOf(false) }
    var logsSearchQuery by remember { mutableStateOf("") }

    // Active Timer Elapsed Stream
    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(activeEntry) {
        if (activeEntry != null) {
            while (true) {
                val diffMs = System.currentTimeMillis() - activeEntry.clockInTime
                elapsedSeconds = (diffMs / 1000).coerceAtLeast(0)
                delay(1000)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val liveTimerFormatted = remember(elapsedSeconds) {
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        String.format("%02d:%02d:%02d", h, m, s)
    }

    if (showCreateProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateProjectDialog = false },
            onSave = { name ->
                viewModel.addProject(name)
                inputProject = name
                showCreateProjectDialog = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Session Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_timer_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeEntry != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (activeEntry != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTIVE TIMING SESSION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = liveTimerFormatted,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 48.sp,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Started at ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(activeEntry.clockInTime))} (${activeEntry.project})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                        if (activeEntry.notes.isNotEmpty()) {
                            Text(
                                text = "\"${activeEntry.notes}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.clockOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clock_out_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Clock Out", modifier = Modifier.padding(end = 6.dp))
                            Text("Clock Out & Log", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "READY TO START WORKING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Project picker header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Project",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { showCreateProjectDialog = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Project", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        if (allProjects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .clickable { showCreateProjectDialog = true }
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.CreateNewFolder,
                                        contentDescription = "No project created",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No projects tracked yet",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap to create your first project",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        } else {
                            // Dropdown Picker for Projects (Standard select without nested interactive click components to avoid conflicts)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { projectDropdownExpanded = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (inputProject.isEmpty()) "Select Project" else inputProject,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Project selection dropdown indicator",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = projectDropdownExpanded,
                                    onDismissRequest = { projectDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    allProjects.forEach { proj ->
                                        DropdownMenuItem(
                                            text = { Text(proj.name, style = MaterialTheme.typography.bodyMedium) },
                                            onClick = {
                                                inputProject = proj.name
                                                projectDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Horizontal Project Management Chip List
                            Text(
                                text = "Your Active Projects (tap to select)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                items(allProjects) { proj ->
                                    val isSelected = inputProject == proj.name
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable { inputProject = proj.name }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = proj.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Notes entry field
                        OutlinedTextField(
                            value = inputNotes,
                            onValueChange = { inputNotes = it },
                            label = { Text("Session Description (Optional)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("work_notes_field"),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (inputProject.isNotBlank()) {
                                    viewModel.clockIn(inputProject, inputNotes)
                                    inputNotes = ""
                                } else {
                                    showCreateProjectDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (inputProject.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clock_in_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Clock In", modifier = Modifier.padding(end = 6.dp))
                            Text(if (inputProject.isNotBlank()) "Clock In Now" else "Create Project First", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action panel
        if (activeEntry == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Forgot to record clock-in status?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Add entries manually anytime",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { showManualAddDialog = true },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("add_manual_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Manual Entry Icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Manual", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Today's summary list or All Logs list
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (viewAllLogs) "ALL LOGGED ENTRIES" else "TODAY'S WORKING ENTRIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { viewAllLogs = !viewAllLogs }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (viewAllLogs) Icons.Default.CalendarMonth else Icons.Default.List,
                            contentDescription = "Toggle All Logs View",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (viewAllLogs) "View Today Only" else "View All History",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (viewAllLogs) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = logsSearchQuery,
                        onValueChange = { logsSearchQuery = it },
                        label = { Text("Search logs by project or notes") },
                        placeholder = { Text("Filter logs...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
                        trailingIcon = if (logsSearchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { logsSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear research query")
                                }
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val filteredEntries = if (viewAllLogs) {
            allEntries.filter { entry ->
                logsSearchQuery.isEmpty() ||
                entry.project.contains(logsSearchQuery, ignoreCase = true) ||
                entry.notes.contains(logsSearchQuery, ignoreCase = true)
            }.sortedByDescending { it.clockInTime }
        } else {
            allEntries.filter { it.date == todayStr }
        }

        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WorkOutline,
                            contentDescription = "No tasks",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (viewAllLogs) "No matching logs found" else "No log sheets recorded today",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            if (viewAllLogs) {
                // Group All Logs by Date to present a beautiful, organized chronology
                val groupedByDate = filteredEntries.groupBy { it.date }
                groupedByDate.forEach { (date, list) ->
                    item {
                        Card(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formattedDate = try {
                                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                                    SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(parsed!!)
                                } catch (e: Exception) {
                                    date
                                }
                                Text(
                                    text = formattedDate,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val dayHours = list.sumOf { it.durationHours }
                                Text(
                                    text = String.format("%.2f hrs", dayHours),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    items(list) { entry ->
                        TimesheetEntryRow(entry, onDelete = { viewModel.deleteEntry(entry) })
                    }
                }
            } else {
                items(filteredEntries) { entry ->
                    TimesheetEntryRow(entry, onDelete = { viewModel.deleteEntry(entry) })
                }
            }
        }
    }

    // Manual entry logging input dialog
    if (showManualAddDialog) {
        ManualAddDialog(
            allProjects = allProjects,
            onDismiss = { showManualAddDialog = false },
            onSave = { date, clockIn, clockOut, project, notes ->
                if (allProjects.none { it.name.equals(project, ignoreCase = true) }) {
                    viewModel.addProject(project)
                }
                val ok = viewModel.addManualEntry(date, clockIn, clockOut, project, notes)
                if (ok) {
                    showManualAddDialog = false
                } else {
                    Toast.makeText(context, "Invalid times or calendar format", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Project", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. Website Overhaul") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        onSave(projectName.trim())
                    }
                },
                enabled = projectName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun TimesheetEntryRow(entry: TimesheetEntry, onDelete: () -> Unit) {
    val inTimeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.clockInTime))
    val outTimeFormatted = entry.clockOutTime?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "Active"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.project.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (entry.isSynced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Synced",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (entry.notes.isNotEmpty()) {
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        text = "Working Session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Logged: $inTimeFormatted - $outTimeFormatted",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.durationFormatted,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

enum class TrendMode(val label: String) {
    DAILY("Last 7 Days"),
    WEEKLY("Weekly View"),
    MONTHLY("Monthly View")
}

@Composable
fun DashboardTab(stats: TimesheetStats, allEntries: List<TimesheetEntry>) {
    var mode by remember { mutableStateOf(TrendMode.DAILY) }
    var weeklyAnchorCal by remember { mutableStateOf(Calendar.getInstance()) }
    var monthlyAnchorCal by remember { mutableStateOf(Calendar.getInstance()) }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Shift calendar helpers
    fun navigatePrevious() {
        when (mode) {
            TrendMode.DAILY -> {}
            TrendMode.WEEKLY -> {
                val nextCal = weeklyAnchorCal.clone() as Calendar
                nextCal.add(Calendar.WEEK_OF_YEAR, -1)
                weeklyAnchorCal = nextCal
            }
            TrendMode.MONTHLY -> {
                val nextCal = monthlyAnchorCal.clone() as Calendar
                nextCal.add(Calendar.MONTH, -1)
                monthlyAnchorCal = nextCal
            }
        }
    }

    fun navigateNext() {
        when (mode) {
            TrendMode.DAILY -> {}
            TrendMode.WEEKLY -> {
                val nextCal = weeklyAnchorCal.clone() as Calendar
                nextCal.add(Calendar.WEEK_OF_YEAR, 1)
                weeklyAnchorCal = nextCal
            }
            TrendMode.MONTHLY -> {
                val nextCal = monthlyAnchorCal.clone() as Calendar
                nextCal.add(Calendar.MONTH, 1)
                monthlyAnchorCal = nextCal
            }
        }
    }

    // Dynamic label for the current view interval
    val headingText = remember(weeklyAnchorCal, monthlyAnchorCal, mode) {
        when (mode) {
            TrendMode.DAILY -> {
                val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val today = Calendar.getInstance()
                val startCal = today.clone() as Calendar
                startCal.add(Calendar.DATE, -6)
                "${df.format(startCal.time)} - ${df.format(today.time)}"
            }
            TrendMode.WEEKLY -> {
                val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val startCal = weeklyAnchorCal.clone() as Calendar
                startCal.firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val endCal = startCal.clone() as Calendar
                endCal.add(Calendar.DATE, 6)
                "Week of ${df.format(startCal.time)} - ${df.format(endCal.time)}"
            }
            TrendMode.MONTHLY -> {
                val mf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                mf.format(monthlyAnchorCal.time)
            }
        }
    }

    // Filter relevant entries for the selected view window to compute stats dynamically!
    val viewedEntries = remember(allEntries, weeklyAnchorCal, monthlyAnchorCal, mode) {
        when (mode) {
            TrendMode.DAILY -> {
                val tempCal = Calendar.getInstance()
                tempCal.add(Calendar.DATE, -6)
                val dates = (0..6).map {
                    val d = dateFormatter.format(tempCal.time)
                    tempCal.add(Calendar.DATE, 1)
                    d
                }
                allEntries.filter { it.date in dates }
            }
            TrendMode.WEEKLY -> {
                val startCal = weeklyAnchorCal.clone() as Calendar
                startCal.firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                val dates = (0..6).map {
                    val d = dateFormatter.format(startCal.time)
                    startCal.add(Calendar.DATE, 1)
                    d
                }
                allEntries.filter { it.date in dates }
            }
            TrendMode.MONTHLY -> {
                val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(monthlyAnchorCal.time)
                allEntries.filter { it.date.startsWith(monthStr) }
            }
        }
    }

    val viewedCumulativeHours = remember(viewedEntries) {
        viewedEntries.sumOf { it.durationHours }
    }

    val viewedProjectBreakdown = remember(viewedEntries) {
        viewedEntries.groupBy { it.project }
            .mapValues { (_, list) -> list.sumOf { it.durationHours } }
    }

    // Prepare trend lists plotted on custom elegant UI Canvas
    val trendList = remember(viewedEntries, weeklyAnchorCal, monthlyAnchorCal, mode) {
        when (mode) {
            TrendMode.DAILY -> {
                val list = mutableListOf<Pair<String, Double>>()
                val tempCal = Calendar.getInstance()
                tempCal.add(Calendar.DATE, -6)
                for (i in 0..6) {
                    val dateStr = dateFormatter.format(tempCal.time)
                    val daySum = allEntries.filter { it.date == dateStr }.sumOf { it.durationHours }
                    list.add(dateStr to daySum)
                    tempCal.add(Calendar.DATE, 1)
                }
                list
            }
            TrendMode.WEEKLY -> {
                val list = mutableListOf<Pair<String, Double>>()
                val tempCal = weeklyAnchorCal.clone() as Calendar
                tempCal.firstDayOfWeek = Calendar.MONDAY
                tempCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                for (i in 0..6) {
                    val dateStr = dateFormatter.format(tempCal.time)
                    val daySum = allEntries.filter { it.date == dateStr }.sumOf { it.durationHours }
                    list.add(dateStr to daySum)
                    tempCal.add(Calendar.DATE, 1)
                }
                list
            }
            TrendMode.MONTHLY -> {
                val monthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(monthlyAnchorCal.time)
                val w1 = allEntries.filter { it.date.startsWith("$monthStr-") && it.date.takeLast(2).toIntOrNull() in 1..7 }.sumOf { it.durationHours }
                val w2 = allEntries.filter { it.date.startsWith("$monthStr-") && it.date.takeLast(2).toIntOrNull() in 8..14 }.sumOf { it.durationHours }
                val w3 = allEntries.filter { it.date.startsWith("$monthStr-") && it.date.takeLast(2).toIntOrNull() in 15..21 }.sumOf { it.durationHours }
                val w4 = allEntries.filter { it.date.startsWith("$monthStr-") && it.date.takeLast(2).toIntOrNull() in 22..28 }.sumOf { it.durationHours }
                val w5 = allEntries.filter { it.date.startsWith("$monthStr-") && it.date.takeLast(2).toIntOrNull() != null && it.date.takeLast(2).toInt() >= 29 }.sumOf { it.durationHours }
                listOf(
                    "Days 1-7" to w1,
                    "Days 8-14" to w2,
                    "Days 15-21" to w3,
                    "Days 22-28" to w4,
                    "Days 29+" to w5
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Range Mode Switcher Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrendMode.values().forEach { tMode ->
                        val isSel = mode == tMode
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { mode = tMode },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = if (isSel) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tMode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Navigation Chevron Controls
        if (mode != TrendMode.DAILY) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { navigatePrevious() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Segment")
                        }
                        Text(
                            text = headingText,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { navigateNext() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Segment")
                        }
                    }
                }
            }
        }

        // High level stats cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Today's Productivity", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f hrs", stats.dailyHours),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Segment Cumulative", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%.1f hrs", viewedCumulativeHours),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Visual Canvas Trend graph
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (mode) {
                            TrendMode.DAILY -> "Daily Trend (${trendList.size} Days)"
                            TrendMode.WEEKLY -> "Weekly Daily Trend"
                            TrendMode.MONTHLY -> "Monthly Progression (Grouped)"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val maxHours = (trendList.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val gridLineColor = MaterialTheme.colorScheme.outline
                        val labelColor = MaterialTheme.colorScheme.secondary

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Baseline grid lines
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, h * 0.1f),
                                end = Offset(w, h * 0.1f),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, h * 0.5f),
                                end = Offset(w, h * 0.5f),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, h * 0.9f),
                                end = Offset(w, h * 0.9f),
                                strokeWidth = 2f
                            )

                            // Render dynamic bars
                            val spacing = w / (trendList.size + 1)
                            val barWidth = if (trendList.size > 5) 20.dp.toPx() else 28.dp.toPx()

                            trendList.forEachIndexed { i, item ->
                                val x = spacing * (i + 1)
                                val barHeight = ((item.second / maxHours) * (h * 0.8f)).toFloat()
                                val y = (h * 0.9f).toFloat() - barHeight

                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(x - barWidth / 2, y),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                    }

                    // Bottom labels row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        trendList.forEach { item ->
                            val labelText = when (mode) {
                                TrendMode.DAILY, TrendMode.WEEKLY -> {
                                    try {
                                        val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.first)
                                        SimpleDateFormat("E", Locale.getDefault()).format(d!!)
                                    } catch (e: Exception) {
                                        item.first.takeLast(2)
                                    }
                                }
                                TrendMode.MONTHLY -> {
                                    item.first.replace("Days ", "")
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(labelText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(String.format("%.1f", item.second), fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }

        // Project breakdown visual lists (dynamic to the viewed range!)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Range Project Distribution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (viewedProjectBreakdown.isEmpty()) {
                        Text(
                            "No projects tracked during the period",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        viewedProjectBreakdown.forEach { (proj, hrs) ->
                            val percent = if (viewedCumulativeHours > 0) (hrs / viewedCumulativeHours) else 0.0
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(proj, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(String.format("%.1f hrs (%.0f%%)", hrs, percent * 100), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                LinearProgressIndicator(
                                    progress = { percent.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ReportsTab(viewModel: TimesheetViewModel, allEntries: List<TimesheetEntry>) {
    val context = LocalContext.current
    var reportType by remember { mutableStateOf("monthly") } // "monthly", "custom", "overall"
    var inputMonth by remember { mutableStateOf("2026-05") }
    var startDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var endDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }

    val allProjects by viewModel.allProjects.collectAsState()
    var selectedProjectFilter by remember { mutableStateOf("All Projects") }
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Article,
            contentDescription = "Report generation icon",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "Generate Timesheet Reports",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Compile custom timesheet reports. Pick your report scope, filter on target projects, and export securely as CSV or PDF.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Report Scope Segment Selector Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "REPORT SCOPE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "monthly" to "Monthly",
                        "custom" to "Custom Range",
                        "overall" to "Overall"
                    ).forEach { (typeKey, typeLabel) ->
                        val isSelected = reportType == typeKey
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { reportType = typeKey },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = typeLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scope Dependent Filters Card
        when (reportType) {
            "monthly" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SELECT STATEMENT MONTH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target Month: $inputMonth",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, y, m, _ ->
                                        inputMonth = String.format("%d-%02d", y, m + 1)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar Picker Icon")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pick Month")
                            }
                        }
                    }
                }
            }
            "custom" -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "SELECT DATE INTERVAL BOUNDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Start Date
                            Column(modifier = Modifier.weight(1f)) {
                                Text("From date", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val calendar = Calendar.getInstance()
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    startDate = String.format("%d-%02d-%02d", y, m + 1, d)
                                                },
                                                calendar.get(Calendar.YEAR),
                                                calendar.get(Calendar.MONTH),
                                                calendar.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(startDate, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "Start Date Icon", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            // End Date
                            Column(modifier = Modifier.weight(1f)) {
                                Text("To date", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val calendar = Calendar.getInstance()
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    endDate = String.format("%d-%02d-%02d", y, m + 1, d)
                                                },
                                                calendar.get(Calendar.YEAR),
                                                calendar.get(Calendar.MONTH),
                                                calendar.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(endDate, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.CalendarMonth, contentDescription = "End Date Icon", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OVERALL RECORD RANGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "compiles and packages all completed activities logged on this workspace into a cumulative master file.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Project Filter Selection Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FILTER BY PROJECT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { projectDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedProjectFilter,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = projectDropdownExpanded,
                        onDismissRequest = { projectDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Projects", fontSize = 14.sp) },
                            onClick = {
                                selectedProjectFilter = "All Projects"
                                projectDropdownExpanded = false
                            }
                        )
                        allProjects.forEach { proj ->
                            DropdownMenuItem(
                                text = { Text(proj.name, fontSize = 14.sp) },
                                onClick = {
                                    selectedProjectFilter = proj.name
                                    projectDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        val matchingEntriesCount = remember(allEntries, reportType, inputMonth, startDate, endDate, selectedProjectFilter) {
            allEntries.filter { entry ->
                val dateMatches = when (reportType) {
                    "monthly" -> entry.date.startsWith(inputMonth)
                    "custom" -> entry.date >= startDate && entry.date <= endDate
                    else -> true // "overall"
                }
                val projectMatches = selectedProjectFilter == "All Projects" || entry.project == selectedProjectFilter
                dateMatches && projectMatches
            }.size
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Report Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "Found $matchingEntriesCount shifts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Export Actions Row 1: CSV
        Text("CSV Report (Spreadsheet Format)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.align(Alignment.Start))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (matchingEntriesCount == 0) {
                        Toast.makeText(context, "No completed entries found", Toast.LENGTH_SHORT).show()
                    } else {
                        val csvText = viewModel.generateCsvDataAdvanced(reportType, inputMonth, startDate, endDate, selectedProjectFilter)
                        val subjectText = when (reportType) {
                            "monthly" -> "Timesheet Statement - $inputMonth - $selectedProjectFilter"
                            "custom" -> "Timesheet Statement - $startDate to $endDate - $selectedProjectFilter"
                            else -> "Overall Timesheet Statement - $selectedProjectFilter"
                        }
                        viewModel.shareReport(context, csvText, subjectText, isCsv = true)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("export_csv_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share CSV", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share CSV", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    if (matchingEntriesCount == 0) {
                        Toast.makeText(context, "No completed entries found", Toast.LENGTH_SHORT).show()
                    } else {
                        val csvText = viewModel.generateCsvDataAdvanced(reportType, inputMonth, startDate, endDate, selectedProjectFilter)
                        val safeProjName = selectedProjectFilter.replace(" ", "_").replace("/", "-")
                        val filename = when (reportType) {
                            "monthly" -> "Timesheet_${inputMonth}_${safeProjName}.csv"
                            "custom" -> "Timesheet_${startDate}_to_${endDate}_${safeProjName}.csv"
                            else -> "Timesheet_Overall_${safeProjName}.csv"
                        }
                        val path = viewModel.downloadReport(context, csvText, filename, isCsv = true)
                        if (path != null) {
                            Toast.makeText(context, "Downloaded successfully as '$filename'!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to download CSV", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("download_csv_button")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Download CSV", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download CSV", fontSize = 12.sp)
            }
        }

        // Export Actions Row 2: PDF
        Text("PDF Report (Formatted Document)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.align(Alignment.Start))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (matchingEntriesCount == 0) {
                        Toast.makeText(context, "No completed entries found", Toast.LENGTH_SHORT).show()
                    } else {
                        val pdfText = viewModel.generatePdfReportDataAdvanced(reportType, inputMonth, startDate, endDate, selectedProjectFilter)
                        val subjectText = when (reportType) {
                            "monthly" -> "Timesheet Report PDF - $inputMonth - $selectedProjectFilter"
                            "custom" -> "Timesheet Report PDF - $startDate to $endDate - $selectedProjectFilter"
                            else -> "Timesheet Report PDF - Overall - $selectedProjectFilter"
                        }
                        viewModel.shareReport(context, pdfText, subjectText, isCsv = false)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("export_pdf_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share PDF", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share PDF", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    if (matchingEntriesCount == 0) {
                        Toast.makeText(context, "No completed entries found", Toast.LENGTH_SHORT).show()
                    } else {
                        val pdfText = viewModel.generatePdfReportDataAdvanced(reportType, inputMonth, startDate, endDate, selectedProjectFilter)
                        val safeProjName = selectedProjectFilter.replace(" ", "_").replace("/", "-")
                        val filename = when (reportType) {
                            "monthly" -> "Timesheet_${inputMonth}_${safeProjName}.pdf"
                            "custom" -> "Timesheet_${startDate}_to_${endDate}_${safeProjName}.pdf"
                            else -> "Timesheet_Overall_${safeProjName}.pdf"
                        }
                        val path = viewModel.downloadReport(context, pdfText, filename, isCsv = false)
                        if (path != null) {
                            Toast.makeText(context, "Downloaded successfully as '$filename'!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to download PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("download_pdf_button")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Download PDF", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download PDF", fontSize = 12.sp)
            }
        }


    }
}

@Composable
fun ManualAddDialog(
    allProjects: List<Project>,
    onDismiss: () -> Unit,
    onSave: (date: String, clockIn: String, clockOut: String, project: String, notes: String) -> Unit
) {
    val context = LocalContext.current
    var dateSelected by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var clockInSelected by remember { mutableStateOf("09:00") }
    var clockOutSelected by remember { mutableStateOf("17:00") }
    var notesInput by remember { mutableStateOf("") }
    var projectSelected by remember { mutableStateOf("") }
    var showManualProjectTextEntry by remember { mutableStateOf(allProjects.isEmpty()) }
    var projectDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(allProjects) {
        if (projectSelected.isEmpty() && allProjects.isNotEmpty()) {
            projectSelected = allProjects.first().name
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manual Session Adjustment", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Date picker launcher
                    Text("Session Date", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Button(
                        onClick = {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d ->
                                dateSelected = String.format("%d-%02d-%02d", y, m + 1, d)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        colors = ButtonDefaults.textButtonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        Text(dateSelected)
                    }
                }

                item {
                    // Timing picks Row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clock In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    TimePickerDialog(context, { _, h, m ->
                                        clockInSelected = String.format("%02d:%02d", h, m)
                                    }, 9, 0, true).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            ) {
                                Text(clockInSelected)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clock Out", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Button(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    TimePickerDialog(context, { _, h, m ->
                                        clockOutSelected = String.format("%02d:%02d", h, m)
                                    }, 17, 0, true).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            ) {
                                Text(clockOutSelected)
                            }
                        }
                    }
                }

                item {
                    // Project selector layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Project", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (allProjects.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    showManualProjectTextEntry = !showManualProjectTextEntry
                                    if (showManualProjectTextEntry) {
                                        projectSelected = ""
                                    } else {
                                        projectSelected = allProjects.firstOrNull()?.name ?: ""
                                    }
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showManualProjectTextEntry) "Select Existing" else "＋ New Project", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    if (showManualProjectTextEntry || allProjects.isEmpty()) {
                        OutlinedTextField(
                            value = projectSelected,
                            onValueChange = { projectSelected = it },
                            label = { Text("Project Name") },
                            placeholder = { Text("e.g. Website Overhaul") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { projectDropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (projectSelected.isEmpty()) "Select Project" else projectSelected,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown icon",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = projectDropdownExpanded,
                                onDismissRequest = { projectDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                allProjects.forEach { proj ->
                                    DropdownMenuItem(
                                        text = { Text(proj.name, fontSize = 14.sp) },
                                        onClick = {
                                            projectSelected = proj.name
                                            projectDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Session Description (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(dateSelected, clockInSelected, clockOutSelected, projectSelected, notesInput)
            }) {
                Text("Confirm Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsTab(viewModel: TimesheetViewModel, allProjects: List<Project>) {
    val context = LocalContext.current
    var newProjectName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "Application Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Securely configure project registries.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section: Project Registry Management
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PROJECT REGISTRY MANAGEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Input field to add a new project
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newProjectName,
                            onValueChange = { newProjectName = it },
                            placeholder = { Text("Add new project...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        Button(
                            onClick = {
                                if (newProjectName.isNotBlank()) {
                                    viewModel.addProject(newProjectName)
                                    newProjectName = ""
                                    Toast.makeText(context, "Project added successfully", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Active Projects (Accidental Deletion Shielded)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (allProjects.isEmpty()) {
                        Text(
                            text = "No projects found. Enter a name above to register your first project.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        allProjects.forEach { proj ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = proj.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { projectToDelete = proj.name },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete project registry",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Backup & Recovery Management
        item {
            val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                if (uri != null) {
                    viewModel.restoreBackupFromFile(
                        context = context,
                        uri = uri,
                        onSuccess = { pRestored, eRestored ->
                            Toast.makeText(
                                context,
                                "Restore successful: Restored $pRestored projects and $eRestored logs!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { errorMsg ->
                            Toast.makeText(context, "Restore failed: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DATA BACKUP & RECOVERY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Uninstalling the app deletes local databases. Keep your project list and clocked-in time history secure by saving a backup file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportBackupToFile(context) { path ->
                                    if (path != null) {
                                        Toast.makeText(context, "Backup downloaded to downloads folder!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Error creating backup file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Download backup", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.shareBackupFile(context)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share data", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Backup", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            importLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Import file", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore Backup File", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Confirmation dialog for project deletion to avoid accidents completely
    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = {
                Text(
                    text = "Confirm Project Deletion",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the project '${projectToDelete}'? Previously clocked-in timesheet logs referencing this project will be kept securely, but the project will be removed from your active selector lists.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameStr = projectToDelete
                        if (nameStr != null) {
                            viewModel.deleteProject(nameStr)
                            Toast.makeText(context, "'$nameStr' deleted successfully.", Toast.LENGTH_SHORT).show()
                        }
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Project")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
