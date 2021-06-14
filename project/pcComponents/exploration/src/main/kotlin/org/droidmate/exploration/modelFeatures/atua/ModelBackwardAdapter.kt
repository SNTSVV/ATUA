package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.DSTG
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
    val incorrectTransition = HashSet<AbstractTransition>()
    val quasibackwardEquivalentMapping = HashMap<Pair<AbstractState, AbstractAction>,HashSet<Pair<AbstractState,AbstractState>>>()

    val checkingQuasibackwardEquivalent = Stack<Triple<AbstractState,AbstractAction,AbstractState>> ()

    fun runtimeAdaptation() {
    }

    fun checkingEquivalence(observedAbstractState: AbstractState, abstractTransition: AbstractTransition, dstg: DSTG) {
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
        if (abstractTransition.abstractAction.isLaunchOrReset()) {
            val baseModelInitialStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                it.isInitalState && it.modelVersion == ModelVersion.BASE
            }
            baseModelInitialStates.forEach {baseModelInitialState->
                val expectedAbstractState = baseModelInitialState
                if (observedAbstractState == expectedAbstractState) {
                    backwardEquivalenceFound = true
                }
                else if (isBackwardEquivant(observedAbstractState, expectedAbstractState)) {
                    backwardEquivalentMapping.putIfAbsent(expectedAbstractState, HashSet())
                    backwardEquivalentMapping[expectedAbstractState]!!.add(observedAbstractState)
                    copyAbstractTransitions(observedAbstractState, expectedAbstractState,dstg)
                    backwardEquivalenceFound = true
                }
            }
            if (backwardEquivalenceFound) {
                return
            }
           /* if (expectedAbstractState.window == observedAbstractState.window) {
                checkingQuasibackwardEquivalent.push(Triple(expectedAbstractState,abstractTransition.abstractAction ,observedAbstractState))
            }*/
        }
        if(!backwardEquivalenceFound) {
            val baseAbstractTransitions = ArrayList<AbstractTransition>()
            baseAbstractTransitions.addAll(abstractTransition.source.abstractTransitions.filter {
                it.abstractAction == abstractTransition.abstractAction
                        && (it.prevWindow == abstractTransition.prevWindow
                        || it.prevWindow == null)
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
                        copyAbstractTransitions(observedAbstractState, dest,dstg)
                        backwardEquivalenceFound = true
                    } else {
                        abstractTransition.source.abstractTransitions.remove(it)
                        incorrectTransition.add(it)
                    }
                }
            } else {
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
                        copyAbstractTransitions(observedAbstractState, it,dstg)
                        backwardEquivalenceFound = true
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

    private fun copyAbstractTransitions(destination: AbstractState, source: AbstractState,dstg: DSTG) {
        source.abstractTransitions.filter { it.isExplicit()  }. forEach {sourceTransition->
            val existingAbstractTransition = destination.abstractTransitions.find {
                it.abstractAction == sourceTransition.abstractAction
                        && it.dest == sourceTransition.dest
                        && it.prevWindow == sourceTransition.prevWindow
                        && it.isImplicit == false
                        && it.modelVersion == ModelVersion.BASE
            }
            if (existingAbstractTransition == null) {
                // create new Abstract Transition
                val newAbstractTransition = AbstractTransition(
                        source = destination,
                        dest = sourceTransition.dest,
                        prevWindow = sourceTransition.prevWindow,
                        abstractAction = sourceTransition.abstractAction,
                        isImplicit = sourceTransition.isImplicit,
                        modelVersion = ModelVersion.BASE
                )
                dstg.add(newAbstractTransition.source,newAbstractTransition.dest,newAbstractTransition)
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
        val matchedAVMs1 = HashMap<AttributeValuationMap,AttributeValuationMap>() // observered - expected
        val matchedAVMs2 = HashMap<AttributeValuationMap,AttributeValuationMap>() // expected - observered
        val unmatchedAVMs1 = ArrayList<AttributeValuationMap>() // observered
        val unmatchedAVMs2 = ArrayList<AttributeValuationMap>() // expected
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
                val matchedAVM1 = expectedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.find {
                    it.isClickable() == avm1.isClickable()
                            && it.isLongClickable() == avm1.isLongClickable()
                            && it.isChecked() == avm1.isChecked()
                            && it.isScrollable() == avm1.isScrollable()
                }
                val replacedWidgets = EWTGDiff.instance.getWidgetReplacement()
                if (replacedWidgets.contains(associatedWidget)) {
                    matchedAVM = expectedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.find {
                        it.isClickable() == avm1.isClickable()
                                && it.isLongClickable() == avm1.isLongClickable()
                                && it.isChecked() == avm1.isChecked()
                                && it.isScrollable() == avm1.isScrollable()
                    }
                }
                assert(matchedAVM1==matchedAVM)
            }
            if (matchedAVM != null){
                matchedAVMs1.put(avm1,matchedAVM)
                matchedAVMs2.put(matchedAVM,avm1)
            } else {
                unmatchedAVMs1.add(avm1)
            }
        }
        expectedAbstractState.attributeValuationMaps.filterNot{ addedAVMS.contains(it) && matchedAVMs2.keys.contains(it) }.forEach { avm1 ->
            var matchedAVM = observedAbstractState.attributeValuationMaps.find { it == avm1  }
            if (matchedAVM == null ) {
                val associatedWidget = expectedAbstractState.EWTGWidgetMapping.get(avm1)
                matchedAVM = observedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.find {
                    it.isClickable() == avm1.isClickable()
                            && it.isLongClickable() == avm1.isLongClickable()
                            && it.isChecked() == avm1.isChecked()
                            && it.isScrollable() == avm1.isScrollable()
                }
            }
            if (matchedAVM != null){
                matchedAVMs2.put(avm1,matchedAVM!!)
                if (!matchedAVMs1.containsKey(matchedAVM!!)) {
                    matchedAVMs1.put(matchedAVM!!,avm1)
                }
            } else {
                unmatchedAVMs2.add(avm1)
            }
        }
        if (unmatchedAVMs1.size == 0 && unmatchedAVMs2.size == 0) {
            return true
        } else {
            val unmatchedWidgets1 =  unmatchedAVMs1.map {  observedAbstractState.EWTGWidgetMapping.get(it)}
            val unmatchedWidgets2 = unmatchedAVMs2.map { expectedAbstractState.EWTGWidgetMapping.get(it) }
            val updateWindowCreatedRuntimeWidgets = observedAbstractState.window.widgets.filter { it.createdAtRuntime &&  it.modelVersion == ModelVersion.RUNNING }
            val baseWindowCreatedRuntimeWidgets = expectedAbstractState.window.widgets.filter { it.createdAtRuntime &&  it.modelVersion == ModelVersion.BASE }
            val condition1 = if (unmatchedWidgets1.isNotEmpty()) unmatchedWidgets1.all {updateWindowCreatedRuntimeWidgets.contains(it) } else true
            val condition2 = if (unmatchedWidgets2.isNotEmpty()) unmatchedWidgets2.all {baseWindowCreatedRuntimeWidgets.contains(it) } else true
            if (condition1 && condition2) {
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