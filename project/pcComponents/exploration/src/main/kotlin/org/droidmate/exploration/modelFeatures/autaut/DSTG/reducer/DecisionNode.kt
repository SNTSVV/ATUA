package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import java.util.*

class DecisionNode (
        val attributePaths: ArrayList<Pair<AttributePath, String>> = ArrayList(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode?=null
) {
    fun containAttributePath(attributePath: AttributePath, activity: String): Boolean {
        attributePaths.filter { it.second == activity }.map { it.first }. forEach {
            if (attributePath.contains(it)) {
                return true
            }
        }
        return false
    }
}