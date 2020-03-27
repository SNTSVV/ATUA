package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.explorationModel.interaction.Interaction

class AbstractInteraction(
        val abstractAction: AbstractAction,
        val interactions: ArrayList<Interaction<*>> = ArrayList(),
        val isImplicit: Boolean
) {
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val handlers = HashMap<String,Boolean>() // handler method id
}