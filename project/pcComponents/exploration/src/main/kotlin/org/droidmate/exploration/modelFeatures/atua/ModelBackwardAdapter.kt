package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.ExplorationContext
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
import org.droidmate.explorationModel.interaction.State
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class ModelBackwardAdapter {
    val backwardEquivalentAbstractStateMapping = HashMap<AbstractState, HashSet<AbstractState>>()
    val backwardEquivalentAbstractTransitionMapping = HashMap<AbstractTransition, HashSet<AbstractTransition>>()
    val observedBaseAbstractState = HashSet<AbstractState>()
    val incorrectTransitions = HashSet<AbstractTransition>()
    val observedBasedAbstractTransitions = HashSet<AbstractTransition>()
    val initialBaseAbstractTransitions = HashSet<AbstractTransition>()
    val keptBaseAbstractTransitions = HashSet<AbstractTransition>()
    val initialBaseAbstractStates = HashSet<AbstractState>()
    val keptBaseAbstractStates = HashSet<AbstractState>()
    val entierlyNewAbstractStates = HashSet<AbstractState>()
    val quasibackwardEquivalentMapping = HashMap<Pair<AbstractState, AbstractAction>,HashSet<Pair<AbstractState,AbstractState>>>()

    val checkingQuasibackwardEquivalent = Stack<Triple<AbstractState,AbstractAction,AbstractState>> ()

    fun runtimeAdaptation() {
    }

    fun checkingEquivalence(guiState: State<*>, observedAbstractState: AbstractState, abstractTransition: AbstractTransition, prevWindowAbstractState: AbstractState?, dstg: DSTG) {
        if (!isConsideringAbstractAction(abstractTransition.abstractAction)) {
            return
        }
        if (observedAbstractState.isHomeScreen)
            return
        /*if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            checkingQuasibackwardEquivalent.clear()
        }*/
        if (entierlyNewAbstractStates.contains(observedAbstractState))
            return
        if (abstractTransition.modelVersion == ModelVersion.BASE) {
            observedBasedAbstractTransitions.add(abstractTransition)
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
        val matchedAVMs1 = HashMap<AttributeValuationMap,ArrayList<AttributeValuationMap>>() // observered - expected
        val matchedAVMs2 = HashMap<AttributeValuationMap,ArrayList<AttributeValuationMap>>() // expected - observered
        if (abstractTransition.abstractAction.isLaunchOrReset()) {
            val baseModelInitialStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
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
                    registerBackwardEquivalence(observedAbstractState, expectedAbstractState, dstg, matchedAVMs2)
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
                        && (!it.guardEnabled || it.dependentAbstractStates.isEmpty()
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
                    val expected = it.dest
                    matchedAVMs1.clear()
                    matchedAVMs2.clear()
                    if (observedAbstractState == expected) {
                        backwardEquivalenceFound = true
                    } else if (isBackwardEquivant(observedAbstractState, expected,matchedAVMs1,matchedAVMs2,false)) {
                        registerBackwardEquivalence(observedAbstractState, expected, dstg, matchedAVMs2)
                        backwardEquivalenceFound = true
                        backwardEquivalentAbstractTransitionMapping.putIfAbsent(abstractTransition,HashSet())
                        backwardEquivalentAbstractTransitionMapping.get(abstractTransition)!!.add(it)
                    } else {
                        abstractTransition.source.abstractTransitions.remove(it)
                        incorrectTransitions.add(it)
                    }
                }
            } else {
                val candidates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                    it.modelVersion == ModelVersion.BASE
                            && it.guiStates.isEmpty()
                            && it !is VirtualAbstractState && it.window == observedAbstractState.window
                            && it.rotation == observedAbstractState.rotation
                            && it.isOpeningMenus == observedAbstractState.isOpeningMenus
                            && it.isOpeningKeyboard == observedAbstractState.isOpeningKeyboard
                }
                candidates.forEach {
                    if (!backwardEquivalenceFound) {
                        matchedAVMs1.clear()
                        matchedAVMs2.clear()
                        if (observedAbstractState == it) {
                            backwardEquivalenceFound = true
                        } else if (isBackwardEquivant(observedAbstractState, it, matchedAVMs1, matchedAVMs2, true)) {
                            registerBackwardEquivalence(observedAbstractState, it, dstg, matchedAVMs2)
                            backwardEquivalenceFound = true
                        }
                    }
                }
            }
            if (backwardEquivalenceFound) {
                return
            } else {
                entierlyNewAbstractStates.add(observedAbstractState)
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

    private fun registerBackwardEquivalence(observedAbstractState: AbstractState, expected: AbstractState, dstg: DSTG, matchedAVMs2: HashMap<AttributeValuationMap, ArrayList<AttributeValuationMap>>) {
        backwardEquivalentAbstractStateMapping.putIfAbsent(observedAbstractState, HashSet())
        backwardEquivalentAbstractStateMapping[observedAbstractState]!!.add(expected)
        copyAbstractTransitions(observedAbstractState, expected, dstg, matchedAVMs2)
        /*AbstractStateManager.INSTANCE.ABSTRACT_STATES.remove(expected)*/
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
                                        sourceDestAVMMatching: Map<AttributeValuationMap,List<AttributeValuationMap>>) {
        source.abstractTransitions.filter { it.isExplicit() && it.modelVersion == ModelVersion.BASE  }. forEach { sourceTransition->
            if (!sourceTransition.abstractAction.isWidgetAction()) {
                val destAbstractAction: AbstractAction = sourceTransition.abstractAction
                copyAbstractTransitionFromBase(sourceTransition, destination, destAbstractAction, dstg)
            } else {
                val destAVMs = sourceDestAVMMatching.get(sourceTransition.abstractAction.attributeValuationMap!!)
                if (destAVMs!=null) {
                    destAVMs.forEach {destAVM->
                        val destAbstractAction = AbstractAction(
                                actionType = sourceTransition.abstractAction.actionType,
                                attributeValuationMap = destAVM,
                                extra = sourceTransition.abstractAction.extra
                        )
                        copyAbstractTransitionFromBase(sourceTransition,destination, destAbstractAction,dstg)
                    }

                }
            }
        }
    }

    private fun copyAbstractTransitionFromBase(baseTransition: AbstractTransition, updatedAbstractState: AbstractState, updatedAbstractAction: AbstractAction, dstg: DSTG) {
        val dependendAbstractStates = ArrayList<AbstractState>()
        for (dependentAbstractState in baseTransition.dependentAbstractStates) {
            if (dependentAbstractState.guiStates.isNotEmpty())
                dependendAbstractStates.add(dependentAbstractState)
            else {
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
        val dest = if (baseTransition.dest.guiStates.isNotEmpty())
            baseTransition.dest
        else
            backwardEquivalentAbstractStateMapping.get(baseTransition.dest)?.firstOrNull() ?: baseTransition.dest
        val existingAbstractTransition = updatedAbstractState.abstractTransitions.find {
            it.modelVersion == ModelVersion.BASE
                    && it.abstractAction == updatedAbstractAction
                    && it.dest == dest
                    /*&& it.prevWindow == sourceTransition.prevWindow*/
                    && it.isImplicit == false
            /* && it.dependentAbstractState == dependentAbstractState*/
        }
        // TODO check
        if (existingAbstractTransition == null) {
            // create new Abstract Transition
            val newAbstractTransition = AbstractTransition(
                    source = updatedAbstractState,
                    dest = baseTransition.dest,
                    /*prevWindow = sourceTransition.prevWindow,*/
                    abstractAction = updatedAbstractAction,
                    isImplicit = baseTransition.isImplicit,
                    modelVersion = ModelVersion.BASE,
                    data = baseTransition.data
            )
            updatedAbstractState.increaseActionCount2(updatedAbstractAction,false)
            newAbstractTransition.dependentAbstractStates.addAll(dependendAbstractStates)
            dstg.add(newAbstractTransition.source, newAbstractTransition.dest, newAbstractTransition)
            newAbstractTransition.userInputs.addAll(baseTransition.userInputs)
            baseTransition.handlers.forEach { handler, _ ->
                newAbstractTransition.handlers.putIfAbsent(handler, false)
            }
            // backwardEquivalentAbstractTransitionMapping.put(newAbstractTransition, baseTransition)
        } else {
            // copy additional information
            existingAbstractTransition.userInputs.addAll(baseTransition.userInputs)
            baseTransition.handlers.forEach { handler, _ ->
                existingAbstractTransition.handlers.putIfAbsent(handler, false)
            }
            existingAbstractTransition.dependentAbstractStates.addAll(dependendAbstractStates)
        }
    }

    private fun isBackwardEquivant(observedAbstractState: AbstractState,
                                   expectedAbstractState: AbstractState,
                                   matchedAVMs1: HashMap<AttributeValuationMap,ArrayList<AttributeValuationMap>>,
                                   matchedAVMs2: HashMap<AttributeValuationMap,ArrayList<AttributeValuationMap>>,
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
            var matchedAVMs = expectedAbstractState.attributeValuationMaps.filter { it == avm1  }
            if (matchedAVMs.isEmpty()) { // this could be caused by over fine-grained of abstraction function
                matchedAVMs = expectedAbstractState.attributeValuationMaps.filter { avm1.isDerivedFrom(it) }
            }
            if (matchedAVMs.isEmpty() ) {
                val associatedWidget = observedAbstractState.EWTGWidgetMapping.get(avm1)
                val matchedAVM1 = expectedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.find {
                    it.isClickable() == avm1.isClickable()
                            /*&& it.isLongClickable() == avm1.isLongClickable()*/
                            && it.isChecked() == avm1.isChecked()
                            && it.isScrollable() == avm1.isScrollable()
                }
                val replacedWidgets = EWTGDiff.instance.getWidgetReplacement()
                if (replacedWidgets.contains(associatedWidget) || true) {
                    matchedAVMs = expectedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.filter {
                        it.isClickable() == avm1.isClickable()
                                /*&& it.isLongClickable() == avm1.isLongClickable()*/
                                && it.isChecked() == avm1.isChecked()
                                /*&& it.isScrollable() == avm1.isScrollable()*/
                    }
                }
            }
            if (matchedAVMs.isNotEmpty()){
                matchedAVMs1.putIfAbsent(avm1, ArrayList())
                val matchedList = matchedAVMs1.get(avm1)!!
                matchedAVMs.forEach {
                    if (!matchedList.contains(it))
                        matchedList.add(it)
                    matchedAVMs2.putIfAbsent(it, ArrayList())
                    val matchedList2 = matchedAVMs2.get(it)!!
                    if (!matchedList2.contains(avm1))
                        matchedList2.add(avm1)
                }
            } else {
                unmatchedAVMs1.add(avm1)
            }
        }
        expectedAbstractState.attributeValuationMaps.filterNot{ matchedAVMs2.keys.contains(it) }.forEach { avm1 ->
            var matchedAVMs = observedAbstractState.attributeValuationMaps.filter { it == avm1  }
            if (matchedAVMs.isEmpty() ) {
                val associatedWidget = expectedAbstractState.EWTGWidgetMapping.get(avm1)
                matchedAVMs = observedAbstractState.EWTGWidgetMapping.filter { it.value == associatedWidget }.keys.filter {
                    it.isClickable() == avm1.isClickable()
                            /*&& it.isLongClickable() == avm1.isLongClickable()*/
                            && it.isChecked() == avm1.isChecked()
                         /*   && it.isScrollable() == avm1.isScrollable()*/
                }
            }
            if (matchedAVMs.isNotEmpty()){
                matchedAVMs2.putIfAbsent(avm1, ArrayList())
                val matchedList = matchedAVMs2.get(avm1)!!
                matchedAVMs.forEach {
                    if (!matchedList.contains(it))
                        matchedList.add(it)
                    matchedAVMs1.putIfAbsent(it, ArrayList())
                    val matchedList2 = matchedAVMs1.get(it)!!
                    if (!matchedList2.contains(avm1))
                        matchedList2.add(avm1)
                }
            } else {
                unmatchedAVMs2.add(avm1)
            }
        }
        unmatchedAVMs1.forEach { avm1->
            val matches = unmatchedAVMs2.filter { avm2-> isEquivalentAttributeValuationMaps(avm1,avm2) }
            if (matches.isNotEmpty()) {
                matchedAVMs1.putIfAbsent(avm1, ArrayList())
                val matchedList = matchedAVMs1.get(avm1)!!
                matches.forEach {
                    if (!matchedList.contains(it))
                        matchedList.add(it)
                    matchedAVMs2.putIfAbsent(it, ArrayList())
                    if (!matchedAVMs2.get(it)!!.contains(avm1))
                        matchedAVMs2.get(it)!!.add(avm1)
                }
            }
        }
        unmatchedAVMs1.removeIf { matchedAVMs1.containsKey(it) }
        unmatchedAVMs2.removeIf { matchedAVMs2.containsKey(it) }
        unmatchedAVMs2.forEach { avm2->
            val matches = unmatchedAVMs1.filter { avm1-> isEquivalentAttributeValuationMaps(avm2, avm1) }
            if (matches.isNotEmpty()) {
                matchedAVMs2.putIfAbsent(avm2, ArrayList())
                val matchedList = matchedAVMs2.get(avm2)!!
                matches.forEach {
                    if (!matchedList.contains(it))
                        matchedList.add(it)
                    matchedAVMs1.putIfAbsent(it, ArrayList())
                    if (!matchedAVMs1.get(it)!!.contains(avm2))
                        matchedAVMs1.get(it)!!.add(avm2)
                }
            }
        }
        unmatchedAVMs1.removeIf { matchedAVMs1.containsKey(it) }
        unmatchedAVMs2.removeIf { matchedAVMs2.containsKey(it) }
        if (unmatchedAVMs1.size == 0 && unmatchedAVMs2.size == 0) {
            return true
        } else {

            val unmatchedWidgets1 =  ArrayList(unmatchedAVMs1.map {  observedAbstractState.EWTGWidgetMapping.get(it)})
            val unmatchedWidgets2 = ArrayList(unmatchedAVMs2.map { expectedAbstractState.EWTGWidgetMapping.get(it) })
            unmatchedWidgets1.removeIf { it == null }
            unmatchedWidgets2.removeIf { it == null }
            if (!strict) {
                if (unmatchedWidgets1.intersect(unmatchedWidgets2).isEmpty())
                    return true
                return false
            }
            /*val baseAbstractStateToAttributeValuationMaps = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                it.window == observedAbstractState.window
                        && it.modelVersion == ModelVersion.BASE
            }. map { it to it.attributeValuationMaps }*/
            val updateWindowCreatedRuntimeWidgets = observedAbstractState.window.widgets
                    .filter { (it.createdAtRuntime ) &&  it.modelVersion == ModelVersion.RUNNING }
            val baseWindowCreatedRuntimeWidgets = expectedAbstractState.window.widgets
                    .filter { (it.createdAtRuntime  )&&  it.modelVersion == ModelVersion.BASE }
            val condition1 = if (unmatchedWidgets1.isNotEmpty()) unmatchedWidgets1.all {
                updateWindowCreatedRuntimeWidgets.contains(it)
                        && !baseWindowCreatedRuntimeWidgets.contains(it)} else true
            val condition2 = if (unmatchedWidgets2.isNotEmpty()) unmatchedWidgets2.all {
                baseWindowCreatedRuntimeWidgets.contains(it)
                        && !updateWindowCreatedRuntimeWidgets.contains(it)} else true
            if (condition1 && condition2) {
                return true
            }
            unmatchedAVMs1.removeIf { avm ->  ! observedAbstractState.abstractTransitions.any { it.abstractAction.attributeValuationMap == avm  }}
            unmatchedAVMs2.removeIf {avm ->  !expectedAbstractState.abstractTransitions.any { it.abstractAction.attributeValuationMap == avm } }
            val unmatchedWidgets1_2 =  ArrayList(unmatchedAVMs1.map {  observedAbstractState.EWTGWidgetMapping.get(it)})
            val unmatchedWidgets2_2 = ArrayList(unmatchedAVMs2.map { expectedAbstractState.EWTGWidgetMapping.get(it) })
            unmatchedWidgets1_2.removeIf { it == null }
            unmatchedWidgets2_2.removeIf { it == null }
            val condition1_2 = if (unmatchedWidgets1_2.isNotEmpty()) unmatchedWidgets1_2.all {
                updateWindowCreatedRuntimeWidgets.contains(it)
                        && !baseWindowCreatedRuntimeWidgets.contains(it)} else true
            val condition2_2 = if (unmatchedWidgets2_2.isNotEmpty()) unmatchedWidgets2_2.all {
                baseWindowCreatedRuntimeWidgets.contains(it)
                        && !updateWindowCreatedRuntimeWidgets.contains(it)} else true
            if (condition1_2 && condition2_2) {
                return true
            }
            return false
        }
    }

    private fun isEquivalentAttributeValuationMaps(avm1: AttributeValuationMap, avm2: AttributeValuationMap): Boolean {
        // avm1.localAttributes should have the same values of all localAttributes of avm2
        avm2.localAttributes.forEach {
            if (!avm1.localAttributes.containsKey(it.key))
                return false
            if (avm1.localAttributes[it.key] != it.value)
                return  false
        }

        return true
    }

    fun outputBackwardEquivalentResult(actionId: Int, guiState: State<*>, expectedAbstractState: AbstractState, observedAbstractState: AbstractState){

    }
     fun produceReport(context: ExplorationContext<*,*,*>) {
            val sb2 = StringBuilder()

            val keptBaseAbstractStates = ModelBackwardAdapter.instance.keptBaseAbstractStates
            val initialBaseAbstractStates = ModelBackwardAdapter.instance.initialBaseAbstractStates
            sb2.appendln("Initial base astract states;${initialBaseAbstractStates.size}")
            sb2.appendln("Kept base astract states;${keptBaseAbstractStates.size}")
            val newlyCreatedAbstractStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                it.modelVersion == ModelVersion.RUNNING
                        && it !is VirtualAbstractState
                        && it.guiStates.isNotEmpty()

            }
            val testedWindowsInBaseModel = initialBaseAbstractStates.map { it.window }.distinct()
            val newAbstractStatesOfUntestedWindowsInBaseModel = newlyCreatedAbstractStates.filter { !testedWindowsInBaseModel.contains(it.window) }
            sb2.appendln("Total new astract states;${newlyCreatedAbstractStates.size}")
            newlyCreatedAbstractStates.forEach {
                sb2.appendln(it.abstractStateId)
            }
            sb2.appendln("New abstract states of untested windows in base model;${newAbstractStatesOfUntestedWindowsInBaseModel.size}")
            val reusedAbstractState = ModelBackwardAdapter.instance.observedBaseAbstractState
            sb2.appendln("Observered abstract state;${reusedAbstractState.size}")
            reusedAbstractState.forEach {
                sb2.appendln(it.abstractStateId)
            }
            sb2.appendln("Backward equivalence identification;${ModelBackwardAdapter.instance.backwardEquivalentAbstractStateMapping.size}")
            ModelBackwardAdapter.instance.backwardEquivalentAbstractStateMapping.forEach { newState, baseStates ->
                sb2.appendln("---")
                sb2.appendln("New state;${newState.abstractStateId}")
                sb2.appendln("Base states count;${baseStates.size}")
                baseStates.forEach {
                    sb2.appendln(it.abstractStateId)
                }
            }
            val initialBaseAbstractTransitions = ModelBackwardAdapter.instance.initialBaseAbstractTransitions
            sb2.appendln("Initial base abstract transitions;${initialBaseAbstractTransitions.size}")
            val keptBaseAbstractTransitions = ModelBackwardAdapter.instance.keptBaseAbstractTransitions
            sb2.appendln("Kept base abstract transitions;${keptBaseAbstractTransitions.size}")
            val transferedAbstractTransitions = AbstractStateManager.INSTANCE.ABSTRACT_STATES
                    .filter { it.modelVersion == ModelVersion.RUNNING }
                    .flatMap { it.abstractTransitions }
                    .filter { it.isExplicit() && it.modelVersion == ModelVersion.BASE }
            sb2.appendln("Transfered abstract transitions;${transferedAbstractTransitions.size}")
            val newAbstractTransitions =  AbstractStateManager.INSTANCE.ABSTRACT_STATES
                    .flatMap { it.abstractTransitions }
                    .filter { it.isExplicit() && it.modelVersion == ModelVersion.RUNNING }
            sb2.appendln("Total new abstract transitions;${newAbstractTransitions.size}")
            val newATsBaseToBaseAS = newAbstractTransitions.filter {
                (it.source.modelVersion == ModelVersion.BASE
                        || backwardEquivalentAbstractStateMapping.containsKey(it.source))
                        && (it.dest.modelVersion == ModelVersion.BASE
                        || backwardEquivalentAbstractStateMapping.contains(it.dest))}
            val newATsBaseToNewAs = newAbstractTransitions.filter {
                (it.source.modelVersion == ModelVersion.BASE
                        || backwardEquivalentAbstractStateMapping.containsKey(it.source))
                        && (it.dest.modelVersion == ModelVersion.RUNNING
                        && !backwardEquivalentAbstractStateMapping.contains(it.dest))}
            val newATsNewToBaseAS = newAbstractTransitions.filter {
                (it.source.modelVersion == ModelVersion.RUNNING
                        && !backwardEquivalentAbstractStateMapping.containsKey(it.source))
                        && (it.dest.modelVersion == ModelVersion.BASE
                        || backwardEquivalentAbstractStateMapping.contains(it.dest))}
            val newATsNewToNewAs = newAbstractTransitions.filter {
                (it.source.modelVersion == ModelVersion.RUNNING
                        && !backwardEquivalentAbstractStateMapping.containsKey(it.source))
                        && (it.dest.modelVersion == ModelVersion.RUNNING
                        && !backwardEquivalentAbstractStateMapping.contains(it.dest))}
            sb2.appendln("Base-Base abstract transitions;${newATsBaseToBaseAS.size}")
            sb2.appendln("Base-New abstract transitions;${newATsBaseToNewAs.size}")
            sb2.appendln("New-Base abstract transitions;${newATsNewToBaseAS.size}")
            sb2.appendln("New-New abstract transitions;${newATsNewToNewAs.size}")
            val observedAbstractTransitions = observedBasedAbstractTransitions

            sb2.appendln("Observed abstract transitions count;${observedAbstractTransitions.size}")
            observedAbstractTransitions.forEach {
                sb2.appendln("${it.source.abstractStateId};${it.dest.abstractStateId};" +
                        "${it.abstractAction.actionType};${it.abstractAction.attributeValuationMap?.avmId};${it.data};" /*+
                    "${it.prevWindow}"*/)
            }
            val correctAbstractTransitions = ModelBackwardAdapter.instance.backwardEquivalentAbstractTransitionMapping
            sb2.appendln("Backward Equivalent abstract transitions count;${correctAbstractTransitions.size}")
            /*correctAbstractTransitions.forEach {
                sb2.appendln("${it.key.source.abstractStateId};${it.dest.abstractStateId};" +
                        "${it.abstractAction.actionType};${it.abstractAction.attributeValuationMap?.avmId};${it.data};" *//*+
                    "${it.prevWindow}"*//*)
            }*/
            val incorrectAbstractTransitions = ModelBackwardAdapter.instance.incorrectTransitions
            sb2.appendln("Incorrect abstract transitions count;${incorrectAbstractTransitions.size}")
            incorrectAbstractTransitions.forEach {
                sb2.appendln("${it.source.abstractStateId};${it.dest.abstractStateId};" +
                        "${it.abstractAction.actionType};${it.abstractAction.attributeValuationMap?.avmId};${it.data};"/* +
                    "${it.prevWindow?.windowId}"*/)
            }
            val modelbackwardReport = context.model.config.baseDir.resolve("backwardEquivalenceReport.txt")
            ATUAMF.log.info("Prepare writing backward equivalence report file: " +
                    "\n- File name: ${modelbackwardReport.fileName}" +
                    "\n- Absolute path: ${modelbackwardReport.toAbsolutePath().fileName}")

            Files.write(modelbackwardReport, sb2.lines())
            ATUAMF.log.info("Finished writing report in ${modelbackwardReport.fileName}")
    }
    companion object {
        val instance: ModelBackwardAdapter by lazy {
            ModelBackwardAdapter()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(ModelBackwardAdapter::class.java) }
    }
}