package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import java.util.*

class DecisionNode (
        val attributePaths: ArrayList<Pair<AttributePath, String>> = ArrayList(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode?=null
) {

}