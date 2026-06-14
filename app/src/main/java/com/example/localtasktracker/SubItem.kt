package com.example.localtasktracker

data class SubItem(
    val id: Int,
    var subItemName: String,
    var isCompleted: Boolean = false
) {
    fun renameSubItem(newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        subItemName = trimmed
        return true
    }

    fun changeSubItemStatus(status: Boolean) {
        isCompleted = status
    }
}
