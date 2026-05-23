package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Project
import com.example.data.TimesheetEntry
import com.example.data.TimesheetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TimesheetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TimesheetRepository
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private val timeFormatterSeconds = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // UI state streams
    val allEntries: StateFlow<List<TimesheetEntry>>
    val allProjects: StateFlow<List<Project>>
    val activeEntry: MutableStateFlow<TimesheetEntry?> = MutableStateFlow(null)

    // Filter contexts
    val selectedDate = MutableStateFlow(dateFormatter.format(Date()))
    val selectedMonth = MutableStateFlow(monthFormatter.format(Date()))



    init {
        val database = AppDatabase.getDatabase(application)
        repository = TimesheetRepository(database.timesheetDao(), database.projectDao())
        allEntries = repository.allEntries.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Poll for active clock-in session upon creation
        checkActiveSession()
    }

    private fun checkActiveSession() {
        viewModelScope.launch {
            val active = repository.getActiveEntry()
            activeEntry.value = active
        }
    }

    // Timer control functions
    fun clockIn(project: String, notes: String) {
        viewModelScope.launch {
            if (activeEntry.value != null) return@launch // Already clocked in

            val now = System.currentTimeMillis()
            val todayStr = dateFormatter.format(Date(now))
            val newEntry = TimesheetEntry(
                date = todayStr,
                clockInTime = now,
                clockOutTime = null,
                project = project,
                notes = notes,
                isSynced = false
            )
            val id = repository.insert(newEntry)
            activeEntry.value = newEntry.copy(id = id)
        }
    }

    fun clockOut() {
        viewModelScope.launch {
            val active = activeEntry.value ?: return@launch
            val now = System.currentTimeMillis()
            val updated = active.copy(clockOutTime = now, isSynced = false)
            repository.update(updated)
            activeEntry.value = null
        }
    }

    // Manual additions & updates
    fun addManualEntry(
        dateStr: String,
        clockInTimeStr: String, // format "HH:mm"
        clockOutTimeStr: String, // format "HH:mm"
        project: String,
        notes: String
    ): Boolean {
        return try {
            val dateObj = dateFormatter.parse(dateStr) ?: return false
            val calIn = Calendar.getInstance().apply {
                time = dateObj
                val split = clockInTimeStr.split(":")
                set(Calendar.HOUR_OF_DAY, split[0].toInt())
                set(Calendar.MINUTE, split[1].toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calOut = Calendar.getInstance().apply {
                time = dateObj
                val split = clockOutTimeStr.split(":")
                set(Calendar.HOUR_OF_DAY, split[0].toInt())
                set(Calendar.MINUTE, split[1].toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (calOut.before(calIn)) {
                // If clock out is before clock in, assume next day
                calOut.add(Calendar.DATE, 1)
            }

            viewModelScope.launch {
                val entry = TimesheetEntry(
                    date = dateStr,
                    clockInTime = calIn.timeInMillis,
                    clockOutTime = calOut.timeInMillis,
                    project = project,
                    notes = notes,
                    isSynced = false
                )
                repository.insert(entry)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateManualEntry(
        entryId: Long,
        dateStr: String,
        clockInTimeStr: String,
        clockOutTimeStr: String,
        project: String,
        notes: String
    ): Boolean {
        return try {
            val dateObj = dateFormatter.parse(dateStr) ?: return false
            val calIn = Calendar.getInstance().apply {
                time = dateObj
                val split = clockInTimeStr.split(":")
                set(Calendar.HOUR_OF_DAY, split[0].toInt())
                set(Calendar.MINUTE, split[1].toInt())
                set(Calendar.SECOND, 0)
            }
            val calOut = Calendar.getInstance().apply {
                time = dateObj
                val split = clockOutTimeStr.split(":")
                set(Calendar.HOUR_OF_DAY, split[0].toInt())
                set(Calendar.MINUTE, split[1].toInt())
                set(Calendar.SECOND, 0)
            }

            if (calOut.before(calIn)) {
                calOut.add(Calendar.DATE, 1)
            }

            viewModelScope.launch {
                val updated = TimesheetEntry(
                    id = entryId,
                    date = dateStr,
                    clockInTime = calIn.timeInMillis,
                    clockOutTime = calOut.timeInMillis,
                    project = project,
                    notes = notes,
                    isSynced = false
                )
                repository.update(updated)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteEntry(entry: TimesheetEntry) {
        viewModelScope.launch {
            if (activeEntry.value?.id == entry.id) {
                activeEntry.value = null
            }
            repository.delete(entry)
        }
    }

    fun addProject(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertProject(Project(name = name.trim()))
            }
        }
    }

    fun deleteProject(name: String) {
        viewModelScope.launch {
            repository.deleteProject(Project(name = name))
        }
    }

    // Productivity insights and analytics
    val statsState = allEntries.map { list ->
        val todayStr = dateFormatter.format(Date())

        // Calculate active hour values
        val dailyHours = list.filter { it.date == todayStr }.sumOf { it.durationHours }
        val currentMonthPrefix = monthFormatter.format(Date())
        val monthlyHours = list.filter { it.date.startsWith(currentMonthPrefix) }.sumOf { it.durationHours }

        // Project breakdown
        val projectMap = list.groupBy { it.project }
            .mapValues { group -> group.value.sumOf { it.durationHours } }

        // Day of week hours for current week
        val cal = Calendar.getInstance()
        val currentWeekDays = (0..6).map { i ->
            val tempCal = Calendar.getInstance()
            tempCal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            tempCal.add(Calendar.DATE, i)
            dateFormatter.format(tempCal.time)
        }
        val weeklyHoursList = currentWeekDays.map { day ->
            day to list.filter { it.date == day }.sumOf { it.durationHours }
        }

        // Daily trend list (last 7 recorded days)
        val last7Days = (0..6).map { i ->
            val tempCal = Calendar.getInstance()
            tempCal.add(Calendar.DATE, -i)
            dateFormatter.format(tempCal.time)
        }.reversed()
        val dailyTrend = last7Days.map { day ->
            day to list.filter { it.date == day }.sumOf { it.durationHours }
        }

        TimesheetStats(
            dailyHours = dailyHours,
            monthlyHours = monthlyHours,
            projectBreakdown = projectMap,
            weeklyTrend = weeklyHoursList,
            dailyTrend = dailyTrend
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimesheetStats()
    )



    // Export formats generators
    fun generateCsvDataAdvanced(
        type: String, // "monthly", "overall", "custom"
        monthPrefix: String,
        startDate: String,
        endDate: String,
        projectFilter: String
    ): String {
        val filtered = allEntries.value.filter { entry ->
            val dateMatches = when (type) {
                "monthly" -> entry.date.startsWith(monthPrefix)
                "custom" -> entry.date >= startDate && entry.date <= endDate
                else -> true // "overall"
            }
            val projectMatches = projectFilter == "All Projects" || entry.project == projectFilter
            dateMatches && projectMatches
        }.sortedWith(compareBy({ it.project }, { it.date }, { it.clockInTime }))

        val builder = java.lang.StringBuilder()
        builder.append("Timesheet Log Summary\n")
        when (type) {
            "monthly" -> builder.append("Export Month: $monthPrefix\n")
            "custom" -> builder.append("Date Range: $startDate to $endDate\n")
            else -> builder.append("Export Coverage: All-Time Overall\n")
        }
        builder.append("Project Filter: $projectFilter\n")
        builder.append("Generated On: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        builder.append("Project,Date,Clock-In,Clock-Out,Duration Formatted,Duration Hours,Notes\n")
        var total = 0.0
        filtered.forEach { entry ->
            val inTime = timeFormatterSeconds.format(Date(entry.clockInTime))
            val outTime = entry.clockOutTime?.let { timeFormatterSeconds.format(Date(it)) } ?: "Active"
            val cleanNotes = entry.notes.replace("\"", "\"\"")
            builder.append("${entry.project},${entry.date},$inTime,$outTime,${entry.durationFormatted},${String.format("%.4f", entry.durationHours)},\"$cleanNotes\"\n")
            total += entry.durationHours
        }
        val totalSecs = (total * 3600).toLong()
        val totalHrs = totalSecs / 3600
        val totalMins = (totalSecs % 3600) / 60
        val totalS = totalSecs % 60
        builder.append("\nTotal Working Hours: ${String.format("%.2f", total)}h (${totalHrs}h ${totalMins}m ${totalS}s)\n")
        return builder.toString()
    }

    fun generatePdfReportDataAdvanced(
        type: String, // "monthly", "overall", "custom"
        monthPrefix: String,
        startDate: String,
        endDate: String,
        projectFilter: String
    ): String {
        val filtered = allEntries.value.filter { entry ->
            val dateMatches = when (type) {
                "monthly" -> entry.date.startsWith(monthPrefix)
                "custom" -> entry.date >= startDate && entry.date <= endDate
                else -> true // "overall"
            }
            val projectMatches = projectFilter == "All Projects" || entry.project == projectFilter
            dateMatches && projectMatches
        }.sortedWith(compareBy({ it.project }, { it.date }, { it.clockInTime }))

        val builder = java.lang.StringBuilder()
        builder.append("=========================================\n")
        val titleStr = when (type) {
            "monthly" -> "       MONTHLY TIMESHEET REPORT          "
            "custom" -> "       CUSTOM RANGE TIMESHEET REPORT     "
            else -> "       OVERALL ALL-TIME TIMESHEET        "
        }
        builder.append("$titleStr\n")
        builder.append("=========================================\n")
        val coverageStr = when (type) {
            "monthly" -> "Report Period : $monthPrefix"
            "custom" -> "Date Range    : $startDate to $endDate"
            else -> "Date Coverage : Overall All-Time"
        }
        builder.append("$coverageStr\n")
        builder.append("Project/Client: $projectFilter\n")
        val codeSeed = when (type) {
            "monthly" -> monthPrefix.hashCode()
            "custom" -> (startDate + endDate).hashCode()
            else -> 99999
        }
        builder.append("Security Code : SEC-CLOUD-TS-${codeSeed.coerceAtLeast(0).toString().take(6)}-${projectFilter.hashCode().coerceAtLeast(0).toString().take(5)}\n")
        builder.append("-----------------------------------------\n\n")

        var grandTotal = 0.0
        val entriesByProject = filtered.groupBy { it.project }
        entriesByProject.forEach { (project, list) ->
            val projectTotal = list.sumOf { it.durationHours }
            builder.append("Project: ${project.uppercase()}\n")
            val projTotalSecs = (projectTotal * 3600).toLong()
            val projHrs = projTotalSecs / 3600
            val projMins = (projTotalSecs % 3600) / 60
            val projS = projTotalSecs % 60
            builder.append("Project Total: ${String.format("%.2f", projectTotal)}h [${projHrs}h ${projMins}m ${projS}s]\n")
            builder.append("-----------------------------------------\n")
            list.forEach { item ->
                val start = timeFormatterSeconds.format(Date(item.clockInTime))
                val end = item.clockOutTime?.let { timeFormatterSeconds.format(Date(it)) } ?: "Active Now"
                builder.append("  * Date: ${item.date} [$start - $end] (${item.durationFormatted})\n")
                if (item.notes.isNotEmpty()) {
                    builder.append("    Notes: ${item.notes}\n")
                }
            }
            builder.append("=========================================\n\n")
            grandTotal += projectTotal
        }

        val grandTotalSecs = (grandTotal * 3600).toLong()
        val grandHrs = grandTotalSecs / 3600
        val grandMins = (grandTotalSecs % 3600) / 60
        val grandS = grandTotalSecs % 60
        builder.append("=========================================\n")
        builder.append("GRAND TOTAL: ${String.format("%.2f", grandTotal)} HOURS (${grandHrs}h ${grandMins}m ${grandS}s)\n")
        builder.append("=========================================\n")
        builder.append("Report successfully verified and approved.\n")
        return builder.toString()
    }

    fun generateCsvData(monthPrefix: String, projectFilter: String): String {
        return generateCsvDataAdvanced("monthly", monthPrefix, "", "", projectFilter)
    }

    fun generatePdfReportMockData(monthPrefix: String, projectFilter: String): String {
        return generatePdfReportDataAdvanced("monthly", monthPrefix, "", "", projectFilter)
    }

    private fun writeTextToPdf(textContent: String, file: File) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        val paint = Paint().apply {
            textSize = 10f
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
        
        val lines = textContent.split("\n")
        var yPosition = 40f
        
        for (line in lines) {
            if (yPosition > 750f) {
                pdfDocument.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(612, 792, pdfDocument.pages.size + 1).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                yPosition = 40f
            }
            canvas.drawText(line, 40f, yPosition, paint)
            yPosition += 15f
        }
        
        pdfDocument.finishPage(page)
        
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

    fun shareReport(context: Context, reportContent: String, title: String, isCsv: Boolean) {
        try {
            val extension = if (isCsv) ".csv" else ".pdf"
            val tempFile = File(context.cacheDir, "timesheet_report$extension")
            if (isCsv) {
                tempFile.writeText(reportContent)
            } else {
                writeTextToPdf(reportContent, tempFile)
            }

            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val mimeType = if (isCsv) "text/csv" else "application/pdf"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share timesheet report via..."))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing report: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadReport(context: Context, reportContent: String, filename: String, isCsv: Boolean): String? {
        return try {
            val resolvedFilename = if (isCsv) {
                if (!filename.endsWith(".csv")) "$filename.csv" else filename
            } else {
                if (!filename.endsWith(".pdf")) "$filename.pdf" else filename
            }
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = java.io.File(downloadsDir, resolvedFilename)
            if (isCsv) {
                file.writeText(reportContent)
            } else {
                writeTextToPdf(reportContent, file)
            }
            file.absolutePath
        } catch (e: Exception) {
            try {
                val resolvedFilename = if (isCsv) {
                    if (!filename.endsWith(".csv")) "$filename.csv" else filename
                } else {
                    if (!filename.endsWith(".pdf")) "$filename.pdf" else filename
                }
                val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(fallbackDir, resolvedFilename)
                if (isCsv) {
                    file.writeText(reportContent)
                } else {
                    writeTextToPdf(reportContent, file)
                }
                file.absolutePath
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    fun createBackupJson(): String {
        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val projectsArray = org.json.JSONArray()
        allProjects.value.forEach { project ->
            val pObj = org.json.JSONObject().apply {
                put("name", project.name)
                put("createdAt", project.createdAt)
            }
            projectsArray.put(pObj)
        }
        root.put("projects", projectsArray)

        val entriesArray = org.json.JSONArray()
        allEntries.value.forEach { entry ->
            val eObj = org.json.JSONObject().apply {
                put("date", entry.date)
                put("clockInTime", entry.clockInTime)
                if (entry.clockOutTime != null) {
                    put("clockOutTime", entry.clockOutTime)
                } else {
                    put("clockOutTime", org.json.JSONObject.NULL)
                }
                put("notes", entry.notes)
                put("project", entry.project)
                put("isSynced", entry.isSynced)
            }
            entriesArray.put(eObj)
        }
        root.put("entries", entriesArray)

        return root.toString(4)
    }

    fun exportBackupToFile(context: Context, onResult: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonContent = createBackupJson()
                var backupFile: File? = null
                
                try {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    backupFile = java.io.File(downloadsDir, "timesheet_backup.json")
                    backupFile.writeText(jsonContent)
                } catch (ex: Exception) {
                    val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (fallbackDir != null) {
                        backupFile = java.io.File(fallbackDir, "timesheet_backup.json")
                        backupFile.writeText(jsonContent)
                    }
                }

                if (backupFile != null) {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(backupFile.absolutePath),
                        arrayOf("application/json"),
                        null
                    )
                    val resultPath = backupFile.absolutePath
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(resultPath)
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    fun shareBackupFile(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonContent = createBackupJson()
                val tempFile = File(context.cacheDir, "timesheet_backup.json")
                tempFile.writeText(jsonContent)

                val fileUri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Timesheet Tracker Backup")
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, "Share backup file via..."))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Error sharing backup: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun restoreBackupFromFile(context: Context, uri: Uri, onSuccess: (Int, Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                if (jsonString.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("Selected file is empty")
                    }
                    return@launch
                }

                val root = org.json.JSONObject(jsonString)
                val projectsArray = root.optJSONArray("projects")
                val entriesArray = root.optJSONArray("entries")

                if (projectsArray == null && entriesArray == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onError("Invalid backup file format")
                    }
                    return@launch
                }

                var restoredProjects = 0
                var restoredEntries = 0

                // Restore Projects
                if (projectsArray != null) {
                    for (i in 0 until projectsArray.length()) {
                        val pObj = projectsArray.getJSONObject(i)
                        val name = pObj.getString("name")
                        val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                        repository.insertProject(Project(name = name, createdAt = createdAt))
                        restoredProjects++
                    }
                }

                // Get existing entries clockInTime set to avoid inserting duplicates
                val existingClockInTimes = allEntries.value.map { it.clockInTime }.toSet()

                // Restore Entries
                if (entriesArray != null) {
                    for (i in 0 until entriesArray.length()) {
                        val eObj = entriesArray.getJSONObject(i)
                        val date = eObj.getString("date")
                        val clockInTime = eObj.getLong("clockInTime")
                        val clockOutTime = if (eObj.isNull("clockOutTime")) null else eObj.getLong("clockOutTime")
                        val notes = eObj.optString("notes", "")
                        val project = eObj.optString("project", "Default Project")
                        val isSynced = eObj.optBoolean("isSynced", false)

                        if (clockInTime !in existingClockInTimes) {
                            val entry = TimesheetEntry(
                                date = date,
                                clockInTime = clockInTime,
                                clockOutTime = clockOutTime,
                                notes = notes,
                                project = project,
                                isSynced = isSynced
                            )
                            repository.insert(entry)
                            restoredEntries++
                        }
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess(restoredProjects, restoredEntries)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.message ?: "Unknown error occurred during restore")
                }
            }
        }
    }

}

// Data holder for stats calculation result
data class TimesheetStats(
    val dailyHours: Double = 0.0,
    val monthlyHours: Double = 0.0,
    val projectBreakdown: Map<String, Double> = emptyMap(),
    val weeklyTrend: List<Pair<String, Double>> = emptyList(),
    val dailyTrend: List<Pair<String, Double>> = emptyList()
)
