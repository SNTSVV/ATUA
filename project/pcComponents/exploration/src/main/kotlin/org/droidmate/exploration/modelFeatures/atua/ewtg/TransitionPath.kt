// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
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