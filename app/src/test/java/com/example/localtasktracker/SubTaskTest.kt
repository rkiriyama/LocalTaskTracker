package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SubTask].
 * Covers: construction, rename, status, subitem CRUD, hasSubItems,
 * computeProgress, and data-integrity guarantees.
 */
class SubTaskTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun subTask(name: String = "Task", completed: Boolean = false) =
        SubTask(id = 1, subTaskName = name, isCompleted = completed)

    private fun subItem(id: Int, name: String, completed: Boolean = false) =
        SubItem(id = id, subItemName = name, isCompleted = completed)

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val st = SubTask(id = 5, subTaskName = "Cook dinner", isCompleted = true)
        assertEquals(5, st.id)
        assertEquals("Cook dinner", st.subTaskName)
        assertTrue(st.isCompleted)
    }

    @Test fun constructor_defaultIsCompleted_false() {
        val st = SubTask(id = 1, subTaskName = "A")
        assertFalse(st.isCompleted)
    }

    @Test fun constructor_defaultSubItems_empty() {
        val st = SubTask(id = 1, subTaskName = "A")
        assertTrue(st.subItems.isEmpty())
    }

    // ── renameSubTask ─────────────────────────────────────────────────────────

    @Test fun rename_validName_returnsTrue_updatesName() {
        val st = subTask("Old")
        assertTrue(st.renameSubTask("New"))
        assertEquals("New", st.subTaskName)
    }

    @Test fun rename_trimsWhitespace() {
        val st = subTask("Old")
        st.renameSubTask("  New Name  ")
        assertEquals("New Name", st.subTaskName)
    }

    @Test fun rename_emptyString_returnsFalse_nameUnchanged() {
        val st = subTask("Original")
        assertFalse(st.renameSubTask(""))
        assertEquals("Original", st.subTaskName)
    }

    @Test fun rename_blankString_returnsFalse_nameUnchanged() {
        val st = subTask("Original")
        assertFalse(st.renameSubTask("   "))
        assertEquals("Original", st.subTaskName)
    }

    @Test fun rename_doesNotAffectIdOrSubItems() {
        val st = SubTask(id = 99, subTaskName = "A")
        st.subItems.add(subItem(1, "SI"))
        st.renameSubTask("B")
        assertEquals(99, st.id)
        assertEquals(1, st.subItems.size)
    }

    // ── changeSubTaskStatus ───────────────────────────────────────────────────

    @Test fun changeStatus_falseToTrue() {
        val st = subTask(completed = false)
        st.changeSubTaskStatus(true)
        assertTrue(st.isCompleted)
    }

    @Test fun changeStatus_trueToFalse() {
        val st = subTask(completed = true)
        st.changeSubTaskStatus(false)
        assertFalse(st.isCompleted)
    }

    @Test fun changeStatus_doesNotAffectSubItems() {
        val st = subTask()
        st.subItems.add(subItem(1, "SI", completed = true))
        st.changeSubTaskStatus(true)
        assertTrue(st.subItems[0].isCompleted) // subitem unchanged
    }

    // ── hasSubItems ───────────────────────────────────────────────────────────

    @Test fun hasSubItems_emptyList_false() {
        assertFalse(subTask().hasSubItems())
    }

    @Test fun hasSubItems_afterAdd_true() {
        val st = subTask()
        st.addSubItem(subItem(1, "SI"))
        assertTrue(st.hasSubItems())
    }

    @Test fun hasSubItems_afterAddThenDeleteAll_false() {
        val st = subTask()
        st.addSubItem(subItem(1, "SI"))
        st.deleteSubItem(1)
        assertFalse(st.hasSubItems())
    }

    // ── addSubItem ────────────────────────────────────────────────────────────

    @Test fun addSubItem_validName_returnsTrue_appended() {
        val st = subTask()
        assertTrue(st.addSubItem(subItem(1, "First")))
        assertEquals(1, st.subItems.size)
        assertEquals("First", st.subItems[0].subItemName)
    }

    @Test fun addSubItem_multipleItems_preservesOrder() {
        val st = subTask()
        st.addSubItem(subItem(1, "A"))
        st.addSubItem(subItem(2, "B"))
        st.addSubItem(subItem(3, "C"))
        assertEquals(listOf("A", "B", "C"), st.subItems.map { it.subItemName })
    }

    @Test fun addSubItem_emptyName_returnsFalse_notAdded() {
        val st = subTask()
        assertFalse(st.addSubItem(subItem(1, "")))
        assertTrue(st.subItems.isEmpty())
    }

    @Test fun addSubItem_blankName_returnsFalse_notAdded() {
        val st = subTask()
        assertFalse(st.addSubItem(subItem(1, "   ")))
        assertTrue(st.subItems.isEmpty())
    }

    @Test fun addSubItem_doesNotAffectSubTaskNameOrStatus() {
        val st = SubTask(id = 1, subTaskName = "Parent", isCompleted = false)
        st.addSubItem(subItem(1, "Child"))
        assertEquals("Parent", st.subTaskName)
        assertFalse(st.isCompleted)
    }

    // ── deleteSubItem ─────────────────────────────────────────────────────────

    @Test fun deleteSubItem_existingId_returnsTrue_removed() {
        val st = subTask()
        st.addSubItem(subItem(1, "A"))
        assertTrue(st.deleteSubItem(1))
        assertTrue(st.subItems.isEmpty())
    }

    @Test fun deleteSubItem_nonExistingId_returnsFalse_listUnchanged() {
        val st = subTask()
        st.addSubItem(subItem(1, "A"))
        assertFalse(st.deleteSubItem(99))
        assertEquals(1, st.subItems.size)
    }

    @Test fun deleteSubItem_emptyList_returnsFalse() {
        val st = subTask()
        assertFalse(st.deleteSubItem(1))
    }

    @Test fun deleteSubItem_removesOnlyMatchingId() {
        val st = subTask()
        st.addSubItem(subItem(1, "Keep"))
        st.addSubItem(subItem(2, "Delete"))
        st.addSubItem(subItem(3, "Keep"))
        st.deleteSubItem(2)
        assertEquals(2, st.subItems.size)
        assertEquals(listOf("Keep", "Keep"), st.subItems.map { it.subItemName })
    }

    @Test fun deleteSubItem_lastItem_listBecomesEmpty() {
        val st = subTask()
        st.addSubItem(subItem(1, "Only"))
        st.deleteSubItem(1)
        assertTrue(st.subItems.isEmpty())
        assertFalse(st.hasSubItems())
    }

    // ── computeProgress ───────────────────────────────────────────────────────

    @Test fun computeProgress_noSubItems_returnsZero() {
        assertEquals(0, subTask().computeProgress())
    }

    @Test fun computeProgress_noneCompleted_returnsZero() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = false))
        st.addSubItem(subItem(2, "B", completed = false))
        assertEquals(0, st.computeProgress())
    }

    @Test fun computeProgress_allCompleted_returns100() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = true))
        st.addSubItem(subItem(2, "B", completed = true))
        assertEquals(100, st.computeProgress())
    }

    @Test fun computeProgress_halfCompleted_returns50() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = true))
        st.addSubItem(subItem(2, "B", completed = false))
        assertEquals(50, st.computeProgress())
    }

    @Test fun computeProgress_oneOfFour_returns25() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = true))
        st.addSubItem(subItem(2, "B", completed = false))
        st.addSubItem(subItem(3, "C", completed = false))
        st.addSubItem(subItem(4, "D", completed = false))
        assertEquals(25, st.computeProgress())
    }

    @Test fun computeProgress_threeOfFour_returns75() {
        val st = subTask()
        repeat(3) { i -> st.addSubItem(subItem(i + 1, "Item$i", completed = true)) }
        st.addSubItem(subItem(4, "Last", completed = false))
        assertEquals(75, st.computeProgress())
    }

    @Test fun computeProgress_singleCompleted_returns100() {
        val st = subTask()
        st.addSubItem(subItem(1, "Only", completed = true))
        assertEquals(100, st.computeProgress())
    }

    @Test fun computeProgress_singleNotCompleted_returnsZero() {
        val st = subTask()
        st.addSubItem(subItem(1, "Only", completed = false))
        assertEquals(0, st.computeProgress())
    }

    @Test fun computeProgress_updatesAfterStatusChange() {
        val st = subTask()
        val si = subItem(1, "A", completed = false)
        st.addSubItem(si)
        assertEquals(0, st.computeProgress())
        si.changeSubItemStatus(true)
        assertEquals(100, st.computeProgress())
    }

    @Test fun computeProgress_updatesAfterSubItemAdded() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = true))
        assertEquals(100, st.computeProgress())
        // Add an incomplete item — progress should drop
        st.addSubItem(subItem(2, "B", completed = false))
        assertEquals(50, st.computeProgress())
    }

    @Test fun computeProgress_updatesAfterSubItemDeleted() {
        val st = subTask()
        st.addSubItem(subItem(1, "A", completed = true))
        st.addSubItem(subItem(2, "B", completed = false))
        assertEquals(50, st.computeProgress())
        st.deleteSubItem(2)
        assertEquals(100, st.computeProgress())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun subItems_listIsIndependentAcrossInstances() {
        val st1 = SubTask(id = 1, subTaskName = "T1")
        val st2 = SubTask(id = 2, subTaskName = "T2")
        st1.addSubItem(subItem(1, "Only in T1"))
        assertTrue(st2.subItems.isEmpty())
    }

    @Test fun deleteSubItem_doesNotCorruptOtherSubItems() {
        val st = subTask()
        st.addSubItem(subItem(1, "A"))
        st.addSubItem(subItem(2, "B"))
        st.addSubItem(subItem(3, "C"))
        st.deleteSubItem(2)
        assertEquals(1, st.subItems[0].id)
        assertEquals(3, st.subItems[1].id)
        assertEquals("A", st.subItems[0].subItemName)
        assertEquals("C", st.subItems[1].subItemName)
    }

    @Test fun addSubItem_preservesExistingSubItemData() {
        val st = subTask()
        val existing = subItem(1, "Existing", completed = true)
        st.addSubItem(existing)
        st.addSubItem(subItem(2, "New"))
        // First subitem must be unchanged
        assertTrue(st.subItems[0].isCompleted)
        assertEquals("Existing", st.subItems[0].subItemName)
    }
}
