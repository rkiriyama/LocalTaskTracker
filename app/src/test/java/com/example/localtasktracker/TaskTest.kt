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

    private fun plainNode(id: Int, name: String, completed: Boolean = false) =
        Node(id, name, NodeType.NODE, completed)

    /** Category pre-loaded with plain (leaf) nodes. */
    private fun categoryWithPlain(
        catId: Int, catName: String, vararg children: Pair<Int, Boolean>
    ): Node {
        val cat = categoryNode(catId, catName)
        children.forEachIndexed { i, (childId, done) ->
            cat.children.add(plainNode(childId, "Child${catId}_$i", done))
        }
        return cat
    }

    /** Category with one child node that itself has children. */
    private fun categoryWithNestedNode(
        catId: Int, catName: String, childId: Int, vararg leaves: Pair<Int, Boolean>
    ): Node {
        val cat = categoryNode(catId, catName)
        val child = Node(childId, "Child$childId", NodeType.NODE)
        leaves.forEach { (leafId, done) ->
            child.children.add(Node(leafId, "Leaf$leafId", NodeType.NODE, done))
        }
        cat.children.add(child)
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

    // ── deleteCategory ────────────────────────────────────────────────────────

    @Test fun deleteCategory_existingId_returnsTrue_removed() {
        val t = task()
        t.addCategory(categoryNode(1, "Cat"))
        assertTrue(t.deleteCategory(1))
        assertTrue(t.children.isEmpty())
    }

    @Test fun deleteCategory_nonExistingId_returnsFalse() {
        val t = task()
        t.addCategory(categoryNode(1, "Cat"))
        assertFalse(t.deleteCategory(99))
        assertEquals(1, t.children.size)
    }

    @Test fun deleteCategory_emptyList_returnsFalse() {
        assertFalse(task().deleteCategory(1))
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

    // ── computeProgress ───────────────────────────────────────────────────────

    @Test fun computeProgress_noCategories_returnsZero() {
        assertEquals(0, task().computeProgress())
    }

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

    @Test fun computeProgress_twoCategories_average() {
        val t = task()
        t.addCategory(categoryWithPlain(1, "C1", 1 to true))
        t.addCategory(categoryWithPlain(2, "C2", 2 to false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_nestedNodes_rollUp() {
        val t = task()
        t.addCategory(categoryWithNestedNode(1, "C", 10, 100 to true, 101 to false))
        assertEquals(50, t.computeProgress())
    }

    @Test fun computeProgress_updatesWhenChildCompleted() {
        val t = task()
        val child = plainNode(1, "A", false)
        val cat = categoryNode(1, "C")
        cat.children.add(child)
        t.addCategory(cat)
        assertEquals(0, t.computeProgress())
        child.changeStatus(true)
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
}
