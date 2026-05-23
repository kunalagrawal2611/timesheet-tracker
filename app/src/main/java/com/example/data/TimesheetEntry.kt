package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timesheet_entries")
data class TimesheetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,          // Short format: "yyyy-MM-dd"
    val clockInTime: Long,     // Milliseconds timestamp
    val clockOutTime: Long?,   // Milliseconds timestamp, null if active
    val notes: String = "",    // Description of what was done
    val project: String = "Default Project", // Project identifier
    val isSynced: Boolean = false // Track sync state
) {
    val durationHours: Double
        get() {
            val endTime = clockOutTime ?: System.currentTimeMillis()
            if (endTime <= clockInTime) return 0.0
            return (endTime - clockInTime).toDouble() / (1000 * 60 * 60)
        }

    val durationFormatted: String
        get() {
            val endTime = clockOutTime ?: System.currentTimeMillis()
            if (endTime <= clockInTime) return "0s"
            val diffMs = endTime - clockInTime
            val totalSecs = diffMs / 1000
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            return when {
                hours > 0 -> "${hours}h ${mins}m ${secs}s"
                mins > 0 -> "${mins}m ${secs}s"
                else -> "${secs}s"
            }
        }
}
