package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.localReducer.AbstractLocalReducer
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

abstract class AbstractReducer(
        val localReducer: AbstractLocalReducer
) {

    abstract fun reduce(guiWidget: Widget, guiState: State<*>, activity: String, rotation: Rotation,autAutMF: AutAutMF
                        , tempWidgetReduceMap: HashMap<Widget,AttributePath>
    , tempChildWidgetAttributePaths: HashMap<Widget,AttributePath>): AttributePath




}