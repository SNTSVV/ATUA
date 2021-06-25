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
    val backwardEquivalentAbstractStateMapping = HashMap<AbstractState, HashSet<AbstractState>>()
    val backwardEquivalentAbstractTransitionMapping = HashMap<AbstractTransition, AbstractTransition>()
    val observedBaseAbstractState = HashSet<AbstractState>()
    val incorrectTransitions = HashSet<AbstractTransition>()
    val correctTransitions = HashSet<AbstractTransition>()
    val quasibackwardEquivalentMapping = HashMap<Pair<AbstractState, AbstractAction>,HashSet<Pair<AbstractState,AbstractState>>>()

    val checkingQuasibackwardEquivalent = Stack<Triple<AbstractState,AbstractAction,AbstractState>> ()

    fun runtimeAdaptation() {
    }

    fun checkingEquivalence(observedAbstractState: AbstractState, abstractTransition: AbstractTransition, prevWindowAbstractState: AbstractState?, dstg: DSTG) {
        if (!isConsideringAbstractAction(abstractTransition.abstractAction)) {
            return
        }
        if (observedAbstractState.isHomeScreen)
            return
        /*if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            checkingQuasibackwardEquivalent.clear()
        }*/
        if (abstractTransition.modelVersion == ModelVersion.BASE) {
            correctTransitions.add(abstractTransition)
            return
        }
        if (observedAbstractState.modelVersion == ModelVersion.BASE) {
            observedBaseAbstractState.add(observedAbstractState)
            return
        }
        if (backwardEquivalentAbstractStateMapping.containsKey(observedAbstractState)) {
            return
        }
        var backwardEquivalenceFound = false
        val matchedAVMs1 = HashMap<AttributeValuationMap,AttributeValuationMap>() // observered - expected
        val matchedAVMs2 = HashMap<AttributeValuationMap,AttributeValuationMap>() // expected - observered
        if (abstractTransition.abstractAction.isLaunchOrReset()) {
            val baseModelInitialStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                it.isInitalState && it.modelVersion == ModelVersion.BASE
            }
            baseModelInitialStates.forEach {baseModelInitialState->
                val expectedAbstractState = baseModelInitialState
                matchedAVMs1.clear()
                matchedAVMs2.clear()
                if (observedAbstractState == expectedAbstractState) {
                    backwardEquivalenceFound = true
                }
                else if (isBackwardEquivant(observedAbstractState, expectedAbstractState,matchedAVMs1,matchedAVMs2,false)) {
                    backwardEquivalentAbstractStateMapping.putIfAbsent(observedAbstractState, HashSet())
                    backwardEquivalentAbstractStateMapping[observedAbstractState]!!.add(expectedAbstractState)
                    copyAbstractTransitions(observedAbstractState, expectedAbstractState,dstg,matchedAVMs2)
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
            val obsoleteBaseAbstractTransitions = ArrayList<AbstractTransition>()
            obsoleteBaseAbstractTransitions.addAll(abstractTransition.source.abstractTransitions.filter {
                it.abstractAction == abstractTransition.abstractAction
                        /*&& (it.prevWindow == abstractTransition.prevWindow
                        || it.prevWindow == null)*/
                       /* && ( it.dependentAbstractState == abstractTransition.dependentAbstractState
                        || backwardEquivalentAbstractStateMapping.get(abstractTransition.dependentAbstractState)?.contains(it.dependentAbstractState)?:false)*/
                        && (it.dependentAbstractStates.isEmpty()
                            || (prevWindowAbstractState!=null &&
                                (it.dependentAbstractStates.contains(prevWindowAbstractState)
                                    || it.dependentAbstractStates.any { it == backwardEquivalentAbstractStateMapping.get(prevWindowAbstractState) })))
                        && it.modelVersion == ModelVersion.BASE
                        && it.interactions.isEmpty()
            })
            // TODO check
            abstractTransition.source.abstractTransitions.removeIf { obsoleteBaseAbstractTransitions.contains(it) }
            obsoleteBaseAbstractTransitions.forEach {
                val edge = dstg.edge(it.source,it.dest,it)
                if (edge!=null)
                    dstg.remove(edge)
            }
            if (obsoleteBaseAbstractTransitions.isNotEmpty()) {
                obsoleteBaseAbstractTransitions.forEach {
                    val dest = it.dest
                    matchedAVMs1.clear()
                    matchedAVMs2.clear()
                    if (observedAbstractState == dest) {
                        backwardEquivalenceFound = true
                    } else if (isBackwardEquivant(observedAbstractState, dest,matchedAVMs1,matchedAVMs2,false)) {
                        backwardEquivalentAbstractStateMapping.putIfAbsent(observedAbstractState, HashSet())
                        backwardEquivalentAbstractStateMapping[observedAbstractState]!!.add(dest)
                        copyAbstractTransitions(observedAbstractState, dest,dstg,matchedAVMs2)
                        backwardEquivalenceFound = true
                        correctTransitions.add(it)
                    } else {
                        abstractTransition.source.abstractTransitions.remove(it)
                        incorrectTransitions.add(it)
                    }
                }
            } else {
                val candidates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                    it.modelVersion == ModelVersion.BASE && it !is VirtualAbstractState && it.window == observedAbstractState.window
                            && it.rotation == observedAbstractState.rotation
                            && it.isOpeningMenus == observedAbstractState.isOpeningMenus
                            && it.isOpeningKeyboard == observedAbstractState.isOpeningKeyboard
                }
                candidates.forEach {
                    matchedAVMs1.clear()
                    matchedAVMs2.clear()
                    if (observedAbstractState == it) {
                        backwardEquivalenceFound = true
                    } else if (isBackwardEquivant(observedAbstractState, it,matchedAVMs1,matchedAVMs2,true)) {
                        backwardEquivalentAbstractStateMapping.putIfAbsent(observedAbstractState, HashSet())
                        backwardEquivalentAbstractStateMapping[observedAbstractState]!!.add(it)
                        copyAbstractTransitions(observedAbstractState, it,dstg,matchedAVMs2)
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
            AbstractActionType.PRESS_HOME -> false
            else -> true
        }
        return result
    }

    private fun copyAbstractTransitions(destination: AbstractState,
                                        source: AbstractState,dstg: DSTG,
                                        sourceDestAVMMatching: Map<AttributeValuationMap,AttributeValuationMap>) {
        source.abstractTransitions.filter { it.isExplicit()  }. forEach {sourceTransition->
            var destAbstractAction: AbstractAction? = null
            if (!sourceTransition.abstractAction.isWidgetAction())
                destAbstractAction = sourceTransition.abstractAction
            else
            {
                val destAVM = sourceDestAVMMatching.get(sourceTransition.abstractAction.attributeValuationMap!!)
                if (destAVM!=null) {
                    destAbstractAction = AbstractAction(
                            actionType = sourceTransition.abstractAction.actionType,
                            attributeValuationMap = destAVM,
                            extra = sourceTransition.abstractAction.extra
                    )
                }
            }
            if (destAbstractAction!=null) {
                val dependendAbstractStates = ArrayList<AbstractState>()
                for (dependentAbstractState in sourceTransition.dependentAbstractStates) {
                    if (dependentAbstractState.guiStates.isNotEmpty())
                        dependendAbstractStates.add(dependentAbstractState)
                    else{
                        val equivalences = backwardEquivalentAbstractStateMapping.filter { it.value.contains(dependentAbstractState) }
                        if (equivalences.isEmpty()) {
                            dependendAbstractStates.add(dependentAbstractState)
                        } else {
                            equivalences.forEach { t, _ ->
                                dependendAbstractStates.add(t)
                            }

                        }
                    }
                }
              /*  val dependentAbstractState = if (sourceTransition.dependentAbstractState!=null) {
                    if (sourceTransition.dependentAbstractState!!.guiStates.isNotEmpty()) {
                        sourceTransition.dependentAbstractState
                    } else {
                        backwardEquivalentAbstractStateMapping.get(sourceTransition.dependentAbstractState!!)?.firstOrNull()?:sourceTransition.dependentAbstractState
                    }
                } else
                    null*/
                val dest = if (sourceTransition.dest.guiStates.isNotEmpty())
                    sourceTransition.dest
                else
                    backwardEquivalentAbstractStateMapping.get(sourceTransition.dest)?.firstOrNull()?:sourceTransition.dest
                val existingAbstractTransition = destination.abstractTransitions.find {
                    it.modelVersion == ModelVersion.BASE
                            && it.abstractAction == destAbstractAction
                            && it.dest == dest
                            /*&& it.prevWindow == sourceTransition.prevWindow*/
                            && it.isImplicit == false
                           /* && it.dependentAbstractState == dependentAbstractState*/
                }
                // TODO check
                if (existingAbstractTransition == null) {
                    // create new Abstract Transition
                    val newAbstractTransition = AbstractTransition(
                            source = destination,
                            dest = sourceTransition.dest,
                            /*prevWindow = sourceTransition.prevWindow,*/
                            abstractAction = destAbstractAction,
                            isImplicit = sourceTransition.isImplicit,
                            modelVersion = ModelVersion.BASE,
                            data = sourceTransition.data
                    )
                    // TODO check
                    newAbstractTransition.dependentAbstractStates.addAll(dependendAbstractStates)
                    dstg.add(newAbstractTransition.source, newAbstractTransition.dest, newAbstractTransition)
                    newAbstractTransition.userInputs.addAll(sourceTransition.userInputs)
                    sourceTransition.handlers.forEach { handler, _ ->
                        newAbstractTransition.handlers.putIfAbsent(handler, false)
                    }
                    backwardEquivalentAbstractTransitionMapping.put(newAbstractTransition,sourceTransition)
                } else {
                    // copy additional information
                    existingAbstractTransition.userInputs.addAll(sourceTransition.userInputs)
                    sourceTransition.handlers.forEach { handler, _ ->
                        existingAbstractTransition.handlers.putIfAbsent(handler, false)
                    }
                    existingAbstractTransition.dependentAbstractStates.addAll(dependendAbstractStates)
                }
            }
        }
    }

    private fun isBackwardEquivant(observedAbstractState: AbstractState,
                                   expectedAbstractState: AbstractState,
                                   matchedAVMs1: HashMap<AttributeValuationMap,AttributeValuationMap>,
                                   matchedAVMs2: HashMap<AttributeValuationMap,AttributeValuationMap>,
                                   strict: Boolean): Boolean {
        if (observedAbstractState.isOpeningKeyboard != expectedAbstractState.isOpeningKeyboard)
            return false
        if (observedAbstractState.isOpeningMenus != expectedAbstractState.isOpeningMenus)
            return false
        if (observedAbstractState.rotation != expectedAbstractState.rotation)
            return false
        val observedAbstractStateAVMCount = observedAbstractState.attributeValuationMaps.size
        val expectedAbstractStateAVMCount = expectedAbstractState.attributeValuationMaps.size
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
            if (!strict) {
                if (unmatchedWidgets1.intersect(unmatchedWidgets2).isEmpty())
                    return true
                return false
            }
            val updateWindowCreatedRuntimeWidgets = observedAbstractState.window.widgets
                    .filter { it.createdAtRuntime &&  it.modelVersion == ModelVersion.RUNNING }
            val baseWindowCreatedRuntimeWidgets = expectedAbstractState.window.widgets
                    .filter { it.createdAtRuntime &&  it.modelVersion == ModelVersion.BASE }
            val condition1 = if (unmatchedWidgets1.isNotEmpty()) unmatchedWidgets1.all {
                updateWindowCreatedRuntimeWidgets.contains(it)
                        && !baseWindowCreatedRuntimeWidgets.contains(it)} else true
            val condition2 = if (unmatchedWidgets2.isNotEmpty()) unmatchedWidgets2.all {
                baseWindowCreatedRuntimeWidgets.contains(it)
                        && !updateWindowCreatedRuntimeWidgets.contains(it)} else true
            if (condition1 && condition2) {
                return true
            }
            return false
        }
    }

    companion object {
        val instance: ModelBackwardAdapter by lazy {
            ModelBackwardAdapter()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(ModelBackwardAdapter::class.java) }
    }
}