package com.example.data

import kotlinx.coroutines.flow.Flow

class TimesheetRepository(
    private val timesheetDao: TimesheetDao,
    private val projectDao: ProjectDao
) {
    val allEntries: Flow<List<TimesheetEntry>> = timesheetDao.getAllEntries()
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    fun getEntriesForDate(date: String): Flow<List<TimesheetEntry>> =
        timesheetDao.getEntriesForDate(date)

    suspend fun getActiveEntry(): TimesheetEntry? =
        timesheetDao.getActiveEntry()

    suspend fun insert(entry: TimesheetEntry): Long =
        timesheetDao.insertEntry(entry)

    suspend fun update(entry: TimesheetEntry) =
        timesheetDao.updateEntry(entry)

    suspend fun markAllAsSynced() =
        timesheetDao.markAllAsSynced()

    suspend fun delete(entry: TimesheetEntry) =
        timesheetDao.deleteEntry(entry)

    suspend fun clearAll() =
        timesheetDao.clearAll()

    suspend fun insertProject(project: Project) =
        projectDao.insertProject(project)

    suspend fun deleteProject(project: Project) =
        projectDao.deleteProject(project)
}
