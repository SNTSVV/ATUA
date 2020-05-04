package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer.AbstractLocalReducer
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

open class IncludeChildrenReducer(
        localReducer: AbstractLocalReducer,
        val childrenReducer: AbstractLocalReducer
)
    : BaseReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>,activity: String, tempWidgetReduceMap: HashMap<Widget,AttributePath>, tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        val parentAttributePath = parentReduce(guiWidget, guiState,activity, tempWidgetReduceMap,tempChildWidgetAttributePaths)

        val childAttributePaths = HashSet<AttributePath>()
        guiWidget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.first { it.idHash == childHash }
            val childAttributePath = childReduce(childWidget, guiState,tempChildWidgetAttributePaths)
            childAttributePaths.add(childAttributePath)
        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePath = parentAttributePath,
                childAttributePaths = childAttributePaths
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun childReduce(childWidget: Widget, guiState: State<*>, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath {
        val localAttributes =  childrenReducer.reduce(childWidget, guiState)
        val parentAttributePath: AttributePath? = null
        val childAttributePaths = HashSet<AttributePath>()
        childWidget.childHashes.forEach {childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash }!!
            if (tempChildWidgetAttributePaths.containsKey(childWidget))
            {
                val childAttributePath = tempChildWidgetAttributePaths[childWidget]!!
                childAttributePaths.add(childAttributePath)
            }
            else
            {
                val childAttributePath = childReduce(childWidget, guiState,tempChildWidgetAttributePaths)
                childAttributePaths.add(childAttributePath)
                tempChildWidgetAttributePaths.put(childWidget,childAttributePath)
            }

        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePath = parentAttributePath,
                childAttributePaths = childAttributePaths
        )
        return attributePath
    }

}