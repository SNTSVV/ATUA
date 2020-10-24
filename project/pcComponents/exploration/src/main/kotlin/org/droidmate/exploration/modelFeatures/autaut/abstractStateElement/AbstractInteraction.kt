package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.explorationModel.interaction.Interaction
import kotlin.contracts.contract

class AbstractInteraction(
        val abstractAction: AbstractAction,
        val interactions: ArrayList<Interaction<*>> = ArrayList(),
        val isImplicit: Boolean,
        val prevWindow: WTGNode?,
        val data: Any? =null,
        var fromWTG: Boolean = false

) {
    val modifiedMethods = HashMap<String,Boolean>() //method id,
    val modifiedMethodStatement = HashMap<String, Boolean>() //statement id,
    val handlers = HashMap<String,Boolean>() // handler method id

    fun isExplicit() = !isImplicit
    fun updateUpdateStatementCoverage(statement: String, autautMF: AutAutMF) {
        if (autautMF.statementMF!!.isModifiedMethodStatement(statement)) {
            this.modifiedMethodStatement.put(statement, true)
            val methodId = autautMF.statementMF!!.statementMethodInstrumentationMap.get(statement)
            if (methodId != null) {
                this.modifiedMethods.put(methodId, true)
            }
        }
    }
}