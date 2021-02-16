package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.LocalReducerLV3
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
        var childStructure: String = ""
        var childText: String = ""
        guiWidget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget!=null) {
                val childInfo = childReduce(childWidget, guiState, activity, rotation,autAutMF, tempChildWidgetAttributePaths)
                childStructure += childInfo.first
                childText += childInfo.second
            }
        }
        localAttributes.put(AttributeType.childrenStructure, childStructure)
        localAttributes.put(AttributeType.childrenText,childText)
        if (childrenReducer is LocalReducerLV3) {
            var siblingInfos = ""
            val siblings = guiState.widgets.filter { it.parentId == guiWidget.parentId }
            siblings.forEach { sibling ->
                val siblingInfo = siblingReduce(sibling, guiState, activity, rotation,autAutMF, tempChildWidgetAttributePaths)
                siblingInfos += siblingInfo
            }
            localAttributes.put(AttributeType.siblingsInfo,siblingInfos)

        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = parentAttributePath?.attributePathId?: emptyUUID,
                activity = activity
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    fun childReduce(widget: Widget, guiState: State<*>, activity: String,rotation: Rotation,autAutMF: AutAutMF, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): Pair<String,String> {
        var nestedChildrenStructure: String = ""
        var nestedChildrenText: String = ""
        widget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget != null) {
                val nestedChildren = childReduce(childWidget,guiState, activity, rotation, autAutMF, tempChildWidgetAttributePaths)
                nestedChildrenStructure += nestedChildren.first
                nestedChildrenText += nestedChildren.second
            }
        }

        val structure = "<${widget.className}-resourceId=${widget.resourceId}>$nestedChildrenStructure</${widget.className}>"
        val text = if (childrenReducer is LocalReducerLV1)
            ""
        else
            "${widget.nlpText}_$nestedChildrenText"
        return Pair(structure,text)
    }

    fun siblingReduce(widget: Widget, guiState: State<*>, activity: String,rotation: Rotation,autAutMF: AutAutMF, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): String {
        var siblingInfo = ""
        widget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget != null) {
                val info = childReduce(childWidget,guiState, activity, rotation, autAutMF, tempChildWidgetAttributePaths)
                siblingInfo += info.second
            }
        }
        val info = "${widget.nlpText}_$siblingInfo"
        return info
    }
}