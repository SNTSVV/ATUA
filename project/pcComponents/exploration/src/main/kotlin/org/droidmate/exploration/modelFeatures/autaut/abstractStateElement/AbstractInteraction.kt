package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.explorationModel.interaction.Interaction
import kotlin.contracts.contract

class AbstractInteraction(
        val abstractAction: AbstractAction,
        val interactions: ArrayList<Interaction<*>> = ArrayList(),
        val isImplicit: Boolean,
        val prevWindow: WTGNode?,
        val data: Any? =null

) {
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val handlers = HashMap<String,Boolean>() // handler method id
    var fromWTG: Boolean = false
}