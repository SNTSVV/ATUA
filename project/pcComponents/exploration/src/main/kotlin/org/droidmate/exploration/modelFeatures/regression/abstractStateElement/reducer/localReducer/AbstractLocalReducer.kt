package org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer.localReducer

import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractLocalReducer {
    abstract fun reduce(guiWidget: Widget, guiState: State<*>, activity: String): HashMap<AttributeType, String>
}