package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer

import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV0: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = HashMap<AttributeType, String>()
        return reducedAttributes
    }
}