package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
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