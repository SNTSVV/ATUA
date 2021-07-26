package org.droidmate.exploration.modelFeatures.atua.dstg.reducer.localReducer

import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.atua.dstg.AttributeType
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper.Companion.deriveStructure
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget


abstract class AbstractLocalReducer {
    abstract fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>
    fun reduceBaseAttributes(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String> {
        val reducedAttributes = HashMap<AttributeType, String>()
        val xpath = guiWidget.deriveStructure()
        reducedAttributes.put(AttributeType.xpath, xpath)
        reducedAttributes.put(AttributeType.className, guiWidget.className)
        reducedAttributes.put(AttributeType.resourceId, guiWidget.resourceId)
        reducedAttributes.put(AttributeType.enabled, guiWidget.enabled.toString())
        reducedAttributes.put(AttributeType.checkable, guiWidget.checked.isEnabled().toString())
        if (guiWidget.checked.isEnabled())
            reducedAttributes.put(AttributeType.checked,guiWidget.checked.toString())
        reducedAttributes.put(AttributeType.isInputField, guiWidget.isInputField.toString())
        reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
        reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
        if (guiWidget.className != "android.webkit.WebView") {
            reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
            reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
            reducedAttributes.put(AttributeType.scrollable, Helper.isScrollableWidget(guiWidget).toString())
            if (reducedAttributes.get(AttributeType.scrollable)!!.toBoolean()==true)
                reducedAttributes.put(AttributeType.scrollDirection, Helper.getViewsChildrenLayout(guiWidget,guiState).toString())
        } else {
            if (Helper.haveClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.clickable, true.toString())
            }
            if (Helper.haveLongClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.longClickable, true.toString())
            }
            if (guiWidget.visibleBounds.width > 200 && guiWidget.visibleBounds.height > 200) {
                reducedAttributes.put(AttributeType.scrollable, true.toString())
            } else {
                reducedAttributes.put(AttributeType.scrollable, false.toString())
            }
        }
        if (guiWidget.selected.isEnabled())
            reducedAttributes.put(AttributeType.selected, guiWidget.selected.toString())

        return reducedAttributes
    }
}