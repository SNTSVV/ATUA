package org.droidmate.explorationModel

import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.StringCreator

internal fun Widget.dataString(sep: String) = StringCreator.createPropertyString(this,sep)
internal fun Interaction<*>.actionString(sep: String) = StringCreator.createActionString(this,sep)
internal val config = ModelConfig("JUnit", true)

