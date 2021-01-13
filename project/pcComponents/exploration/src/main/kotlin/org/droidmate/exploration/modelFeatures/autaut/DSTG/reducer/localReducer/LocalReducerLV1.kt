package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer

import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV1: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = HashMap<AttributeType, String>()
        reducedAttributes.put(AttributeType.className,guiWidget.className)
        reducedAttributes.put(AttributeType.resourceId, guiWidget.resourceId)
        reducedAttributes.put(AttributeType.enabled, guiWidget.enabled.toString())
        reducedAttributes.put(AttributeType.checkable,guiWidget.checked.isEnabled().toString())
        reducedAttributes.put(AttributeType.isInputField, guiWidget.isInputField.toString())

        if (guiWidget.className != "android.webkit.WebView") {
            reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
            reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
            reducedAttributes.put(AttributeType.scrollable, Helper.isScrollableWidget(guiWidget).toString())
            if (reducedAttributes.get(AttributeType.scrollable) == "TRUE")
                reducedAttributes.put(AttributeType.scrollDirection, Helper.getViewsChildrenLayout(guiWidget,guiState).toString())
        } else {
            if (Helper.haveClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.clickable, true.toString())
            }
            if (Helper.haveLongClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.longClickable, true.toString())
            }
            reducedAttributes.put(AttributeType.scrollable, Helper.haveScrollableChild(guiState.widgets,guiWidget).toString())
        }
        if (guiWidget.selected.isEnabled())
            reducedAttributes.put(AttributeType.selected, guiWidget.selected.toString())
        return reducedAttributes
    }
}