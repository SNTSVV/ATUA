package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer

import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeType
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractLocalReducer {
    abstract fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>
}