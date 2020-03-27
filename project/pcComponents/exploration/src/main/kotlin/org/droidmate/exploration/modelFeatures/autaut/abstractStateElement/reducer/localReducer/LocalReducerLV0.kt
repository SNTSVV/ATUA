package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV0: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>, activity: String): HashMap<AttributeType, String>  {
        val reducedAttributes = HashMap<AttributeType, String>()
        return reducedAttributes
    }
}