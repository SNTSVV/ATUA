package org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributePath
import java.util.*

class DecisionNode (
        val attributePaths: ArrayList<Pair<AttributePath, String>> = ArrayList(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode?=null
) {

}