// ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
// Copyright (C) 2019 - 2021 University of Luxembourg
//
// This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.droidmate.exploration.modelFeatures.atua.dstg

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.AbstractionFunction2
import org.droidmate.exploration.modelFeatures.atua.dstg.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.ewtg.*
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Activity
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.ContextMenu
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.DialogType
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.FakeWindow
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.calm.ModelBackwardAdapter
import org.droidmate.exploration.modelFeatures.calm.ewtgdiff.EWTGDiff
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput.TextInput
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Exception
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class AbstractStateManager() {
    val ABSTRACT_STATES: ArrayList<AbstractState> = ArrayList()
    val launchStates: HashMap<LAUNCH_STATE, State<*>> = HashMap()
    lateinit var appResetState: AbstractState
    lateinit var atuaMF: ATUAMF
    lateinit var appName: String
    val attrValSetsFrequency = HashMap<Window, HashMap<AttributeValuationMap, Int>>()
    fun init(regressionTestingMF: ATUAMF, appPackageName: String) {
        this.atuaMF = regressionTestingMF
        this.appName = appPackageName

        //create initial abstract state (after App reset)
        appResetState = LauncherAbstractState()

        regressionTestingMF.dstg = DSTG()


    }

    fun initVirtualAbstractStates() {
        WindowManager.instance.updatedModelWindows.filter { it !is FakeWindow }.forEach {
            if (!ABSTRACT_STATES.any {
                        it is VirtualAbstractState
                                && it.window == it
                    }) {
                val virtualAbstractState = VirtualAbstractState(it.classType, it, it is Launcher)
                ABSTRACT_STATES.add(virtualAbstractState)
            }
            // regressionTestingMF.abstractStateVisitCount[virtualAbstractState] = 0
        }
    }

    fun initAbstractInteractionsForVirtualAbstractStates() {
        ABSTRACT_STATES.filter { it is VirtualAbstractState }.forEach {
            initAbstractInteractions(it, null)
        }
    }

    fun createVirtualAbstractState(window: Window) {
        val virtualAbstractState = VirtualAbstractState(window.classType, window, false)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState, null)
        updateLaunchAndResetAbstractTransitions(virtualAbstractState)
    }

    val backwardEquivalences = HashMap<AbstractState, AbstractState>()
    fun verifyBackwardEquivalent(observedState: AbstractState, expectedState: AbstractState) {
        val matchedAVMs = ArrayList<AttributeValuationMap>()
        for (attributeValuationMap1 in observedState.attributeValuationMaps) {
            for (attributeValuationMap2 in expectedState.attributeValuationMaps) {
                if (attributeValuationMap1.hashCode == attributeValuationMap2.hashCode) {
                    matchedAVMs.add(attributeValuationMap1)
                }
            }
        }
        val addedAVMs = ArrayList<AttributeValuationMap>()
        val unmatchedAVMs = ArrayList<AttributeValuationMap>()
        for (mutableEntry in observedState.EWTGWidgetMapping) {
            val avm = mutableEntry.key
            val ewtgWidget = mutableEntry.value
            if (matchedAVMs.contains(avm)) {
                continue
            }
            if (EWTGDiff.instance.getWidgetAdditions().contains(ewtgWidget)) {
                addedAVMs.add(avm)
            } else {
                unmatchedAVMs.add(avm)
            }
        }
        if (unmatchedAVMs.isEmpty()) {
            backwardEquivalences.put(observedState, expectedState)
        }
    }

    fun getOrCreateNewAbstractState(guiState: State<*>,
                                    i_activity: String,
                                    rotation: Rotation,
                                    window: Window?,
                                    forcedCreateNew: Boolean = false): AbstractState {
        if (!forcedCreateNew) {
            val exisitingAbstractState = getAbstractState(guiState)
            if (exisitingAbstractState != null) {
                return exisitingAbstractState
            }
        }
        var abstractState: AbstractState? = null
        var activity = i_activity
        /*var internetStatus = when (internet) {
            true -> InternetStatus.Enable
            false -> InternetStatus.Disable
        }*/
        if (guiState.isHomeScreen) {
            var homeState = ABSTRACT_STATES.find { it.isHomeScreen }
            if (homeState != null) {
                abstractState = homeState
                if (!homeState.guiStates.contains(guiState)) {
                    mapGuiStateToAbstractState(homeState, guiState)
                }
            } else {
                abstractState = VirtualAbstractState(activity = i_activity,
                        staticNode = Launcher.instance!!,
                        isHomeScreen = true)
                mapGuiStateToAbstractState(abstractState, guiState)
                ABSTRACT_STATES.add(abstractState)
            }
        } /*else if (activity.isBlank() || guiState.isRequestRuntimePermissionDialogBox) {
            var outOfAppState = ABSTRACT_STATES.find { it.isOutOfApplication && it.activity == activity }
            if (outOfAppState != null) {
                abstractState = outOfAppState
                if (!outOfAppState.guiStates.contains(guiState)) {
                    outOfAppState.guiStates.add(guiState)
                }
            } else {
                outOfAppState = AbstractState(activity = activity,
                        isOutOfApplication = true,
                        window = WTGOutScopeNode.getOrCreateNode(activity),
                        rotation = rotation,
                        internet = internetStatus)
                if (outOfAppState.window.activityClass.isBlank()) {
                    outOfAppState.window.activityClass = activity
                }
                outOfAppState.guiStates.add(guiState)
                ABSTRACT_STATES.add(outOfAppState)
                abstractState = outOfAppState
            }
        }*/ else if (guiState.isAppHasStoppedDialogBox) {
            var stopState = ABSTRACT_STATES.find {
                it.isAppHasStoppedDialogBox && it.rotation == rotation
            }
            if (stopState != null) {
                abstractState = stopState
                if (!stopState.guiStates.contains(guiState)) {
                    mapGuiStateToAbstractState(abstractState, guiState)
                }
            } else {
                stopState = AbstractState(activity = activity,
                        isAppHasStoppedDialogBox = true,
                        window = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity, false),
                        avmCardinalities = HashMap(),
                        rotation = rotation)
                mapGuiStateToAbstractState(stopState, guiState)
                ABSTRACT_STATES.add(stopState)
                abstractState = stopState
            }
        } else {
            //log.info("Activity: $activity")
            var time1: Long = 0
            var time2: Long = 0
            var time3: Long = 0
            var time4: Long = 0
            measureNanoTime {
                val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
                val isOpeningKeyboard = guiState.widgets.any { it.isKeyboard }
                val guiWidget_ewtgWidgets = HashMap<Widget, EWTGWidget>()
                val matchedWindow = if (window == null) {
                    val similarAbstractStates = ABSTRACT_STATES.filter { it.guiStates.any { it.uid == guiState.uid } }
                    if (similarAbstractStates.isEmpty() || similarAbstractStates.groupBy { it.window }.size > 1) {
                        //log.info("Matching window")
                        matchWindow(guiState, activity, rotation, guiWidget_ewtgWidgets)
                    } else {
                        similarAbstractStates.first().window
                    }
                } else {
                    window
                }
                val guiTreeRectangle = Helper.computeGuiTreeDimension(guiState)
                var isOptionsMenu = if (!Helper.isDialog(rotation, guiTreeRectangle, guiState, atuaMF))
                    Helper.isOptionsMenuLayout(guiState)
                else
                    false
                /*val isOptionsMenu2 = Helper.isOptionsMenuLayout(guiState)
                if (isOptionsMenu != isOptionsMenu2) {
                    log.debug("Check")
                }*/
                Helper.matchingGUIWidgetWithEWTGWidgets(guiWidget_ewtgWidgets, guiState, matchedWindow, isOptionsMenu, appName)
                var widget_AvmHashMap = HashMap<Widget, AttributeValuationMap>()
                //log.info("State reducing.")
                measureNanoTime {
                    widget_AvmHashMap = StateReducer.reduce(guiState, matchedWindow, rotation,guiTreeRectangle, isOptionsMenu, atuaMF)
                }.let {
                    time1 = it
                }
                val avmCardinalitis = HashMap<AttributeValuationMap, Cardinality>()
                widget_AvmHashMap.values.groupBy { it }.forEach { t, u ->
                    if (u.size==1)
                        avmCardinalitis.put(t,Cardinality.ONE)
                    else
                        avmCardinalitis.put(t,Cardinality.MANY)
                }
                TextInput.saveSpecificTextInputData(guiState)
                var derivedAVMs = widget_AvmHashMap.map { it.value }.distinct()
                // log.info("Find Abstract states.")
                val matchingTestState = findAbstractState(ABSTRACT_STATES, derivedAVMs, avmCardinalitis, matchedWindow, rotation, isOpeningKeyboard)
                if (matchingTestState != null) {
                    if (matchingTestState.guiStates.isEmpty()) {
                        // This is because of the rebuild model process
                        matchingTestState.countAVMFrequency()
                    }
                    mapGuiStateToAbstractState(matchingTestState, guiState)
                    return matchingTestState
                }
                /*     var staticMapping = Pair<Window, HashMap<AttributeValuationMap, EWTGWidget>>(first = Launcher.instance!!, second = HashMap())
                     measureNanoTime {
                         staticMapping = matchWindowAndWidgets(widget_AvmHashMap, guiState, activity, rotation,window)
                     }.let {
                         time3 = it
                     }*/
                //log.info("Matching AVMs with EWTG Widgets")
                var avm_ewtgWidgetsHashMap = matchingAvmWithEWTGWidgets(guiState, widget_AvmHashMap, guiWidget_ewtgWidgets)
                var refinementIncrease = false
                while (avm_ewtgWidgetsHashMap.any { it.value.size > 1 } && !forcedCreateNew) {
                    var tempRefinementIncrease = false
                    val ambigousAVMs = avm_ewtgWidgetsHashMap.filter { it.value.size > 1 }.keys
                    ambigousAVMs.forEach { avm ->
                        val relatedGUIWidgets = widget_AvmHashMap.filter { it.value == avm }.keys
                        val abstractionFunction = AbstractionFunction2.INSTANCE
                        relatedGUIWidgets.forEach { guiWidget ->
                            if (abstractionFunction.increaseReduceLevel(guiWidget, guiState, guiWidget_ewtgWidgets.get(guiWidget)!!, matchedWindow.classType, rotation, atuaMF)) {
                                refinementIncrease = true
                                tempRefinementIncrease = true
                            }
                        }
                    }
                    if (tempRefinementIncrease == false)
                        break
                    widget_AvmHashMap = StateReducer.reduce(guiState, matchedWindow, rotation,guiTreeRectangle,isOptionsMenu, atuaMF)
                    avm_ewtgWidgetsHashMap = matchingAvmWithEWTGWidgets(guiState, widget_AvmHashMap, guiWidget_ewtgWidgets)
                }
                avmCardinalitis.clear()
                widget_AvmHashMap.values.groupBy { it }.forEach { t, u ->
                    if (u.size==1)
                        avmCardinalitis.put(t,Cardinality.ONE)
                    else
                        avmCardinalitis.put(t,Cardinality.MANY)
                }
                val isOutOfApp = if (matchedWindow is OutOfApp)
                    true
                else
                    WindowManager.instance.updatedModelWindows.find { it.classType == activity } is OutOfApp
                val avm_ewtgWidgets = HashMap(avm_ewtgWidgetsHashMap.entries.filter { it.value.isNotEmpty() }.associate { it.key to it.value.first() })
                measureNanoTime {
                    abstractState = AbstractState(activity = activity,
                            attributeValuationMaps = ArrayList(derivedAVMs),
                            avmCardinalities = avmCardinalitis,
                            isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                            isOpeningKeyboard = isOpeningKeyboard,
                            EWTGWidgetMapping = avm_ewtgWidgets,
                            window = matchedWindow,
                            rotation = rotation,
                            isOutOfApplication = isOutOfApp)
                    if (abstractState!!.window is Dialog || abstractState!!.window is OptionsMenu || abstractState!!.window is ContextMenu) {
                        abstractState!!.hasOptionsMenu = false
                    }
                    if (isOptionsMenu) {
                        abstractState!!.isOpeningMenus = true
                    }
                    ABSTRACT_STATES.add(abstractState!!)
                    mapGuiStateToAbstractState(abstractState!!, guiState)
                    initAbstractInteractions(abstractState!!, guiState)
                    /*                val ambigousWidgetGroup = staticMapping.second.filter {
                                                //In certain cases, static analysis distinguishes same resourceId widgets based on unknown criteria.
                                                !havingSameResourceId(it.value)
                                    }
                                    if (ambigousWidgetGroup.isEmpty() || ambigousWidgetGroup.isNotEmpty()) {



                                    }*/
                }.let {
                    time4 = it
                }
                if (refinementIncrease) {
                    rebuildModel(matchedWindow)
                }
            }.let {
                if (it > 10e8) {
                    log.debug("AbstractState creation took: ${it / 10e6.toDouble()} milliseconds, in which: ")
                    log.debug("State reducing took: ${time1 / 10e6.toDouble()} milliseconds,")
                    log.debug("Finding matching abstract state took: ${time2 / 10e6.toDouble()} milliseconds,")
                    log.debug("Get matching static widgets reducing took: ${time3 / 10e6.toDouble()} milliseconds.")
                    log.debug("Init Abstract Interactions took: ${time4 / 10e6.toDouble()} milliseconds.")
                }
            }


        }
        return abstractState!!
    }

    private fun matchingAvmWithEWTGWidgets(guiState: State<*>, guiWidget_AVMs: HashMap<Widget, AttributeValuationMap>, guiWidget_ewtgWidgets: HashMap<Widget, EWTGWidget>): HashMap<AttributeValuationMap, ArrayList<EWTGWidget>> {
        val avm_ewtgWidgets = HashMap<AttributeValuationMap, ArrayList<EWTGWidget>>()
//                guiWidget_AVMs.entries.associate { it.value to it.key }
        guiWidget_AVMs.forEach { guiWidget, avm ->
            avm_ewtgWidgets.putIfAbsent(avm, ArrayList())
            val ewtgWidget = guiWidget_ewtgWidgets.get(guiWidget)
            if (ewtgWidget != null) {
                if (!avm_ewtgWidgets.get(avm)!!.contains(ewtgWidget)) {
                    avm_ewtgWidgets.get(avm)!!.add(ewtgWidget)
                }
            }
        }
        /*avm_guiWidgets.forEach {
            val ewtgWidget = guiWidget_ewtgWidgets.get(it.value)
            if (ewtgWidget!=null)
                result.put(it.key,ewtgWidget)
        }*/
        return avm_ewtgWidgets
    }

    private fun mapGuiStateToAbstractState(matchingTestState: AbstractState, guiState: State<*>) {
        if (!matchingTestState.guiStates.contains(guiState))
            matchingTestState.guiStates.add(guiState)
        guiState_AbstractState_Map.put(guiState.stateId, matchingTestState)
    }

    enum class LAUNCH_STATE {
        NONE,
        NORMAL_LAUNCH,
        RESET_LAUNCH
    }

    private fun findAbstractState(abstractStateList: List<AbstractState>,
                                  guiReducedAttributeValuationMap: List<AttributeValuationMap>,
                                  avmCardinalites: Map<AttributeValuationMap,Cardinality>,
                                  window: Window,
                                  rotation: Rotation,
                                  isOpeningKeyboard: Boolean): AbstractState? {
        val predictedAbstractStateHashcode = AbstractState.computeAbstractStateHashCode(guiReducedAttributeValuationMap, avmCardinalites, window, rotation)
        var result: AbstractState? = null
        for (abstractState in abstractStateList.filter {
            it !is VirtualAbstractState
                    && it.window == window
                    && it.rotation == rotation
                    && it.isOpeningKeyboard == isOpeningKeyboard
        }) {
            if (abstractState.hashCode == predictedAbstractStateHashcode) {
                return abstractState
            }
            val matchedAVMs = HashMap<AttributeValuationMap, AttributeValuationMap>()

        }
        return result
    }

    private fun havingSameResourceId(eWTGWidget: EWTGWidget): Boolean {
        var resourceId: String = eWTGWidget.resourceIdName
        if (!eWTGWidget.resourceIdName.equals(resourceId)) {
            return false
        }
        return true
    }

    fun initAbstractInteractions(abstractState: AbstractState, guiState: State<*>? = null) {
        abstractState.initAction()

        val inputs = abstractState.window.inputs
        inputs.forEach { input ->
            if (input.widget != null)
                initWidgetInputForAbstractState(abstractState, input)
            else
                initWindowInputForAbstractState(abstractState, input)
        }
        createAbstractTransitionsFromWTG(abstractState)

        createAbstractTransitionsForImplicitIntents(abstractState)
        //create implicit widget interactions from VirtualAbstractState
        if (abstractState is VirtualAbstractState) {
            return
        }
        val virtualAbstractStates = ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == abstractState.window }
        if (virtualAbstractStates.isEmpty()) {
            return
        }
        val virtualAbstractState = virtualAbstractStates.first()

        createAbstractActionsFromVirtualAbstractState(virtualAbstractState, abstractState, guiState)

        createAbstractTransitionsFromVirtualAbstractState(virtualAbstractState, abstractState)
        // updateLaunchAndResetAbstractTransitions(abstractState)
    }

    private fun createAbstractTransitionsFromVirtualAbstractState(virtualAbstractState: AbstractState, abstractState: AbstractState) {
        val virtualTransitions = virtualAbstractState.abstractTransitions.filter {
            //we will not process any self edge
            it.dest != virtualAbstractState
        }

        for (implicitTransition in virtualTransitions) {
            if (implicitTransition.abstractAction.actionType == AbstractActionType.PRESS_BACK
                    || implicitTransition.abstractAction.isLaunchOrReset()) {
                continue
            }
            var ignoreTransition = false
            for (changeResult in implicitTransition.changeEffects) {
                if (changeResult.affectedElementType == AffectElementType.Rotation) {
                    if (changeResult.changed) {
                        if (implicitTransition.dest.rotation == abstractState.rotation) {
                            ignoreTransition = true
                            break
                        }
                    } else {
                        if (implicitTransition.dest.rotation != abstractState.rotation) {
                            ignoreTransition = true
                            break
                        }
                    }
                }
            }

            if (ignoreTransition)
                continue

            val userInputs = implicitTransition.userInputs
            // initAbstractActionCount
            val virtualAbstractAction = implicitTransition.abstractAction
            val existingAction = abstractState.getAvailableActions().find {
                it == implicitTransition.abstractAction
            }

            if (existingAction != null) {
                val existingAbstractTransition = AbstractTransition.findExistingAbstractTransitions(
                        abstractState.abstractTransitions.toList(),
                        existingAction,
                        abstractState,
                        implicitTransition.dest,
                        true
                )
                if (existingAbstractTransition == null) {
                    val abstractInteraction = AbstractTransition(
                            abstractAction = existingAction,
                            isImplicit = false,
                            /*prevWindow = transition.prevWindow,*/
                            data = implicitTransition.data,
                            source = abstractState,
                            dest = implicitTransition.dest)
                    abstractInteraction.guaranteedAVMs.clear()
                    abstractInteraction.guaranteedAVMs.addAll(implicitTransition.guaranteedAVMs)
                    abstractInteraction.userInputs.addAll(userInputs)
                    abstractInteraction.dependentAbstractStates.addAll(implicitTransition.dependentAbstractStates)
                    abstractInteraction.guardEnabled = implicitTransition.guardEnabled
                    val newEdge = atuaMF.dstg.add(abstractState, implicitTransition.dest, abstractInteraction)
                    // add edge condition
                } else {
                    if (existingAbstractTransition.data != implicitTransition.data)
                        existingAbstractTransition.data = implicitTransition.data
                    implicitTransition.dependentAbstractStates.forEach {
                        if (!existingAbstractTransition.dependentAbstractStates.contains(it))
                            existingAbstractTransition.dependentAbstractStates.add(it)
                    }
                }
            }
        }
    }

    private fun createAbstractActionsFromVirtualAbstractState(virtualAbstractState: AbstractState, abstractState: AbstractState, guiState: State<*>?) {
        virtualAbstractState.getAvailableActions().forEach { virtualAbstractAction ->
            val isTarget = virtualAbstractState.targetActions.contains(virtualAbstractAction)
            var existingAction = abstractState.getAvailableActions().find {
                it == virtualAbstractAction
            }
            if (existingAction == null) {
                if (virtualAbstractAction.attributeValuationMap != null) {
                    val avm = abstractState.attributeValuationMaps.find { it.equals(virtualAbstractAction.attributeValuationMap) }
                    if (avm != null) {
                        existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                attributeValuationMap = avm,
                                extra = virtualAbstractAction.extra)
                    } else if (guiState != null) {
                        val guiWidget = virtualAbstractAction.attributeValuationMap.getGUIWidgets(guiState).firstOrNull()
                        // guiState.widgets.find { virtualAbstractAction.widgetGroup.isAbstractRepresentationOf(it,guiState) }
                        if (guiWidget != null) {
                            val newAttributePath = AttributeValuationMap.allWidgetAVMHashMap[abstractState.window]!!.get(guiWidget)!!
                            val newWidgetGroup = newAttributePath
                            existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                    attributeValuationMap = newWidgetGroup,
                                    extra = virtualAbstractAction.extra)
                        }
                    }
                } else {
                    existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                            attributeValuationMap = null,
                            extra = virtualAbstractAction.extra)
                }
            }
            if (existingAction != null) {
                if (isTarget) {
                    abstractState.targetActions.add(existingAction)
                }
                val oldActionCount = abstractState.getActionCount(existingAction)
                if (oldActionCount == 0) {
                    val actionCount = virtualAbstractState.getActionCount(virtualAbstractAction)
                    abstractState.setActionCount(existingAction, actionCount)
                }
            }
        }
    }

    private fun createAbstractTransitionsForImplicitIntents(abstractState: AbstractState) {
        WindowManager.instance.intentFilter.forEach { window, intentFilters ->
            val destVirtualAbstractState = ABSTRACT_STATES.find { it.window == window && it is VirtualAbstractState }
            if (destVirtualAbstractState != null) {
                intentFilters.forEach {
                    val abstractAction = AbstractAction(
                            actionType = AbstractActionType.SEND_INTENT,
                            extra = it
                    )
                    val abstractInteraction = AbstractTransition(
                            abstractAction = abstractAction,
                            isImplicit = true,
                            /*prevWindow = null,*/
                            data = null,
                            source = abstractState,
                            dest = destVirtualAbstractState,
                            fromWTG = true)
                    atuaMF.dstg.add(abstractState, destVirtualAbstractState, abstractInteraction)

                }
            }
        }
    }

    private fun createAbstractTransitionsFromWTG(abstractState: AbstractState) {
        //create implicit non-widget interactions
        val nonTrivialWindowTransitions = atuaMF.wtg.edges(abstractState.window)
                .filter { it.label.input.widget == null
                        && it.label.input.eventType != EventType.implicit_back_event
                        && it.source.data == it.destination?.data }
        //create implicit widget interactions from static Node
        val nonTrivialWidgetWindowsTransitions = atuaMF.wtg.edges(abstractState.window)
                .filter { it.label.input.widget != null }
                .filterNot { it.source.data == it.destination?.data }

    /*    nonTrivialWindowTransitions
                .forEach { windowTransition ->
                    val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label.input) }.map { it.key }
                    val destWindow = windowTransition.destination!!.data
                    val destAbstractState = ABSTRACT_STATES.find { it.window == destWindow && it is VirtualAbstractState }
                    if (destAbstractState != null) {
                        abstractActions.forEach { abstractAction ->
                            createAbstractTransitionFromWindowTransition(abstractState, abstractAction, windowTransition, destAbstractState)
                        }
                    }
                }*/

        nonTrivialWidgetWindowsTransitions
                .forEach { windowTransition ->
                    val destWindow = windowTransition.destination!!.data
                    val destAbstractState = ABSTRACT_STATES.find { it.window == destWindow && it is VirtualAbstractState }
                    if (destAbstractState != null) {
                        val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label.input) }.map { it.key }
                        abstractActions.forEach { abstractAction ->
                            createAbstractTransitionFromWindowTransition(abstractState, abstractAction, windowTransition, destAbstractState)
                        }
                    }
                }
    }

    private fun createAbstractTransitionFromWindowTransition(abstractState: AbstractState, abstractAction: AbstractAction, windowTransition: Edge<Window, WindowTransition>, destAbstractState: AbstractState) {
        val abstractEdge = atuaMF.dstg.edges(abstractState).find {
            it.label.abstractAction == abstractAction
                    && it.label.data == windowTransition.label.input.data
                    /*&& it.label.prevWindow == null*/
                    && it.label.dest == destAbstractState
                    && it.label.fromWTG == true
        }

        var abstractTransition: AbstractTransition
        if (abstractEdge != null) {
            abstractTransition = abstractEdge.label
            if (!abstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                abstractState.inputMappings.put(abstractTransition.abstractAction, hashSetOf(windowTransition.label.input))
            } else {
                if (!abstractState.inputMappings[abstractTransition.abstractAction]!!.contains(windowTransition.label.input)) {
                    abstractState.inputMappings[abstractTransition.abstractAction]!!.add(windowTransition.label.input)
                }
            }
        } else {
            abstractTransition = AbstractTransition(abstractAction = abstractAction,
                    isImplicit = true,
                    /*prevWindow = null,*/
                    data = windowTransition.label.input.data,
                    fromWTG = true,
                    source = abstractState,
                    dest = destAbstractState)
            windowTransition.label.input.modifiedMethods.forEach {
                abstractTransition.modifiedMethods.put(it.key, false)
            }
            abstractState.inputMappings.put(abstractTransition.abstractAction, hashSetOf(windowTransition.label.input))
            if (atuaMF.notFullyExercisedTargetInputs.contains(windowTransition.label.input)) {
                abstractState.targetActions.add(abstractTransition.abstractAction)
            }
            atuaMF.dstg.add(abstractState, destAbstractState, abstractTransition)
            abstractState.abstractTransitions.add(abstractTransition)
        }

    }

    private fun initWidgetInputForAbstractState(abstractState: AbstractState, input: Input) {
        if (input.widget == null)
            return
        val avms = abstractState.EWTGWidgetMapping.filter { m -> m.value == input.widget }.map { it.key }.toMutableList()
        if (avms.isEmpty() && abstractState is VirtualAbstractState) {
            //create a fake widgetGroup
            val staticWidget = input.widget
            val localAttributes = HashMap<AttributeType, String>()
            localAttributes.put(AttributeType.resourceId, staticWidget!!.resourceIdName)
            localAttributes.put(AttributeType.className, staticWidget!!.className)

            val attributePath = AttributePath(localAttributes = localAttributes, window = abstractState.window)
            val virtAVM = AttributeValuationMap(attributePath = attributePath, window = abstractState.window)
            //abstractState.addAttributeValuationSet(virtAVM)
            abstractState.EWTGWidgetMapping.put(virtAVM, input.widget!!)
            avms.add(virtAVM)
        }
        avms.forEach { avm ->
            var widgetAbstractAction = abstractState.getAvailableActions().find {
                it.actionType == input.convertToExplorationActionName()
                        && it.attributeValuationMap == avm
                        && it.extra == input.data
            }
            if (widgetAbstractAction == null) {
                if (abstractState is VirtualAbstractState) {
                    widgetAbstractAction = AbstractAction(
                            actionType = input.convertToExplorationActionName(),
                            attributeValuationMap = avm,
                            extra = input.data)
                } else {
                    val actionName = input.convertToExplorationActionName()
                    if (actionName == AbstractActionType.ITEM_CLICK || actionName == AbstractActionType.ITEM_LONGCLICK) {
                        widgetAbstractAction = AbstractAction(
                                actionType = input.convertToExplorationActionName(),
                                attributeValuationMap = avm,
                                extra = input.data)
                    }
                }
            }
            if (widgetAbstractAction != null) {
                if (!abstractState.getAvailableActions().contains(widgetAbstractAction)) {
                    abstractState.addAction(widgetAbstractAction)
                }
                if (!abstractState.inputMappings.containsKey(widgetAbstractAction)) {
                    abstractState.inputMappings.put(widgetAbstractAction, hashSetOf())
                }
                val inputMapping = abstractState.inputMappings.get(widgetAbstractAction)!!
                if (!inputMapping.contains(input)) {
                    inputMapping.add(input)
                }
            }
        }
    }

    private fun initWindowInputForAbstractState(abstractState: AbstractState, input: Input) {
        var abstractAction = abstractState.actionCount.keys.find {
            it.actionType == input.convertToExplorationActionName()
                    && it.extra == input.data
        }
        if (abstractAction == null) {
            if (abstractState.validateInput(input))
                abstractAction = AbstractAction(
                        actionType = input.convertToExplorationActionName(),
                        extra = input.data)
        }
        if (abstractAction == null)
            return
        abstractState.actionCount.put(abstractAction, 0)
        if (!abstractState.inputMappings.containsKey(abstractAction))
            abstractState.inputMappings.put(abstractAction, hashSetOf())
        val inputMapping = abstractState.inputMappings.get(abstractAction)!!
        if (!inputMapping.contains(input))
            inputMapping.add(input)
    }

    fun updateLaunchAndResetAbstractTransitions(abstractState: AbstractState) {
        val launchState = launchStates[LAUNCH_STATE.NORMAL_LAUNCH]
        if (launchState == null)
            return
        val resetState = launchStates[LAUNCH_STATE.RESET_LAUNCH]
        if (resetState == null)
            return
        val launchAbstractState = getAbstractState(launchState!!)
        val resetAbstractState = getAbstractState(resetState!!)
        if (launchAbstractState == null)
            throw Exception("Launch Abstract State is null")
        if (resetAbstractState == null)
            throw Exception("Reset Abstract State is null")
        updateOrCreateLaunchAppTransition(abstractState, launchAbstractState!!)
        updateOrCreateResetAppTransition(abstractState, resetAbstractState!!)
    }

    val guiState_AbstractState_Map = HashMap<ConcreteId, AbstractState>()
    fun getAbstractState(guiState: State<*>): AbstractState? {
        var abstractState = guiState_AbstractState_Map.get(guiState.stateId)
        if (abstractState == null) {
            abstractState = ABSTRACT_STATES.find { it.guiStates.contains(guiState) }
            if (abstractState != null) {
                return abstractState
            }
        }
        return abstractState
    }

    fun hasSameAVS(widgetGroups1: Set<AttributeValuationMap>, widgetGroups2: Set<AttributeValuationMap>): Boolean {
        if (widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun matchWindow(guiState: State<*>, activity: String, rotation: Rotation, guiWidget_ewtgWidgets: HashMap<Widget, EWTGWidget>): Window {
        //check if the previous state is homescreen
        var bestMatchedNode: Window? = null
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        val isMenuOpen = Helper.isOptionsMenuLayout(guiState)
        var activityNode: Window? = WindowManager.instance.updatedModelWindows.find { it.classType == activity }
        if (activityNode == null) {
            val newWTGNode =
                    if (guiState.widgets.any { it.packageName == atuaMF.packageName } && !guiState.isRequestRuntimePermissionDialogBox) {
                        Activity.getOrCreateNode(
                                nodeId = Activity.getNodeId(),
                                classType = activity,
                                runtimeCreated = true,
                                isBaseMode = false
                        )
                    } else {
                        OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity, false)
                    }
            createVirtualAbstractState(newWTGNode)
            activityNode = newWTGNode
        }
        val windowId = guiState.widgets.find { !it.isKeyboard }?.metaInfo?.find { it.contains("windowId") }?.split(" = ")?.get(1)
        if (windowId != null) {
            val sameWindowIdWindow = WindowManager.instance.updatedModelWindows.find { it.windowRuntimeIds.contains(windowId) }
            if (sameWindowIdWindow != null) {
                bestMatchedNode = sameWindowIdWindow
            }
        }
        if (bestMatchedNode == null) {
            val allPossibleNodes = ArrayList<Window>()
            //if the previous state is not homescreen
            //Get candidate nodes
            val isDialog = Helper.isDialog(rotation, guiTreeDimension, guiState, atuaMF)
            if (!isDialog) {
                bestMatchedNode = activityNode
            } else {
                if (activityNode !is OutOfApp) {
                    val allApplicationDialogClasses = WindowManager.instance.dialogClasses.filter {
                        it.key == DialogType.APPLICATION_DIALOG
                                || it.key == DialogType.DIALOG_FRAGMENT
                    }.map { it.value }.flatten()
                    val recentExecutedMethods = atuaMF!!.statementMF!!.recentExecutedMethods.map { atuaMF.statementMF!!.methodInstrumentationMap.get(it)!! }
                    val dialogMethods = recentExecutedMethods
                            .filter { m ->
                                allApplicationDialogClasses.any {
                                    m.contains("$it: void <init>()")
                                            || m.contains("$it: void onCreate(android.os.Bundle)")
                                            || m.contains("$it: android.app.Dialog onCreateDialog(android.os.Bundle)")
                                            || m.contains("$it: android.view.View onCreateView(android.view.LayoutInflater,android.view.ViewGroup,android.os.Bundle)")
                                }
                            }.associateWith { m -> allApplicationDialogClasses.find { m.contains(it) }!! }
                    val possibleDialogs = WindowManager.instance.allMeaningWindows
                            .filter { it is Dialog }
                            .filter {
                                dialogMethods.values.contains(it.classType)
                            }
                    if (possibleDialogs.isEmpty()) {
                        val allLibrayDialogs = WindowManager.instance.dialogClasses.filter { it.key == DialogType.LIBRARY_DIALOG }.map { it.value }.flatten()
                        val recentExecuteStatements = atuaMF!!.statementMF!!.recentExecutedStatements.map { atuaMF.statementMF!!.statementInstrumentationMap.get(it)!! }
                        val libraryDialogMethods = recentExecuteStatements
                                .filter { m -> allLibrayDialogs.any { m.contains(it) } }
                                .associateWith { m -> allLibrayDialogs.find { m.contains(it) }!! }
                        val possibleLibraryDialogs = WindowManager.instance.allMeaningWindows
                                .filter { it is Dialog }
                                .filter {
                                    libraryDialogMethods.values.contains(it.classType)
                                            && !recentExecuteStatements.any { s ->
                                        s.contains("${it.classType}: void dismiss()")
                                    }
                                }
                        if (possibleLibraryDialogs.isNotEmpty()) {
                            allPossibleNodes.addAll(possibleLibraryDialogs)
                        }
                        val candidateDialogs = WindowManager.instance.allMeaningWindows
                                .filter { it is Dialog && it.isRuntimeCreated && it.ownerActivitys.contains(activityNode) }
                        allPossibleNodes.addAll(candidateDialogs)
                    } else {
                        allPossibleNodes.addAll(possibleDialogs)
                    }
                } else {
                    val candidateDialogs = WindowManager.instance.allMeaningWindows
                            .filter { it is Dialog && it.isRuntimeCreated && it.ownerActivitys.contains(activityNode) }
                    allPossibleNodes.addAll(candidateDialogs)
                }
                /*val dialogNodes = ArrayList(atuaMF.wtg.getDialogs(activityNode))
                //val dialogNodes = WTGDialogNode.allNodes
                WindowManager.instance.updatedModelWindows.filter {it is Dialog && it.activityClass == activity }.forEach {
                    if (!dialogNodes.contains(it)) {
                        dialogNodes.add(it as Dialog)
                    }
                }
                allPossibleNodes.addAll(dialogNodes)*/

            }

            if (bestMatchedNode == null) {
                //Find the most similar node
                //only at least 1 widget matched is in the return result
                if (allPossibleNodes.size > 0 && !isDialog) {
                    bestMatchedNode = activityNode
                } else if (allPossibleNodes.size > 0) {
                    val matchWeights = Helper.calculateMatchScoreForEachNode2(guiState, allPossibleNodes, appName, isMenuOpen)
                    //sort and get the highest ranking of the match list as best matched node
                    val sortedWeight = matchWeights.map { it.value }.sortedDescending()
                    val largestWeight = sortedWeight.first()
                    if (largestWeight != Double.NEGATIVE_INFINITY) {
                        val topMatchingNodes = matchWeights.filter { it.value == largestWeight }
                        if (topMatchingNodes.size == 1) {
                            bestMatchedNode = topMatchingNodes.entries.first().key
                        } else {
                            val sortByPercentage = topMatchingNodes.toSortedMap(compareByDescending { matchWeights[it]!! / it.widgets.size.toDouble() })
                            bestMatchedNode = topMatchingNodes.filter { it.value == sortByPercentage[sortByPercentage.firstKey()]!! }.entries.firstOrNull()?.key
                            if (bestMatchedNode == null) {
                                bestMatchedNode = sortByPercentage.firstKey()
                            }
                        }
                    } else {
                        val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard,guiState.isRequestRuntimePermissionDialogBox)
                        bestMatchedNode = newWTGDialog
                    }
                } else {
                    if (!isDialog) {
                        bestMatchedNode = activityNode
                    } else {
                        val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard,guiState.isRequestRuntimePermissionDialogBox)
                        bestMatchedNode = newWTGDialog
                    }
                }
            }
        }
        if (isDimensionEmpty(bestMatchedNode!!, rotation, isOpeningKeyboard)) {
            setDimension(bestMatchedNode, rotation, guiTreeDimension, isOpeningKeyboard)
        }
        // bestMatchedNode.activityClass = activity
        if (bestMatchedNode is Dialog) {
            bestMatchedNode.ownerActivitys.add(activityNode)
        }
        if (windowId != null)
            bestMatchedNode!!.windowRuntimeIds.add(windowId)
        return bestMatchedNode
    }

    private fun createNewDialog(activity: String, activityNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean,isGrantedRuntimeDialog: Boolean): Dialog {
        val newId = Dialog.getNodeId()
        val newWTGDialog = Dialog.getOrCreateNode(newId,
                activity + newId, "", true, false,isGrantedRuntimeDialog)
        //autautMF.wtg.add(activityNode, newWTGDialog, FakeEvent(activityNode))
        setDimension(newWTGDialog, rotation, guiTreeDimension, isOpeningKeyboard)
        // regressionTestingMF.transitionGraph.copyNode(activityNode!!,newWTGDialog)
        createVirtualAbstractState(newWTGDialog)
        newWTGDialog.ownerActivitys.add(activityNode)
        return newWTGDialog
    }

    private fun setDimension(bestMatchedNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean) {
        if (!isOpeningKeyboard) {
            if (rotation == Rotation.PORTRAIT) {
                bestMatchedNode.portraitDimension = guiTreeDimension
                return
            }
            bestMatchedNode.landscapeDimension = guiTreeDimension
            return
        }
        if (rotation == Rotation.PORTRAIT) {
            bestMatchedNode.portraitKeyboardDimension = guiTreeDimension
            return
        }
        bestMatchedNode.landscapeKeyboardDimension = guiTreeDimension
        return
    }

    private fun isSameDimension(window: Window, guiTreeDimension: Rectangle, rotation: Rotation, isOpeningKeyboard: Boolean): Boolean {
        if (!isOpeningKeyboard) {
            if (rotation == Rotation.PORTRAIT) {
                return window.portraitDimension == guiTreeDimension
            }
            return window.landscapeDimension == guiTreeDimension
        }
        if (rotation == Rotation.PORTRAIT) {
            return window.portraitKeyboardDimension == guiTreeDimension
        }
        return window.landscapeKeyboardDimension == guiTreeDimension
    }

    private fun isDimensionEmpty(window: Window, rotation: Rotation, isOpeningKeyboard: Boolean): Boolean {
        if (!isOpeningKeyboard) {
            if (rotation == Rotation.PORTRAIT) {
                return window.portraitDimension.isEmpty()
            }
            return window.landscapeDimension.isEmpty()
        }
        if (rotation == Rotation.PORTRAIT) {
            return window.portraitKeyboardDimension.isEmpty()
        }
        return window.landscapeKeyboardDimension.isEmpty()
    }

    val REFINEMENT_MAX = 25

    val guardedTransitions = ArrayList<Pair<Window,Input>>()
    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>, abstractTransition: AbstractTransition): Int {
        val abstractionFunction = AbstractionFunction2.INSTANCE
        val actionWidget = guiInteraction.targetWidget
        if (actionWidget == null || abstractTransition.abstractAction.actionType == AbstractActionType.RANDOM_KEYBOARD) {
            return 0
        }
        AbstractionFunction2.backup(atuaMF)

        var refinementGrainCount = 0
        val originalActionAbstractState = getAbstractState(actionGUIState)!!
        //val attributeValuationSet = originalActionAbstractState.getAttributeValuationSet(guiInteraction.targetWidget!!,actionGUIState,atuaMF = atuaMF)!!
        val guiTreeRectangle = Helper.computeGuiTreeDimension(actionGUIState)
        var isOptionsMenu = if (!Helper.isDialog(originalActionAbstractState.rotation, guiTreeRectangle, actionGUIState, atuaMF))
            Helper.isOptionsMenuLayout(actionGUIState)
        else
            false
        if (AbstractionFunction2.INSTANCE.isAbandonedAbstractTransition(originalActionAbstractState.activity, abstractTransition))
            return 0
        AbstractionFunction2.backup(atuaMF)
        while (!validateModel(guiInteraction, actionGUIState)) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val actionEWTGWidget = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(actionAbstractState!!.window)!!.get(actionWidget)!!
            val abstractTransition = actionAbstractState.abstractTransitions.find {
                it.interactions.contains(guiInteraction)
                        && !it.isImplicit
            }
            if (abstractTransition == null)
                break
            log.info("Increase refinement")
            if (abstractionFunction.increaseReduceLevel(actionWidget, actionGUIState, actionEWTGWidget, actionAbstractState.window.classType, actionAbstractState.rotation, atuaMF, 2)) {
                refinementGrainCount += 1
//                rebuildPartly(guiInteraction, actionGUIState)
                rebuildModel(originalActionAbstractState.window)
            } else {
                var needRefine = true
                val input = actionAbstractState.inputMappings.get(abstractTransition!!.abstractAction)?.firstOrNull()
                if (input!=null) {
                    if (!guardedTransitions.contains(Pair(actionAbstractState.window, input))) {
                        guardedTransitions.add(Pair(actionAbstractState.window, input))
                    }
                }
                val similarExplicitTransitions = ArrayList<AbstractTransition>()
                val similarAbstractStates = ArrayList<AbstractState>()
                similarAbstractStates.addAll(getSimilarAbstractStates(actionAbstractState, abstractTransition).filter {
                    it.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap!!)
                })
                similarAbstractStates.add(actionAbstractState)
                getType1SimilarAbstractTransitions(abstractTransition.source, abstractTransition, abstractTransition.userInputs, similarExplicitTransitions)
                similarExplicitTransitions.add(abstractTransition)
                similarExplicitTransitions.forEach {
                    if (it.guardEnabled == false) {
                        it.guardEnabled = true
                        needRefine = false
                    }
                }
               /* if (abstractTransition!!.dependentAbstractStates.map { it.window }.contains(abstractTransition.dest.window)) {


                }*/
                if (needRefine) {
                    if (abstractionFunction.increaseReduceLevel(actionWidget, actionGUIState, actionEWTGWidget, actionAbstractState.window.classType, actionAbstractState.rotation, atuaMF)) {
                        refinementGrainCount += 1
                        rebuildModel(originalActionAbstractState.window)
                    } else {
                        if (refinementGrainCount>0) {
                            AbstractionFunction2.restore(atuaMF)
                            rebuildModel(originalActionAbstractState.window)
                        }
                        refinementGrainCount = 0
                        val actionAbstractState = getAbstractState(actionGUIState)!!
                        val abstractTransition = actionAbstractState.abstractTransitions.find {
                            it.interactions.contains(guiInteraction)
                                    && !it.isImplicit
                        }
                        if (abstractTransition != null) {
                            AbstractionFunction2.INSTANCE.abandonedAbstractTransitions.add(abstractTransition)
                            val similarExplicitTransitions = ArrayList<AbstractTransition>()
                            val similarAbstractStates = ArrayList<AbstractState>()
                            similarAbstractStates.addAll(getSimilarAbstractStates(actionAbstractState, abstractTransition).filter {
                                it.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap!!)
                            })
                            similarAbstractStates.add(actionAbstractState)
                            getType1SimilarAbstractTransitions(abstractTransition.source, abstractTransition,abstractTransition.userInputs, similarExplicitTransitions)
                            // try remove userInput
                            var solved = false
                            if (abstractTransition.abstractAction.actionType == AbstractActionType.TEXT_INSERT) {
                                abstractTransition.data = guiInteraction.data
                                solved = true
                            }
                            similarExplicitTransitions.forEach {
                                if (it.userInputs.intersect(abstractTransition.userInputs).isNotEmpty()) {
                                    it.userInputs.removeAll(abstractTransition.userInputs)
                                    solved = true
                                }
                            }
                            if (!solved) {
                                log.debug("Non-deterministic transitions not solved by abstract state refinement.")
                            }
                        } else {
                            log.debug("Interaction lost")
                        }
                        /*similarExpliciTransitions.forEach {
                        val inputGUIStates = it.interactions.map { interaction -> interaction.prevState }
                        it.inputGUIStates.addAll(inputGUIStates)
                    }*/
                        break
                    }
                }
            }
        }
      /*  if (refinementGrainCount == 0) {
            rebuildModel(originalActionAbstractState.window, true, actionGUIState, guiInteraction)
        }*/
        //get number of Abstract Interaction
        log.debug("Refinement grain increased count: $refinementGrainCount")
        return refinementGrainCount
    }

    private fun rebuildPartly(guiInteraction: Interaction<*>, actionGUIState: State<*>) {
        val actionAbstractState = getAbstractState(actionGUIState)!!
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val abstractEdge = atuaMF.dstg.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }!!
        abstractEdges.add(abstractEdge)
        val oldAbstractStatesByWindow = HashMap<Window, ArrayList<AbstractState>>()
        oldAbstractStatesByWindow.put(actionAbstractState.window, arrayListOf(actionAbstractState))
        recomputeAbstractStatesAndAbstractTransitions(oldAbstractStatesByWindow)
    }

    private fun validateModel2(guiInteractionSequence: LinkedList<Interaction<*>>): Boolean {

        return true
    }

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        if (guiInteraction.targetWidget != null && guiInteraction.targetWidget!!.isKeyboard)
            return true
        if (guiInteraction.targetWidget != null && Helper.hasParentWithType(guiInteraction.targetWidget!!, actionGUIState, "WebView"))
            return true
        val actionAbstractState = getAbstractState(actionGUIState)
        if (actionAbstractState == null)
            return true
        if (actionAbstractState.isRequestRuntimePermissionDialogBox)
            return true
        val abstractTransition = atuaMF.dstg.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }?.label
        if (abstractTransition == null)
            return true
        if (abstractTransition.abstractAction.attributeValuationMap == null)
            return true
        /*val abstractStates = if (guiInteraction.targetWidget == null) {
            ABSTRACT_STATES.filterNot{ it is VirtualAbstractState}. filter { it.window == actionAbstractState.window}
        } else {
            val widgetGroup = actionAbstractState.getWidgetGroup(guiInteraction.targetWidget!!, actionGUIState)
            ABSTRACT_STATES.filterNot { it is VirtualAbstractState }. filter { it.window == actionAbstractState.window
                    && it.widgets.contains(widgetGroup)}
        }*/
        val userInputs = abstractTransition.userInputs


        //val abstractStates = arrayListOf<AbstractState>(actionAbstractState)
        val similartAbstractTransitions = ArrayList<AbstractTransition>()
        similartAbstractTransitions.add(abstractTransition)
        getType1SimilarAbstractTransitions(actionAbstractState, abstractTransition, userInputs, similartAbstractTransitions)
        similartAbstractTransitions.removeIf {
            AbstractionFunction2.INSTANCE.isAbandonedAbstractTransition(actionAbstractState.activity, it)
        }
        val distinctAbstractInteractions2 = similartAbstractTransitions.distinctBy { it.dest}
        if (distinctAbstractInteractions2.size > 1) {
            return false
        }

        /*val abstractStates = arrayListOf(actionAbstractState)
        similartAbstractEdges.clear()
        abstractStates.addAll(getSimilarAbstractStates(actionAbstractState, abstractTransition).filter { it.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap!!) })
        similartAbstractEdges.add(abstractTransition)
        //validate going to the same window
        getType2SimilarAbstractTransition(abstractStates, abstractTransition, similartAbstractEdges)

        val distinctAbstractInteractions1 = similartAbstractEdges.groupBy { it.dest.window }
        if (distinctAbstractInteractions1.size > 1) {
            if (distinctAbstractInteractions1.any { it.key is Dialog && it.value.all { it.dest.isRequestRuntimePermissionDialogBox } }
                    && distinctAbstractInteractions1.size == 2) {
                val requestPermissionTransitions = distinctAbstractInteractions1.filter { it.value.all { it.dest.isRequestRuntimePermissionDialogBox } }.values.flatten()
                if (requestPermissionTransitions.size == 1) {
                    distinctAbstractInteractions1
                            .filter { it.value.all { !it.dest.isRequestRuntimePermissionDialogBox } }
                            .values.flatten().forEach {
                        it.requiringPermissionRequestTransition = requestPermissionTransitions.single()
                    }
                    return true
                }
            }
            return false
        }*/
        return true
    }

    private fun getType2SimilarAbstractTransition(sourceAbstractStates: List<AbstractState>, abstractTransition:  AbstractTransition, output: ArrayList<AbstractTransition>) {
        sourceAbstractStates.forEach {
            val similarEdges = it.abstractTransitions.filter {
                it != abstractTransition
                        && it.abstractAction == abstractTransition.abstractAction
                        /*&& it.label.prevWindow == abstractTransition.label.prevWindow*/
                        && it.requiringPermissionRequestTransition == abstractTransition.requiringPermissionRequestTransition
                        && it.isExplicit()
            }
            similarEdges.forEach {
                /* val similarEdgeCondition = autautMF.abstractTransitionGraph.edgeConditions[it]!!
                 if (similarEdgeCondition.equals(edgeCondition)) {
                     abstractEdges.add(it)
                 }*/
                output.add(it)
            }
        }
    }

    private fun getType1SimilarAbstractTransitions(sourceState: AbstractState, abstractTransition: AbstractTransition, userInputs: ArrayList<HashMap<UUID, String>>, output: ArrayList<AbstractTransition>) {
        val similarExplicitEdges = sourceState.abstractTransitions.filter {
            it != abstractTransition
                    && it.abstractAction == abstractTransition.abstractAction
                    && it.data == abstractTransition.data
                    /*&& it.label.prevWindow == abstractTransition.label.prevWindow*/
                    && it.requiringPermissionRequestTransition == abstractTransition.requiringPermissionRequestTransition
                    && it.isExplicit()
                    && (!it.guardEnabled || (abstractTransition.guardEnabled && it.dependentAbstractStates.intersect(abstractTransition.dependentAbstractStates).isNotEmpty()))
                    && (it.userInputs.isEmpty() || userInputs.isEmpty() || it.userInputs.intersect(userInputs).isNotEmpty())
            /*&& (it.label.inputGUIStates.intersect(abstractTransition.label.inputGUIStates).isNotEmpty()
                        || abstractTransition.label.inputGUIStates.isEmpty())*/
        }
        similarExplicitEdges.forEach {
            output.add(it)
        }
    }

    private fun getSimilarAbstractStates(abstractState: AbstractState, abstractTransition: AbstractTransition): List<AbstractState> {
        val similarAbstractStates = ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == abstractState.window
                    && it != abstractState
                    && it.isOpeningKeyboard == abstractState.isOpeningKeyboard
                    && it.rotation == abstractState.rotation
                    && it.isOpeningMenus == abstractState.isOpeningMenus
        }
        if (!abstractTransition.abstractAction.isWidgetAction()) {
            return similarAbstractStates
        } else
            return similarAbstractStates.filter { it.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap) }
    }

    fun rebuildModel(window: Window) {

        val affectedWindow = arrayListOf<Window>(window)
        //reset virtual abstract state
        val toRemoveVirtualAbstractState = ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == window }
        toRemoveVirtualAbstractState.forEach {
            atuaMF.dstg.removeVertex(it)
            it.attributeValuationMaps.clear()
            it.abstractTransitions.clear()
            //initAbstractInteractions(it)
        }
        val oldAbstractStatesByWindow = HashMap<Window, ArrayList<AbstractState>>()

        affectedWindow.forEach { window ->
            AbstractStateManager.INSTANCE.attrValSetsFrequency.get(window)?.clear()
            AttributeValuationMap.allWidgetAVMHashMap.get(window)?.clear()

            val oldAbstractStates = ABSTRACT_STATES.filter {
                it.window == window && it !is VirtualAbstractState
                        && it.guiStates.isNotEmpty()
            }

            oldAbstractStatesByWindow.put(window, ArrayList(oldAbstractStates))
        }
        recomputeAbstractStatesAndAbstractTransitions(oldAbstractStatesByWindow)

        //get all related abstract state

        updateLaunchAndResetTransition()

        PathFindingHelper.allAvailableTransitionPaths.forEach { t, u ->
            val transitionPaths = u.toList()
            transitionPaths.forEach { transitionPath ->
                if (!ABSTRACT_STATES.contains(transitionPath.root)) {
                    u.remove(transitionPath)
                } else if (transitionPath.path.values.map { it.dest }.any { !ABSTRACT_STATES.contains(it) }) {
                    u.remove(transitionPath)
                }
            }
        }

    }

    fun updateLaunchAndResetTransition() {
        val allAbstractStates = ABSTRACT_STATES.filter { it !is VirtualAbstractState }
        val launchState = launchStates[LAUNCH_STATE.NORMAL_LAUNCH]
        if (launchState == null)
            throw Exception("Launch state is null")
        val resetState = launchStates[LAUNCH_STATE.RESET_LAUNCH]
        if (resetState == null)
            throw Exception("Reset state is null")
        val launchAbstractState = getAbstractState(launchState!!)
        val resetAbstractState = getAbstractState(resetState!!)
        if (launchAbstractState == null)
            throw Exception("Launch Abstract State is null")
        if (resetAbstractState == null)
            throw Exception("Reset Abstract State is null")
        allAbstractStates.forEach { abstractState ->
            updateOrCreateLaunchAppTransition(abstractState, launchAbstractState!!)
            updateOrCreateResetAppTransition(abstractState, resetAbstractState!!)
        }
    }

    private fun recomputeAbstractStatesAndAbstractTransitions(oldAbstractStatesByWindow: Map<Window, ArrayList<AbstractState>>) {
        val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
        val recomputeGuistates = ArrayList<State<*>>()
        recomputeAbstractStates(oldAbstractStatesByWindow, recomputeGuistates, old_newAbstractStates)
        val missingGuistates = recomputeGuistates.filter { state ->
            ABSTRACT_STATES.all { !it.guiStates.contains(state) }
        }
        if (missingGuistates.isNotEmpty())
            throw Exception("GUI states are not fully recomputed")
        var computeInteractionsTime: Long = 0
        var computeGuiStateTime: Long = 0

        val processedGUIInteractions = ArrayList<Interaction<Widget>>()
        val newEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val inEdgeMap = HashMap<AbstractState, HashSet<Edge<AbstractState, AbstractTransition>>>()
        old_newAbstractStates.keys.forEach { abstractState ->
            inEdgeMap.put(abstractState, HashSet())
        }
        atuaMF.dstg.edges().forEach {
            if (inEdgeMap.containsKey(it.destination?.data)) {
                inEdgeMap[it.destination?.data]!!.add(it)
            }
        }

        //compute new abstract interactions
        val newAbstractTransitions = ArrayList<AbstractTransition>()
        measureTimeMillis {
            recomputeAbstractTransitions(old_newAbstractStates, inEdgeMap, newAbstractTransitions, processedGUIInteractions, newEdges)
        }.also {
            log.info("Recompute Abstract Transitions took $it ms")
        }
        removeObsoleteAbstractStatesAndAbstractTransitions(oldAbstractStatesByWindow)
        /*       newAbstractTransitions.distinct().forEach {abstractTransition->
                   if (abstractTransition.interactions.isNotEmpty()) {
                       abstractTransition.interactions.forEach { interaction ->
                           val resState = atuaMF.stateList.find { it.stateId ==  interaction.resState}
                           val transitionId = atuaMF.interactionsTracing.get(listOf(interaction))
                           addImplicitAbstractInteraction(resState,
                                   abstractTransition = abstractTransition,
                                   transitionId = transitionId)
                       }
                   } else {
                       addImplicitAbstractInteraction(currentState =  null,
                               abstractTransition = abstractTransition,
                               transitionId = null)
                   }
               }*/
    }

    private fun removeObsoleteAbstractStatesAndAbstractTransitions(oldAbstractStatesByWindow: Map<Window, ArrayList<AbstractState>>) {
        val oldAbstractStates = ArrayList(oldAbstractStatesByWindow.values.flatten())
        oldAbstractStatesByWindow.forEach {
            it.value.forEach {
                if (it.guiStates.isEmpty() && !it.loadedFromModel) {
                    ABSTRACT_STATES.remove(it)
                    oldAbstractStates.remove(it)
                    atuaMF.dstg.removeVertex(it)
                }
            }
        }
        ABSTRACT_STATES.forEach {
            it.abstractTransitions.removeIf {
                !ABSTRACT_STATES.contains(it.dest)
            }
        }
    }

    private fun recomputeAbstractTransitions(old_newAbstractStates: HashMap<AbstractState, ArrayList<AbstractState>>, inEdgeMap: HashMap<AbstractState, HashSet<Edge<AbstractState, AbstractTransition>>>, newAbstractTransitions: ArrayList<AbstractTransition>, processedGUIInteractions: ArrayList<Interaction<Widget>>, newEdges: ArrayList<Edge<AbstractState, AbstractTransition>>) {
        var guiInteractionCount = 0
        old_newAbstractStates.entries.forEach {
            val oldAbstractState = it.key
            val newAbstractStates = it.value
            // process out-edges

            val outAbstractEdges = atuaMF.dstg.edges(oldAbstractState).toMutableList()
            outAbstractEdges.filter { it.label.isImplicit }.forEach {
                atuaMF.dstg.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }
            val inAbstractEdges = inEdgeMap[oldAbstractState]!!
            inAbstractEdges.filter { it.label.isImplicit }.forEach {
                atuaMF.dstg.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }

            val explicitOutAbstractEdges = outAbstractEdges.filter { !it.label.isImplicit }

            explicitOutAbstractEdges.forEach { oldAbstractEdge ->

                if (oldAbstractEdge.label.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    atuaMF.dstg.remove(oldAbstractEdge)
                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                    // Remove the edge first
                    guiInteractionCount+=1
                    atuaMF.dstg.remove(oldAbstractEdge)
                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)

                    val newAbstractTransition = recomputeActionQueueAbstractTransition(oldAbstractEdge)
                    newAbstractTransitions.add(newAbstractTransition)

                } else {
                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val interactions = oldAbstractEdge.label.interactions.toList()
                    interactions.forEach { interaction ->
                        if (processedGUIInteractions.contains(interaction)) {
                            //log.debug("Processed interaction in refining model")
                        } else {
                            guiInteractionCount+=1
                            processedGUIInteractions.add(interaction)
                            val newEdge = deriveGUIInteraction(interaction, oldAbstractEdge, isTarget)

                            if (newEdge != null) {
                                newEdges.add(newEdge)
                                if (oldAbstractEdge.label != newEdge.label) {
                                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                    atuaMF.dstg.remove(oldAbstractEdge)
                                }
                                newAbstractTransitions.add(newEdge.label)
                            }
                        }
                    }
                }
            }

            // process in-edges

            val explicitInAbstractEdges = inAbstractEdges.filter { !it.label.isImplicit }

            explicitInAbstractEdges.forEach { oldAbstractEdge ->
                if (oldAbstractEdge.label.abstractAction.actionType == AbstractActionType.RESET_APP) {
                    atuaMF.dstg.remove(oldAbstractEdge)
                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                    // log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                    guiInteractionCount+=1
                    // Remove the edge first
                    atuaMF.dstg.remove(oldAbstractEdge)
                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                    val newAbstractTransition = recomputeActionQueueAbstractTransition(oldAbstractEdge)
                    newAbstractTransitions.add(newAbstractTransition)
                } else {
                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val interactions = oldAbstractEdge.label.interactions.toList()
                    interactions.forEach { interaction ->
                        if (processedGUIInteractions.contains(interaction)) {
                            // log.debug("Processed interaction in refining model")
                        } else {
                            guiInteractionCount+=1
                            processedGUIInteractions.add(interaction)
                            val newEdge = deriveGUIInteraction(interaction, oldAbstractEdge, isTarget)
                            if (newEdge != null) {
                                newAbstractTransitions.add(newEdge.label)
                                newEdges.add(newEdge)
                                if (oldAbstractEdge.label != newEdge.label) {
                                    oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                    atuaMF.dstg.remove(oldAbstractEdge)
                                }
                            }

                        }
                    }
                }
            }
        }
        log.info("Number of recomputed gui interactions: $guiInteractionCount")
    }

    private fun recomputeActionQueueAbstractTransition(oldAbstractEdge: Edge<AbstractState, AbstractTransition>): AbstractTransition {
        val interactionQueue = oldAbstractEdge.label.data as List<Interaction<*>>
        val interaction = interactionQueue.first()
        val tracing = atuaMF.interactionsTracingMap.get(interactionQueue)
        var sourceState: State<*>? = null
        var destState: State<*>? = null
        sourceState = atuaMF.stateList.find { it.stateId == interaction.prevState }!!
        destState = atuaMF.stateList.find { it.stateId == interaction.resState }!!

        var sourceAbstractState = getAbstractState(sourceState)

        if (sourceAbstractState == null)
            throw Exception("Cannot find new prevState's abstract state")

        val destinationAbstractState = getAbstractState(destState)
        if (destinationAbstractState == null) {
            throw Exception("Cannot find new resState's abstract state")
        }
        val modelVersion = oldAbstractEdge.label.modelVersion
        val newAbstractTransition = AbstractTransition(
                abstractAction = oldAbstractEdge.label.abstractAction,
                data = oldAbstractEdge.label.data,
                interactions = oldAbstractEdge.label.interactions,
                /*prevWindow = oldAbstractEdge.label.prevWindow,*/
                fromWTG = oldAbstractEdge.label.fromWTG,
                source = sourceAbstractState,
                dest = destinationAbstractState,
                isImplicit = false,
                modelVersion = modelVersion
        )
        newAbstractTransition.copyPotentialInfoFrom(oldAbstractEdge.label)
        sourceAbstractState.abstractTransitions.add(newAbstractTransition)
        atuaMF.dstg.add(sourceAbstractState, destinationAbstractState, newAbstractTransition)
        addImplicitAbstractInteraction(destState, newAbstractTransition, tracing)
        return newAbstractTransition
    }

    private fun deriveGUIInteraction(interaction: Interaction<*>, oldAbstractEdge: Edge<AbstractState, AbstractTransition>, isTarget: Boolean): Edge<AbstractState, AbstractTransition>? {
        var sourceState: State<*>? = null
        var destState: State<*>? = null
        sourceState = atuaMF.stateList.find { it.stateId == interaction.prevState }!!
        destState = atuaMF.stateList.find { it.stateId == interaction.resState }!!

        var sourceAbstractState = getAbstractState(sourceState)

        if (sourceAbstractState == null)
            throw Exception("Cannot find new prevState's abstract state")

        val destinationAbstractState = getAbstractState(destState)
        /* var destinationAbstractState = if (oldAbstractEdge.destination!!.data.window == staticNode) {
                                        stateId_AbstractStateMap[interaction.resState]
                                    } else {
                                        oldAbstractEdge.destination!!.data
                                    }*/
        if (destinationAbstractState == null) {
            throw Exception("Cannot find new resState's abstract state")
        }
        val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!, destState!!)
        return newEdge
    }

    private fun recomputeAbstractStates(oldAbstractStatesByWindow: Map<Window, ArrayList<AbstractState>>, recomputeGuistates: ArrayList<State<*>>, old_newAbstractStates: HashMap<AbstractState, ArrayList<AbstractState>>) {
        oldAbstractStatesByWindow.forEach { window, oldAbstractStates ->
            val processedGuiState = HashSet<State<*>>()
            val derivedWidgets = AttributeValuationMap.allWidgetAVMHashMap[window]!!
            val oldGuiStates = HashMap<AbstractState, List<State<*>>>()
            oldAbstractStates.forEach {
                oldGuiStates.put(it, ArrayList(it.guiStates))
                recomputeGuistates.addAll(it.guiStates)
                it.guiStates.forEach {
                    guiState_AbstractState_Map.remove(it.stateId)
                    it.widgets.forEach {
                        derivedWidgets.remove(it)
                    }
                }
                it.guiStates.clear()
            }
            var getGuiStateTime: Long = 0
            measureTimeMillis {
                //compute new AbstractStates for each old one
                oldAbstractStates.forEach { oldAbstractState ->
                    val newAbstractStates = ArrayList<AbstractState>()
                    oldGuiStates[oldAbstractState]!!.filterNot { processedGuiState.contains(it) }.forEach { guiState ->
                        processedGuiState.add(guiState)
                        /*var internet = when (oldAbstractState.internet) {
                            InternetStatus.Enable -> true
                            InternetStatus.Disable -> false
                            else -> true
                        }*/
                        val abstractState = getOrCreateNewAbstractState(guiState, oldAbstractState.activity, oldAbstractState.rotation, oldAbstractState.window, true)
                        //val abstractState = refineAbstractState(possibleAbstractStates, guiState, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                        atuaMF.abstractStateVisitCount.putIfAbsent(abstractState, 0)
                        if (!newAbstractStates.contains(abstractState)) {
                            newAbstractStates.add(abstractState)
                            atuaMF.abstractStateVisitCount[abstractState] = 1
                        } else {
                            atuaMF.abstractStateVisitCount[abstractState] = atuaMF.abstractStateVisitCount[abstractState]!! + 1
                        }
                        mapGuiStateToAbstractState(abstractState, guiState)
                        if (launchStates[LAUNCH_STATE.RESET_LAUNCH] == guiState) {
                            abstractState.isInitalState = true
                        }
                    }
                    old_newAbstractStates.put(oldAbstractState, newAbstractStates)
                }
                val allGuiStatesRebuilt = oldGuiStates.map { it.value }.flatten().all {
                    guiState_AbstractState_Map.containsKey(it.stateId)
                }
                if (!allGuiStatesRebuilt)
                    throw Exception()
                val toRemoveAbstractStates = oldAbstractStates.filter { old ->
                    old_newAbstractStates.values.find { it.contains(old) } == null
                }
                /*            toRemoveAbstractStates.forEach {
                                ABSTRACT_STATES.remove(it)
                                //it.abstractTransitions.removeIf { it.isImplicit}
                            }*/

            }.let {
                log.info("Recompute Abstract states took $it millis")
            }

        }
    }

    private fun updateOrCreateResetAppTransition(abstractState: AbstractState, resetAbstractState: AbstractState) {
        val resetInteractions = atuaMF.dstg.edges(abstractState).filter {
            it.label.abstractAction.actionType == AbstractActionType.RESET_APP
        }
        if (resetInteractions.isNotEmpty()) {
            resetInteractions.forEach {
                atuaMF.dstg.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }

        }
        val resetAction = AbstractAction(
                actionType = AbstractActionType.RESET_APP
        )
        val abstractInteraction = AbstractTransition(abstractAction = resetAction,
                isImplicit = true,
                /*prevWindow = null,*/
                source = abstractState,
                dest = resetAbstractState)
        atuaMF.dstg.add(abstractState, resetAbstractState, abstractInteraction)
        abstractState.abstractTransitions.add(abstractInteraction)
    }

    private fun updateOrCreateLaunchAppTransition(abstractState: AbstractState, launchAbstractState: AbstractState) {
        val launchInteractions = atuaMF.dstg.edges(abstractState).filter {
            it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP
                    && it.label.isImplicit
        }
        if (launchInteractions.isNotEmpty()) {
            launchInteractions.forEach {
                atuaMF.dstg.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }

        }
        val launchAction = AbstractAction(
                actionType = AbstractActionType.LAUNCH_APP
        )

        val isImplicit = true
        val abstractInteraction = AbstractTransition(abstractAction = launchAction,
                isImplicit = isImplicit,
                /*prevWindow = null,*/
                source = abstractState,
                dest = launchAbstractState)
        abstractState.abstractTransitions.add(abstractInteraction)
        atuaMF.dstg.add(abstractState, launchAbstractState, abstractInteraction)

    }

    private fun updateAbstractTransition(oldAbstractEdge: Edge<AbstractState, AbstractTransition>
                                         , isTarget: Boolean
                                         , sourceAbstractState: AbstractState
                                         , destinationAbstractState: AbstractState
                                         , interaction: Interaction<*>
                                         , sourceState: State<*>
                                         , destState: State<*>): Edge<AbstractState, AbstractTransition>? {
        //Extract text input widget data
        var newAbstractionTransition: AbstractTransition? = null
        var newEdge: Edge<AbstractState, AbstractTransition>? = null
        val condition = HashMap(Helper.extractInputFieldAndCheckableWidget(sourceState))
        val tracing = atuaMF.interactionsTracingMap.get(listOf(interaction))
        val prevWindowAbstractState: AbstractState?
        if (tracing == null || oldAbstractEdge.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP) {
            prevWindowAbstractState = null
        } else if (tracing.second - 1 <= 1) {
            prevWindowAbstractState = null
        } else {
            val traveredInteraction = atuaMF.interactionsTracingMap.entries.find { it.value == Pair(tracing.first, tracing.second - 1) }
            if (traveredInteraction == null)
                throw Exception()
            if (!atuaMF.interactionPrevWindowStateMapping.containsKey(traveredInteraction.key.last())) {
                prevWindowAbstractState = null
            } else {
                val prevWindowState = atuaMF.interactionPrevWindowStateMapping.get(traveredInteraction.key.last())!!
                prevWindowAbstractState = getAbstractState(prevWindowState)
            }
        }
        if (!oldAbstractEdge.label.abstractAction.isWidgetAction()) {
            //Reuse Abstract action
            val abstractAction = AbstractAction.getOrCreateAbstractAction(oldAbstractEdge.label.abstractAction.actionType,
                    interaction, sourceState, sourceAbstractState, null, atuaMF)
            if (isTarget) {
                sourceAbstractState.targetActions.add(abstractAction)
            }
            /*val interactionData = AbstractTransition.computeAbstractTransitionData(abstractAction.actionType,
                    interaction, sourceState, sourceAbstractState, atuaMF)*/
            val interactionData = oldAbstractEdge.label.data
            //check if the interaction was created
            val exisitingAbstractTransition = AbstractTransition.findExistingAbstractTransitions(
                    sourceAbstractState.abstractTransitions.toList(),
                    abstractAction,
                    sourceAbstractState,
                    destinationAbstractState,
                    false
            )
            if (exisitingAbstractTransition == null) {
                //Create explicit edge for linked abstractState
                val pair = createNewAbstractTransitionForNonWidgetInteraction( abstractAction, interactionData, sourceAbstractState, destinationAbstractState, interaction, oldAbstractEdge, newEdge, condition, prevWindowAbstractState)
                newAbstractionTransition = pair.first
                newEdge = pair.second
            } else {
                newEdge = atuaMF.dstg.add(sourceAbstractState, destinationAbstractState, exisitingAbstractTransition)
                newAbstractionTransition = exisitingAbstractTransition
                exisitingAbstractTransition.interactions.add(interaction)
                if (oldAbstractEdge.label.inputGUIStates.isNotEmpty()) {
                    newAbstractionTransition.inputGUIStates.add(interaction.prevState)
                }
                if (tracing != null) {
                    newAbstractionTransition.tracing.add(tracing)
                }
                if (prevWindowAbstractState != null)
                    exisitingAbstractTransition.dependentAbstractStates.add(prevWindowAbstractState)
            }
            sourceAbstractState.increaseActionCount2(abstractAction, true)
        } else {

            //get widgetgroup
            var newAttributeValuationSet = sourceAbstractState.getAttributeValuationSet(interaction.targetWidget!!,sourceState,atuaMF)
/*            if (newAttributeValuationSet == null) {
                newAttributeValuationSet = oldAbstractEdge.label.abstractAction.attributeValuationSet
            }*/
            /*if (newAttributeValuationSet == null) {
                val guiTreeRectangle = Helper.computeGuiTreeDimension(sourceState)
                var isOptionsMenu = if (!Helper.isDialog(sourceAbstractState.rotation, guiTreeRectangle, sourceState, atuaMF))
                    Helper.isOptionsMenuLayout(sourceState)
                else
                    false
                val newAttributePath = AbstractionFunction2.INSTANCE.reduce(interaction.targetWidget!!,
                        sourceState,
                        oldAbstractEdge.source.data.EWTGWidgetMapping.get(oldAbstractEdge.label.abstractAction.attributeValuationMap!!)!!,
                        isOptionsMenu,
                        guiTreeRectangle,
                        sourceAbstractState.window,
                        sourceAbstractState.rotation, atuaMF, HashMap(), HashMap())
                newAttributeValuationSet = sourceAbstractState.attributeValuationMaps.find { it.haveTheSameAttributePath(newAttributePath) }
                *//*  newAttributeValuationSet = AttributeValuationSet(newAttributePath, Cardinality.ONE, sourceAbstractState.activity, HashMap())
                  activity_widget_AttributeValuationSetHashMap[sourceAbstractState.activity]!!.put(interaction.targetWidget!!,newAttributeValuationSet)*//*


                //newWidgetGroup.guiWidgets.add(interaction.targetWidget!!)
                //sourceAbstractState.addWidgetGroup(newWidgetGroup)
            }*/
            if (newAttributeValuationSet != null) {
                val abstractAction = AbstractAction.getOrCreateAbstractAction(
                        oldAbstractEdge.label.abstractAction.actionType,
                        interaction,
                        sourceState,
                        sourceAbstractState,
                        newAttributeValuationSet,
                        atuaMF

                )
                val interactionData = AbstractTransition.computeAbstractTransitionData(abstractAction.actionType,
                        interaction, sourceState, sourceAbstractState, atuaMF)
                val availableAction = sourceAbstractState.getAvailableActions().find {
                    it.equals(abstractAction)
                }
                if (availableAction == null) {
                    sourceAbstractState.addAction(abstractAction)
                }
                //sourceAbstractState.addAction(abstractAction)
                if (isTarget) {
                    sourceAbstractState.targetActions.add(abstractAction)
                }
                //check if there is exisiting interaction
                val exisitingAbstractTransition = AbstractTransition.findExistingAbstractTransitions(
                        sourceAbstractState.abstractTransitions.toList(),
                        abstractAction,
                        sourceAbstractState,
                        destinationAbstractState,
                        false
                )
                if (exisitingAbstractTransition != null) {
                    newEdge = atuaMF.dstg.add(sourceAbstractState, destinationAbstractState, exisitingAbstractTransition)
                    newAbstractionTransition = exisitingAbstractTransition
                    exisitingAbstractTransition.interactions.add(interaction)
                    if (oldAbstractEdge.label.inputGUIStates.isNotEmpty()) {
                        newAbstractionTransition.inputGUIStates.add(interaction.prevState)
                    }
                    if (tracing != null) {
                        newAbstractionTransition.tracing.add(tracing)
                    }
                    if (prevWindowAbstractState != null)
                        exisitingAbstractTransition.dependentAbstractStates.add(prevWindowAbstractState)
                } else {
                    //Create explicit edge for linked abstractState
                    val pair = createNewAbstractTransionFromWidgetInteraction(abstractAction, interactionData, sourceAbstractState, destinationAbstractState, interaction, oldAbstractEdge, newEdge, condition, prevWindowAbstractState)
                    newAbstractionTransition = pair.first
                    newEdge = pair.second

                }
                sourceAbstractState.increaseActionCount2(abstractAction, true)

            }

        }
        if (newAbstractionTransition != null) {
            // update coverage
            if (atuaMF.guiInteractionCoverage.containsKey(interaction)) {
                val interactionCoverage = atuaMF.guiInteractionCoverage.get(interaction)!!
                interactionCoverage.forEach {
                    newAbstractionTransition.updateUpdateStatementCoverage(it, atuaMF)
                }
            }
            addImplicitAbstractInteraction(destState, newAbstractionTransition, tracing)
        }
        return newEdge
    }

    private fun createNewAbstractTransionFromWidgetInteraction(abstractAction: AbstractAction,
                                                               interactionData: Any?,
                                                               sourceAbstractState: AbstractState,
                                                               destinationAbstractState: AbstractState,
                                                               interaction: Interaction<*>,
                                                               oldAbstractEdge: Edge<AbstractState, AbstractTransition>,
                                                               newEdge: Edge<AbstractState, AbstractTransition>?,
                                                               condition: HashMap<UUID, String>,
                                                               prevWindowAbstractState: AbstractState?): Pair<AbstractTransition?, Edge<AbstractState, AbstractTransition>?> {
        var newAbstractionTransition1:AbstractTransition
        var newEdge1 = newEdge
        val modelVersion = oldAbstractEdge.label.modelVersion
        newAbstractionTransition1 = AbstractTransition(
                abstractAction = abstractAction,
                isImplicit = false,
                /*prevWindow = oldAbstractEdge.label.prevWindow,*/
                data = interactionData,
                source = sourceAbstractState,
                dest = destinationAbstractState,
                modelVersion = modelVersion
        )

        newAbstractionTransition1.interactions.add(interaction)
        if (prevWindowAbstractState != null)
            newAbstractionTransition1.dependentAbstractStates.add(prevWindowAbstractState)
        if (oldAbstractEdge.label.inputGUIStates.isNotEmpty()) {
            newAbstractionTransition1.inputGUIStates.add(interaction.prevState)
        }
        newAbstractionTransition1.handlers.putAll(oldAbstractEdge.label.handlers)
        val tracing = atuaMF.interactionsTracingMap.get(listOf(interaction))
        if (tracing != null) {
            newAbstractionTransition1.tracing.add(tracing)
        }
        newEdge1 = atuaMF.dstg.add(
                sourceAbstractState,
                destinationAbstractState,
                newAbstractionTransition1
        )
        if (condition.isNotEmpty())
            if (!newEdge1.label.userInputs.contains(condition))
                newEdge1.label.userInputs.add(condition)
        newAbstractionTransition1.guardEnabled = oldAbstractEdge.label.guardEnabled
        return Pair(newAbstractionTransition1, newEdge1)
    }

    private fun createNewAbstractTransitionForNonWidgetInteraction(abstractAction: AbstractAction,
                                                                   interactionData: Any?,
                                                                   sourceAbstractState: AbstractState,
                                                                   destinationAbstractState: AbstractState,
                                                                   interaction: Interaction<*>,
                                                                   oldAbstractEdge: Edge<AbstractState, AbstractTransition>,
                                                                   newEdge: Edge<AbstractState, AbstractTransition>?,
                                                                   condition: HashMap<UUID, String>,
                                                                   prevWindowAbstractState: AbstractState?): Pair<AbstractTransition?, Edge<AbstractState, AbstractTransition>?> {
        var newAbstractionTransition:AbstractTransition
        var newEdge1 = newEdge
        val modeVersion = oldAbstractEdge.label.modelVersion
        newAbstractionTransition = AbstractTransition(
                abstractAction = abstractAction,
                isImplicit = false,
                /*prevWindow = oldAbstractEdge.label.prevWindow,*/
                data = interactionData,
                source = sourceAbstractState,
                dest = destinationAbstractState,
                modelVersion = modeVersion
        )
        newAbstractionTransition.interactions.add(interaction)
        if (prevWindowAbstractState != null)
            newAbstractionTransition.dependentAbstractStates.add(prevWindowAbstractState)
        if (oldAbstractEdge.label.inputGUIStates.isNotEmpty()) {
            newAbstractionTransition.inputGUIStates.add(interaction.prevState)
        }
        val tracing = atuaMF.interactionsTracingMap.get(listOf(interaction))
        if (tracing != null) {
            newAbstractionTransition.tracing.add(tracing)
        }
        newEdge1 = atuaMF.dstg.add(sourceAbstractState, destinationAbstractState, newAbstractionTransition)
        if (condition.isNotEmpty())
            if (!newEdge1.label.userInputs.contains(condition))
                newEdge1.label.userInputs.add(condition)
        newAbstractionTransition!!.guardEnabled = oldAbstractEdge.label.guardEnabled
        return Pair(newAbstractionTransition, newEdge1)
    }


    val widget_StaticWidget = HashMap<Window, HashMap<ConcreteId, ArrayList<EWTGWidget>>>()

    fun addImplicitAbstractInteraction(currentState: State<*>?,
                                       abstractTransition: AbstractTransition,
                                       transitionId: Pair<Int, Int>?) {
        //AutAutMF.log.debug("Add implicit abstract interaction")
        var addedCount = 0
        var processedStateCount = 0
        // add implicit back events
        addedCount = 0
        processedStateCount = 0
        val currentAbstractState = abstractTransition.dest
        val prevAbstractState = abstractTransition.source
        if (transitionId != null && currentState != null
                && !currentAbstractState.isOpeningKeyboard
                && abstractTransition.abstractAction.actionType != AbstractActionType.PRESS_BACK
                && !abstractTransition.abstractAction.isLaunchOrReset()) {
            // val implicitBackWindow = computeImplicitBackWindow(currentAbstractState, prevAbstractState, prevWindow)
            val prevWindowAbstractState = atuaMF.getPrevWindowAbstractState(transitionId.first, transitionId.second)
            if (prevWindowAbstractState != null) {
                if (!prevWindowAbstractState.isHomeScreen &&
                        prevAbstractState != currentAbstractState) {
                    val pair = createImplicitPressBackTransition(currentAbstractState, prevWindowAbstractState, currentState, processedStateCount, prevAbstractState, addedCount)
                    addedCount = pair.first
                    processedStateCount = pair.second
                }
            }
        }
        if (transitionId != null && currentState != null && currentAbstractState.isOpeningKeyboard) {
            var keyboardClosedAbstractState: AbstractState? = atuaMF.getKeyboardClosedAbstractState(currentState, transitionId)
            if (keyboardClosedAbstractState == null) {
                keyboardClosedAbstractState = ABSTRACT_STATES.find {
                    it is VirtualAbstractState && it.window == currentAbstractState.window }
            }
            if (keyboardClosedAbstractState != null)
                createImplicitCloseKeyboardTransition(currentAbstractState, keyboardClosedAbstractState, currentState, processedStateCount, addedCount)
        }
        if (abstractTransition.abstractAction.actionType == AbstractActionType.SWIPE
                && abstractTransition.abstractAction.attributeValuationMap != null
                && prevAbstractState != currentAbstractState
        ) {
            //check if the swipe action changed the content
            if (currentAbstractState.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap)) {
                val currentWidgetGroup = currentAbstractState.attributeValuationMaps.find { it == abstractTransition.abstractAction.attributeValuationMap }!!
                if (!currentWidgetGroup.havingSameContent(currentAbstractState, abstractTransition.abstractAction.attributeValuationMap!!, prevAbstractState)) {
                    //add implicit sysmetric action
                    createImplictiInverseSwipeTransition(abstractTransition, currentAbstractState, prevAbstractState)
                }
            }
        }

        if (abstractTransition.abstractAction.actionType == AbstractActionType.ROTATE_UI
                && prevAbstractState != currentAbstractState
                && prevAbstractState.window == currentAbstractState.window
                && prevAbstractState.isOpeningMenus == currentAbstractState.isOpeningMenus) {
            createImplicitInverseRotationTransition(currentAbstractState, prevAbstractState)
        }

        if (abstractTransition.abstractAction.actionType == AbstractActionType.ENABLE_DATA
                || abstractTransition.abstractAction.actionType == AbstractActionType.DISABLE_DATA
        ) {
            return
        }

        //add to virtualAbstractState
        val isTargetAction = prevAbstractState.targetActions.contains(abstractTransition.abstractAction)

        val virtualAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == prevAbstractState.window }.firstOrNull()


        if (virtualAbstractState != null
                && consideredForVirtualAbstractStateAction(abstractTransition.abstractAction)
        ) {
            createImplicitTransitionForVirtualAbstractState(abstractTransition, virtualAbstractState)
        }

        //do not add implicit transition if this is Launch/Reset/Swipe
        if (consideredForVirtualAbstractStateAction(abstractTransition.abstractAction)) {
            createImplicitTransitionForOtherAbstractStates(prevAbstractState, processedStateCount, abstractTransition)
        }

    }

    private fun createImplicitCloseKeyboardTransition(currentAbstractState: AbstractState, keyboardClosedAbstractState: AbstractState, currentState: State<*>, processedStateCount: Int, addedCount: Int) {
        val abstractAction = AbstractAction(actionType = AbstractActionType.CLOSE_KEYBOARD)
        val existingExplicitAT = AbstractTransition.findExistingAbstractTransitions(
                abstractTransitionSet = currentAbstractState.abstractTransitions.toList(),
                abstractAction = abstractAction,
                isImplicit = false,
                dest = keyboardClosedAbstractState,
                source = currentAbstractState
        )
        if (existingExplicitAT != null)
            return
        val implicitAT = AbstractTransition.findExistingAbstractTransitions(
                abstractTransitionSet = currentAbstractState.abstractTransitions.toList(),
                abstractAction = abstractAction,
                isImplicit = true,
                dest = keyboardClosedAbstractState,
                source = currentAbstractState
        )
        if (implicitAT != null)
            return
        val newAbstractionTransition = AbstractTransition(
                abstractAction = abstractAction,
                isImplicit = false,
                source = currentAbstractState,
                dest = keyboardClosedAbstractState,
                modelVersion = ModelVersion.RUNNING,
                data = null,
                fromWTG = false
        )
        atuaMF.dstg.add(newAbstractionTransition.source, newAbstractionTransition.dest, newAbstractionTransition)
    }

    private fun consideredForVirtualAbstractStateAction(abstractAction: AbstractAction): Boolean {
        return !abstractAction.isLaunchOrReset()
                && abstractAction.actionType != AbstractActionType.PRESS_BACK
                && abstractAction.actionType != AbstractActionType.MINIMIZE_MAXIMIZE
                && abstractAction.actionType != AbstractActionType.WAIT
                && abstractAction.actionType != AbstractActionType.CLOSE_KEYBOARD
                && abstractAction.actionType != AbstractActionType.ROTATE_UI
    }

    private fun computeImplicitBackWindow(currentAbstractState: AbstractState, prevAbstractState: AbstractState, prevWindow: Window?): Window? {
        val implicitBackWindow = if (currentAbstractState.isOpeningKeyboard || currentAbstractState.isOpeningMenus) {
            currentAbstractState.window
        } else if (prevAbstractState.window == currentAbstractState.window) {
            prevWindow
        } else if (currentAbstractState.window == prevWindow) {
            null
        } else if (prevAbstractState.window is Dialog) {
            prevWindow
        } else {
            prevAbstractState.window
        }
        return implicitBackWindow
    }

    private fun inferItemActionTransitions(action: AbstractActionType, abstractTransition: AbstractTransition, prevAbstractState: AbstractState, currentAbstractState: AbstractState) {
        val itemAction = when (action) {
            AbstractActionType.CLICK -> AbstractActionType.ITEM_CLICK
            else -> AbstractActionType.ITEM_LONGCLICK
        }
        //val parentWidgetGroups = HashSet<WidgetGroup>()
        var parentAttributeValuationSetId = abstractTransition.abstractAction.attributeValuationMap!!.parentAttributeValuationMapId
        while (parentAttributeValuationSetId != "") {
            val parentAVS = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[prevAbstractState.window]!!.get(parentAttributeValuationSetId)
            if (parentAVS != null) {
                if (prevAbstractState.attributeValuationMaps.contains(parentAVS)) {
                    val itemAbtractAction = AbstractAction(
                            actionType = itemAction,
                            attributeValuationMap = parentAVS
                    )
                    if (parentAVS.actionCount.containsKey(itemAbtractAction)) {
                        prevAbstractState.increaseActionCount2(itemAbtractAction, false)
                        var implicitInteraction =
                                atuaMF.dstg.edges(prevAbstractState).find {
                                    it.label.isImplicit == true
                                            && it.label.abstractAction == itemAbtractAction
                                            /*&& it.label.prevWindow == abstractTransition.prevWindow*/
                                            && it.destination?.data == currentAbstractState
                                }?.label
                        if (implicitInteraction == null) {
                            // create new explicit interaction
                            implicitInteraction = AbstractTransition(
                                    abstractAction = itemAbtractAction,
                                    isImplicit = true,
                                    /*prevWindow = abstractTransition.prevWindow,*/
                                    source = prevAbstractState,
                                    dest = currentAbstractState
                            )
                            atuaMF.dstg.add(prevAbstractState, currentAbstractState, implicitInteraction)
                        }
                        //addImplicitAbstractInteraction(currentState, prevAbstractState, currentAbstractState, implicitInteraction, prevprevWindow, edgeCondition)
                    }
                }
                parentAttributeValuationSetId = parentAVS.parentAttributeValuationMapId
            } else {
                parentAttributeValuationSetId = ""
            }

        }
    }

/*    private fun updateResetAndLaunchTransitions(abstractTransition: AbstractTransition, currentAbstractState: AbstractState) {
        val allAbstractStates = ABSTRACT_STATES
        var resetAppAction: AbstractAction?
        var launchAppAction: AbstractAction?
        if (abstractTransition.abstractAction.actionType == AbstractActionType.RESET_APP) {
            launchAppAction = AbstractAction.getLaunchAction()
            resetAppAction = abstractTransition.abstractAction
        } else {
            launchAppAction = abstractTransition.abstractAction
            resetAppAction = null
        }

        allAbstractStates.forEach { abstractState ->
            updateLaunchTransitions(abstractState, launchAppAction!!, currentAbstractState, abstractTransition)
            if (resetAppAction != null) {
                updateLaunchTransitions(abstractState, resetAppAction, currentAbstractState, abstractTransition)
            }
        }
    }*/

    private fun createImplicitTransitionForOtherAbstractStates(prevAbstractState: AbstractState, processedStateCount: Int, abstractTransition: AbstractTransition) {
        var processedStateCount1 = processedStateCount
        /*if (abstractTransition.source.window == abstractTransition.dest.window)
            return*/
        val destVirtualAbstractState = ABSTRACT_STATES.find {
            it is VirtualAbstractState
                    && it.window == abstractTransition.dest.window
        }
        if (destVirtualAbstractState == null)
            return
        val sourceVirtualAbstractState = ABSTRACT_STATES.find {
            it is VirtualAbstractState
                    && it.window == abstractTransition.source.window
        }
        if (sourceVirtualAbstractState == null)
            return
        if (abstractTransition.source.window is Dialog && abstractTransition.dest.window is Activity
                && abstractTransition.source.activity == abstractTransition.dest.activity)
        // for the transitions go from a dialog back to an activity
        // we should not create implict transition
            return
        if (abstractTransition.source == abstractTransition.dest
                && abstractTransition.source.isOpeningMenus
                && !abstractTransition.dest.isOpeningMenus)
            return
        val guaranteedAVMs = ArrayList<AttributeValuationMap>()
        val existingVirtualTransitions = sourceVirtualAbstractState.abstractTransitions.filter {
            it.abstractAction == abstractTransition.abstractAction
                    /*&& it.prevWindow == abstractTransition.prevWindow*/
                    && it.data == abstractTransition.data
                    && it.dest == abstractTransition.dest
        }
        if (existingVirtualTransitions.isNotEmpty()) {
            guaranteedAVMs.addAll(existingVirtualTransitions.first().guaranteedAVMs)
        } else {
            guaranteedAVMs.addAll(abstractTransition.dest.attributeValuationMaps)
        }
        val otherSameStaticNodeAbStates = getSimilarAbstractStates(prevAbstractState, abstractTransition)
        addImplicitAbstractTransitionToSameWindowAbstractStates(otherSameStaticNodeAbStates, processedStateCount1, abstractTransition, abstractTransition.dest, guaranteedAVMs)
    }


    private fun addImplicitAbstractTransitionToSameWindowAbstractStates(otherSameStaticNodeAbStates: List<AbstractState>,
                                                                        processedStateCount1: Int,
                                                                        abstractTransition: AbstractTransition,
                                                                        destVirtualAbstractState: AbstractState,
                                                                        guaranteedAVMs: List<AttributeValuationMap>) {
        var processedStateCount11 = processedStateCount1

        otherSameStaticNodeAbStates.forEach {
            processedStateCount11 += 1
            var implicitAbstractTransition: AbstractTransition?
            implicitAbstractTransition = it.abstractTransitions.find {
                it.abstractAction == abstractTransition.abstractAction
                        && it.dest == destVirtualAbstractState
                        /*&& it.prevWindow == abstractTransition.prevWindow*/
                        && it.data == abstractTransition.data
            }
            if (implicitAbstractTransition == null) {
                implicitAbstractTransition = AbstractTransition(
                        abstractAction = abstractTransition.abstractAction,
                        source = it,
                        dest = destVirtualAbstractState,
                        /*prevWindow = abstractTransition.prevWindow,*/
                        data = abstractTransition.data,
                        isImplicit = false
                )
                implicitAbstractTransition!!.guardEnabled = abstractTransition.guardEnabled
                // implicitAbstractTransition!!.guaranteedAVMs.addAll(guaranteedAVMs)
                atuaMF.dstg.add(it, destVirtualAbstractState, implicitAbstractTransition)
            }
        }
    }

    private fun createImplicitTransitionForVirtualAbstractState(abstractTransition: AbstractTransition, virtualAbstractState: AbstractState) {
        val abstractAction = abstractTransition.abstractAction
        if (abstractTransition.source.window is Dialog && abstractTransition.dest.window is Activity
                && abstractTransition.source.activity == abstractTransition.dest.activity)
        // for the transitions go from a dialog back to an activity
        // we should not create implict transition
            return
        if (abstractTransition.source == abstractTransition.dest
                && abstractTransition.source.isOpeningMenus
                && !abstractTransition.dest.isOpeningMenus)
            return
/*        if (abstractTransition.source.window == abstractTransition.dest.window)
            return*/
        if (abstractAction.isWidgetAction() && !virtualAbstractState.attributeValuationMaps.contains(abstractAction.attributeValuationMap!!)) {
            virtualAbstractState.attributeValuationMaps.add(abstractAction.attributeValuationMap!!)
        }
        val guaranteedAVMs = ArrayList<AttributeValuationMap>()
        val existingVirtualTransitions = virtualAbstractState.abstractTransitions.filter {
            it.abstractAction == abstractAction
                    /*&& it.prevWindow == abstractTransition.prevWindow*/
                    && it.data == abstractTransition.data
        }
        if (existingVirtualTransitions.isNotEmpty()) {
            val dests = ArrayList(existingVirtualTransitions.map { it.dest })
            dests.add(abstractTransition.dest)
            val destWindows = dests.map { it.window }.distinct()
            if (destWindows.size > 1) {
                return
            }

            guaranteedAVMs.addAll(dests.map { it.attributeValuationMaps }.reduce { interset, avms ->
                ArrayList(interset.intersect(avms))
            })
            // update guaranteedAVMs for abstract transitions
            existingVirtualTransitions.forEach {
                it.guaranteedAVMs.clear()
                it.guaranteedAVMs.addAll(guaranteedAVMs)
            }
        } else {
            guaranteedAVMs.addAll(abstractTransition.dest.attributeValuationMaps)
        }
        val changeEffects: List<ChangeEffect> = computeChanges(abstractTransition)
        val destVirtualAbstractState = ABSTRACT_STATES.find {
            it is VirtualAbstractState && it.window == abstractTransition.dest.window
        }
        if (destVirtualAbstractState != null) {
            val existingVirtualTransition1 = virtualAbstractState.abstractTransitions.find {
                it.abstractAction == abstractAction
                        && it.dest == destVirtualAbstractState
                        /*&& it.prevWindow == abstractTransition.prevWindow*/
                        && it.data == abstractTransition.data
            }
            if (existingVirtualTransition1 == null) {
                val newVirtualTransition = AbstractTransition(
                        abstractAction = abstractAction,
                        source = virtualAbstractState,
                        dest = destVirtualAbstractState,
                        /*prevWindow = abstractTransition.prevWindow,*/
                        data = abstractTransition.data,
                        isImplicit = true
                )
                newVirtualTransition.guaranteedAVMs.addAll(guaranteedAVMs)
                newVirtualTransition.changeEffects.addAll(changeEffects)
                virtualAbstractState.abstractTransitions.add(newVirtualTransition)
                newVirtualTransition.dependentAbstractStates.addAll(abstractTransition.dependentAbstractStates)
                atuaMF.dstg.add(virtualAbstractState, destVirtualAbstractState, newVirtualTransition)
            } else {
                existingVirtualTransition1.changeEffects.addAll(changeEffects)
            }
        }
        val existingVirtualTransition2 = virtualAbstractState.abstractTransitions.find {
            it.abstractAction == abstractAction
                    && it.dest == abstractTransition.dest
                    /*&& it.prevWindow == abstractTransition.prevWindow*/
                    && it.data == abstractTransition.data
        }
        if (existingVirtualTransition2 == null) {
            val newVirtualTransition = AbstractTransition(
                    abstractAction = abstractAction,
                    source = virtualAbstractState,
                    dest = abstractTransition.dest,
                    /*prevWindow = abstractTransition.prevWindow,*/
                    data = abstractTransition.data,
                    isImplicit = true
            )
            newVirtualTransition.guaranteedAVMs.addAll(guaranteedAVMs)
            newVirtualTransition.changeEffects.addAll(changeEffects)
            virtualAbstractState.abstractTransitions.add(newVirtualTransition)
            atuaMF.dstg.add(virtualAbstractState, abstractTransition.dest, newVirtualTransition)
        } else {
            existingVirtualTransition2.changeEffects.addAll(changeEffects)
        }
        /* // get existing action
         var virtualAbstractAction = virtualAbstractState.getAvailableActions().find {
             it == abstractAction
         }


         if (virtualAbstractAction == null) {
             if (abstractAction.attributeValuationMap != null) {
                 val avm = virtualAbstractState.attributeValuationMaps.find {it == abstractAction.attributeValuationMap}
                 if (avm != null) {
                     virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                             attributeValuationMap = avm,
                             extra = abstractAction.extra)

                 } else {
                     val newAttributeValuationSet = abstractAction.attributeValuationMap
                     virtualAbstractState.attributeValuationMaps.add(newAttributeValuationSet)
                     //virtualAbstractState.addAttributeValuationSet(newAttributeValuationSet)
                     virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                             attributeValuationMap = newAttributeValuationSet,
                             extra = abstractAction.extra)
                 }
             } else {
                 virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                         attributeValuationMap = null,
                         extra = abstractAction.extra)
             }
             virtualAbstractState.addAction(virtualAbstractAction)
         }
         if (virtualAbstractAction!=null ) {
             virtualAbstractState.increaseActionCount(virtualAbstractAction)
         }*/
        /* if (isTargetAction) {
             virtualAbstractState.targetActions.add(virtualAbstractAction)
         }
         val implicitDestAbstractState = if (currentAbstractState != prevAbstractState) {
             currentAbstractState
         } else {
             virtualAbstractState
         }
         if (implicitDestAbstractState == virtualAbstractState)
             return
         val abstractEdge = autautMF.abstractTransitionGraph.edges(virtualAbstractState)
                 .filter {
                     it.label.abstractAction == virtualAbstractAction
                             && it.label.prevWindow == prevprevWindow
                             && it.destination?.data == implicitDestAbstractState
                             && it.label.data == abstractTransition.data
                 }
         if (abstractEdge.isEmpty()) {
             val existingAbstractInteraction = autautMF.abstractTransitionGraph.edges(virtualAbstractState).find {
                 it.label.abstractAction == virtualAbstractAction
                         && it.label.prevWindow == prevprevWindow
                         && it.label.data == abstractTransition.data
                         && it.label.dest == implicitDestAbstractState
             }?.label
             val implicitAbstractInteraction = if (existingAbstractInteraction != null) {
                 existingAbstractInteraction
             } else
                 AbstractTransition(
                         abstractAction = virtualAbstractAction,
                         isImplicit = true,
                         data = abstractTransition.data,
                         prevWindow = prevprevWindow,
                         source = virtualAbstractState,
                         dest = implicitDestAbstractState
                 )

             val edge = autautMF.abstractTransitionGraph.add(virtualAbstractState, implicitDestAbstractState, implicitAbstractInteraction)
             if (!edge.label.userInputs.contains(edgeCondition)) {
                 edge.label.userInputs.add(edgeCondition)
             }

         }*/
    }

    private fun computeChanges(abstractTransition: AbstractTransition): List<ChangeEffect> {
        val sourceAbstractState = abstractTransition.source
        val destAbstractState = abstractTransition.dest
        if (sourceAbstractState.rotation != destAbstractState.rotation) {
            val changeResult = ChangeEffect(AffectElementType.Rotation, null, true)
            return listOf(changeResult)
        } else {
            val changeResult = ChangeEffect(AffectElementType.Rotation, null, false)
            return listOf(changeResult)
        }
        return emptyList()
    }

    private fun createImplicitInverseRotationTransition(currentAbstractState: AbstractState, prevAbstractState: AbstractState) {
        val inverseAbstractAction = currentAbstractState.getAvailableActions().find {
            it.actionType == AbstractActionType.ROTATE_UI
        }
        if (inverseAbstractAction != null) {
            val inverseAbstractInteraction = AbstractTransition(
                    abstractAction = inverseAbstractAction,
                    /*prevWindow = implicitBackWindow,*/
                    isImplicit = false,
                    source = currentAbstractState,
                    dest = prevAbstractState
            )
            atuaMF.dstg.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            //currentAbstractState.increaseActionCount(inverseAbstractAction)
        }
    }

    private fun createImplictiInverseSwipeTransition(abstractTransition: AbstractTransition, currentAbstractState: AbstractState, prevAbstractState: AbstractState) {
        val swipeDirection = abstractTransition.abstractAction.extra
        var inverseSwipeDirection = if (swipeDirection == "SwipeUp") {
            "SwipeDown"
        } else if (swipeDirection == "SwipeDown") {
            "SwipeUp"
        } else if (swipeDirection == "SwipeLeft") {
            "SwipeRight"
        } else {
            "SwipeLeft"
        }
        val inverseAbstractAction = currentAbstractState.getAvailableActions().find {
            it.actionType == AbstractActionType.SWIPE
                    && it.attributeValuationMap == abstractTransition.abstractAction.attributeValuationMap
                    && it.extra == inverseSwipeDirection
        }
        if (inverseAbstractAction != null) {
            val inverseAbstractInteraction = AbstractTransition(
                    abstractAction = inverseAbstractAction,
                    data = inverseAbstractAction.extra,
                    /*prevWindow = implicitBackWindow,*/
                    isImplicit = false,
                    source = currentAbstractState,
                    dest = prevAbstractState
            )
            atuaMF.dstg.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            currentAbstractState.increaseActionCount2(inverseAbstractAction, false)
        }
    }

    private fun createImplicitPressBackTransition(currentAbstractState: AbstractState, prevWindowAbstractState: AbstractState?, currentState: State<*>, processedStateCount: Int, prevAbstractState: AbstractState, addedCount: Int): Pair<Int, Int> {
        var processedStateCount1 = processedStateCount
        var addedCount1 = addedCount
        if (prevAbstractState == currentAbstractState) {
            return Pair(addedCount1, processedStateCount1)
        }
        val backAbstractAction = AbstractAction(actionType = AbstractActionType.PRESS_BACK,
                attributeValuationMap = null)

        //check if there is any pressback action go to another window
/*        val sameAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == currentAbstractState.window
                    && it.isOpeningKeyboard == currentAbstractState.isOpeningKeyboard
                    && it.isOpeningMenus == currentAbstractState.isOpeningMenus
        }*/
        var isBackToOtherWindow = false
        /*for (abstractState in sameAbstractStates) {
            if (abstractState.abstractTransitions.any {
                        it.abstractAction == backAbstractAction
                                *//*&& it.prevWindow == prevprevWindow*//*
                                && it.dest.window != implicitBackWindow
                                && !it.isImplicit
                    }) {
                // TODO check
                isBackToOtherWindow = true
                break
            }
        }*/
        var backAbstractState: AbstractState? = null
        /*var backWindow: Window? = null
        if (!isBackToOtherWindow) {
            backWindow = implicitBackWindow
        }
        else {
            val backWindows = sameAbstractStates.map{
                it.abstractTransitions.filter {
                    it.abstractAction == backAbstractAction
                    *//*&& it.prevWindow == prevprevWindow*//*
                            && !it.isImplicit} }.flatten().map { it.dest.window }.distinct()
            // TODO check
            if (backWindows.size==1) {
                backWindow = backWindows.single()
            }
        }
        if (backWindow!=null) {
            val lastIndexOfCurrentState = atuaMF.stateList.indexOfLast {
                it == currentState
            }
            if (currentAbstractState.isOpeningMenus) {
                for (i in lastIndexOfCurrentState-1 downTo 0) {
                    val guiState = atuaMF.stateList[i]
                    val abstractState = guiState_AbstractState_Map[guiState.stateId]
                    if (abstractState == null) {
                        log.debug("$guiState has not mapped with an abstract state")
                    } else {
                        if (abstractState.window == backWindow
                                && !abstractState.isOpeningKeyboard) {
                            backAbstractState = abstractState
                            break
                        }
                    }

                }
            } else {
                for (i in lastIndexOfCurrentState-1 downTo 0) {
                    val guiState = atuaMF.stateList[i]
                    val abstractState = getAbstractState(guiState)
                    if (abstractState == null) {
                        log.debug("$guiState has not mapped with an abstract state")
                    } else {
                        if (abstractState.window == backWindow
                                && !abstractState.isOpeningKeyboard
                                && !abstractState.isOpeningMenus) {
                            backAbstractState = abstractState
                            break
                        }
                    }
                }
            }

        }*/
        backAbstractState = prevWindowAbstractState
        if (backAbstractState != null) {
            val existingAT = AbstractTransition.findExistingAbstractTransitions(
                    abstractTransitionSet = currentAbstractState.abstractTransitions.toList(),
                    abstractAction = backAbstractAction,
                    isImplicit = true,
                    source = currentAbstractState,
                    dest = backAbstractState
            )
            if (existingAT == null) {
                // TODO check
                val backAbstractInteraction = AbstractTransition(
                        abstractAction = backAbstractAction,
                        isImplicit = false,
                        /*prevWindow = prevprevWindow,*/
                        source = currentAbstractState,
                        dest = backAbstractState!!
                )
                // TODO check
                backAbstractInteraction.dependentAbstractStates.add(backAbstractState)
                atuaMF.dstg.add(currentAbstractState, backAbstractState, backAbstractInteraction)
                // add edge condition
                addedCount1 += 1
            } else {
                if (!existingAT.dependentAbstractStates.contains(backAbstractState)) {
                    existingAT.dependentAbstractStates.add(backAbstractState)
                }
            }
        }
        return Pair(addedCount1, processedStateCount1)
    }

/*    private fun updateLaunchTransitions(abstractState: AbstractState, launchAppAction: AbstractAction, currentAbstractState: AbstractState, abstractTransition: AbstractTransition) {
        val existingEdges = atuaMF.dstg.edges(abstractState).filter {
            it.label.abstractAction == launchAppAction
                    && !it.label.isImplicit
        }
        if (existingEdges.isNotEmpty()) {
            existingEdges.forEach {
                atuaMF.dstg.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }
        }
        var implicitAbstractInteraction = AbstractTransition(
                abstractAction = launchAppAction,
                isImplicit = true,
                data = abstractTransition.data,
                *//*prevWindow = null,*//*
                source = abstractState,
                dest = currentAbstractState
        )
        // TODO check
        atuaMF.dstg.add(abstractState, currentAbstractState, implicitAbstractInteraction)
    }*/

    // This should not be implicit added to another abstract states
    private fun isSwipeScreenGoToAnotherWindow(abstractAction: AbstractAction, currentAbstractState: AbstractState, prevAbstractState: AbstractState) =
            (abstractAction.actionType == AbstractActionType.SWIPE && abstractAction.attributeValuationMap == null
                    && currentAbstractState.window != prevAbstractState.window)

    private fun getOrCreateImplicitAbstractInteraction(abstractTransition: AbstractTransition, sourceAbstractState: AbstractState, destinationAbstractState: AbstractState): AbstractTransition? {
        var implicitAbstractTransition: AbstractTransition?

        if (abstractTransition.abstractAction.attributeValuationMap == null) {
            //find existing interaction again
            val existingEdge = atuaMF.dstg.edges(sourceAbstractState).find {
                it.label.abstractAction == abstractTransition.abstractAction
                        /*&& it.label.prevWindow == abstractTransition.prevWindow*/
                        && it.label.data == abstractTransition.data
                        && it.destination?.data == destinationAbstractState
            }
            // TODO check
            if (existingEdge != null) {
                return null
            }
            implicitAbstractTransition =
                    AbstractTransition(
                            abstractAction = abstractTransition.abstractAction,
                            isImplicit = true,
                            data = abstractTransition.data,
                            /*prevWindow = abstractTransition.prevWindow,*/
                            source = sourceAbstractState,
                            dest = destinationAbstractState
                    )
            implicitAbstractTransition!!.guardEnabled = abstractTransition.guardEnabled
// TODO check
        } else {
            //find Widgetgroup
            val widgetGroup = sourceAbstractState.attributeValuationMaps.find {
                it.equals(abstractTransition.abstractAction.attributeValuationMap)
            }
            if (widgetGroup != null) {
                //find existing interaction again
                val existingEdge = atuaMF.dstg.edges(sourceAbstractState).filter {
                    it.label.abstractAction.equals(abstractTransition.abstractAction)
                            /*&& it.label.prevWindow == abstractTransition.prevWindow*/
                            && it.label.data == abstractTransition.data
                            && destinationAbstractState == it.destination?.data
                }
                // TODO check
                if (existingEdge.isNotEmpty()) {
                    return null
                }
                implicitAbstractTransition =
                        AbstractTransition(
                                abstractAction = AbstractAction(
                                        actionType = abstractTransition.abstractAction.actionType,
                                        attributeValuationMap = widgetGroup,
                                        extra = abstractTransition.abstractAction.extra
                                ),
                                isImplicit = true,
                                data = abstractTransition.data,
                                /*prevWindow = abstractTransition.prevWindow,*/
                                source = sourceAbstractState,
                                dest = destinationAbstractState
                        )
                // TODO check
            } else {
                implicitAbstractTransition = null
            }
            if (implicitAbstractTransition != null) {
                sourceAbstractState.increaseActionCount2(implicitAbstractTransition.abstractAction, false)
                implicitAbstractTransition!!.guardEnabled = abstractTransition.guardEnabled
            }
        }

        return implicitAbstractTransition
    }

    fun getPotentialAbstractStates(): List<AbstractState> {
        return ABSTRACT_STATES.filterNot {
            it is VirtualAbstractState
                    || it.guiStates.isEmpty()
                    || it.window is Launcher
                    || it.window is OutOfApp
                    || it.attributeValuationMaps.isEmpty()
        }
    }

    val usefulUnseenBaseAbstractStates = ArrayList<AbstractState>()
    fun dump(dstgFolder: Path) {
        val resetAbstractState = getAbstractState(launchStates.get(LAUNCH_STATE.RESET_LAUNCH)!!)!!
        File(dstgFolder.resolve("AbstractStateList.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            ABSTRACT_STATES.filter { it !is VirtualAbstractState && it.guiStates.isNotEmpty() }.forEach {
                all.newLine()
                val abstractStateInfo = dumpAbstractState(it)
                all.write(abstractStateInfo)
            }
            val unreachedWindow = WindowManager.instance.updatedModelWindows.filter {
                !ABSTRACT_STATES.any {
                    it !is VirtualAbstractState
                            && it.guiStates.isNotEmpty()
                }
            }
            usefulUnseenBaseAbstractStates.addAll(ABSTRACT_STATES.filter {
                it !is VirtualAbstractState
                        && it.window == unreachedWindow
                        && it.modelVersion == ModelVersion.BASE
            })
            val allUnexercisedBaseAbstractTransitions = ABSTRACT_STATES.filter { it !is VirtualAbstractState && it.guiStates.isNotEmpty() }
                    .map { it.abstractTransitions }.flatten()
                    .filter {
                        it.interactions.isEmpty()
                                && it.modelVersion == ModelVersion.BASE
                                && it.isExplicit()
                    }
            ABSTRACT_STATES.filter {
                it !is VirtualAbstractState
                        && it.modelVersion == ModelVersion.BASE
                        && it.window != unreachedWindow
                        && !ModelBackwardAdapter.instance.backwardEquivalentAbstractStateMapping.values.flatten().contains(it)
            }.forEach { abstractState ->
                if (allUnexercisedBaseAbstractTransitions.any { it.dest == abstractState }) {
                    usefulUnseenBaseAbstractStates.add(abstractState)
                }
            }
            usefulUnseenBaseAbstractStates.forEach {
                all.newLine()
                val abstractStateInfo = dumpAbstractState(it)
                all.write(abstractStateInfo)
            }
        }

        val abstractStatesFolder = dstgFolder.resolve("AbstractStates")
        Files.createDirectory(abstractStatesFolder)
        ABSTRACT_STATES.filter { it !is VirtualAbstractState }.forEach {
            it.dump(abstractStatesFolder)
        }
    }

    private fun dumpAbstractState(it: AbstractState): String {
        return "${it.abstractStateId};${it.activity};" +
                "${it.window.windowId};${it.rotation};${it.isOpeningMenus};" +
                "${it.isHomeScreen};${it.isRequestRuntimePermissionDialogBox};" +
                "${it.isAppHasStoppedDialogBox};" +
                "${it.isOutOfApplication};${it.isOpeningKeyboard};" +
                "${it.hasOptionsMenu};" +
                "\"${it.guiStates.map { it.stateId }.joinToString(separator = ";")}\";" +
                "${it.hashCode};${it.isInitalState};${it.modelVersion}"
    }

    private fun header(): String {
        return "[1]abstractStateID;[2]activity;[3]window;[4]rotation;[5]menuOpen;" +
                "[6]isHomeScreen;[7]isRequestRuntimePermissionDialogBox;[8]isAppHasStoppedDialogBox;" +
                "[9]isOutOfApplication;[10]isOpeningKeyboard;[11]hasOptionsMenu;[12]guiStates;[13]hashcode;[14]isInitialState;[15]modelVersion;"
    }

    fun removeObsoleteAbsstractTransitions(correctAbstractTransition: AbstractTransition) {
        val similarAbstractTransitions = ArrayList<AbstractTransition>()
        getType1SimilarAbstractTransitions(correctAbstractTransition.source,correctAbstractTransition,correctAbstractTransition.userInputs,similarAbstractTransitions)
        similarAbstractTransitions.removeIf { it.interactions.isNotEmpty() }
        correctAbstractTransition.source.abstractTransitions.removeIf { similarAbstractTransitions.contains(it) }
        }

    companion object {
        val INSTANCE: AbstractStateManager by lazy {
            AbstractStateManager()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}