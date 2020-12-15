package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class BaseReducer(
        localReducer: AbstractLocalReducer
)
    : AbstractReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, rotation: Rotation,autAutMF: AutAutMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>
    , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        val parentAttributePath = parentReduce(guiWidget, guiState,activity,rotation,autAutMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)

        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = parentAttributePath?.attributePathId?: emptyUUID,
                activity = activity
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun parentReduce(guiWidget: Widget, guiState: State<*>,activity: String, rotation: Rotation, autAutMF: AutAutMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>,
                     tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath?{
        if (guiWidget.hasParent)
        {
            val parentWidget = guiState.widgets.find { it.idHash == guiWidget.parentHash
                                                        && it != guiWidget}
            if (parentWidget==null)
            {
                return null
            }
            if (tempWidgetReduceMap.containsKey(parentWidget))
            {
                return tempWidgetReduceMap[parentWidget]
            }
            val parentAttributePath: AttributePath = AbstractionFunction.INSTANCE.reduce(parentWidget,guiState,activity,rotation,autAutMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)
            //tempWidgetReduceMap.put(parentWidget,parentAttributePath)
            return parentAttributePath
        }
        else
        {
            return null
        }
    }

}