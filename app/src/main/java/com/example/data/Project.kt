package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
