// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.EWTG.WindowManager
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
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