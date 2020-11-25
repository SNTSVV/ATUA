package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.explorationModel.interaction.Interaction

class AbstractTransition(
        val abstractAction: AbstractAction,
        val interactions: ArrayList<Interaction<*>> = ArrayList(),
        val isImplicit: Boolean,
        val prevWindow: Window?,
        val data: Any? =null,
        var fromWTG: Boolean = false,
        val source: AbstractState,
        val dest: AbstractState
) {
    init {
        source.abstractTransitions.add(this)
    }
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val handlers = HashMap<String,Boolean>() // handler method id

    fun isExplicit() = !isImplicit
    fun updateUpdateStatementCoverage(statement: String, autautMF: AutAutMF) {
        if (autautMF.statementMF!!.isModifiedMethodStatement(statement)) {
            this.modifiedMethodStatement.put(statement, true)
            val methodId = autautMF.statementMF!!.statementMethodInstrumentationMap.get(statement)
            if (methodId != null) {
                autautMF.allModifiedMethod.put(methodId,true)
                this.modifiedMethods.put(methodId, true)
            }
        }
    }
}