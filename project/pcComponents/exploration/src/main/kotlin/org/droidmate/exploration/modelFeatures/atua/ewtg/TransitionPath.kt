package org.droidmate.exploration.modelFeatures.atua.ewtg

import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import kotlin.collections.HashMap

class TransitionPath(val root: AbstractState, val pathType: PathFindingHelper.PathType, val destination: AbstractState) {
    val path: HashMap<Int, AbstractTransition> = HashMap()

    fun getFinalDestination(): AbstractState{
        return destination
    }

    fun containsLoop(): Boolean {
        val orginalSize = path.values.map { it.source }.size
        val reducedSize = path.values.map { it.source }.distinct().size
        if (reducedSize<orginalSize)
            return true
        return false
    }


}

class PathTraverser (val transitionPath: TransitionPath) {
    var latestEdgeId: Int? = null
    fun reset() {
        latestEdgeId = null
    }
    fun getCurrentTransition(): AbstractTransition? {
        if (latestEdgeId == null)
            return null
        return transitionPath.path[latestEdgeId!!]
    }
    fun next(): AbstractTransition? {
        if(finalStateAchieved())
            return null
        if (latestEdgeId == null)
            latestEdgeId = 0
        else
            latestEdgeId = latestEdgeId!! + 1
        val edge = transitionPath.path[latestEdgeId!!]
        return edge
    }

    fun finalStateAchieved(): Boolean {
        return latestEdgeId == transitionPath.path!!.size-1
    }
}