package com.example.localtasktracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [Node] — the unified recursive tree node.
 *
 * Covers: construction, rename, status, children CRUD, hasChildren,
 * computeProgress (leaf, single-level, multi-level, unlimited depth),
 * completedChildCount, and data-integrity guarantees.
 */
class NodeTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun category(id: Int = 1, name: String = "Cat") =
        Node(id, name, NodeType.CATEGORY)

    private fun node(id: Int = 1, name: String = "N", completed: Boolean = false) =
        Node(id, name, NodeType.NODE, completed)

    /** Category pre-loaded with plain (leaf) child nodes. */
    private fun categoryWithChildren(
        catId: Int, catName: String, vararg children: Pair<Int, Boolean>
    ): Node {
        val cat = category(catId, catName)
        children.forEachIndexed { i, (childId, done) ->
            cat.children.add(node(childId, "Child${catId}_$i", done))
        }
        return cat
    }

    /** Node pre-loaded with child nodes (for nested levels). */
    private fun nodeWithChildren(
        parentId: Int, parentName: String, vararg items: Pair<Int, Boolean>
    ): Node {
        val parent = node(parentId, parentName)
        items.forEach { (childId, done) ->
            parent.children.add(node(childId, "Sub$childId", done))
        }
        return parent
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
        assertFalse(Node(1, "A", NodeType.NODE).isCompleted)
    }

    @Test fun constructor_completedTrue() {
        assertTrue(Node(1, "A", NodeType.NODE, isCompleted = true).isCompleted)
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
        n.children.add(node(1, "Child"))
        n.rename("NewCat")
        assertEquals(1, n.children.size)
    }

    // ── changeStatus ──────────────────────────────────────────────────────────

    @Test fun changeStatus_falseToTrue() {
        val n = node(completed = false)
        n.changeStatus(true)
        assertTrue(n.isCompleted)
    }

    @Test fun changeStatus_trueToFalse() {
        val n = node(completed = true)
        n.changeStatus(false)
        assertFalse(n.isCompleted)
    }

    @Test fun changeStatus_doesNotAffectChildren() {
        val n = node()
        n.children.add(node(2, "Child", completed = true))
        n.changeStatus(true)
        assertTrue(n.children[0].isCompleted) // child unchanged
    }

    // ── hasChildren ───────────────────────────────────────────────────────────

    @Test fun hasChildren_emptyList_false() {
        assertFalse(category().hasChildren())
    }

    @Test fun hasChildren_afterAddChild_true() {
        val n = category()
        n.addChild(node())
        assertTrue(n.hasChildren())
    }

    @Test fun hasChildren_afterAddThenRemoveAll_false() {
        val n = category()
        n.addChild(node(1, "X"))
        n.removeChild(1)
        assertFalse(n.hasChildren())
    }

    // ── addChild ──────────────────────────────────────────────────────────────

    @Test fun addChild_validName_returnsTrue_appended() {
        val n = category()
        assertTrue(n.addChild(node(1, "First")))
        assertEquals(1, n.children.size)
    }

    @Test fun addChild_multipleItems_preservesOrder() {
        val n = category()
        n.addChild(node(1, "A"))
        n.addChild(node(2, "B"))
        n.addChild(node(3, "C"))
        assertEquals(listOf("A", "B", "C"), n.children.map { it.name })
    }

    @Test fun addChild_emptyName_returnsFalse_notAdded() {
        val n = category()
        assertFalse(n.addChild(node(1, "")))
        assertTrue(n.children.isEmpty())
    }

    @Test fun addChild_blankName_returnsFalse_notAdded() {
        val n = category()
        assertFalse(n.addChild(node(1, "   ")))
        assertTrue(n.children.isEmpty())
    }

    // ── removeChild ───────────────────────────────────────────────────────────

    @Test fun removeChild_existingId_returnsTrue_removed() {
        val n = category()
        n.addChild(node(1, "A"))
        assertTrue(n.removeChild(1))
        assertTrue(n.children.isEmpty())
    }

    @Test fun removeChild_nonExistingId_returnsFalse_listUnchanged() {
        val n = category()
        n.addChild(node(1, "A"))
        assertFalse(n.removeChild(99))
        assertEquals(1, n.children.size)
    }

    @Test fun removeChild_emptyList_returnsFalse() {
        assertFalse(category().removeChild(1))
    }

    @Test fun removeChild_removesOnlyMatchingId() {
        val n = category()
        n.addChild(node(1, "Keep"))
        n.addChild(node(2, "Delete"))
        n.addChild(node(3, "Keep"))
        n.removeChild(2)
        assertEquals(listOf("Keep", "Keep"), n.children.map { it.name })
    }

    // ── findChild ─────────────────────────────────────────────────────────────

    @Test fun findChild_existingId_returnsNode() {
        val n = category()
        val child = node(5, "Found")
        n.addChild(child)
        assertEquals(child, n.findChild(5))
    }

    @Test fun findChild_missingId_returnsNull() {
        assertNull(category().findChild(99))
    }

    // ── computeProgress — leaf nodes ──────────────────────────────────────────

    @Test fun computeProgress_leafCompleted_returns100() {
        assertEquals(100, node(completed = true).computeProgress())
    }

    @Test fun computeProgress_leafNotCompleted_returnsZero() {
        assertEquals(0, node(completed = false).computeProgress())
    }

    // ── computeProgress — single level of children ────────────────────────────

    @Test fun computeProgress_noChildren_category_returnsZero() {
        assertEquals(0, category().computeProgress())
    }

    @Test fun computeProgress_allChildrenComplete_returns100() {
        val n = categoryWithChildren(1, "C", 1 to true, 2 to true)
        assertEquals(100, n.computeProgress())
    }

    @Test fun computeProgress_noChildrenComplete_returnsZero() {
        val n = categoryWithChildren(1, "C", 1 to false, 2 to false)
        assertEquals(0, n.computeProgress())
    }

    @Test fun computeProgress_halfChildrenComplete_returns50() {
        val n = categoryWithChildren(1, "C", 1 to true, 2 to false)
        assertEquals(50, n.computeProgress())
    }

    @Test fun computeProgress_oneOfFour_returns25() {
        val n = categoryWithChildren(1, "C", 1 to true, 2 to false, 3 to false, 4 to false)
        assertEquals(25, n.computeProgress())
    }

    // ── computeProgress — recursive (multi-level) ─────────────────────────────

    @Test fun computeProgress_recursive_allLeavesDone_returns100() {
        val cat = category(1, "C")
        cat.children.add(nodeWithChildren(1, "Parent", 10 to true, 11 to true))
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_noLeavesDone_returnsZero() {
        val cat = category(1, "C")
        cat.children.add(nodeWithChildren(1, "Parent", 10 to false, 11 to false))
        assertEquals(0, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_halfLeavesDone_returns50() {
        val cat = category(1, "C")
        cat.children.add(nodeWithChildren(1, "Parent", 10 to true, 11 to false))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_recursive_mixedPlainAndParentChildren() {
        // plain node done (100) + parent node half done (50) → avg = 75
        val cat = category(1, "C")
        cat.children.add(node(1, "Plain", completed = true))
        cat.children.add(nodeWithChildren(2, "Parent", 10 to true, 11 to false))
        assertEquals(75, cat.computeProgress())
    }

    // ── computeProgress — deep nesting (unlimited depth) ──────────────────────

    @Test fun computeProgress_fourLevelsDeep() {
        // cat → node1 → node2 → leaf(done)
        val cat = category(1, "C")
        val level1 = node(2, "L1")
        val level2 = node(3, "L2")
        val leaf = node(4, "Leaf", completed = true)
        level2.children.add(leaf)
        level1.children.add(level2)
        cat.children.add(level1)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_fiveLevelsDeep_mixed() {
        // cat → n1 → n2 → n3 → [leaf_done, leaf_not]
        val cat = category(1, "C")
        val n1 = node(2, "N1")
        val n2 = node(3, "N2")
        val n3 = node(4, "N3")
        n3.children.add(node(10, "Done", completed = true))
        n3.children.add(node(11, "Pending", completed = false))
        n2.children.add(n3)
        n1.children.add(n2)
        cat.children.add(n1)
        assertEquals(50, cat.computeProgress())
    }

    // ── computeProgress — live updates ───────────────────────────────────────

    @Test fun computeProgress_updatesWhenChildStatusChanges() {
        val cat = category()
        val child = node(1, "A", false)
        cat.children.add(child)
        assertEquals(0, cat.computeProgress())
        child.changeStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenGrandchildStatusChanges() {
        val cat = category()
        val mid = node(1, "Mid")
        val leaf = node(10, "Leaf", false)
        mid.children.add(leaf)
        cat.children.add(mid)
        assertEquals(0, cat.computeProgress())
        leaf.changeStatus(true)
        assertEquals(100, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenChildAdded() {
        val cat = category()
        cat.children.add(node(1, "Done", completed = true))
        assertEquals(100, cat.computeProgress())
        cat.children.add(node(2, "New", completed = false))
        assertEquals(50, cat.computeProgress())
    }

    @Test fun computeProgress_updatesWhenChildRemoved() {
        val cat = category()
        cat.children.add(node(1, "Done", completed = true))
        cat.children.add(node(2, "Pending", completed = false))
        assertEquals(50, cat.computeProgress())
        cat.removeChild(2)
        assertEquals(100, cat.computeProgress())
    }

    // ── completedChildCount ───────────────────────────────────────────────────

    @Test fun completedChildCount_none_returnsZero() {
        val cat = category()
        cat.children.add(node(1, "A", false))
        assertEquals(0, cat.completedChildCount())
    }

    @Test fun completedChildCount_some_returnsCount() {
        val cat = category()
        cat.children.add(node(1, "A", true))
        cat.children.add(node(2, "B", false))
        cat.children.add(node(3, "C", true))
        assertEquals(2, cat.completedChildCount())
    }

    @Test fun completedChildCount_emptyChildren_returnsZero() {
        assertEquals(0, category().completedChildCount())
    }

    // ── Data integrity ────────────────────────────────────────────────────────

    @Test fun childrenListIsIndependentAcrossInstances() {
        val n1 = category(1, "C1")
        val n2 = category(2, "C2")
        n1.addChild(node(1, "OnlyInN1"))
        assertTrue(n2.children.isEmpty())
    }

    @Test fun removeChild_doesNotCorruptRemainingChildren() {
        val n = category()
        n.addChild(node(1, "A"))
        n.addChild(node(2, "B"))
        n.addChild(node(3, "C"))
        n.removeChild(2)
        assertEquals(1, n.children[0].id)
        assertEquals(3, n.children[1].id)
    }

    @Test fun addChild_preservesExistingChildData() {
        val n = category()
        val existing = node(1, "Existing", completed = true)
        n.addChild(existing)
        n.addChild(node(2, "New"))
        assertTrue(n.children[0].isCompleted)
        assertEquals("Existing", n.children[0].name)
    }

    // ── Any nodeType can hold children (key invariant) ────────────────────────

    @Test fun nodeType_NODE_canHoldChildren() {
        val parent = node(1, "Parent")
        parent.addChild(node(10, "Child"))
        assertEquals(1, parent.children.size)
        assertTrue(parent.hasChildren())
    }

    @Test fun nodeType_CATEGORY_canHoldCategoryChildren() {
        val outer = category(1, "Outer")
        val inner = category(2, "Inner")
        outer.addChild(inner)
        assertEquals(1, outer.children.size)
        assertEquals(NodeType.CATEGORY, outer.children[0].nodeType)
    }

    @Test fun unlimitedDepth_fiveNestedLevels() {
        val root = category(1, "Root")
        var current: Node = root
        for (i in 2..6) {
            val child = node(i, "Level$i")
            current.addChild(child)
            current = child
        }
        // Verify depth
        assertEquals("Level2", root.children[0].name)
        assertEquals("Level3", root.children[0].children[0].name)
        assertEquals("Level4", root.children[0].children[0].children[0].name)
        assertEquals("Level5", root.children[0].children[0].children[0].children[0].name)
        assertEquals("Level6", root.children[0].children[0].children[0].children[0].children[0].name)
    }
}
