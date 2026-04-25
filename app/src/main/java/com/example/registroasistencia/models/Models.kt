package com.example.registroasistencia.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "" // "teacher" or "student"
)

data class Course(
    val id: String = "",
    val name: String = "",
    val teacherId: String = "",
    val code: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 0f,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val bluetoothAddress: String = ""
)

data class Attendance(
    val studentId: String = "",
    val studentName: String = "",
    val courseId: String = "",
    val timestamp: Long = 0
)
