package org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer

import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV1: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = reduceBaseAttributes(guiWidget,guiState)
        return reducedAttributes
    }
}