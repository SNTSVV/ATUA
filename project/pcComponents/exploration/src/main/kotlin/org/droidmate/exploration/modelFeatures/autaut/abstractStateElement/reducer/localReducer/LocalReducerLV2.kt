package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.localReducer

import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeType
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
        reducedAttributes.put(AttributeType.clickable, guiWidget.clickable.toString())
        reducedAttributes.put(AttributeType.longClickable, guiWidget.longClickable.toString())
        reducedAttributes.put(AttributeType.scrollable, guiWidget.scrollable.toString())
        reducedAttributes.put(AttributeType.text,guiWidget.text)
        return reducedAttributes
    }
}