package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.ewtgdiff.AdditionSet
import org.droidmate.exploration.modelFeatures.atua.ewtgdiff.EWTGDiff
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class ModelBackwardAdapter {
    val backwardEquivalentMapping = HashMap<AbstractState, HashSet<AbstractState>>()
    val remappedBaseAbstractState = HashSet<AbstractState>()
    val quasibackwardEquivalentMapping = HashMap<Pair<AbstractState, AbstractAction>,HashSet<Pair<AbstractState,AbstractState>>>()

    val checkingQuasibackwardEquivalent = Stack<Triple<AbstractState,AbstractAction,AbstractState>> ()

    fun runtimeAdaptation() {
    }
    fun checkingEquivalence(observedAbstractState: AbstractState, abstractTransition: AbstractTransition) {
        if (!isConsideringAbstractAction(abstractTransition.abstractAction)) {
            return
        }
        /*if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            checkingQuasibackwardEquivalent.clear()
        }*/
        if (observedAbstractState.modelVersion == ModelVersion.BASE) {
            remappedBaseAbstractState.add(observedAbstractState)
            return
        }
        if (abstractTransition.modelVersion == ModelVersion.BASE) {
            return
        }

        if (backwardEquivalentMapping.values.any {it.contains(observedAbstractState)}) {
            return
        }
        var backwardEquivalenceFound = false
        if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            val baseModelInitialState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                it.isInitalState && it.modelVersion == ModelVersion.BASE
            }!!
            val expectedAbstractState = baseModelInitialState
            if (observedAbstractState == expectedAbstractState) {
                backwardEquivalenceFound = true
            }
            else if (isBackwardEquivant(observedAbstractState, expectedAbstractState)) {
                backwardEquivalentMapping.putIfAbsent(expectedAbstractState, HashSet())
                backwardEquivalentMapping[expectedAbstractState]!!.add(observedAbstractState)
                copyAbstractTransitions(observedAbstractState, expectedAbstractState)
                backwardEquivalenceFound = true
            }
            if (backwardEquivalenceFound) {
                return
            }
           /* if (expectedAbstractState.window == observedAbstractState.window) {
                checkingQuasibackwardEquivalent.push(Triple(expectedAbstractState,abstractTransition.abstractAction ,observedAbstractState))
            }*/
        } else {
            val baseAbstractTransitions = ArrayList<AbstractTransition>()
            /*if (checkingQuasibackwardEquivalent.isNotEmpty()) {
                val checkingQuasiEquivalence = checkingQuasibackwardEquivalent.peek().first
                baseAbstractTransitions.addAll(checkingQuasiEquivalence.abstractTransitions.filter {
                    it.abstractAction == abstractTransition.abstractAction
                            && it.prevWindow == abstractTransition.prevWindow
                            && it.modelVersion == ModelVersion.BASE
                })
                baseAbstractTransitions.forEach {
                    val dest = it.dest
                    if (observedAbstractState == dest) {
                        backwardEquivalenceFound = true
                    }
                    else if (isBackwardEquivant(observedAbstractState, dest)) {
                        backwardEquivalentMapping.putIfAbsent(dest, HashSet())
                        backwardEquivalentMapping[dest]!!.add(observedAbstractState)
                        copyAbstractTransitions(observedAbstractState, dest)
                        backwardEquivalenceFound = true
                    }
                }
                if (backwardEquivalenceFound) {
                    if (checkingQuasibackwardEquivalent.isNotEmpty()) {
                        var dest = observedAbstractState
                        while (checkingQuasibackwardEquivalent.isEmpty()) {
                            val item = checkingQuasibackwardEquivalent.pop()
                            val actionStatePair = Pair(item.first,item.second)
                            quasibackwardEquivalentMapping.putIfAbsent(actionStatePair, HashSet())
                            quasibackwardEquivalentMapping.get(actionStatePair)!!.add(Pair(item.third,dest))
                            dest = item.first
                        }
                    }
                    return
                }
                var continueCheckingQuasibackwardEquivalent = false
                baseAbstractTransitions.forEach {
                    val dest = it.dest
                    if (dest.window == observedAbstractState.window) {
                        checkingQuasibackwardEquivalent.push(Triple(dest,abstractTransition.abstractAction ,observedAbstractState))
                        continueCheckingQuasibackwardEquivalent = true
                    }
                }
                if (!continueCheckingQuasibackwardEquivalent) {
                    checkingQuasibackwardEquivalent.clear()
                }
            } else {
                baseAbstractTransitions.addAll(abstractTransition.source.abstractTransitions.filter {
                    it.abstractAction == abstractTransition.abstractAction
                            && it.prevWindow == abstractTransition.prevWindow
                            && it.modelVersion == ModelVersion.BASE
                })
                baseAbstractTransitions.forEach {
                    val dest = it.dest
                    if (observedAbstractState == dest) {
                        backwardEquivalenceFound = true
                    }
                    else if (isBackwardEquivant(observedAbstractState, dest)) {
                        backwardEquivalentMapping.putIfAbsent(dest, HashSet())
                        backwardEquivalentMapping[dest]!!.add(observedAbstractState)
                        copyAbstractTransitions(observedAbstractState, dest)
                        backwardEquivalenceFound = true
                    }
                }
                if (backwardEquivalenceFound) {
                    return
                }
                var continueCheckingQuasibackwardEquivalent = false
                baseAbstractTransitions.forEach {
                    val dest = it.dest
                    if (dest.window == observedAbstractState.window) {
                        checkingQuasibackwardEquivalent.push(Triple(dest,abstractTransition.abstractAction ,observedAbstractState))
                        continueCheckingQuasibackwardEquivalent = true
                    }
                }
            }*/
            baseAbstractTransitions.addAll(abstractTransition.source.abstractTransitions.filter {
                it.abstractAction == abstractTransition.abstractAction
                        && (it.prevWindow == abstractTransition.prevWindow
                        || it.prevWindow == null)
                        && (it.preWindowAbstractState == abstractTransition.preWindowAbstractState)
                        && it.modelVersion == ModelVersion.BASE
                        && it.dest.guiStates.isEmpty() // which means that the destination has not observed before
            })
            if (baseAbstractTransitions.isNotEmpty()) {
                baseAbstractTransitions.forEach {
                    val dest = it.dest
                    if (observedAbstractState == dest) {
                        backwardEquivalenceFound = true
                    } else if (isBackwardEquivant(observedAbstractState, dest)) {
                        backwardEquivalentMapping.putIfAbsent(dest, HashSet())
                        backwardEquivalentMapping[dest]!!.add(observedAbstractState)
                        copyAbstractTransitions(observedAbstractState, dest)
                        backwardEquivalenceFound = true
                    } else {
                        abstractTransition.source.abstractTransitions.remove(it)
                    }
                }
            } else {
                if (abstractTransition.source.abstractTransitions.any {
                            it.abstractAction == abstractTransition.abstractAction
                                    && it.dest.window == observedAbstractState.window
                                    && it.fromWTG}) {
                    val candidates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                        it !is VirtualAbstractState && it.window == observedAbstractState.window
                                && it.modelVersion == ModelVersion.BASE
                    }
                    candidates.forEach {
                        if (observedAbstractState == it) {
                            backwardEquivalenceFound = true
                        } else if (isBackwardEquivant(observedAbstractState, it)) {
                            backwardEquivalentMapping.putIfAbsent(it, HashSet())
                            backwardEquivalentMapping[it]!!.add(observedAbstractState)
                            copyAbstractTransitions(observedAbstractState, it)
                            backwardEquivalenceFound = true
                        }
                    }
                }
            }
            if (backwardEquivalenceFound) {
                return
            }
           /* var continueCheckingQuasibackwardEquivalent = false
            baseAbstractTransitions.forEach {
                val dest = it.dest
                if (dest.window == observedAbstractState.window) {
                    checkingQuasibackwardEquivalent.push(Triple(dest,abstractTransition.abstractAction ,observedAbstractState))
                    continueCheckingQuasibackwardEquivalent = true
                }
            }*/
        }
    }

    private fun isConsideringAbstractAction(abstractAction: AbstractAction): Boolean {
        val result = when (abstractAction.actionType) {
            AbstractActionType.WAIT -> false
            else -> true
        }
        return result
    }

    private fun copyAbstractTransitions(destination: AbstractState, source: AbstractState) {
        source.abstractTransitions.forEach {sourceTransition->
            val existingAbstractTransition = destination.abstractTransitions.find {
                it.abstractAction == sourceTransition.abstractAction
                        && it.dest == sourceTransition.dest
                        && it.prevWindow == sourceTransition.prevWindow
                        && it.isImplicit == sourceTransition.isImplicit
            }
            if (existingAbstractTransition == null) {
                // create new Abstract Transition
                val newAbstractTransition = AbstractTransition(
                        source = destination,
                        dest = sourceTransition.dest,
                        prevWindow = sourceTransition.prevWindow,
                        abstractAction = sourceTransition.abstractAction,
                        isImplicit = sourceTransition.isImplicit
                )
                newAbstractTransition.modelVersion == ModelVersion.BASE
                newAbstractTransition.userInputs.addAll(sourceTransition.userInputs)
                sourceTransition.handlers.forEach { handler, _ ->
                    newAbstractTransition.handlers.putIfAbsent(handler,false)
                }
            } else {
                // copy additional information
                existingAbstractTransition.userInputs.addAll(sourceTransition.userInputs)
                sourceTransition.handlers.forEach { handler, _ ->
                    existingAbstractTransition.handlers.putIfAbsent(handler,false)
                }
            }
        }
    }

    private fun isBackwardEquivant(observedAbstractState: AbstractState, expectedAbstractState: AbstractState): Boolean {
        val observedAbstractStateAVMCount = observedAbstractState.attributeValuationMaps.size
        val expectedAbstractStateAVMCount = expectedAbstractState.attributeValuationMaps.size
        val matchedAVMs = ArrayList<Pair<AttributeValuationMap,AttributeValuationMap>>() // observered - expected
        val unmatchedAVMs = ArrayList<AttributeValuationMap>() // observered
        val addedAVMS = ArrayList<AttributeValuationMap>()
        val addedWidgets = EWTGDiff.instance.widgetDifferentSets.get("AdditionSet") as AdditionSet
        observedAbstractState.EWTGWidgetMapping.forEach { avm, widget ->
            if (addedWidgets.addedElements.contains(widget)) {
                addedAVMS.add(avm)
            }
        }
        observedAbstractState.attributeValuationMaps.filterNot{ addedAVMS.contains(it) }.forEach {avm1->
            var matchedAVM = expectedAbstractState.attributeValuationMaps.find { it == avm1  }
            if (matchedAVM == null ) {
                val associatedWidget = observedAbstractState.EWTGWidgetMapping.get(avm1)
                matchedAVM = expectedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.find {
                    it.isClickable() == avm1.isClickable()
                            && it.isLongClickable() == avm1.isLongClickable()
                            && it.isChecked() == avm1.isChecked()
                            && it.isScrollable() == avm1.isScrollable()
                }
            }
            if (matchedAVM != null){
                matchedAVMs.add(Pair(avm1,matchedAVM))
            } else {
                unmatchedAVMs.add(avm1)
            }
        }
        if (unmatchedAVMs.size == 0) {
            return true
        } else {
            val unmatchedWidgets =  unmatchedAVMs.map {  observedAbstractState.EWTGWidgetMapping.get(it)}
            val windowBaseCreatedRuntimeWidgets = observedAbstractState.window.widgets.filter { it.createdAtRuntime &&  it.modelVersion == ModelVersion.BASE }
            if (unmatchedWidgets.all {windowBaseCreatedRuntimeWidgets.contains(it) }) {
                return true
            }
        }
        return false
    }

    companion object {
        val instance: ModelBackwardAdapter by lazy {
            ModelBackwardAdapter()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(ModelBackwardAdapter::class.java) }
    }
}