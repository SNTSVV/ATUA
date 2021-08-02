package org.droidmate.exploration.modelFeatures.atua.dstg.reducer

import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTGWidget
import java.util.*

class DecisionNode2 (
        val ewtgWidgets: ArrayList<EWTGWidget> = ArrayList(),
        val reducer: AbstractReducer,
        var nextNode: DecisionNode2?=null
) {

    fun needPassToNextLevel(ewtgWidget: EWTGWidget): Boolean {
        return ewtgWidgets.contains(ewtgWidget)
    }
}