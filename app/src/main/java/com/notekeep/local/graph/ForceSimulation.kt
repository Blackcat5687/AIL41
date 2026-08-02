package com.notekeep.local.graph

import kotlin.math.sqrt

class ForceSimulation(private val data: GraphData) {

    var centerStrength: Float = 0.3f
    var repelStrength: Float = 1200f
    var linkStrength: Float = 0.4f
    var linkDistance: Float = 140f

    var alpha: Float = 1f
    private val alphaDecay = 0.985f
    private val velocityDamping = 0.82f

    var centerX: Float = 0f
    var centerY: Float = 0f

    // Built once per simulation instead of calling the O(n) List.indexOf() inside the tick loop -
    // that was turning every tick into O(nodes * edges) work and was the main cause of stutter
    // on graphs with more than a couple dozen notes/tags.
    private val indexOf: Map<String, Int> = data.nodes.withIndex().associate { (i, node) -> node.id to i }

    fun reheat(minAlpha: Float = 1f) {
        alpha = alpha.coerceAtLeast(minAlpha)
    }

    val isActive: Boolean
        get() = alpha > 0.01f

    fun tick() {
        if (!isActive) return
        val nodes = data.nodes
        val n = nodes.size
        if (n == 0) return

        val fx = FloatArray(n)
        val fy = FloatArray(n)

        // repulsion between every pair (fine for typical personal note counts)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = nodes[i]
                val b = nodes[j]
                var dx = a.x - b.x
                var dy = a.y - b.y
                var distSq = dx * dx + dy * dy
                if (distSq < 1f) {
                    dx = (Math.random().toFloat() - 0.5f)
                    dy = (Math.random().toFloat() - 0.5f)
                    distSq = 1f
                }
                val dist = sqrt(distSq)
                val force = repelStrength / distSq
                val ux = dx / dist
                val uy = dy / dist
                fx[i] += ux * force
                fy[i] += uy * force
                fx[j] -= ux * force
                fy[j] -= uy * force
            }
        }

        // spring links pulling connected nodes toward the target distance
        for (edge in data.edges) {
            val ia = indexOf[edge.sourceId] ?: continue
            val ib = indexOf[edge.targetId] ?: continue
            val a = nodes[ia]
            val b = nodes[ib]

            var dx = b.x - a.x
            var dy = b.y - a.y
            var dist = sqrt(dx * dx + dy * dy)
            if (dist < 0.01f) dist = 0.01f
            val diff = (dist - linkDistance) * linkStrength
            val ux = dx / dist
            val uy = dy / dist
            fx[ia] += ux * diff
            fy[ia] += uy * diff
            fx[ib] -= ux * diff
            fy[ib] -= uy * diff
        }

        // gentle pull toward the canvas center so the graph doesn't drift away
        for (i in 0 until n) {
            val node = nodes[i]
            fx[i] += (centerX - node.x) * centerStrength * 0.02f
            fy[i] += (centerY - node.y) * centerStrength * 0.02f
        }

        for (i in 0 until n) {
            val node = nodes[i]
            if (node.fixed) {
                node.vx = 0f
                node.vy = 0f
                continue
            }
            node.vx = (node.vx + fx[i] * alpha) * velocityDamping
            node.vy = (node.vy + fy[i] * alpha) * velocityDamping
            node.x += node.vx * 0.02f
            node.y += node.vy * 0.02f
        }

        alpha *= alphaDecay
    }
}
