package org.droidmate.exploration.modelFeatures.atua.dstg.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributePath
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class WidgetReducer {
    companion object{

        fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, window: Window, rotation: Rotation, atuaMF: ATUAMF, tempFullAttributePaths: HashMap<Widget,AttributePath>,
                   tempRelativeAttributePaths: HashMap<Widget,AttributePath>): AttributePath
        {
            val abstractFunctionDecision = AbstractionFunction2.INSTANCE
            val ewtgWidget = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(window)!!.get(guiWidget)
            return abstractFunctionDecision.reduce(guiWidget,guiState,ewtgWidget,isOptionsMenu,guiTreeRectangle,  window,rotation,atuaMF, tempFullAttributePaths,tempRelativeAttributePaths)
        }
    }
}