package org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributeType
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer.localReducer.AbstractLocalReducer
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractReducer(
        val localReducer: AbstractLocalReducer
) {

    abstract fun reduce(guiWidget: Widget, guiState: State<*>, activity: String
                        , tempWidgetReduceMap: HashMap<Widget,AttributePath>
    , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath




}