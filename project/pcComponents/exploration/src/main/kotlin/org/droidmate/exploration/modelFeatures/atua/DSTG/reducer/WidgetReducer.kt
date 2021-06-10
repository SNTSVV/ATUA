package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class WidgetReducer {
    companion object{

        fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, classType: String, rotation: Rotation, atuaMF: ATUAMF, tempFullAttributePaths: HashMap<Widget,AttributePath>,
                   tempRelativeAttributePaths: HashMap<Widget,AttributePath>): AttributePath
        {
            val abstractFunctionDecision = AbstractionFunction.INSTANCE
            return abstractFunctionDecision.reduce(guiWidget,guiState,isOptionsMenu,guiTreeRectangle,  classType,rotation,atuaMF, tempFullAttributePaths,tempRelativeAttributePaths)
        }
    }
}