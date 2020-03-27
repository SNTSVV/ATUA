package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.AbstractLocalReducer
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractReducer(
        val localReducer: AbstractLocalReducer
) {

    abstract fun reduce(guiWidget: Widget, guiState: State<*>, activity: String
                        , tempWidgetReduceMap: HashMap<Widget,AttributePath>
    , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath




}