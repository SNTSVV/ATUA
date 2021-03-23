package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractReducer(
        val localReducer: AbstractLocalReducer
) {

    abstract fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, rotation: Rotation, atuaMF: ATUAMF
                        , tempWidgetReduceMap: HashMap<Widget,AttributePath>
                        , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath




}