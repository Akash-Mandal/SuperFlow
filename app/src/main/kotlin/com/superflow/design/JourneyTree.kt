package com.superflow.design

/**
 * The Journey hierarchy (§11.2).
 *
 * Journey is currently four flat lists stacked on top of each other with
 * section headers. The plan asks for a tree that shows the actual chain -
 * identity shapes the goal, the goal needs a system, the system runs on
 * habits - because that chain is the whole thesis of the app and the flat
 * list hides it completely.
 *
 * Everything here is arithmetic over ids, which is exactly the part that
 * goes wrong in a screen like this: a goal whose identity was deleted
 * silently disappears, a collapsed parent hides a child that then cannot be
 * reached, a "4 habits" badge counts archived ones. So the flattening,
 * counting and orphan handling live here where they can be asserted, and
 * `ui/` only draws rows.
 *
 * The one invariant that matters more than any other: **every node handed in
 * comes back out**, exactly once, whatever the state of its parent links.
 * There is no arrangement of data that makes one of the user's entities
 * unreachable.
 */
object JourneyTree {

    /* ------------------------------------------------------------- kinds */

    /**
     * The four levels of the hierarchy.
     *
     * `accent` is the symbolic colour role from §6.5; `ui/` maps it to a real
     * colour, since this package cannot see R or a theme.
     */
    enum class Kind(
        val key: String,
        val label: String,
        val plural: String,
        /** Position in the chain, 0 = top. */
        val rank: Int,
        val accent: String,
    ) {
        IDENTITY("identity", "Identity", "Identities", 0, "primary"),
        GOAL("goal", "Goal", "Goals", 1, "secondary"),
        SYSTEM("system", "System", "Systems", 2, "tertiary"),
        HABIT("habit", "Habit", "Habits", 3, "neutral");

        /** The kind a node of this kind may link upward to. */
        val parent: Kind? get() = ordered.getOrNull(rank - 1)

        /** The kind that may link upward to this one. */
        val child: Kind? get() = ordered.getOrNull(rank + 1)

        companion object {
            val ordered: List<Kind> = listOf(IDENTITY, GOAL, SYSTEM, HABIT)
            fun byKey(key: String): Kind? = ordered.firstOrNull { it.key == key }
        }
    }

    /** The breadcrumb printed under the screen title. */
    val chainLabel: String = Kind.ordered.joinToString(" \u2192 ") { it.label }

    /* ------------------------------------------------------------- input */

    /**
     * One entity, flattened out of the repository.
     *
     * `parentId` is whatever the entity stores - it is *not* trusted: it may
     * name something deleted, or something of the wrong kind after an import.
     * Both cases are handled as "unlinked" rather than as a crash.
     */
    data class Node(
        val id: String,
        val kind: Kind,
        val parentId: String?,
        val title: String,
        val detail: String = "",
        /**
         * Whether this entity is currently doing anything: an active habit,
         * a goal being pursued. Drives the dormant treatment, not visibility.
         */
        val active: Boolean = true,
        val archived: Boolean = false,
    )

    /* ------------------------------------------------------------ output */

    /** A node placed in the tree, ready to draw. */
    data class Row(
        val node: Node,
        /** Indent level: how many ancestors are actually present. */
        val depth: Int,
        /** Direct children, archived ones included. */
        val childCount: Int,
        /** Active, non-archived habits anywhere below this node. */
        val habitCount: Int,
        /** Everything below this node, at any depth. */
        val descendantCount: Int,
        /** True when this node's children are currently shown. */
        val expanded: Boolean,
        /** Last among its siblings - the connector elbow instead of a tee. */
        val last: Boolean,
        /**
         * Nothing below it is running. Drawn at reduced weight: still
         * legible, clearly not part of today's system.
         */
        val dormant: Boolean,
        /** Its stored parent is missing, archived away or the wrong kind. */
        val orphan: Boolean,
    ) {
        val expandable: Boolean get() = childCount > 0
        /** Stable across reorders and title edits; safe as a list key. */
        val key: String get() = node.kind.key + ":" + node.id
    }

    /**
     * The whole screen's worth of rows.
     *
     * `linked` starts at identities and walks down. `unlinked` holds
     * everything whose chain is broken, grouped by kind in hierarchy order,
     * so the fix ("give this goal an identity") is one tap away instead of
     * invisible.
     */
    data class Tree(
        val linked: List<Row>,
        val unlinked: List<Row>,
        val summary: Summary,
    ) {
        val rows: List<Row> get() = linked + unlinked
        val isEmpty: Boolean get() = linked.isEmpty() && unlinked.isEmpty()
    }

    /** Counts for the header strip. */
    data class Summary(
        val identities: Int,
        val goals: Int,
        val systems: Int,
        val habits: Int,
        val activeHabits: Int,
        val unlinked: Int,
        /**
         * Length of the longest unbroken identity-rooted chain, 0..4. Four
         * means at least one habit traces all the way back to an identity,
         * which is the state the whole app is arguing for.
         */
        val deepestChain: Int,
    )

    /* ------------------------------------------------------------- build */

    /**
     * Flatten [nodes] into display order.
     *
     * @param expanded ids whose children should be shown. A node not in the
     *   set is collapsed; its subtree is counted but not emitted.
     *
     * Input order is preserved among siblings, so whatever ordering the
     * repository applies (and whatever the user drags into place) survives.
     */
    fun build(nodes: List<Node>, expanded: Set<String> = emptySet()): Tree {
        val byId = HashMap<String, Node>(nodes.size * 2)
        for (n in nodes) byId[key(n)] = n

        // Resolve each node's real parent once. A link only counts when the
        // target exists and sits exactly one rank above: an import that
        // pointed a habit straight at an identity would otherwise render as
        // a two-level jump with a missing middle.
        //
        // This is also what makes the recursive walks below safe without a
        // visited set. A parent is always exactly one rank above its child,
        // so rank strictly decreases on every step upward and the parent
        // chain cannot revisit a node: a cycle would need two nodes each one
        // rank above the other. Corrupt data cannot produce one either,
        // because the rank comes from the node's own kind and not from the
        // stored link. If a fifth level or a same-rank link is ever added,
        // `tally` and `walk` need a visited set and `summarise` needs a
        // depth cap.
        val parentOf = HashMap<String, Node?>(nodes.size * 2)
        for (n in nodes) {
            val want = n.kind.parent
            val pid = n.parentId
            val p = if (want == null || pid.isNullOrBlank()) null else byId[want.key + ":" + pid]
            parentOf[key(n)] = p
        }

        val childrenOf = HashMap<String, MutableList<Node>>()
        val roots = ArrayList<Node>()
        val orphans = ArrayList<Node>()
        for (n in nodes) {
            val p = parentOf[key(n)]
            if (p != null) childrenOf.getOrPut(key(p)) { ArrayList() }.add(n)
            else if (n.kind == Kind.IDENTITY) roots.add(n)
            else orphans.add(n)
        }

        // Subtree statistics, computed bottom-up in one pass per root so a
        // deep tree is still linear.
        val habitTotals = HashMap<String, Int>()
        val descTotals = HashMap<String, Int>()
        fun tally(n: Node): Pair<Int, Int> {
            val kids = childrenOf[key(n)].orEmpty()
            var habits = if (n.kind == Kind.HABIT && n.active && !n.archived) 1 else 0
            var desc = 0
            for (c in kids) {
                val (h, d) = tally(c)
                habits += h
                desc += d + 1
            }
            habitTotals[key(n)] = habits
            descTotals[key(n)] = desc
            return habits to desc
        }
        for (n in nodes) if (parentOf[key(n)] == null) tally(n)

        fun rowFor(n: Node, depth: Int, last: Boolean, orphan: Boolean): Row {
            val kids = childrenOf[key(n)].orEmpty()
            val habits = habitTotals[key(n)] ?: 0
            // A habit is dormant on its own account; a container is dormant
            // when nothing beneath it is running. An identity with three
            // paused habits reads as dormant, which is the honest signal.
            val dormant = when (n.kind) {
                Kind.HABIT -> !n.active || n.archived
                else -> habits == 0
            }
            return Row(
                node = n,
                depth = depth,
                childCount = kids.size,
                habitCount = habits,
                descendantCount = descTotals[key(n)] ?: 0,
                expanded = key(n) in expanded,
                last = last,
                dormant = dormant,
                orphan = orphan,
            )
        }

        val out = ArrayList<Row>(nodes.size)
        fun walk(n: Node, depth: Int, last: Boolean, orphan: Boolean) {
            val row = rowFor(n, depth, last, orphan)
            out.add(row)
            if (!row.expanded) return
            val kids = childrenOf[key(n)].orEmpty()
            kids.forEachIndexed { i, c -> walk(c, depth + 1, i == kids.lastIndex, false) }
        }
        roots.forEachIndexed { i, r -> walk(r, 0, i == roots.lastIndex, false) }
        val linked = ArrayList(out)

        out.clear()
        // Unlinked roots are grouped by kind so the section reads
        // "goals, then systems, then habits" rather than import order.
        val grouped = orphans.sortedBy { it.kind.rank }
        grouped.forEachIndexed { i, r ->
            val lastOfKind = i == grouped.lastIndex || grouped[i + 1].kind != r.kind
            walk(r, 0, lastOfKind, true)
        }
        val unlinked = ArrayList(out)

        return Tree(linked, unlinked, summarise(nodes, parentOf))
    }

    /**
     * Identity of a node inside this module: kind plus id.
     *
     * Namespaced because the four entity tables generate ids independently
     * and an imported file can legitimately contain a goal and a habit with
     * the same string. Keying expansion on the bare id would then open both.
     */
    private fun key(n: Node) = n.kind.key + ":" + n.id

    private fun summarise(nodes: List<Node>, parentOf: Map<String, Node?>): Summary {
        var deepest = 0
        for (n in nodes) {
            var depth = 1
            var cur: Node = n
            while (true) {
                val p = parentOf[key(cur)] ?: break
                depth++
                cur = p
            }
            // Only chains that actually reach an identity count. A goal with
            // a system and a habit hanging off it is three deep but rooted
            // in nothing, and the point of the number is the root.
            if (cur.kind == Kind.IDENTITY && depth > deepest) deepest = depth
        }
        return Summary(
            identities = nodes.count { it.kind == Kind.IDENTITY },
            goals = nodes.count { it.kind == Kind.GOAL },
            systems = nodes.count { it.kind == Kind.SYSTEM },
            habits = nodes.count { it.kind == Kind.HABIT },
            activeHabits = nodes.count { it.kind == Kind.HABIT && it.active && !it.archived },
            unlinked = nodes.count { it.kind != Kind.IDENTITY && parentOf[key(it)] == null },
            deepestChain = deepest,
        )
    }

    /* --------------------------------------------------------- expansion */

    /** Toggle one node. Ids are namespaced by kind so a shared id is safe. */
    fun toggle(expanded: Set<String>, kind: Kind, id: String): Set<String> {
        val k = kind.key + ":" + id
        return if (k in expanded) expanded - k else expanded + k
    }

    fun expansionKey(kind: Kind, id: String): String = kind.key + ":" + id

    /**
     * The default expansion for a first visit: open every identity, and open
     * goals only while the tree is small enough that doing so is not a wall
     * of text. Past that the user opens what they care about.
     */
    fun defaultExpansion(nodes: List<Node>): Set<String> {
        val out = HashSet<String>()
        for (n in nodes) if (n.kind == Kind.IDENTITY) out += key(n)
        if (nodes.size <= SMALL_TREE) for (n in nodes) if (n.kind == Kind.GOAL) out += key(n)
        return out
    }

    /** Below this many entities, opening two levels still fits a thumb-scroll. */
    const val SMALL_TREE = 12

    /** Every ancestor of [id], so a deep link can reveal a collapsed node. */
    fun revealPath(nodes: List<Node>, kind: Kind, id: String): Set<String> {
        val byId = nodes.associateBy { key(it) }
        val out = HashSet<String>()
        var cur = byId[kind.key + ":" + id] ?: return out
        while (true) {
            val want = cur.kind.parent ?: break
            val pid = cur.parentId ?: break
            val p = byId[want.key + ":" + pid] ?: break
            out += key(p)
            cur = p
        }
        return out
    }

    /* ------------------------------------------------------------- gaps */

    /** Something the hierarchy is missing, phrased as an invitation. */
    data class Gap(
        val kind: Kind,
        /** The node the gap hangs off, or null when the level is empty. */
        val nodeId: String?,
        val title: String,
        val body: String,
        /** Lower sorts first. */
        val priority: Int,
    )

    /**
     * What to suggest next, most useful first.
     *
     * This is the "guided prompts" of the plan's empty states, generalised:
     * an empty level and a dangling entity are the same problem seen from
     * two sides, and a screen that only prompts when a level is *completely*
     * empty stops helping the moment the user creates one of anything.
     *
     * Capped, because a list of fifteen chores is a list nobody reads.
     */
    fun gaps(nodes: List<Node>, limit: Int = 3): List<Gap> {
        val tree = build(nodes)
        val out = ArrayList<Gap>()

        if (tree.summary.identities == 0) {
            out.add(Gap(Kind.IDENTITY, null, "Who are you becoming?",
                "An identity statement gives every habit a reason to exist.", 0))
        }
        if (tree.summary.habits == 0) {
            out.add(Gap(Kind.HABIT, null, "Pick one small action",
                "Every habit needs a version you can start in two minutes.", 1))
        }

        // Dangling entities: present but attached to nothing above.
        for (r in tree.unlinked) {
            if (r.depth != 0) continue
            val above = r.node.kind.parent ?: continue
            out.add(Gap(r.node.kind, r.node.id,
                "\u201c${r.node.title}\u201d has no ${above.label.lowercase()}",
                "Linking it back up is what turns a to-do into a system.", 2 + r.node.kind.rank))
        }

        // Present but leading nowhere: an identity with no goal, a system
        // with no habits. Only worth saying once the level below exists at
        // all, otherwise it duplicates the empty-level prompt above.
        for (r in tree.linked) {
            val below = r.node.kind.child ?: continue
            if (r.childCount > 0) continue
            if (r.node.archived) continue
            out.add(Gap(r.node.kind, r.node.id,
                "\u201c${r.node.title}\u201d has no ${below.plural.lowercase()}",
                "It cannot move anything until something below it runs.", 6 + r.node.kind.rank))
        }

        return out.sortedBy { it.priority }.take(limit)
    }

    /* --------------------------------------------------------- reordering */

    /**
     * Whether a drag from [from] to [to] within [rows] is allowed.
     *
     * Reordering is a sibling operation. Dragging a habit into another
     * system would be a re-parent, which is a different, destructive action
     * and belongs behind an explicit menu - not behind a gesture the user
     * can trigger by scrolling slightly wrong.
     */
    fun canMove(rows: List<Row>, from: Int, to: Int): Boolean {
        if (from == to) return false
        val a = rows.getOrNull(from) ?: return false
        val b = rows.getOrNull(to) ?: return false
        if (a.node.kind != b.node.kind) return false
        if (a.orphan != b.orphan) return false
        return a.node.parentId == b.node.parentId
    }

    /**
     * Apply a sibling move to the underlying node list.
     *
     * Returns the list unchanged when the move is not allowed, so a caller
     * that forgot to check [canMove] degrades to "nothing happened" rather
     * than to a scrambled hierarchy.
     */
    fun move(nodes: List<Node>, rows: List<Row>, from: Int, to: Int): List<Node> {
        if (!canMove(rows, from, to)) return nodes
        val moving = rows[from].node
        val target = rows[to].node
        val out = ArrayList(nodes)
        val i = out.indexOfFirst { key(it) == key(moving) }
        val j = out.indexOfFirst { key(it) == key(target) }
        if (i < 0 || j < 0) return nodes
        out.removeAt(i)
        out.add(j, moving)
        return out
    }

    /* ------------------------------------------------------------ labels */

    /** "3 goals · 4 habits", omitting whatever is zero. */
    fun connectionLabel(row: Row): String {
        val parts = ArrayList<String>(2)
        val below = row.node.kind.child
        if (below != null && row.childCount > 0) {
            parts.add("${row.childCount} " + plural(below, row.childCount))
        }
        if (row.node.kind != Kind.HABIT && row.habitCount > 0) {
            parts.add("${row.habitCount} " + plural(Kind.HABIT, row.habitCount))
        }
        return parts.joinToString(" \u00b7 ")
    }

    private fun plural(kind: Kind, n: Int): String =
        if (n == 1) kind.label.lowercase() else kind.plural.lowercase()
}
