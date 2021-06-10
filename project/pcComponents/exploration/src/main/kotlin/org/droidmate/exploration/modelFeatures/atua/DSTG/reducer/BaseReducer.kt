package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class BaseReducer(
        localReducer: AbstractLocalReducer
)
    : AbstractReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, classType: String, rotation: Rotation, atuaMF: ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>
                        , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        val parentAttributePath = parentReduce(guiWidget, guiState,isOptionsMenu,guiTreeRectangle, classType,rotation,atuaMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)

        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = parentAttributePath?.attributePathId?: emptyUUID,
                activity = classType
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun parentReduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, classType: String, rotation: Rotation, atuaMF: ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>,
                     tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath?{
        var currentWidget = guiWidget
        var parentGUIWidget = guiState.widgets.find { it.id == guiWidget.parentId }
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
            val parentAttributePath: AttributePath = AbstractionFunction.INSTANCE.reduce(parentWidget,guiState,isOptionsMenu,guiTreeRectangle, classType,rotation,atuaMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)
            //tempWidgetReduceMap.put(parentWidget,parentAttributePath)
            return parentAttributePath
        }
        else
        {
            return null
        }
    }

}