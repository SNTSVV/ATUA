package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer

import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeType
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class LocalReducerLV2: AbstractLocalReducer() {
    override fun reduce(guiWidget: Widget, guiState: State<*>): HashMap<AttributeType, String>  {
        val reducedAttributes = reduceBaseAttributes(guiWidget,guiState)
        if (guiWidget.checked.isEnabled())
            reducedAttributes.put(AttributeType.checked,guiWidget.checked.toString())
        if (!guiWidget.isInputField) {
            reducedAttributes.put(AttributeType.text, guiWidget.text)
            reducedAttributes.put(AttributeType.contentDesc, guiWidget.contentDesc)
        }
        return reducedAttributes
    }
}