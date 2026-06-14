package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SubItem].
 * Covers: construction, rename, status change, and edge/guard cases.
 */
class SubItemTest {

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val si = SubItem(id = 1, subItemName = "Buy milk", isCompleted = false)
        assertEquals(1, si.id)
        assertEquals("Buy milk", si.subItemName)
        assertFalse(si.isCompleted)
    }

    @Test fun constructor_defaultIsCompleted_false() {
        val si = SubItem(id = 2, subItemName = "Walk dog")
        assertFalse(si.isCompleted)
    }

    @Test fun constructor_completedTrue() {
        val si = SubItem(id = 3, subItemName = "Done item", isCompleted = true)
        assertTrue(si.isCompleted)
    }

    // ── renameSubItem ─────────────────────────────────────────────────────────

    @Test fun rename_validName_returnsTrue_updatesName() {
        val si = SubItem(id = 1, subItemName = "Old")
        val result = si.renameSubItem("New")
        assertTrue(result)
        assertEquals("New", si.subItemName)
    }

    @Test fun rename_trimsWhitespace() {
        val si = SubItem(id = 1, subItemName = "Old")
        si.renameSubItem("  Trimmed  ")
        assertEquals("Trimmed", si.subItemName)
    }

    @Test fun rename_emptyString_returnsFalse_nameUnchanged() {
        val si = SubItem(id = 1, subItemName = "Original")
        val result = si.renameSubItem("")
        assertFalse(result)
        assertEquals("Original", si.subItemName)
    }

    @Test fun rename_blankString_returnsFalse_nameUnchanged() {
        val si = SubItem(id = 1, subItemName = "Original")
        val result = si.renameSubItem("   ")
        assertFalse(result)
        assertEquals("Original", si.subItemName)
    }

    @Test fun rename_sameNameAsExisting_succeeds() {
        val si = SubItem(id = 1, subItemName = "Same")
        val result = si.renameSubItem("Same")
        assertTrue(result)
        assertEquals("Same", si.subItemName)
    }

    @Test fun rename_singleCharacter_succeeds() {
        val si = SubItem(id = 1, subItemName = "A")
        val result = si.renameSubItem("B")
        assertTrue(result)
        assertEquals("B", si.subItemName)
    }

    // ── changeSubItemStatus ───────────────────────────────────────────────────

    @Test fun changeStatus_falseToTrue() {
        val si = SubItem(id = 1, subItemName = "Item", isCompleted = false)
        si.changeSubItemStatus(true)
        assertTrue(si.isCompleted)
    }

    @Test fun changeStatus_trueToFalse() {
        val si = SubItem(id = 1, subItemName = "Item", isCompleted = true)
        si.changeSubItemStatus(false)
        assertFalse(si.isCompleted)
    }

    @Test fun changeStatus_trueToTrue_noChange() {
        val si = SubItem(id = 1, subItemName = "Item", isCompleted = true)
        si.changeSubItemStatus(true)
        assertTrue(si.isCompleted)
    }

    @Test fun changeStatus_falseToFalse_noChange() {
        val si = SubItem(id = 1, subItemName = "Item", isCompleted = false)
        si.changeSubItemStatus(false)
        assertFalse(si.isCompleted)
    }

    // ── Identity / equality ───────────────────────────────────────────────────

    @Test fun dataClass_equalityByAllFields() {
        val a = SubItem(id = 1, subItemName = "X", isCompleted = false)
        val b = SubItem(id = 1, subItemName = "X", isCompleted = false)
        assertEquals(a, b)
    }

    @Test fun dataClass_differentId_notEqual() {
        val a = SubItem(id = 1, subItemName = "X")
        val b = SubItem(id = 2, subItemName = "X")
        assertNotEquals(a, b)
    }

    @Test fun dataClass_differentName_notEqual() {
        val a = SubItem(id = 1, subItemName = "X")
        val b = SubItem(id = 1, subItemName = "Y")
        assertNotEquals(a, b)
    }

    @Test fun dataClass_differentStatus_notEqual() {
        val a = SubItem(id = 1, subItemName = "X", isCompleted = true)
        val b = SubItem(id = 1, subItemName = "X", isCompleted = false)
        assertNotEquals(a, b)
    }

    @Test fun rename_doesNotAffectId() {
        val si = SubItem(id = 42, subItemName = "Before")
        si.renameSubItem("After")
        assertEquals(42, si.id)
    }

    @Test fun changeStatus_doesNotAffectNameOrId() {
        val si = SubItem(id = 7, subItemName = "Stable")
        si.changeSubItemStatus(true)
        assertEquals(7, si.id)
        assertEquals("Stable", si.subItemName)
    }
}
