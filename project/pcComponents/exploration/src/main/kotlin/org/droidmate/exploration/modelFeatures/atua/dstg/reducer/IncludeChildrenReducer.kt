package org.droidmate.exploration.modelFeatures.atua.dstg.reducer

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributePath
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeType
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.LocalReducerLV1
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer.LocalReducerLV3
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import kotlin.collections.HashMap

open class IncludeChildrenReducer(
        localReducer: AbstractLocalReducer,
        val childrenReducer: AbstractLocalReducer
)
    : BaseReducer(localReducer = localReducer)
{
    override fun reduce(guiWidget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, window: Window, rotation: Rotation, autAutMF: ATUAMF, tempWidgetReduceMap: HashMap<Widget,AttributePath>, tempChildWidgetAttributePaths: HashMap<Widget, AttributePath>): AttributePath {
        val localAttributes = localReducer.reduce(guiWidget,guiState)
//        val parentAttributePath = parentReduce(guiWidget, guiState,isOptionsMenu,guiTreeRectangle, window,rotation,autAutMF, tempWidgetReduceMap,tempChildWidgetAttributePaths)
        var childStructure: String = ""
        var childText: String = ""
        if (shouldIncludeChildrenInfo(guiState,guiWidget) ) {
            guiWidget.childHashes.forEach { childHash ->
                val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
                if (childWidget != null) {
                    val childInfo = childReduce(childWidget, guiState, isOptionsMenu, guiTreeRectangle, rotation, autAutMF, tempChildWidgetAttributePaths)
                    childStructure += childInfo.first
                    childText += childInfo.second
                }
            }
            localAttributes.put(AttributeType.childrenStructure, childStructure)
            localAttributes.put(AttributeType.childrenText,childText)
        }


        if (childrenReducer is LocalReducerLV3) {
            var siblingInfos = ""
            if (needSiblingInfos(guiState,guiWidget) ) {
                val siblings = guiState.widgets.filter { it.parentId == guiWidget.parentId && it != guiWidget }
                siblings.forEach { sibling ->
                    if (sibling.className != guiWidget.className) {
                        val siblingInfo = siblingReduce(sibling, guiState, isOptionsMenu, guiTreeRectangle, rotation, autAutMF, tempChildWidgetAttributePaths)
                        siblingInfos += siblingInfo
                    }
                }
            }
            localAttributes.put(AttributeType.siblingsInfo,siblingInfos)

        }
        val attributePath = AttributePath(
                localAttributes = localAttributes,
                parentAttributePathId = emptyUUID,
                window = window
        )
        tempWidgetReduceMap.put(guiWidget,attributePath)
        return attributePath
    }

    private fun needSiblingInfos(guiState: State<*>, guiWidget: Widget): Boolean {
        // if guiWidget is not contained in a RecyclerView or ListView, it does not need the sibling infos
        if (Helper.hasParentWithType(guiWidget,guiState, "RecyclerView")
                || Helper.hasParentWithType(guiWidget,guiState, "ListView"))
            return true
        return false
    }

    private fun shouldIncludeChildrenInfo(guiState: State<*>, guiWidget: Widget) : Boolean {
        if (guiWidget.className.contains("RecyclerView")
                || guiWidget.className.contains("ListView")
                || guiWidget.className.contains("ViewPager")
                || guiWidget.className.contains("WebView")
        )
            return false
        return true

    }
    fun childReduce(widget: Widget, guiState: State<*>, isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, rotation: Rotation, autAutMF: ATUAMF, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): Pair<String,String> {
        var nestedChildrenStructure: String = ""
        var nestedChildrenText: String = ""
        widget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget != null) {
                val nestedChildren = childReduce(childWidget,guiState,isOptionsMenu,guiTreeRectangle, rotation, autAutMF, tempChildWidgetAttributePaths)
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

    fun siblingReduce(widget: Widget, guiState: State<*>,isOptionsMenu:Boolean, guiTreeRectangle: Rectangle, rotation: Rotation, autAutMF: ATUAMF, tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): String {
        var siblingInfo = ""
        widget.childHashes.forEach { childHash ->
            val childWidget = guiState.widgets.find { it.idHash == childHash && it.isVisible }
            if (childWidget != null) {
                val info = childReduce(childWidget,guiState,isOptionsMenu,guiTreeRectangle, rotation, autAutMF, tempChildWidgetAttributePaths)
                siblingInfo += info.second
            }
        }
        val info = "${widget.nlpText}_$siblingInfo"
        return info
    }
}