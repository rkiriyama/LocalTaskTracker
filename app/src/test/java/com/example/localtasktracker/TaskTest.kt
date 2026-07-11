package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [Task].
 * Covers: construction, rename, category CRUD, computeProgress (full
 * hierarchy roll-up through Node tree), and data-integrity guarantees.
 */
class TaskTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun task(title: String = "My Task") = Task(id = 1, title = title)

    private fun categoryNode(id: Int, name: String) =
        Node(id, name, NodeType.CATEGORY)

    private fun plainSubtask(id: Int, name: String, completed: Boolean = false) =
        Node(id, name, NodeType.SUBTASK, completed)

    /** Category pre-loaded with plain (leaf) subtasks. */
    private fun categoryWithPlain(
        catId: Int, catName: String, vararg subtasks: Pair<Int, Boolean>
    ): Node {
        val cat = categoryNode(catId, catName)
        subtasks.forEachIndexed { i, (stId, done) ->
            cat.children.add(plainSubtask(stId, "Sub${catId}_$i", done))
        }
        return cat
    }

    /** Category with one subtask that itself has subitems. */
    private fun categoryWithParentSubtask(
        catId: Int, catName: String, stId: Int, vararg subItems: Pair<Int, Boolean>
    ): Node {
        val cat = categoryNode(catId, catName)
        val sub = Node(stId, "Sub$stId", NodeType.SUBTASK)
        subItems.forEach { (siId, done) ->
            sub.children.add(Node(siId, "SI$siId", NodeType.SUBITEM, done))
        }
        cat.children.add(sub)
        return cat
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val t = Task(id = 7, title = "Shopping")
        assertEquals(7, t.id)
        assertEquals("Shopping", t.title)
        assertFalse(t.isCompleted)
        assertTrue(t.children.isEmpty())
    }

    // ── renameTask ────────────────────────────────────────────────────────────

    @Test fun rename_valid_returnsTrue_updatesTitle() {
        val t = task("Old")
        assertTrue(t.renameTask("New"))
        assertEquals("New", t.title)
    }

    @Test fun rename_trimsWhitespace() {
        val t = task("Old")
        t.renameTask("  Trimmed  ")
        assertEquals("Trimmed", t.title)
    }

    @Test fun rename_empty_returnsFalse_titleUnchanged() {
        val t = task("Original")
        assertFalse(t.renameTask(""))
        assertEquals("Original", t.title)
    }

    @Test fun rename_blank_returnsFalse_titleUnchanged() {
        val t = task("Original")
        assertFalse(t.renameTask("   "))
        assertEquals("Original", t.title)
    }

    @Test fun rename_doesNotAffectCategories() {
        val t = task("T")
        t.addCategory(categoryNode(1, "C"))
        t.renameTask("NewT")
        assertEquals(1, t.children.size)
    }

    // ── addCategory ───────────────────────────────────────────────────────────

    @Test fun addCategory_valid_returnsTrue_appended() {
        val t = task()
        assertTrue(t.addCategory(categoryNode(1, "Cat")))
        assertEquals(1, t.children.size)
    }

    @Test fun addCategory_multipleCategories_preservesOrder() {
        val t = task()
        t.addCategory(categoryNode(1, "A"))
        t.addCategory(categoryNode(2, "B"))
        t.addCategory(categoryNode(3, "C"))
        assertEquals(listOf("A", "B", "C"), t.children.map { it.name })
    }

    @Test fun addCategory_emptyName_returnsFalse_notAdded() {
        val t = task()
        assertFalse(t.addCategory(categoryNode(1, "")))
        assertTrue(t.children.isEmpty())
    }

    @Test fun addCategory_blankName_returnsFalse_notAdded() {
        val t = task()
        assertFalse(t.addCategory(categoryNode(1, "  ")))
        assertTrue(t.children.isEmpty())
    }

    // ── deleteCategory ────────────────────────────────────────────────────────

    @Test fun deleteCategory_existingId_returnsTrue_removed() {
        val t = task()
        t.addCategory(categoryNode(1, "Cat"))
        assertTrue(t.deleteCategory(1))
        assertTrue(t.children.isEmpty())
    }

    @Test fun deleteCategory_nonExistingId_returnsFalse_listUnchanged() {
        val t = task()
        t.addCategory(categoryNode(1, "Cat"))
        assertFalse(t.deleteCategory(99))
        assertEquals(1, t.children.size)
    }

    @Test fun deleteCategory_emptyList_returnsFalse() {
        assertFalse(task().deleteCategory(1))
    }

    @Test fun deleteCategory_removesOnlyMatchingId() {
        val t = task()
        t.addCategory(categoryNode(1, "Keep"))
        t.addCategory(categoryNode(2, "Delete"))
        t.addCategory(categoryNode(3, "Keep"))
        t.deleteCategory(2)
        assertEquals(listOf("Keep", "Keep"), t.children.map { it.name })
    }

    // ── findCategory ──────────────────────────────────────────────────────────

    @Test fun findCategory_existingId_returnsNode() {
        val t = task()
        val cat = categoryNode(5, "Found")
        t.addCategory(cat)
        assertEquals(cat, t.findCategory(5))
    }

    @Test fun findCategory_missingId_returnsNull() {
        assertNull(task().findCategory(99))
    }

    // ── computeProgress — empty ───────────────────────────────────────────────

    @Test fun computeProgress_noCategories_returnsZero() {
        assertEquals(0, task().computeProgress())
    }

    @Test fun computeProgress_emptyCategoryOnly_returnsZero() {
        val t = task()
        t.addCategory(categoryNode(1, "Empty"))
        assertEquals(0, t.computeProgress())
    }

    // ── computeProgress — single category, plain subtasks ─────────────────────

    @Test fun computeProgress_oneCategory_allComplete_returns100() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to true, 2 to true))
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_oneCategory_noneComplete_returnsZero() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to false, 2 to false))
        assertEquals(0, t.computeProgress())
    }

    @Test fun computeProgress_oneCategory_halfComplete_returns50() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C", 1 to true, 2 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── computeProgress — multiple categories ────────────────────────────────

    @Test fun computeProgress_twoCategories_average() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_threeCategories_average() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to true, 3 to false))
        t.addCategory(categoryWithPlain(3, "C3", 4 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── computeProgress — recursive sub-item roll-up ──────────────────────────

    @Test fun computeProgress_subItems_rollUpThroughTree() {
        val t = task()
        t.addCategory(categoryWithParentSubtask(1, "C", 10, 100 to true, 101 to false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_allSubItemsDone_returns100() {
        val t = task()
        t.addCategory(categoryWithParentSubtask(1, "C", 10, 100 to true, 101 to true))
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_noSubItemsDone_returnsZero() {
        val t = task()
        t.addCategory(categoryWithParentSubtask(1, "C", 10, 100 to false, 101 to false))
        assertEquals(0, t.computeProgress())
    }

    @Test fun computeProgress_mixedPlainAndParentSubtasks() {
        // plain(100) + parent(50) → category = 75 → task = 75
        val t = task()
        val cat = categoryNode(1, "C")
        cat.children.add(plainSubtask(1, "Plain", completed = true))
        val sub = Node(2, "Parent", NodeType.SUBTASK)
        sub.children.add(Node(10, "SI10", NodeType.SUBITEM, true))
        sub.children.add(Node(11, "SI11", NodeType.SUBITEM, false))
        cat.children.add(sub)
        t.addCategory(cat)
        assertEquals(75, t.computeProgress())
    }

    // ── computeProgress — live updates ───────────────────────────────────────

    @Test fun computeProgress_updatesWhenSubTaskCompleted() {
        val t = task()
        val st = plainSubtask(1, "A", false)
        val cat = categoryNode(1, "C")
        cat.children.add(st)
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
        st.changeStatus(true)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenSubItemCompleted() {
        val t = task()
        val si = Node(10, "SI", NodeType.SUBITEM, false)
        val sub = Node(1, "Parent", NodeType.SUBTASK)
        sub.children.add(si)
        val cat = categoryNode(1, "C")
        cat.children.add(sub)
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
        si.changeStatus(true)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenCategoryDeleted() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
        t.deleteCategory(2)
        assertEquals(100, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenCategoryAdded() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        assertEquals(100, t.computeProgress())
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun childrenListIsIndependentAcrossTasks() {
        val t1 = Task(id = 1, title = "T1")
        val t2 = Task(id = 2, title = "T2")
        t1.addCategory(categoryNode(1, "OnlyInT1"))
        assertTrue(t2.children.isEmpty())
    }

    @Test fun deleteCategory_doesNotCorruptRemainingCategories() {
        val t = task()
        t.addCategory(categoryNode(1, "A"))
        t.addCategory(categoryNode(2, "B"))
        t.addCategory(categoryNode(3, "C"))
        t.deleteCategory(2)
        assertEquals(1, t.children[0].id)
        assertEquals(3, t.children[1].id)
    }

    @Test fun addCategory_preservesExistingCategoryData() {
        val t = task()
        val existing = categoryNode(1, "Existing").also {
            it.children.add(plainSubtask(1, "Sub", completed = true))
        }
        t.addCategory(existing)
        t.addCategory(categoryNode(2, "New"))
        assertEquals(1, t.children[0].children.size)
        assertTrue(t.children[0].children[0].isCompleted)
    }
}
