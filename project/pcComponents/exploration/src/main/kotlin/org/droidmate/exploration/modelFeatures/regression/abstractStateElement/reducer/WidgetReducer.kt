package org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributePath
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class WidgetReducer {
    companion object{
        val level2WidgetPatterns = ArrayList<AttributePath>()
        val level3WidgetPatterns = ArrayList<AttributePath>()
        val level4WidgetPatterns = ArrayList<AttributePath>()

        fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, tempFullAttributePaths: HashMap<Widget,AttributePath>,
                   tempRelativeAttributePaths: HashMap<Widget,AttributePath>): AttributePath
        {
            val abstractFunctionDecision = AbstractionFunction.INSTANCE
            return abstractFunctionDecision.reduce(guiWidget,guiState, activity,tempFullAttributePaths,tempRelativeAttributePaths)
        }
    }
}