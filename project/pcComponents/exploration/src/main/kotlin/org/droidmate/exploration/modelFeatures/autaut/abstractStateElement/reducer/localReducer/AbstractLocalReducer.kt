package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractLocalReducer {
    abstract fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>
}