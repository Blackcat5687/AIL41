package com.notekeep.local.graph

enum class GraphNodeType { NOTE, TAG, LABEL }

data class GraphNode(
    val id: String,
    val label: String,
    val type: GraphNodeType,
    val noteId: Long? = null,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var degree: Int = 0,
    /** true while the user is dragging this node, or after they've dropped it in place. */
    var fixed: Boolean = false
) {
    val isTag: Boolean get() = type == GraphNodeType.TAG
}

data class GraphEdge(
    val sourceId: String,
    val targetId: String
)

data class GraphGroup(val query: String, val color: Int)

class GraphData(
    val nodes: MutableList<GraphNode>,
    val edges: MutableList<GraphEdge>
) {
    private val indexById = nodes.associateBy { it.id }.let { HashMap(it) }

    fun nodeById(id: String): GraphNode? = indexById[id]

    companion object {
        /** Builds a graph connecting each note to the #tags found inside it and to any labels (categories) assigned to it. */
        fun build(
            notes: List<com.notekeep.local.data.Note>,
            hideOrphans: Boolean,
            includeTags: Boolean = true,
            labels: List<com.notekeep.local.data.Label> = emptyList(),
            noteLabelPairs: List<Pair<Long, Long>> = emptyList(),
            /** The graph currently on screen, if any. Nodes that also exist here keep their
             *  position/velocity/pinned state instead of jumping back to a scattered layout. */
            previous: GraphData? = null
        ): GraphData {
            val nodes = LinkedHashMap<String, GraphNode>()
            val edges = mutableListOf<GraphEdge>()
            val degreeCount = HashMap<String, Int>()

            val labelById = labels.associateBy { it.id }
            val labelIdsByNote = noteLabelPairs.groupBy({ it.first }, { it.second })

            for (note in notes) {
                val tags = note.extractTags()
                val noteLabelIds = labelIdsByNote[note.id].orEmpty()
                if (tags.isEmpty() && noteLabelIds.isEmpty() && hideOrphans) continue

                val noteNodeId = "note_${note.id}"
                val label = note.title.ifBlank {
                    note.content.take(18).ifBlank { "بدون عنوان" }
                }
                nodes.getOrPut(noteNodeId) {
                    GraphNode(id = noteNodeId, label = label, type = GraphNodeType.NOTE, noteId = note.id)
                }

                if (includeTags) {
                    for (tag in tags) {
                        val tagNodeId = "tag_$tag"
                        nodes.getOrPut(tagNodeId) {
                            GraphNode(id = tagNodeId, label = tag, type = GraphNodeType.TAG)
                        }
                        edges.add(GraphEdge(noteNodeId, tagNodeId))
                        degreeCount[noteNodeId] = (degreeCount[noteNodeId] ?: 0) + 1
                        degreeCount[tagNodeId] = (degreeCount[tagNodeId] ?: 0) + 1
                    }
                }

                for (labelId in noteLabelIds) {
                    val labelEntity = labelById[labelId] ?: continue
                    val labelNodeId = "label_$labelId"
                    nodes.getOrPut(labelNodeId) {
                        GraphNode(id = labelNodeId, label = labelEntity.name, type = GraphNodeType.LABEL)
                    }
                    edges.add(GraphEdge(noteNodeId, labelNodeId))
                    degreeCount[noteNodeId] = (degreeCount[noteNodeId] ?: 0) + 1
                    degreeCount[labelNodeId] = (degreeCount[labelNodeId] ?: 0) + 1
                }
            }

            val nodeList = nodes.values.toMutableList()
            nodeList.forEach { it.degree = degreeCount[it.id] ?: 0 }

            // Nodes that already existed keep their place, velocity and pin state so the layout
            // doesn't reshuffle every time the graph reloads (e.g. after opening/closing a note).
            // Only nodes that are genuinely new get scattered - and they're scattered near the
            // existing cluster's centre, not the corner of the canvas, so they don't fly in from afar.
            val existingNodes = previous?.nodes.orEmpty()
            val centerX = existingNodes.map { it.x }.average().takeIf { !it.isNaN() }?.toFloat() ?: 400f
            val centerY = existingNodes.map { it.y }.average().takeIf { !it.isNaN() }?.toFloat() ?: 400f
            val rnd = java.util.Random()
            for (node in nodeList) {
                val old = previous?.nodeById(node.id)
                if (old != null) {
                    node.x = old.x
                    node.y = old.y
                    node.vx = old.vx
                    node.vy = old.vy
                    node.fixed = old.fixed
                } else {
                    node.x = centerX + (rnd.nextFloat() - 0.5f) * 300f
                    node.y = centerY + (rnd.nextFloat() - 0.5f) * 300f
                }
            }

            return GraphData(nodeList, edges)
        }
    }
}
