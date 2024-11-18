package com.example.appusage

data class AppSession(
    val packageName: String,
    val appName: String,
    val startTime: Long,
    var endTime: Long = 0,
    var duration: Long = 0
)
