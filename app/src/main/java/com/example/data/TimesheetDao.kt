package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimesheetDao {
    @Query("SELECT * FROM timesheet_entries ORDER BY clockInTime DESC")
    fun getAllEntries(): Flow<List<TimesheetEntry>>

    @Query("SELECT * FROM timesheet_entries WHERE date = :date ORDER BY clockInTime DESC")
    fun getEntriesForDate(date: String): Flow<List<TimesheetEntry>>

    @Query("SELECT * FROM timesheet_entries WHERE clockOutTime IS NULL LIMIT 1")
    suspend fun getActiveEntry(): TimesheetEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TimesheetEntry): Long

    @Update
    suspend fun updateEntry(entry: TimesheetEntry)

    @Query("UPDATE timesheet_entries SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllAsSynced()

    @Delete
    suspend fun deleteEntry(entry: TimesheetEntry)

    @Query("DELETE FROM timesheet_entries")
    suspend fun clearAll()
}
