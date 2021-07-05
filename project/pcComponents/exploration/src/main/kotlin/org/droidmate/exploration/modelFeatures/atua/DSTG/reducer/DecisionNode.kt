package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import java.util.*

class DecisionNode (
        val attributePaths: HashMap<String, ArrayList<AttributePath>> = HashMap(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode?=null
) {
    fun containAttributePath(attributePath: AttributePath, activity: String): Boolean {
        attributePaths.get(activity)?.forEach {
           /* if (attributePath.contains(it,activity)) {
                return true
            }*/
        }
        return false
    }
}