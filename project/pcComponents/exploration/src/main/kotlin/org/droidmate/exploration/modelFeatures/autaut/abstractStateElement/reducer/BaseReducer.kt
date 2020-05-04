package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.AbstractLocalReducer
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class BaseReducer(
        localReducer: AbstractLocalReducer
)
    : AbstractReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath>
    , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        val parentAttributePath = parentReduce(guiWidget, guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)

        val childAttributePaths = HashSet<AttributePath>()

        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePath = parentAttributePath,
                childAttributePaths = childAttributePaths
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun parentReduce(guiWidget: Widget, guiState: State<*>,activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath>,
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
            val parentAttributePath: AttributePath = AbstractionFunction.INSTANCE.reduce(parentWidget,guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)
            //tempWidgetReduceMap.put(parentWidget,parentAttributePath)
            return parentAttributePath
        }
        else
        {
            return null
        }
    }

}