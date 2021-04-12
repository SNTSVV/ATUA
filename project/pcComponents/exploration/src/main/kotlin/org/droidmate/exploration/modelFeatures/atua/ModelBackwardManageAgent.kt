package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ModelBackwardManageAgent {
    val backwardEquivalentMapping = HashMap<AbstractState, ArrayList<AbstractState>>()
    val quasibackwardEquivalentMapping = HashMap<Pair<AbstractState, AbstractAction>,AbstractState>()

    val checkingQuasibackwardEquivalent = Stack<Triple<AbstractState,AbstractAction,AbstractState>> ()

    fun runtimeAdaptation(observedAbstractState: AbstractState, abstractTransition: AbstractTransition) {
        if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            checkingQuasibackwardEquivalent.clear()
        }
        if (abstractTransition.version == ModelVersion.BASE) {
            return
        }
        val baseAbstractTransitions = abstractTransition.source.abstractTransitions.filter {
            it.abstractAction == abstractTransition.abstractAction
                    && it.dest.loaded
        }
        // we are gonna check if the observedAbstractState is a dest of any abstractTransition in the base model
        var backwardEquivalenceFound = false
        baseAbstractTransitions.forEach {
            val dest = it.dest
            if (isBackwardEquivant(observedAbstractState,dest)) {
                backwardEquivalentMapping.putIfAbsent(dest, ArrayList())
                backwardEquivalentMapping[dest]!!.add(observedAbstractState)
                backwardEquivalenceFound = true
            }
        }
        if (backwardEquivalenceFound) {

            return
        }
        baseAbstractTransitions.forEach {
            val dest = it.dest
            if (dest.window == observedAbstractState.window) {
                checkingQuasibackwardEquivalent.push(Triple(dest,abstractTransition.abstractAction ,dest))
            }
        }


    }

    private fun isBackwardEquivant(observedAbstractState: AbstractState, expectedAbstractState: AbstractState): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}