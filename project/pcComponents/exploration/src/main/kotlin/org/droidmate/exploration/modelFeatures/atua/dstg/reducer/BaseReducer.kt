/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.exploration.modelFeatures.atua.dstg.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributePath
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class BaseReducer(
        localReducer: AbstractLocalReducer
)
    : AbstractReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, window: Window, rotation: Rotation, atuaMF: ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>
                        , tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        // val parentAttributePath = parentReduce(guiWidget, guiState,isOptionsMenu,guiTreeRectangle, window,rotation,atuaMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)

        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = emptyUUID,
                window = window
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun parentReduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, window: Window, rotation: Rotation, atuaMF: ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>,
                     tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath?{
        var currentWidget = guiWidget
/*        while (parentGUIWidget!=null &&
                parentGUIWidget.text.isBlank()
                        && parentGUIWidget.resourceId.isBlank()
                && parentGUIWidget.childHashes.size == 1
        ) {
            currentWidget = parentGUIWidget
            parentGUIWidget = guiState.widgets.find { it.id == currentWidget.parentId }
        }*/
        if (currentWidget.hasParent)
        {
            val parentWidget = guiState.widgets.find { it.idHash == currentWidget.parentHash
                                                        && it != currentWidget}
            if (parentWidget==null)
            {
                return null
            }
            if (tempWidgetReduceMap.containsKey(parentWidget))
            {
                return tempWidgetReduceMap[parentWidget]
            }
            val ewtgWidget = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(window)!!.get(parentWidget)
            val parentAttributePath: AttributePath = AbstractionFunction2.INSTANCE.reduce(parentWidget,guiState, ewtgWidget, isOptionsMenu,guiTreeRectangle,window, rotation,atuaMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)
            //tempWidgetReduceMap.put(parentWidget,parentAttributePath)
            return parentAttributePath
        }
        else
        {
            return null
        }
    }

}