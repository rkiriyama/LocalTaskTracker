package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [Node] — the unified recursive tree node.
 *
 * Covers: construction, rename, status, children CRUD, hasChildren,
 * computeProgress (leaf, single-level, multi-level), completedChildCount,
 * and data-integrity guarantees.
 *
 * These tests replace the old TaskCategoryTest, SubTaskTest, and SubItemTest.
 */
class NodeTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun category(id: Int = 1, name: String = "Cat") =
        Node(id, name, NodeType.CATEGORY)

    private fun subtask(id: Int = 1, name: String = "Sub", completed: Boolean = false) =
        Node(id, name, NodeType.SUBTASK, completed)

    private fun subitem(id: Int = 1, name: String = "SI", completed: Boolean = false) =
        Node(id, name, NodeType.SUBITEM, completed)

    /** Category pre-loaded with plain (leaf) subtasks. */
    private fun categoryWithSubtasks(
        catId: Int, catName: String, vararg subtasks: Pair<Int, Boolean>
    ): Node {
        val cat = category(catId, catName)
        subtasks.forEachIndexed { i, (stId, done) ->
            cat.children.add(subtask(stId, "Sub${catId}_$i", done))
        }
        return cat
    }

    /** Subtask pre-loaded with subitems. */
    private fun subtaskWithSubItems(
        stId: Int, stName: String, vararg items: Pair<Int, Boolean>
    ): Node {
        val st = subtask(stId, stName)
        items.forEach { (siId, done) -> st.children.add(subitem(siId, "SI$siId", done)) }
        return st
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test fun constructor_setsFields() {
        val n = Node(id = 7, name = "Work", nodeType = NodeType.CATEGORY)
        assertEquals(7, n.id)
        assertEquals("Work", n.name)
        assertEquals(NodeType.CATEGORY, n.nodeType)
        assertFalse(n.isCompleted)
        assertTrue(n.children.isEmpty())
    }

    @Test fun constructor_completedDefault_false() {
        assertFalse(Node(1, "A", NodeType.SUBTASK).isCompleted)
    }

    @Test fun constructor_completedTrue() {
        assertTrue(Node(1, "A", NodeType.SUBITEM, isCompleted = true).isCompleted)
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test fun rename_valid_returnsTrue_updatesName() {
        val n = category(name = "Old")
        assertTrue(n.rename("New"))
        assertEquals("New", n.name)
    }

    @Test fun rename_trimsWhitespace() {
        val n = category(name = "Old")
        n.rename("  Trimmed  ")
        assertEquals("Trimmed", n.name)
    }

    @Test fun rename_empty_returnsFalse_nameUnchanged() {
        val n = category(name = "Original")
        assertFalse(n.rename(""))
        assertEquals("Original", n.name)
    }

    @Test fun rename_blank_returnsFalse_nameUnchanged() {
        val n = category(name = "Original")
        assertFalse(n.rename("   "))
        assertEquals("Original", n.name)
    }

    @Test fun rename_doesNotAffectChildren() {
        val n = category()
        n.children.add(subtask(1, "Child"))
        n.rename("NewCat")
        assertEquals(1, n.children.size)
    }

    // ── changeStatus ──────────────────────────────────────────────────────────

    @Test fun changeStatus_falseToTrue() {
        val n = subtask(completed = false)
        n.changeStatus(true)
        assertTrue(n.isCompleted)
    }

    @Test fun changeStatus_trueToFalse() {
        val n = subtask(completed = true)
        n.changeStatus(false)
        assertFalse(n.isCompleted)
    }

    @Test fun changeStatus_doesNotAffectChildren() {
        val n = subtask()
        n.children.add(subitem(completed = true))
        n.changeStatus(true)
        assertTrue(n.children[0].isCompleted) // child unchanged
    }

    // ── hasChildren ───────────────────────────────────────────────────────────

    @Test fun hasChildren_emptyList_false() {
        assertFalse(category().hasChildren())
    }

    @Test fun hasChildren_afterAddChild_true() {
        val n = category()
        n.addChild(subtask())
        assertTrue(n.hasChildren())
    }

    @Test fun hasChildren_afterAddThenRemoveAll_false() {
        val n = category()
        n.addChild(subtask(1, "X"))
        n.removeChild(1)
        assertFalse(n.hasChildren())
    }

    // ── addChild ──────────────────────────────────────────────────────────────

    @Test fun addChild_validName_returnsTrue_appended() {
        val n = category()
        assertTrue(n.addChild(subtask(1, "First")))
        assertEquals(1, n.children.size)
    }

    @Test fun addChild_multipleItems_preservesOrder() {
        val n = category()
        n.addChild(subtask(1, "A"))
        n.addChild(subtask(2, "B"))
        n.addChild(subtask(3, "C"))
        assertEquals(listOf("A", "B", "C"), n.children.map { it.name })
    }

    @Test fun addChild_emptyName_returnsFalse_notAdded() {
        val n = category()
        assertFalse(n.addChild(subtask(1, "")))
        assertTrue(n.children.isEmpty())
    }

    @Test fun addChild_blankName_returnsFalse_notAdded() {
        val n = category()
        assertFalse(n.addChild(subtask(1, "   ")))
        assertTrue(n.children.isEmpty())
    }

    // ── removeChild ───────────────────────────────────────────────────────────

    @Test fun removeChild_existingId_returnsTrue_removed() {
        val n = category()
        n.addChild(subtask(1, "A"))
        assertTrue(n.removeChild(1))
        assertTrue(n.children.isEmpty())
    }

    @Test fun removeChild_nonExistingId_returnsFalse_listUnchanged() {
        val n = category()
        n.addChild(subtask(1, "A"))
        assertFalse(n.removeChild(99))
        assertEquals(1, n.children.size)
    }

    @Test fun removeChild_emptyList_returnsFalse() {
        assertFalse(category().removeChild(1))
    }

    @Test fun removeChild_removesOnlyMatchingId() {
        val n = category()
        n.addChild(subtask(1, "Keep"))
        n.addChild(subtask(2, "Delete"))
        n.addChild(subtask(3, "Keep"))
        n.removeChild(2)
        assertEquals(listOf("Keep", "Keep"), n.children.map { it.name })
    }

    // ── findChild ─────────────────────────────────────────────────────────────

    @Test fun findChild_existingId_returnsNode() {
        val n = category()
        val child = subtask(5, "Found")
        n.addChild(child)
        assertEquals(child, n.findChild(5))
    }

    @Test fun findChild_missingId_returnsNull() {
        assertNull(category().findChild(99))
    }

    // ── computeProgress — leaf nodes ──────────────────────────────────────────

    @Test fun computeProgress_leafCompleted_returns100() {
        assertEquals(100, subitem(completed = true).computeProgress())
    }

    @Test fun computeProgress_leafNotCompleted_returnsZero() {
        assertEquals(0, subitem(completed = false).computeProgress())
    }

    @Test fun computeProgress_subtaskLeaf_completed_returns100() {
        assertEquals(100, subtask(completed = true).computeProgress())
    }

    @Test fun computeProgress_subtaskLeaf_notCompleted_returnsZero() {
        assertEquals(0, subtask(completed = false).computeProgress())
    }

    // ── computeProgress — single level of children ────────────────────────────

    @Test fun computeProgress_noChildren_returnsZero() {
        assertEquals(0, category().computeProgress())
    }

    @Test fun computeProgress_allChildrenComplete_returns100() {
        val n = categoryWithSubtasks(1, "C", 1 to true, 2 to true)
        assertEquals(100, n.computeProgress())
    }

    @Test fun computeProgress_noChildrenComplete_returnsZero() {
        val n = categoryWithSubtasks(1, "C", 1 to false, 2 to false)
        assertEquals(0, n.computeProgress())
    }

    @Test fun computeProgress_halfChildrenComplete_returns50() {
        val n = categoryWithSubtasks(1, "C", 1 to true, 2 to false)
        assertEquals(50, n.computeProgress())
    }

    @Test fun computeProgress_oneOfFour_returns25() {
        val n = categoryWithSubtasks(1, "C", 1 to true, 2 to false, 3 to false, 4 to false)
        assertEquals(25, n.computeProgress())
    }

    // ── computeProgress — recursive (category → subtask → subitem) ────────────

    @Test fun computeProgress_recursive_allLeavesDone_returns100() {
        val cat = category(1, "C")
        cat.children.add(subtaskWithSubItems(1, "Parent", 10 to true, 11 to true))
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_noLeavesDone_returnsZero() {
        val cat = category(1, "C")
        cat.children.add(subtaskWithSubItems(1, "Parent", 10 to false, 11 to false))
        assertEquals(0, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_halfLeavesDone_returns50() {
        val cat = category(1, "C")
        cat.children.add(subtaskWithSubItems(1, "Parent", 10 to true, 11 to false))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_mixedPlainAndParentChildren() {
        // plain subtask done (100) + parent subtask half done (50) → avg = 75
        val cat = category(1, "C")
        cat.children.add(subtask(1, "Plain", completed = true))
        cat.children.add(subtaskWithSubItems(2, "Parent", 10 to true, 11 to false))
        assertEquals(75, cat.computeProgress())
    }

    @Test fun computeProgress_deepNesting_threeLevels() {
        // category → subtask → subitem (3 levels)
        // 1 of 2 subitems done → subtask = 50 → category = 50
        val cat = category(1, "C")
        val sub = subtask(1, "Sub")
        sub.children.add(subitem(10, "Done",    completed = true))
        sub.children.add(subitem(11, "Pending", completed = false))
        cat.children.add(sub)
        assertEquals(50, cat.computeProgress())
    }

    // ── computeProgress — live updates ───────────────────────────────────────

    @Test fun computeProgress_updatesWhenChildStatusChanges() {
        val cat = category()
        val child = subtask(1, "A", false)
        cat.children.add(child)
        assertEquals(0, cat.computeProgress())
        child.changeStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenGrandchildStatusChanges() {
        val cat = category()
        val sub = subtask(1, "Sub")
        val si  = subitem(10, "SI", false)
        sub.children.add(si)
        cat.children.add(sub)
        assertEquals(0, cat.computeProgress())
        si.changeStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenChildAdded() {
        val cat = category()
        cat.children.add(subtask(1, "Done", completed = true))
        assertEquals(100, cat.computeProgress())
        cat.children.add(subtask(2, "New", completed = false))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenChildRemoved() {
        val cat = category()
        cat.children.add(subtask(1, "Done",    completed = true))
        cat.children.add(subtask(2, "Pending", completed = false))
        assertEquals(50, cat.computeProgress())
        cat.removeChild(2)
        assertEquals(100, cat.computeProgress())
    }

    // ── completedChildCount ───────────────────────────────────────────────────

    @Test fun completedChildCount_none_returnsZero() {
        val cat = category()
        cat.children.add(subtask(1, "A", false))
        assertEquals(0, cat.completedChildCount())
    }

    @Test fun completedChildCount_some_returnsCount() {
        val cat = category()
        cat.children.add(subtask(1, "A", true))
        cat.children.add(subtask(2, "B", false))
        cat.children.add(subtask(3, "C", true))
        assertEquals(2, cat.completedChildCount())
    }

    @Test fun completedChildCount_emptyChildren_returnsZero() {
        assertEquals(0, category().completedChildCount())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun childrenListIsIndependentAcrossInstances() {
        val n1 = category(1, "C1")
        val n2 = category(2, "C2")
        n1.addChild(subtask(1, "OnlyInN1"))
        assertTrue(n2.children.isEmpty())
    }

    @Test fun removeChild_doesNotCorruptRemainingChildren() {
        val n = category()
        n.addChild(subtask(1, "A"))
        n.addChild(subtask(2, "B"))
        n.addChild(subtask(3, "C"))
        n.removeChild(2)
        assertEquals(1, n.children[0].id)
        assertEquals(3, n.children[1].id)
    }

    @Test fun addChild_preservesExistingChildData() {
        val n = category()
        val existing = subtask(1, "Existing", completed = true)
        n.addChild(existing)
        n.addChild(subtask(2, "New"))
        assertTrue(n.children[0].isCompleted)
        assertEquals("Existing", n.children[0].name)
    }

    // ── Any nodeType can hold children (key invariant of unified model) ────────

    @Test fun subtaskNode_canHoldChildNodes() {
        val sub = subtask(1, "Parent Sub")
        sub.addChild(subitem(10, "Child SI"))
        assertEquals(1, sub.children.size)
        assertTrue(sub.hasChildren())
    }

    @Test fun subitemNode_canHoldChildNodes() {
        val si = subitem(1, "Parent SI")
        si.addChild(subitem(2, "Nested SI"))
        assertEquals(1, si.children.size)
        assertTrue(si.hasChildren())
    }

    @Test fun categoryNode_canHoldCategoryChildren() {
        // A category can itself be a child of another category
        val outer = category(1, "Outer")
        val inner = category(2, "Inner")
        outer.addChild(inner)
        assertEquals(1, outer.children.size)
        assertEquals(NodeType.CATEGORY, outer.children[0].nodeType)
    }
}
