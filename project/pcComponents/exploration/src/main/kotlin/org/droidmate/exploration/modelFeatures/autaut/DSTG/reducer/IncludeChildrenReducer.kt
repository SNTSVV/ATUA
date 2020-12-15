package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

open class IncludeChildrenReducer(
        localReducer: AbstractLocalReducer,
        val childrenReducer: AbstractLocalReducer
)
    : BaseReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>,activity: String, rotation: Rotation,autAutMF: AutAutMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>, tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
        val parentAttributePath = parentReduce(guiWidget, guiState,activity,rotation,autAutMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        val childAttributePaths = HashSet<UUID>()
        guiWidget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget!=null) {
                val childAttributePath = childReduce(childWidget, guiState, activity, rotation,autAutMF, tempChildWidgetAttributePaths)
                childAttributePaths.add(childAttributePath.attributePathId)
            }
        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = parentAttributePath?.attributePathId?: emptyUUID,
                childAttributePathIds = childAttributePaths,
                activity = activity
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun childReduce(widget: Widget, guiState: State<*>, activity: String,rotation: Rotation,autAutMF: AutAutMF, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath {
        val localAttributes =  childrenReducer.reduce(widget, guiState)
        val childAttributePaths = HashSet<UUID>()
        widget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget != null) {
                if (tempChildWidgetAttributePaths.containsKey(childWidget)) {
                    val childAttributePath = tempChildWidgetAttributePaths[childWidget]!!
                    childAttributePaths.add(childAttributePath.attributePathId)
                } else {
                    val childAttributePath = childReduce(childWidget, guiState,activity,rotation,autAutMF, tempChildWidgetAttributePaths)
                    childAttributePaths.add(childAttributePath.attributePathId)
                    tempChildWidgetAttributePaths.put(childWidget, childAttributePath)
                }
            }
        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = emptyUUID,
                childAttributePathIds = childAttributePaths,
                activity = activity
        )
        return attributePath
    }

}