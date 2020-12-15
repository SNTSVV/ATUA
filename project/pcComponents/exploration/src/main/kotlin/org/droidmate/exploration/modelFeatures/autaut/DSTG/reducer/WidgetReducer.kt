package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class WidgetReducer {
    companion object{

        fun reduce(guiWidget: Widget, guiState: State<*>, activity: String,rotation: Rotation,autAutMF: AutAutMF,  tempFullAttributePaths: HashMap<Widget,AttributePath>,
                   tempRelativeAttributePaths: HashMap<Widget,AttributePath>): AttributePath
        {
            val abstractFunctionDecision = AbstractionFunction.INSTANCE
            return abstractFunctionDecision.reduce(guiWidget,guiState, activity,rotation,autAutMF, tempFullAttributePaths,tempRelativeAttributePaths)
        }
    }
}