package com.example.localtasktracker

data class Tasks(
    val id: Int,
    var title: String,
    var isCompleted: Boolean = false,
    var completionProgress: Int = 0,
    var categoryCount: Int = 0,
    val categories: MutableList<TaskCategory> = mutableListOf(),
) {

}