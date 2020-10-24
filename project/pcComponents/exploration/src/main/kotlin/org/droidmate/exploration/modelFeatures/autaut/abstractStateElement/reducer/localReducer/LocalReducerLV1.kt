package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer

import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeType
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV1: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = HashMap<AttributeType, String>()
        reducedAttributes.put(AttributeType.className,guiWidget.className)
        reducedAttributes.put(AttributeType.resourceId, guiWidget.resourceId)
        reducedAttributes.put(AttributeType.enabled, guiWidget.enabled.toString())
        reducedAttributes.put(AttributeType.selected, guiWidget.selected.toString())
        reducedAttributes.put(AttributeType.checkable,guiWidget.checked.isEnabled().toString())
        reducedAttributes.put(AttributeType.isInputField, guiWidget.isInputField.toString())
        if (guiWidget.className != "android.webkit.WebView" ) {
            reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
            reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
            if (guiWidget.visibleBounds.height > 200 && guiWidget.visibleBounds.width > 200)
                reducedAttributes.put(AttributeType.scrollable, guiWidget.scrollable.toString())

        } else if(guiWidget.resourceId.isNotBlank()) {
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
        return reducedAttributes
    }
}