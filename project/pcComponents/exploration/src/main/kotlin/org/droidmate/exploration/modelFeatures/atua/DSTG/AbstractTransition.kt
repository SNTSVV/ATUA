package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import org.droidmate.explorationModel.interaction.Interaction
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class AbstractTransition(
        val abstractAction: AbstractAction,
        val interactions: HashSet<Interaction<*>> = HashSet(),
        val isImplicit: Boolean,
        var prevWindow: Window?,
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
    val tracing = HashSet<Pair<Int,Int>>() // list of traceId-transitionId
    val userInputs = ArrayList<HashMap<UUID,String>>()
    val statementCoverage = HashSet<String>()
    val methodCoverage = HashSet<String>()
    val version = ModelVersion.BASE
    fun isExplicit() = !isImplicit

    fun updateUpdateStatementCoverage(statement: String, autautMF: ATUAMF) {
        val methodId = autautMF.statementMF!!.statementMethodInstrumentationMap.get(statement)
        if (autautMF.statementMF!!.isModifiedMethodStatement(statement)) {
            this.modifiedMethodStatement.put(statement, true)
            val methodId = autautMF.statementMF!!.statementMethodInstrumentationMap.get(statement)
            if (methodId != null) {
                autautMF.allModifiedMethod.put(methodId,true)
                this.modifiedMethods.put(methodId, true)
            }
        }
        statementCoverage.add(statement)
        methodCoverage.add(methodId!!)
    }

    fun copyPotentialInfo(other: AbstractTransition) {
        this.userInputs.addAll(other.userInputs)
        this.tracing.addAll(other.tracing)
        this.handlers.putAll(other.handlers)
        this.modifiedMethods.putAll(other.modifiedMethods)
        this.modifiedMethodStatement.putAll(other.modifiedMethodStatement)
        this.methodCoverage.addAll(other.methodCoverage)
        this.statementCoverage.addAll(other.statementCoverage)
    }
}