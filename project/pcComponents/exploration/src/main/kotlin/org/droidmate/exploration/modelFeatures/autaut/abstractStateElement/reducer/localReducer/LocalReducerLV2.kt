package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV2: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = HashMap<AttributeType, String>()
        reducedAttributes.put(AttributeType.className,guiWidget.className)
        reducedAttributes.put(AttributeType.resourceId, guiWidget.resourceId)
        reducedAttributes.put(AttributeType.contentDesc,guiWidget.contentDesc)
        reducedAttributes.put(AttributeType.enabled, guiWidget.enabled.toString())
        reducedAttributes.put(AttributeType.selected, guiWidget.selected.toString())
        reducedAttributes.put(AttributeType.checked,guiWidget.checked.toString())
        reducedAttributes.put(AttributeType.isInputField, guiWidget.isInputField.toString())
        if (guiWidget.className != "android.webkit.WebView") {
            reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
            reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
            reducedAttributes.put(AttributeType.scrollable, guiWidget.scrollable.toString())
        } else {
            if (Helper.haveClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.clickable, true.toString())
            }
            if (Helper.haveLongClickableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.longClickable, true.toString())
            }
            if (Helper.haveScrollableChild(guiState.widgets,guiWidget)) {
                reducedAttributes.put(AttributeType.scrollable, true.toString())
            }
        }
        reducedAttributes.put(AttributeType.text,guiWidget.text)
        return reducedAttributes
    }
}