package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.atua.DSTG.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.DialogType
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.ewtgdiff.EWTGDiff
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput.TextInput
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
    val activity_widget_AttributeValuationSetHashMap = HashMap<Window, HashMap<Widget,AttributeValuationMap>>()
    val activity_attrValSetsMap = HashMap<String, ArrayList<AttributeValuationMap>>()

    fun init(regressionTestingMF: ATUAMF, appPackageName: String) {
        this.atuaMF = regressionTestingMF
        this.appName = appPackageName

        //create initial abstract state (after App reset)
        appResetState = LauncherAbstractState()

        regressionTestingMF.DSTG = DSTG()


    }

    fun initVirtualAbstractStates(){

        WindowManager.instance.updatedModelWindows.filter { it is Activity || it is Launcher}. forEach {
            val virtualAbstractState = VirtualAbstractState(it.classType, it, it is Launcher)
            ABSTRACT_STATES.add(virtualAbstractState)
            // regressionTestingMF.abstractStateVisitCount[virtualAbstractState] = 0
        }


    }

    fun initAbstractInteractionsForVirtualAbstractStates(){
        ABSTRACT_STATES.filter { it is VirtualAbstractState }.forEach {
            initAbstractInteractions(it,null)
        }
    }
    fun createVirtualAbstractState(window: Window) {
        val virtualAbstractState = VirtualAbstractState(window.classType, window, false)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState,null)
        updateLaunchAndResetAbstractTransitions(virtualAbstractState)
    }

    val backwardEquivalences = HashMap<AbstractState, AbstractState>()
    fun verifyBackwardEquivalent(observedState: AbstractState, expectedState: AbstractState) {
        val matchedAVMs = ArrayList<AttributeValuationMap>()
        for (attributeValuationMap1 in observedState.attributeValuationMaps) {
            for (attributeValuationMap2 in expectedState.attributeValuationMaps) {
                if (attributeValuationMap1.hashCode == attributeValuationMap2.hashCode){
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
            backwardEquivalences.put(observedState,expectedState)
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
                    mapGuiStateToAbstractState(homeState,guiState)
                }
            } else {
                abstractState = VirtualAbstractState(activity = i_activity,
                        staticNode = Launcher.instance!!,
                        isHomeScreen = true)
                mapGuiStateToAbstractState(abstractState,guiState)
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
            var stopState = ABSTRACT_STATES.find { it.isAppHasStoppedDialogBox }
            if (stopState != null) {
                abstractState = stopState
                if (!stopState.guiStates.contains(guiState)) {
                   mapGuiStateToAbstractState(abstractState,guiState)
                }
            } else {
                stopState = AbstractState(activity = activity,
                        isAppHasStoppedDialogBox = true,
                        window = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity,false),
                        rotation = rotation)
               mapGuiStateToAbstractState(stopState,guiState)
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
                val guiWidget_ewtgWidgets = HashMap<Widget,EWTGWidget>()
                val matchedWindow = if (window == null) {
                    log.info("Matching window")
                    matchWindow(guiState, activity, rotation, guiWidget_ewtgWidgets)
                }
                else
                    window
                var widget_AvmHashMap = HashMap<Widget, AttributeValuationMap>()
                log.info("State reducing.")
                measureNanoTime {

                    widget_AvmHashMap = StateReducer.reduce(guiState, matchedWindow.classType, atuaMF.packageName,rotation,atuaMF)
                }.let {
                    time1 = it
                }
                if (!activity_widget_AttributeValuationSetHashMap.containsKey(matchedWindow)) {
                    activity_widget_AttributeValuationSetHashMap.put(matchedWindow, HashMap())
                }
                val widget_AttributeValuationSetHashMap = activity_widget_AttributeValuationSetHashMap[matchedWindow]!!
                widget_AvmHashMap.forEach {
                    widget_AttributeValuationSetHashMap.put(it.key,it.value)
                }
                TextInput.saveSpecificTextInputData(guiState)
                var derivedAVMs = widget_AvmHashMap.map { it.value }.distinct()
                log.info("Find Abstract states.")
                val matchingTestState = findAbstractState(ABSTRACT_STATES, derivedAVMs, activity, rotation, isOpeningKeyboard)
                if (matchingTestState != null) {
                    if (matchingTestState.guiStates.isEmpty()) {
                        // This is because of the rebuild model process
                        matchingTestState.countAVMFrequency()
                    }
                    if (!matchingTestState.guiStates.contains(guiState)) {
                        mapGuiStateToAbstractState(matchingTestState, guiState)
                    }
                    return matchingTestState
                }
           /*     var staticMapping = Pair<Window, HashMap<AttributeValuationMap, EWTGWidget>>(first = Launcher.instance!!, second = HashMap())
                measureNanoTime {
                    staticMapping = matchWindowAndWidgets(widget_AvmHashMap, guiState, activity, rotation,window)
                }.let {
                    time3 = it
                }*/
                log.info("Matching AVMs with EWTG Widgets")
                var avm_ewtgWidgetsHashMap = matchingAvmWithEWTGWidgets(guiState,widget_AvmHashMap ,guiWidget_ewtgWidgets)
                var refinementIncrease = false
                while (avm_ewtgWidgetsHashMap.any { it.value.size>1 }) {
                    val ambigousAVMs = avm_ewtgWidgetsHashMap.filter { it.value.size > 1 }.keys
                    ambigousAVMs.forEach { avm ->
                        val relatedGUIWidgets = widget_AvmHashMap.filter { it.value == avm }.keys
                        val abstractionFunction = AbstractionFunction.INSTANCE
                        relatedGUIWidgets.forEach {guiWidget->
                            val attributePath = abstractionFunction.reduce(guiWidget, guiState, matchedWindow.classType,rotation,atuaMF, HashMap(), HashMap())
                            if(abstractionFunction.increaseReduceLevel(attributePath,matchedWindow.classType, false)) {
                                refinementIncrease = true
                            }
                        }
                    }
                    widget_AvmHashMap = StateReducer.reduce(guiState, matchedWindow.classType, atuaMF.packageName,rotation,atuaMF)
                    avm_ewtgWidgetsHashMap = matchingAvmWithEWTGWidgets(guiState,widget_AvmHashMap ,guiWidget_ewtgWidgets)
                }
                val avm_ewtgWidgets = HashMap(avm_ewtgWidgetsHashMap.entries.filter{it.value.isNotEmpty()}.associate { it.key to it.value.first() })
                measureNanoTime {
                    abstractState = AbstractState(activity = activity,
                            attributeValuationMaps = ArrayList(derivedAVMs),
                            isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                            isOpeningKeyboard = isOpeningKeyboard,
                            EWTGWidgetMapping = avm_ewtgWidgets,
                            window = matchedWindow,
                            rotation = atuaMF.currentRotation,
                            isOutOfApplication = matchedWindow is OutOfApp)
                    if (abstractState!!.window is Dialog || abstractState!!.window is OptionsMenu || abstractState!!.window is ContextMenu) {
                        abstractState!!.hasOptionsMenu = false
                    }
                    if (Helper.isOptionsMenuLayout(guiState)) {
                        abstractState!!.isMenusOpened = true
                    }
                    ABSTRACT_STATES.add(abstractState!!)
                    mapGuiStateToAbstractState(abstractState!!,guiState)
                    initAbstractInteractions(abstractState!!,guiState)
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
                    rebuildModel(matchedWindow,false,guiState,null)
                }
            }.let {
                if (it > 10e8) {
                    log.debug("AbstractState creation took: ${it/10e6.toDouble()} milliseconds, in which: ")
                    log.debug("State reducing took: ${time1/10e6.toDouble()} milliseconds,")
                    log.debug("Finding matching abstract state took: ${time2/10e6.toDouble()} milliseconds,")
                    log.debug("Get matching static widgets reducing took: ${time3/10e6.toDouble()} milliseconds.")
                    log.debug("Init Abstract Interactions took: ${time4/10e6.toDouble()} milliseconds.")
                }
            }


        }
        return abstractState!!
    }

    private fun matchingAvmWithEWTGWidgets(guiState: State<*>, guiWidget_AVMs: HashMap<Widget, AttributeValuationMap>,  guiWidget_ewtgWidgets: HashMap<Widget, EWTGWidget>): HashMap<AttributeValuationMap, ArrayList<EWTGWidget>> {
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
                                  classType: String,
                                  rotation: Rotation,
                                  isOpeningKeyboard: Boolean): AbstractState? {
        val predictedAbstractStateHashcode = AbstractState.computeAbstractStateHashCode(guiReducedAttributeValuationMap, classType, rotation)
        var result: AbstractState? = null
        for (abstractState in abstractStateList.filter {
            it !is VirtualAbstractState
                    && it.window.classType == classType
                    && it.rotation == rotation
                    && it.isOpeningKeyboard == isOpeningKeyboard
        }) {
            if (abstractState.hashCode == predictedAbstractStateHashcode) {
                return abstractState
            }
            val matchedAVMs = HashMap<AttributeValuationMap, AttributeValuationMap>()
            for (attributeValuationMap in guiReducedAttributeValuationMap) {
                for (attributeValuationMap2 in abstractState.attributeValuationMaps) {
                    if (attributeValuationMap.hashCode == attributeValuationMap2.hashCode) {
                        matchedAVMs.put(attributeValuationMap, attributeValuationMap2)
                        break
                    }
                }
            }
            val size = matchedAVMs.size
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

     fun initAbstractInteractions(abstractState: AbstractState, guiState: State<*>?=null) {
        abstractState.initAction()

         val inputs = abstractState.window.inputs
         inputs.forEach {input ->
             if (input.widget != null)
                initWidgetInputForAbstractState(abstractState, input)
             else
                 initWindowInputForAbstractState(abstractState,input)
         }
         //create implicit non-widget interactions
        val nonTrivialWindowTransitions = atuaMF.wtg.edges(abstractState.window).filter { it.label.input.widget == null && it.label.input.eventType!=EventType.implicit_back_event && it.source.data == it.destination?.data}
        //create implicit widget interactions from static Node
        val nonTrivialWidgetWindowsTransitions = atuaMF.wtg.edges(abstractState.window).filter { it.label.input.widget != null }.filterNot { it.source.data == it.destination?.data }

        nonTrivialWindowTransitions
                .forEach { windowTransition ->
                    val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label.input) }.map { it.key}
                    val destWindow = windowTransition.destination!!.data
                    val destAbstractState = ABSTRACT_STATES.find { it.window == destWindow && it is VirtualAbstractState }
                    if (destAbstractState!=null) {
                        abstractActions.forEach { abstractAction ->
                            createAbstractTransitionFromWindowTransition(abstractState, abstractAction, windowTransition, destAbstractState)
                        }
                    }
                }

        nonTrivialWidgetWindowsTransitions
                .forEach { windowTransition ->
                    val destWindow = windowTransition.destination!!.data
                    val destAbstractState = ABSTRACT_STATES.find { it.window == destWindow && it is VirtualAbstractState }
                    if (destAbstractState != null) {
                        val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label.input) }.map { it.key}
                        abstractActions.forEach {abstractAction ->
                           createAbstractTransitionFromWindowTransition(abstractState,abstractAction,windowTransition,destAbstractState)
                        }
                    }
                }

         WindowManager.instance.intentFilter.forEach { window, intentFilters ->
             val destVirtualAbstractState = ABSTRACT_STATES.find { it.window == window && it is VirtualAbstractState }
             if (destVirtualAbstractState!=null) {
                 intentFilters.forEach {
                     val abstractAction = AbstractAction(
                             actionType = AbstractActionType.SEND_INTENT,
                             extra = it
                     )
                     val abstractInteraction = AbstractTransition(
                             abstractAction = abstractAction,
                             isImplicit = true,
                             prevWindow = null,
                             data = null,
                             source = abstractState,
                             dest = destVirtualAbstractState,
                             fromWTG = true)
                     val newEdge = atuaMF.DSTG.add(abstractState, destVirtualAbstractState, abstractInteraction)

                 }
             }


         }
        //create implicit widget interactions from VirtualAbstractState
        if (abstractState is VirtualAbstractState) {
            return
        }
        val virtualAbstractStates = ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == abstractState.window }
        if (virtualAbstractStates.isEmpty()) {
            return
        }
        val virtualAbstractState = virtualAbstractStates.first()

        // firstly, set action count
       virtualAbstractState.getAvailableActions().forEach {virtualAbstractAction->
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
                    } else if (guiState!=null){
                        val guiWidget = virtualAbstractAction.attributeValuationMap.getGUIWidgets(guiState).firstOrNull()
                        // guiState.widgets.find { virtualAbstractAction.widgetGroup.isAbstractRepresentationOf(it,guiState) }
                        if (guiWidget != null) {
                            val newAttributePath = activity_widget_AttributeValuationSetHashMap[abstractState.window]!!.get(guiWidget)!!
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

        val virtualTransitions =  virtualAbstractState.abstractTransitions.filter {
            //we will not process any self edge
            it.dest != virtualAbstractState
        }
         for (transition in virtualTransitions) {
             if (transition.abstractAction.actionType == AbstractActionType.PRESS_BACK
                     || transition.abstractAction.isLaunchOrReset()) {
                 continue
             }
             var ignoreTransition = false
             for (changeResult in transition.changeEffects) {
                 if (changeResult.affectedElementType == AffectElementType.Rotation) {
                     if (changeResult.changed) {
                         if (transition.dest.rotation == abstractState.rotation) {
                             ignoreTransition = true
                             break
                         }
                     }
                     else {
                         if (transition.dest.rotation != abstractState.rotation) {
                             ignoreTransition = true
                             break
                         }
                     }
                 }
             }

             if (ignoreTransition)
                 continue
             val userInputs = transition.userInputs
             // initAbstractActionCount
             val virtualAbstractAction = transition.abstractAction
             val existingAction = abstractState.getAvailableActions().find {
                 it == transition.abstractAction
             }
             if (existingAction != null) {
                 val existingEdge = abstractState.abstractTransitions.find {
                     it.abstractAction == transition.abstractAction
                             && it.dest == transition.dest
                             && it.prevWindow == transition.prevWindow
                 }
                 if (existingEdge == null) {
                     val abstractInteraction = AbstractTransition(
                             abstractAction = existingAction,
                             isImplicit = true,
                             prevWindow = transition.prevWindow,
                             data = transition.data,
                             source = abstractState,
                             dest = transition.dest)
                     val newEdge = atuaMF.DSTG.add(abstractState, transition.dest, abstractInteraction)
                     // add edge condition
                     newEdge.label.userInputs.addAll(userInputs)
                 }
             }
         }
         updateLaunchAndResetAbstractTransitions(abstractState)
     }

    private fun createAbstractTransitionFromWindowTransition(abstractState: AbstractState, abstractAction: AbstractAction, windowTransition: Edge<Window, WindowTransition>, destAbstractState: AbstractState) {
        val abstractEdge = atuaMF.DSTG.edges(abstractState).find {
            it.label.isImplicit
                    && it.label.abstractAction == abstractAction
                    && it.label.data == windowTransition.label.input.data
                    && it.label.prevWindow == null
                    && it.label.dest == destAbstractState
        }

        var abstractTransition: AbstractTransition
        if (abstractEdge != null) {
            abstractTransition = abstractEdge.label
            if (!abstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                abstractState.inputMappings.put(abstractTransition.abstractAction, arrayListOf(windowTransition.label.input))
            } else {
                if (!abstractState.inputMappings[abstractTransition.abstractAction]!!.contains(windowTransition.label.input)) {
                    abstractState.inputMappings[abstractTransition.abstractAction]!!.add(windowTransition.label.input)
                }
            }
        } else {
            abstractTransition = AbstractTransition(abstractAction = abstractAction,
                    isImplicit = true,
                    prevWindow = null,
                    data = windowTransition.label.input.data,
                    fromWTG = true,
                    source = abstractState,
                    dest = destAbstractState)
            windowTransition.label.input.modifiedMethods.forEach {
                abstractTransition.modifiedMethods.put(it.key, false)
            }
            abstractState.inputMappings.put(abstractTransition.abstractAction, arrayListOf(windowTransition.label.input))
            if (atuaMF.allTargetInputs.contains(windowTransition.label.input)) {
                abstractState.targetActions.add(abstractTransition.abstractAction)
            }
            atuaMF.DSTG.add(abstractState, destAbstractState, abstractTransition)
            abstractState.abstractTransitions.add(abstractTransition)
        }

    }

    private fun initWidgetInputForAbstractState(abstractState: AbstractState, input: Input) {
        if (input.widget==null)
            return
        val avms = abstractState.EWTGWidgetMapping.filter { m -> m.value == input.widget }.map { it.key }.toMutableList()
        if (avms.isEmpty() && abstractState is VirtualAbstractState) {
            //create a fake widgetGroup
            val staticWidget = input.widget
            val localAttributes = HashMap<AttributeType, String>()
            localAttributes.put(AttributeType.resourceId, staticWidget!!.resourceIdName)
            localAttributes.put(AttributeType.className, staticWidget!!.className)

            val attributePath = AttributePath(localAttributes = localAttributes, activity = abstractState.activity)
            val virtAVM = AttributeValuationMap(attributePath = attributePath, cardinality = Cardinality.ONE, windowClassType = abstractState.window.classType, attributPath_cardinality = HashMap<AttributePath, Cardinality>())
            //abstractState.addAttributeValuationSet(virtAVM)
            abstractState.EWTGWidgetMapping.put(virtAVM, input.widget!!)
            avms.add(virtAVM)
        }
        avms.forEach { avm ->
            var widgetAbstractAction = abstractState.getAvailableActions().find {
                it.actionType == input.convertToExplorationActionName()
                        && it.attributeValuationMap == avm
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
            } else {
                if (widgetAbstractAction.extra == null) {
                    widgetAbstractAction.extra = input.data
                }
            }
            if (widgetAbstractAction != null) {
                if (!abstractState.getAvailableActions().contains(widgetAbstractAction)) {
                    abstractState.addAction(widgetAbstractAction)
                }
                if (!abstractState.inputMappings.containsKey(widgetAbstractAction)) {
                    abstractState.inputMappings.put(widgetAbstractAction, arrayListOf())
                }
                val inputMapping = abstractState.inputMappings.get(widgetAbstractAction)!!
                if (!inputMapping.contains(input)) {
                    inputMapping.add(input)
                }
            }
        }
    }

    private fun initWindowInputForAbstractState(abstractState: AbstractState, input: Input) {
        var abstractAction = abstractState.actionCount.keys.find { it.actionType == input.convertToExplorationActionName() }
        if (abstractAction == null) {
            if (abstractState.validateInput(input))
            abstractAction = AbstractAction(
                    actionType = input.convertToExplorationActionName(),
                    extra = input.data)
        } else {
            if (abstractAction.extra == null) {
                abstractAction.extra = input.data
            }
        }
        if (abstractAction == null)
            return
        abstractState.actionCount.put(abstractAction, 0)
        if (!abstractState.inputMappings.containsKey(abstractAction))
            abstractState.inputMappings.put(abstractAction, arrayListOf())
        val inputMapping = abstractState.inputMappings.get(abstractAction)!!
        if (!inputMapping.contains(input))
            inputMapping.add(input)
    }

    fun updateLaunchAndResetAbstractTransitions(abstractState: AbstractState) {
        if (launchStates.containsKey(LAUNCH_STATE.NORMAL_LAUNCH)) {
            val normalLaunchStates = launchStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
            val launchAction = AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
            val launchAbstractState = getAbstractState(normalLaunchStates)
            if (launchAbstractState != null) {
                val existingTransition = atuaMF.DSTG.edges(abstractState).find {
                    it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP }
                if (existingTransition!=null) {
                    atuaMF.DSTG.remove(existingTransition)
                    launchAbstractState.abstractTransitions.remove(existingTransition.label)
                }
                val abstractInteraction = AbstractTransition(abstractAction = launchAction,
                        isImplicit = true, prevWindow = null, source = abstractState, dest = launchAbstractState)
                atuaMF.DSTG.add(abstractState, launchAbstractState, abstractInteraction)

            }
        }
        if (launchStates.containsKey(LAUNCH_STATE.RESET_LAUNCH)) {
            // add reset action
            val resetLaunchStates = launchStates[LAUNCH_STATE.RESET_LAUNCH]!!
            val resetAction = AbstractAction(
                    actionType = AbstractActionType.RESET_APP
            )
            val resetAbstractState = getAbstractState(resetLaunchStates)
            if (resetAbstractState != null) {
                val existingTransition = atuaMF.DSTG.edges(abstractState).find { it.label.abstractAction.actionType == AbstractActionType.RESET_APP }
                if (existingTransition!=null) {
                    atuaMF.DSTG.remove(existingTransition)
                    resetAbstractState.abstractTransitions.remove(existingTransition.label)
                }
                val resetAbstractInteraction = AbstractTransition(abstractAction = resetAction,
                        isImplicit = true, prevWindow = null, source = abstractState, dest = resetAbstractState)

                atuaMF.DSTG.add(abstractState, resetAbstractState, resetAbstractInteraction)
            }
        }
    }

    val guiState_AbstractState_Map = HashMap<ConcreteId,AbstractState>()
    fun getAbstractState(guiState: State<*>): AbstractState? {
        val abstractState = guiState_AbstractState_Map.get(guiState.stateId)
        return abstractState
    }

    fun hasSameAVS(widgetGroups1: Set<AttributeValuationMap>, widgetGroups2: Set<AttributeValuationMap>): Boolean {
        if (widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun matchWindowAndWidgets(widget_AttributeValuationMapHashMap: HashMap<Widget, AttributeValuationMap>
                              , guiState: State<*>
                              , activity: String
                              , rotation: Rotation
                              , window: Window?): Pair<Window, HashMap<AttributeValuationMap, EWTGWidget>> {
        //check if the previous state is homescreen
        var bestMatchedNode: Window? = null
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        val windowId = guiState.widgets.find { !it.isKeyboard }?.metaInfo?.find { it.contains("windowId") }?.split(" = ")?.get(1)
        if (windowId != null) {
            val sameWindowIdWindow = WindowManager.instance.updatedModelWindows.find {it.windowRuntimeIds.contains(windowId)}
            if (sameWindowIdWindow != null) {
                bestMatchedNode = sameWindowIdWindow
            }
        }
        if (window == null && bestMatchedNode==null) {

            val allPossibleNodes = ArrayList<Window>()
            if (activity.isBlank()) {
                return Pair(first = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity,false), second = HashMap())
            }
            //if the previous state is not homescreen
            //Get candidate nodes
            var activityNode: Window? = WindowManager.instance.updatedModelWindows.find {it is Activity && it.classType == activity }
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
                            OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity,false)
                        }
                val virtualAbstractState = VirtualAbstractState(newWTGNode.classType, newWTGNode, false)
                ABSTRACT_STATES.add(virtualAbstractState)
                activityNode = newWTGNode
            }


            if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                bestMatchedNode = activityNode
            } else {
                if (activityNode !is OutOfApp) {
                    val allApplicationDialogClasses = WindowManager.instance.dialogClasses.filter {
                        it.key == DialogType.APPLICATION_DIALOG
                                || it.key == DialogType.DIALOG_FRAGMENT
                    }.map { it.value }.flatten()
                    val recentExecutedMethods = atuaMF!!.statementMF!!.recentExecutedMethods.map { atuaMF.statementMF!!.methodInstrumentationMap.get(it)!! }
                    val dialogMethods = recentExecutedMethods
                            .filter { m -> allApplicationDialogClasses.any { m.contains(it) } }
                            .associateWith { m -> allApplicationDialogClasses.find { m.contains(it) }!! }
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
                                }
                        if (possibleLibraryDialogs.isNotEmpty()) {
                            allPossibleNodes.addAll(possibleLibraryDialogs)
                        }
                    } else {
                        allPossibleNodes.addAll(possibleDialogs)
                    }
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
                //try to calculate the match weight of each node.
                //only at least 1 widget matched is in the return result
                if (allPossibleNodes.size > 0) {
                    val matchWeights = Helper.calculateMatchScoreForEachNode(guiState, allPossibleNodes, appName, activity,widget_AttributeValuationMapHashMap, atuaMF)
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
                        if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                            bestMatchedNode = activityNode
                        } else {
                           /* if (Helper.isOptionsMenuLayout(guiState)) {
                                val existingOptionsMenu = allPossibleNodes.find { it is OptionsMenu }
                                if (existingOptionsMenu != null) {
                                    bestMatchedNode = existingOptionsMenu
                                } else {
                                    val newOptionsMenu = createOptionsMenu(activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                                    bestMatchedNode = newOptionsMenu
                                }
                            } else {
                            }*/
                            val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                            bestMatchedNode = newWTGDialog
                        }
                    }
                } else {
                    // FIXME create Dialog only
                    if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                        bestMatchedNode = activityNode
                    } else {
                        val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                        bestMatchedNode = newWTGDialog
                    }
                }
            }
        } else {
            if (window != null)
                bestMatchedNode = window
        }
        if (isDimensionEmpty(bestMatchedNode!!, rotation, isOpeningKeyboard)) {
            setDimension(bestMatchedNode, rotation, guiTreeDimension, isOpeningKeyboard)
        }
        // bestMatchedNode.activityClass = activity
        if (bestMatchedNode is Dialog) {
            var activityNode: Window? = WindowManager.instance.updatedModelWindows.find {it is Activity && it.classType == activity }
            bestMatchedNode.ownerActivitys.add(activityNode!!)
        }
        if (windowId!=null)
            bestMatchedNode!!.windowRuntimeIds.add(windowId)
        val widgetGroup_staticWidgetHashMap = ewtg_avm_matching(widget_AttributeValuationMapHashMap, guiState, bestMatchedNode!!)
        return Pair(first = bestMatchedNode, second = widgetGroup_staticWidgetHashMap)
    }

    fun matchWindow(guiState: State<*>, activity: String, rotation: Rotation, guiWidget_ewtgWidgets: HashMap<Widget, EWTGWidget>): Window {
        //check if the previous state is homescreen
        var bestMatchedNode: Window? = null
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        var activityNode: Window? = WindowManager.instance.updatedModelWindows.find {it.classType == activity }
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
                        OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity,false)
                    }
            val virtualAbstractState = VirtualAbstractState(newWTGNode.classType, newWTGNode, false)
            ABSTRACT_STATES.add(virtualAbstractState)
            activityNode = newWTGNode
        }
        val windowId = guiState.widgets.find { !it.isKeyboard }?.metaInfo?.find { it.contains("windowId") }?.split(" = ")?.get(1)
        if (windowId != null) {
            val sameWindowIdWindow = WindowManager.instance.updatedModelWindows.find {it.windowRuntimeIds.contains(windowId)}
            if (sameWindowIdWindow != null) {
                bestMatchedNode = sameWindowIdWindow
            }
        }
        if (bestMatchedNode==null) {

            val allPossibleNodes = ArrayList<Window>()
            if (activity.isBlank()) {
                val newOutOfAppWindow = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity,false)
                return newOutOfAppWindow
            }
            //if the previous state is not homescreen
            //Get candidate nodes



            if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                bestMatchedNode = activityNode
            } else {
                if (activityNode !is OutOfApp) {
                    val allApplicationDialogClasses = WindowManager.instance.dialogClasses.filter {
                        it.key == DialogType.APPLICATION_DIALOG
                                || it.key == DialogType.DIALOG_FRAGMENT
                    }.map { it.value }.flatten()
                    val recentExecutedMethods = atuaMF!!.statementMF!!.recentExecutedMethods.map { atuaMF.statementMF!!.methodInstrumentationMap.get(it)!! }
                    val dialogMethods = recentExecutedMethods
                            .filter { m -> allApplicationDialogClasses.any { m.contains(it) } }
                            .associateWith { m -> allApplicationDialogClasses.find { m.contains(it) }!! }
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
                                }
                        if (possibleLibraryDialogs.isNotEmpty()) {
                            allPossibleNodes.addAll(possibleLibraryDialogs)
                        } else {
                            val candidateDialogs = WindowManager.instance.allMeaningWindows
                                    .filter { it is Dialog && it.isRuntimeCreated && it.ownerActivitys.contains(activityNode)}
                            allPossibleNodes.addAll(candidateDialogs)
                        }
                    } else {
                        allPossibleNodes.addAll(possibleDialogs)
                    }
                } else {
                    val candidateDialogs = WindowManager.instance.allMeaningWindows
                            .filter { it is Dialog && it.isRuntimeCreated && it.ownerActivitys.contains(activityNode)}
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
                if (allPossibleNodes.size > 0) {
                    val matchWeights = Helper.calculateMatchScoreForEachNode2(guiState, allPossibleNodes, appName, activity, atuaMF)
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
                        if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                            bestMatchedNode = activityNode
                        } else {
                            /* if (Helper.isOptionsMenuLayout(guiState)) {
                                 val existingOptionsMenu = allPossibleNodes.find { it is OptionsMenu }
                                 if (existingOptionsMenu != null) {
                                     bestMatchedNode = existingOptionsMenu
                                 } else {
                                     val newOptionsMenu = createOptionsMenu(activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                                     bestMatchedNode = newOptionsMenu
                                 }
                             } else {
                             }*/
                            val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                            bestMatchedNode = newWTGDialog
                        }
                    }
                } else {
                    // FIXME create Dialog only
                    if (!Helper.isDialog(rotation, guiTreeDimension,guiState, atuaMF)) {
                        bestMatchedNode = activityNode
                    } else {
                        val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
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
        if (windowId!=null)
            bestMatchedNode!!.windowRuntimeIds.add(windowId)
        Helper.matchingGUIWidgetWithEWTGWidgets(guiWidget_ewtgWidgets,guiState,bestMatchedNode!!)
        return bestMatchedNode
    }


    /*private fun createOptionsMenu(activityNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean): OptionsMenu {
        val optionsMenu = OptionsMenu.getOrCreateNode(OptionsMenu.getNodeId(),activityNode.activityClass,runtimeCreated = true,isBaseModel = false)
        setDimension(optionsMenu,rotation,guiTreeDimension,isOpeningKeyboard)
        createVirtualAbstractState(optionsMenu)
        return optionsMenu
    }*/



    private fun createNewDialog(activity: String, activityNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean): Dialog {
        val newId = Dialog.getNodeId()
        val newWTGDialog = Dialog.getOrCreateNode( newId,
                activity+newId,"",true,false)
        //autautMF.wtg.add(activityNode, newWTGDialog, FakeEvent(activityNode))
        setDimension(newWTGDialog, rotation, guiTreeDimension, isOpeningKeyboard)
        // regressionTestingMF.transitionGraph.copyNode(activityNode!!,newWTGDialog)
        //createVirtualAbstractState(newWTGDialog)
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


    fun matchWindowAndWidgets(widget_AttributeValuationMapHashMap: HashMap<Widget, AttributeValuationMap>, guiState: State<*>, window: Window): Pair<Window, HashMap<AttributeValuationMap, EWTGWidget>> {
        val widgetGroup_staticWidgetHashMap = ewtg_avm_matching(widget_AttributeValuationMapHashMap, guiState, window)
        return Pair(first = window, second = widgetGroup_staticWidgetHashMap)
    }
    val REFINEMENT_MAX = 25

    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>, abstractTransition: AbstractTransition): Int {
        val abstractionFunction = AbstractionFunction.INSTANCE
        val actionWidget = guiInteraction.targetWidget
        if (actionWidget == null || abstractTransition.abstractAction.actionType == AbstractActionType.RANDOM_KEYBOARD)
        {
            return 0
        }
        AbstractionFunction.backup(atuaMF)

        var refinementGrainCount = 0

        val originalActionAbstractState = getAbstractState(actionGUIState)!!
        val attributeValuationSet = originalActionAbstractState.getAttributeValuationSet(guiInteraction.targetWidget!!,actionGUIState,atuaMF = atuaMF)!!
        if (AbstractionFunction.INSTANCE.isAbandonedAbstractTransition(originalActionAbstractState.activity,abstractTransition))
            return 0
        while (!validateModel(guiInteraction, actionGUIState)) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val attributePath = abstractionFunction.reduce(actionWidget, actionGUIState, actionAbstractState.window.classType,actionAbstractState.rotation,atuaMF, HashMap(), HashMap())
            log.info("Increase refinement")
            if (abstractionFunction.increaseReduceLevel(attributePath, actionAbstractState.window.classType, false)) {
                /*if (!refineAbstractionFunction(actionAbstractState)) {
                    AbstractionFunction.restore()
                    refinementGrainCount = 0
                    rebuildModel(actionAbstractState.window)
                    AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Pair(attributePath, abstractInteraction))
                    break
                } else {
                }*/
                refinementGrainCount += 1
                //rebuildModel(actionAbstractState.window)
                rebuildPartly(guiInteraction,actionGUIState)
            }/* else {
                break
            }*/
             else {
                //rebuild all related GUI states
                log.info("Restore refinement")
                AbstractionFunction.restore(atuaMF)
                refinementGrainCount =-1
                rebuildModel(actionAbstractState.window,true,actionGUIState ,guiInteraction)
                val finalActionAbstractState = getAbstractState(actionGUIState)!!
                val attributeValuationSet = finalActionAbstractState.getAttributeValuationSet(guiInteraction.targetWidget!!,actionGUIState,atuaMF = atuaMF)
                val finalAbstractTransition = finalActionAbstractState.abstractTransitions.find { it.interactions.contains(guiInteraction) }!!
                if (attributeValuationSet!=null) {
                    AbstractionFunction.INSTANCE.abandonedAbstractTransitions.add(finalAbstractTransition)
                    log.info("Add new abandoned attribute valuation set for: $guiInteraction")
                }
                break
            }
            /*if (refinementGrainCount>REFINEMENT_MAX) {
                break
            }*/
        }
        if (refinementGrainCount>0) {
            rebuildModel(originalActionAbstractState.window,false,actionGUIState ,guiInteraction)
        }
        //get number of Abstract Interaction
        log.debug("Refinement grain increased count: $refinementGrainCount")
        return refinementGrainCount
    }

    private fun rebuildPartly(guiInteraction: Interaction<*>, actionGUIState: State<*>) {
        val actionAbstractState = getAbstractState(actionGUIState)!!
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val abstractEdge = atuaMF.DSTG.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }!!
        abstractEdges.add(abstractEdge)
        val oldAbstractStatesByWindow = HashMap<Window,ArrayList<AbstractState>>()
        oldAbstractStatesByWindow.put(actionAbstractState.window, arrayListOf(actionAbstractState))
        rebuildAbstractStates(oldAbstractStatesByWindow)
    }

    private fun validateModel2(guiInteractionSequence: LinkedList<Interaction<*>>): Boolean {

        return true
    }

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        if (guiInteraction.targetWidget!=null && guiInteraction.targetWidget!!.isKeyboard)
            return true
        if (guiInteraction.targetWidget!=null && Helper.hasParentWithType(guiInteraction.targetWidget!!,actionGUIState,"WebView"))
            return true
        val actionAbstractState = getAbstractState(actionGUIState)
        if (actionAbstractState==null)
            return true
        val abstractEdge = atuaMF.DSTG.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }

        if (abstractEdge == null)
            return true
        if (abstractEdge.destination?.data?.window is OutOfApp) {
            return true
        }
        if (abstractEdge.label.abstractAction.attributeValuationMap == null)
            return true
        /*val abstractStates = if (guiInteraction.targetWidget == null) {
            ABSTRACT_STATES.filterNot{ it is VirtualAbstractState}. filter { it.window == actionAbstractState.window}
        } else {
            val widgetGroup = actionAbstractState.getWidgetGroup(guiInteraction.targetWidget!!, actionGUIState)
            ABSTRACT_STATES.filterNot { it is VirtualAbstractState }. filter { it.window == actionAbstractState.window
                    && it.widgets.contains(widgetGroup)}
        }*/
        val userInputs = abstractEdge.label.userInputs
        val abstractStates = arrayListOf(actionAbstractState)
        abstractStates.addAll(getSimilarAbstractStates(actionAbstractState,abstractEdge.label).filter { it.attributeValuationMaps.contains(abstractEdge.label.abstractAction.attributeValuationMap!!) })
        //val abstractStates = arrayListOf<AbstractState>(actionAbstractState)
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        abstractEdges.add(abstractEdge)
        //validate going to the same window
        abstractStates.forEach {
            val similarEdges = atuaMF.DSTG.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.prevWindow == abstractEdge.label.prevWindow
                        && it.label.interactions.isNotEmpty()
                        && it.label.isExplicit()
            }
            similarEdges.forEach {
               /* val similarEdgeCondition = autautMF.abstractTransitionGraph.edgeConditions[it]!!
                if (similarEdgeCondition.equals(edgeCondition)) {
                    abstractEdges.add(it)
                }*/
                abstractEdges.add(it)
            }
        }

        val distinctAbstractInteractions1 = abstractEdges.distinctBy { it.destination?.data?.window }
        if (distinctAbstractInteractions1.size > 1) {
            return false
        }
        abstractStates.clear()
        abstractEdges.clear()
        abstractStates.add(actionAbstractState)
        abstractStates.forEach {
            val similarExplicitEdges = atuaMF.DSTG.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.prevWindow == abstractEdge.label.prevWindow
                        && it.label.interactions.isNotEmpty()
                        && it.label.isExplicit()
            }
            similarExplicitEdges.forEach {
              /*  val similarEdgeCondition = it.label.userInputs
                if (similarEdgeCondition.equals(userInputs)) {
                    abstractEdges.add(it)
                }*/
                abstractEdges.add(it)
            }
        }

        val distinctAbstractInteractions2 = abstractEdges.distinctBy { it.destination?.data }
        if (distinctAbstractInteractions2.size > 1) {
           return false
        }

        return true
    }

    private fun getSimilarAbstractStates(abstractState: AbstractState, abstractTransition: AbstractTransition ): List<AbstractState> {
        val similarAbstractStates = ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == abstractState.window
                    && it != abstractState
                    && it.isOpeningKeyboard == abstractState.isOpeningKeyboard
                    && it.rotation == abstractState.rotation
        }
        if (!abstractTransition.abstractAction.isWidgetAction()) {
            return similarAbstractStates
        } else
            return similarAbstractStates.filter { it.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap) }
    }

    fun rebuildModel(window: Window, isRestore: Boolean, actionGUIState: State<*>, guiInteraction: Interaction<*>?) {
        if (isRestore) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
            val abstractEdge = atuaMF.DSTG.edges(actionAbstractState).find {
                it.label.interactions.contains(guiInteraction)
                        && !it.label.isImplicit
            }!!
            abstractEdges.add(abstractEdge)
            val oldAbstractStatesByWindow = HashMap<Window,ArrayList<AbstractState>>()
            oldAbstractStatesByWindow.put(actionAbstractState.window, arrayListOf(actionAbstractState))
            rebuildAbstractStates(oldAbstractStatesByWindow)
        } else {
            val affectedWindow = arrayListOf<Window>(window)
            //reset virtual abstract state
            val toRemoveVirtualAbstractState = ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window.classType == window.classType }
            toRemoveVirtualAbstractState.forEach {
                atuaMF.DSTG.removeVertex(it)
                it.attributeValuationMaps.clear()
                it.abstractTransitions.clear()
                initAbstractInteractions(it)
            }
            val oldAbstractStatesByWindow = HashMap<Window, ArrayList<AbstractState>>()
            affectedWindow.forEach { window ->
                AbstractStateManager.instance.attrValSetsFrequency.get(window)?.clear()
                val oldAbstractStates = ABSTRACT_STATES.filter {
                    it.window == window && it !is VirtualAbstractState
                            && it.guiStates.isNotEmpty()
                }
                oldAbstractStatesByWindow.put(window, ArrayList(oldAbstractStates))
            }
            rebuildAbstractStates(oldAbstractStatesByWindow)
        }
        //get all related abstract state

        val allAbstractStates = ABSTRACT_STATES
        val launchState = launchStates[LAUNCH_STATE.NORMAL_LAUNCH]
        if (launchState == null)
            throw Exception("Launch state is null")
        val resetState = launchStates[LAUNCH_STATE.RESET_LAUNCH]
        if (resetState == null)
            throw Exception ("Reset state is null")
        val launchAbstractState = getAbstractState(launchState!!)
        val resetAbstractState = getAbstractState(resetState!!)
        if (launchAbstractState == null)
            throw Exception ("Launch Abstract State is null")
        if (resetAbstractState == null)
            throw Exception ("Reset Abstract State is null")
        measureTimeMillis {
            allAbstractStates.forEach { abstractState ->
                updateOrCreateLaunchAppTransition(abstractState, launchAbstractState!!)
                updateOrCreateResetAppTransition(abstractState, resetAbstractState!!)
            }
        }.let {
            //log.debug("Recompute Launch interactions took $it millis")
        }

        PathFindingHelper.allAvailableTransitionPaths.forEach { t, u ->
            val transitionPaths = u.toList()
            transitionPaths.forEach { transitionPath ->
                if (!ABSTRACT_STATES.contains(transitionPath.root)) {
                    u.remove(transitionPath)
                }
                else if (transitionPath.path.values.map { it.dest }.any { !ABSTRACT_STATES.contains(it) }) {
                    u.remove(transitionPath)
                }
            }
        }

    }

    private fun rebuildAbstractStates(oldAbstractStatesByWindow: HashMap<Window,ArrayList<AbstractState>>) {
        val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
        oldAbstractStatesByWindow.forEach { window, oldAbstractStates ->
            val processedGuiState = HashSet<State<*>>()
            val derivedWidgets = AttributeValuationMap.allWidgetAVMHashMap[window.classType]!!
            val oldGuiStates = HashMap<AbstractState, List<State<*>>>()
            oldAbstractStates.forEach {
                oldGuiStates.put(it, ArrayList(it.guiStates))
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
                        val abstractState = getOrCreateNewAbstractState(guiState, oldAbstractState.activity, oldAbstractState.rotation, oldAbstractState.window,true)
                        //val abstractState = refineAbstractState(possibleAbstractStates, guiState, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                        atuaMF.abstractStateVisitCount.putIfAbsent(abstractState,0)
                        if (!newAbstractStates.contains(abstractState)) {
                            newAbstractStates.add(abstractState)
                            atuaMF.abstractStateVisitCount[abstractState] = 1
                        } else {
                            atuaMF.abstractStateVisitCount[abstractState] = atuaMF.abstractStateVisitCount[abstractState]!! + 1
                        }
                        if (!abstractState.guiStates.contains(guiState)) {
                            mapGuiStateToAbstractState(abstractState,guiState)
                        }
                    }
                    old_newAbstractStates.put(oldAbstractState, newAbstractStates)
                }
                val toRemoveAbstractStates = oldAbstractStates.filter { old ->
                    old_newAbstractStates.values.find { it.contains(old) } == null
                }
/*            toRemoveAbstractStates.forEach {
                ABSTRACT_STATES.remove(it)
                //it.abstractTransitions.removeIf { it.isImplicit}
            }*/

            }.let {
                log.debug("Recompute Abstract states took $it millis")
                log.debug("Get gui state took ${getGuiStateTime / 1000000} millis")
            }
        }


        var computeInteractionsTime: Long = 0
        var computeGuiStateTime: Long = 0

        val processedGUIInteractions = ArrayList<Interaction<Widget>>()
        val newEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val inEdgeMap = HashMap<AbstractState, HashSet<Edge<AbstractState, AbstractTransition>>>()
        old_newAbstractStates.keys.forEach { abstractState ->
            inEdgeMap.put(abstractState, HashSet())
        }
        atuaMF.DSTG.edges().forEach {
            if (inEdgeMap.containsKey(it.destination?.data)) {
                inEdgeMap[it.destination?.data]!!.add(it)
            }
        }

        //compute new abstract interactions
        val newAbstractTransitions = ArrayList<AbstractTransition>()
        measureTimeMillis {
            old_newAbstractStates.entries.forEach {
                val oldAbstractState = it.key
                val newAbstractStates = it.value
                // process out-edges

                val outAbstractEdges = atuaMF.DSTG.edges(oldAbstractState).toMutableList()
                outAbstractEdges.filter{it.label.isImplicit}.forEach {
                    atuaMF.DSTG.remove(it)
                    it.source.data.abstractTransitions.remove(it.label)
                }
                val inAbstractEdges = inEdgeMap[oldAbstractState]!!
                inAbstractEdges.filter{it.label.isImplicit}.forEach {
                    atuaMF.DSTG.remove(it)
                    it.source.data.abstractTransitions.remove(it.label)
                }

                val explicitOutAbstractEdges = outAbstractEdges.filter { !it.label.isImplicit }

                explicitOutAbstractEdges.forEach { oldAbstractEdge ->

                    if (oldAbstractEdge.label.abstractAction.actionType == AbstractActionType.RESET_APP) {
                        atuaMF.DSTG.remove(oldAbstractEdge)
                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                    } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                        // Remove the edge first
                        atuaMF.DSTG.remove(oldAbstractEdge)
                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)

                        val newDestinationAbstractStates = ArrayList<AbstractState>()
                        if (old_newAbstractStates.containsKey(oldAbstractEdge.destination?.data)) {
                            newDestinationAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.destination!!.data)!!)
                        } else {
                            newDestinationAbstractStates.add(oldAbstractEdge.destination!!.data)
                        }
                        newAbstractStates.forEach { source ->
                            newDestinationAbstractStates.forEach { dest ->
                                val newAbstractTransition = AbstractTransition(
                                        abstractAction = oldAbstractEdge.label.abstractAction,
                                        data = oldAbstractEdge.label.data,
                                        interactions = oldAbstractEdge.label.interactions,
                                        prevWindow = oldAbstractEdge.label.prevWindow,
                                        fromWTG = oldAbstractEdge.label.fromWTG,
                                        source = source,
                                        dest = dest,
                                        isImplicit = false
                                )
                                newAbstractTransition.copyPotentialInfo(oldAbstractEdge.label)
                                source.abstractTransitions.add(newAbstractTransition)
                                atuaMF.DSTG.add(source, dest, newAbstractTransition)
                                newAbstractTransitions.add(newAbstractTransition)
                            }
                        }

                    } else {
                        val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                        val interactions = oldAbstractEdge.label.interactions.toList()
                        interactions.forEach { interaction ->
                            if (processedGUIInteractions.contains(interaction)) {
                                //log.debug("Processed interaction in refining model")
                            } else {
                                processedGUIInteractions.add(interaction)
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
                                if (destinationAbstractState == null)
                                    throw Exception("Cannot find new resState's abstract state")
                                if (sourceAbstractState != oldAbstractEdge.label.source
                                        || destinationAbstractState != oldAbstractEdge.label.dest) {
                                    val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!, destState!!)

                                    if (newEdge != null) {
                                        newEdges.add(newEdge)
                                        if (oldAbstractEdge.label != newEdge.label) {
                                            oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                            atuaMF.DSTG.remove(oldAbstractEdge)
                                        }
                                        newAbstractTransitions.add(newEdge.label)
                                    }
                                } else {
                                    newAbstractTransitions.add(oldAbstractEdge.label)
                                }
                            }
                        }
                    }
                }

                // process in-edges

                val explicitInAbstractEdges = inAbstractEdges.filter { !it.label.isImplicit }

                explicitInAbstractEdges.forEach { oldAbstractEdge ->
                    if (oldAbstractEdge.label.abstractAction.actionType == AbstractActionType.RESET_APP) {
                        atuaMF.DSTG.remove(oldAbstractEdge)
                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                        // log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                    } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                        // Remove the edge first
                        atuaMF.DSTG.remove(oldAbstractEdge)
                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                        // Try to keep the edge
                        val newSourceAbstractStates = ArrayList<AbstractState>()
                        if (old_newAbstractStates.containsKey(oldAbstractEdge.source.data)) {
                            newSourceAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.source.data)!!)
                        } else {
                            newSourceAbstractStates.add(oldAbstractEdge.source!!.data)
                        }
                        newAbstractStates.forEach { dest ->
                            newSourceAbstractStates.forEach { source ->
                                val newAbstractTransition = AbstractTransition(
                                        abstractAction = oldAbstractEdge.label.abstractAction,
                                        data = oldAbstractEdge.label.data,
                                        interactions = oldAbstractEdge.label.interactions,
                                        prevWindow = oldAbstractEdge.label.prevWindow,
                                        fromWTG = oldAbstractEdge.label.fromWTG,
                                        source = source,
                                        dest = dest,
                                        isImplicit = false
                                )
                                newAbstractTransition.copyPotentialInfo(oldAbstractEdge.label)
                                source.abstractTransitions.add(newAbstractTransition)
                                atuaMF.DSTG.add(source, dest, newAbstractTransition)
                                newAbstractTransitions.add(newAbstractTransition)
                            }
                        }
                        //autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                    } else {
                        val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                        val interactions = oldAbstractEdge.label.interactions.toList()
                        interactions.filter {
                            val destStateId = it.resState
                            val desState = atuaMF.stateGraph!!.getVertices().find { it.data.stateId.equals(destStateId) }?.data
                            if (desState == null) {
                                true
                            } else {
                                val abstractState = getAbstractState(desState)
                                if (abstractState == null)
                                    true
                                else if (oldAbstractStatesByWindow.keys.contains(abstractState.window))
                                    true
                                else
                                    false
                            }
                        }.forEach { interaction ->
                            if (processedGUIInteractions.contains(interaction)) {
                                // log.debug("Processed interaction in refining model")
                            } else {
                                processedGUIInteractions.add(interaction)
                                var sourceState: State<*>? = null
                                var destState: State<*>? = null
                                sourceState = atuaMF.stateList.find { it.stateId == interaction.prevState }!!
                                destState = atuaMF.stateList.find { it.stateId == interaction.resState }!!

                                var destinationAbstractState = getAbstractState(destState!!)

                                if (destinationAbstractState == null) {
                                    throw Exception("Cannot find new resState' abstract state")
                                }

                                var sourceAbstractState = getAbstractState(sourceState!!)
                                if (sourceAbstractState == null)
                                    throw Exception("Cannot find new prevState' abstract state")

                                if (sourceAbstractState!=oldAbstractEdge.label.source ||
                                        destinationAbstractState!=oldAbstractEdge.label.dest) {
                                    //let create new interaction
                                    val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!, destState!!)
                                    if (newEdge!=null) {
                                        newAbstractTransitions.add(newEdge.label)
                                        newEdges.add(newEdge)
                                        if (oldAbstractEdge.label != newEdge.label) {
                                            oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                            atuaMF.DSTG.remove(oldAbstractEdge)
                                        }
                                    }
                                } else {
                                    newAbstractTransitions.add(oldAbstractEdge.label)
                                }
                            }
                        }
                    }
                }

                newAbstractStates.forEach {
                    updateLaunchAndResetAbstractTransitions(it)
                }
            }
        }
        val oldAbstractStates = ArrayList(oldAbstractStatesByWindow.values.flatten())
        oldAbstractStatesByWindow.forEach {
            it.value.forEach {
                if (it.guiStates.isEmpty() && !it.loadedFromModel) {
                    ABSTRACT_STATES.remove(it)
                    oldAbstractStates.remove(it)
                    atuaMF.DSTG.removeVertex(it)
                }
            }
        }
        ABSTRACT_STATES.forEach {
            it.abstractTransitions.removeIf {
                !ABSTRACT_STATES.contains(it.dest)
            }
        }
        newAbstractTransitions.distinct().forEach {abstractTransition->
            if (abstractTransition.interactions.isNotEmpty()) {
                abstractTransition.interactions.forEach { interaction ->
                    val resState = atuaMF.stateList.find { it.stateId ==  interaction.resState}
                    addImplicitAbstractInteraction(resState, abstractTransition = abstractTransition)
                }
            } else {
                addImplicitAbstractInteraction(null, abstractTransition = abstractTransition)
            }
        }
    }

    private fun updateOrCreateResetAppTransition(abstractState: AbstractState, resetAbstractState: AbstractState) {
        val resetInteractions = atuaMF.DSTG.edges(abstractState).filter {
            it.label.abstractAction.actionType == AbstractActionType.RESET_APP
        }
        if (resetInteractions.isNotEmpty()) {
            resetInteractions.forEach {
                atuaMF.DSTG.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }

        }
        val resetAction = AbstractAction(
                actionType = AbstractActionType.RESET_APP
        )
        val abstractInteraction = AbstractTransition(abstractAction = resetAction,
                isImplicit = true, prevWindow = null,source = abstractState,dest = resetAbstractState)
        atuaMF.DSTG.add(abstractState, resetAbstractState, abstractInteraction)
        abstractState.abstractTransitions.add(abstractInteraction)
    }

    private fun updateOrCreateLaunchAppTransition(abstractState: AbstractState, launchAbstractState: AbstractState) {
        val launchInteractions = atuaMF.DSTG.edges(abstractState).filter {
            it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP
                    && it.label.isImplicit
        }
        if (launchInteractions.isNotEmpty()) {
            launchInteractions.forEach {
                atuaMF.DSTG.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }

        }
        val launchAction = AbstractAction(
                actionType = AbstractActionType.LAUNCH_APP
        )

        val isImplicit = false
        val abstractInteraction = AbstractTransition(abstractAction = launchAction,
                isImplicit = isImplicit, prevWindow = null,source = abstractState,dest = launchAbstractState)
        abstractState.abstractTransitions.add(abstractInteraction)
        atuaMF.DSTG.add(abstractState, launchAbstractState, abstractInteraction)

    }

    private fun updateAbstractTransition(oldAbstractEdge: Edge<AbstractState, AbstractTransition>
                                         , isTarget: Boolean
                                         , sourceAbstractState: AbstractState
                                         , destinationAbstractState: AbstractState
                                         , interaction: Interaction<*>
                                         , sourceState: State<*>
                                         , destState: State<*>): Edge<AbstractState, AbstractTransition>? {
        //Extract text input widget data
        var newAbstractionTransition: AbstractTransition?=null
        var newEdge: Edge<AbstractState, AbstractTransition>? = null
        val condition = HashMap(Helper.extractInputFieldAndCheckableWidget(sourceState))
       /* val prevWindowAbstractState: AbstractState?
        val prevWindow = oldAbstractEdge.label.prevWindow
        val trace = atuaMF.interactionsTracing.get(listOf(interaction))
        if (trace!=null && prevWindow !is Launcher && sourceAbstractState.window != destinationAbstractState.window) {
            val prevInteractions = atuaMF.interactionsTracing.filter {
                it.value.first == trace.first
                        && it.value.second < trace.second
            }.map { it.key }.associateBy { atuaMF.interactionsTracing[it]!!}
            val prevGUIStates = prevInteractions.map { it.key to atuaMF.stateList.find{state-> state.stateId == it.value.first().prevState}!! }.toMap()
            val prevAbstractStates = prevGUIStates.map { it.key to getAbstractState(it.value) }.toMap()
            prevWindowAbstractState = prevAbstractStates.entries.sortedByDescending { it.key.second }.find { it.value?.window == prevWindow }?.value
        } else {
            prevWindowAbstractState = null
        }*/
        if (oldAbstractEdge.label.abstractAction.attributeValuationMap == null) {
            //Reuse Abstract action
            val abstractAction = oldAbstractEdge.label.abstractAction
            if (isTarget) {
                sourceAbstractState.targetActions.add(abstractAction)
            }


    /*        //Update launch destination
            when (abstractAction.actionName) {
                "LaunchApp" -x> {
                    launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]=guiEdge.destination?.data
                }
                "ResetApp" -> {
                    launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]=guiEdge.destination?.data
                }
            }*/

            //check if the interaction was created
            val existingAbstractEdge = atuaMF.DSTG.edges(sourceAbstractState, destinationAbstractState)
                    .find {
                        it.label.abstractAction == abstractAction
                                && it.label.data == oldAbstractEdge.label.data
                                && it.label.prevWindow == oldAbstractEdge.label.prevWindow
                                && it.label.isImplicit == false
                    }
            if (existingAbstractEdge == null) {
                //Create explicit edge for linked abstractState
                newAbstractionTransition = AbstractTransition(
                        abstractAction = abstractAction,
                        isImplicit = false,
                        prevWindow = oldAbstractEdge.label.prevWindow,
                        data = oldAbstractEdge.label.data,
                        source = sourceAbstractState,
                        dest = destinationAbstractState
                )
                newAbstractionTransition.interactions.add(interaction)
                val tracing = atuaMF.interactionsTracing.get(listOf(interaction))
                if (tracing != null) {
                    newAbstractionTransition.tracing.add(tracing)
                }
                newEdge = atuaMF.DSTG.add(sourceAbstractState, destinationAbstractState, newAbstractionTransition)
                if(!newEdge.label.userInputs.contains(condition))
                    newEdge.label.userInputs.add(condition)
               // addImplicitAbstractInteraction(destState,sourceAbstractState, destinationAbstractState, newAbstractionTransition, newAbstractionTransition.prevWindow,condition)

                //Create implicit edges for other abstractState
                /*val implicitAbstractInteraction = AbstractInteraction(
                                                abstractAction = abstractAction,
                                                isImplicit = true,
                                                prevWindow = oldAbstractEdge.label.prevWindow,
                                                data = oldAbstractEdge.label.data
                                        )
                                        val otherAbstractStates = newAbstractStates.filterNot { it == sourceAbstractState }
                                        otherAbstractStates.forEach {
                                            autautMF.abstractTransitionGraph.add(it, destinationAbstractState, implicitAbstractInteraction)
                                            it.increaseActionCount(abstractAction)

                                        }*/
            } else {
                newEdge = existingAbstractEdge
                newAbstractionTransition = existingAbstractEdge.label
                existingAbstractEdge.label.interactions.add(interaction)
                val tracing = atuaMF.interactionsTracing.get(listOf(interaction))
                if (tracing != null) {
                    newAbstractionTransition.tracing.add(tracing)
                }
            }
            sourceAbstractState.increaseActionCount2(abstractAction,true)
        } else {
            //get widgetgroup
            var newAttributeValuationSet = sourceAbstractState.attributeValuationMaps.find { it.isAbstractRepresentationOf(interaction.targetWidget!!, sourceState,false) }
/*            if (newAttributeValuationSet == null) {
                newAttributeValuationSet = oldAbstractEdge.label.abstractAction.attributeValuationSet
            }*/
            if (newAttributeValuationSet == null) {
                val newAttributePath = AbstractionFunction.INSTANCE.reduce(interaction.targetWidget!!, sourceState, sourceAbstractState.window.classType,sourceAbstractState.rotation,atuaMF, HashMap(), HashMap())
                newAttributeValuationSet = sourceAbstractState.attributeValuationMaps.find { it.haveTheSameAttributePath(newAttributePath) }
              /*  newAttributeValuationSet = AttributeValuationSet(newAttributePath, Cardinality.ONE, sourceAbstractState.activity, HashMap())
                activity_widget_AttributeValuationSetHashMap[sourceAbstractState.activity]!!.put(interaction.targetWidget!!,newAttributeValuationSet)*/


                //newWidgetGroup.guiWidgets.add(interaction.targetWidget!!)
                //sourceAbstractState.addWidgetGroup(newWidgetGroup)
            }
            if (newAttributeValuationSet != null) {
                val abstractAction = AbstractAction(
                        actionType = oldAbstractEdge.label.abstractAction.actionType,
                        attributeValuationMap = newAttributeValuationSet,
                        extra = oldAbstractEdge.label.abstractAction.extra
                )
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
                val exisitingAbstractEdge = atuaMF.DSTG.edges(sourceAbstractState, destinationAbstractState).find {
                    it.label.abstractAction == abstractAction
                            && it.label.data == oldAbstractEdge.label.data
                            && it.label.prevWindow == oldAbstractEdge.label.prevWindow
                            && it.label.isImplicit == false
                }
                if (exisitingAbstractEdge != null) {
                    newEdge = exisitingAbstractEdge
                    newAbstractionTransition = exisitingAbstractEdge.label
                    exisitingAbstractEdge.label.interactions.add(interaction)
                    val tracing = atuaMF.interactionsTracing.get(listOf(interaction))
                    if (tracing != null) {
                        newAbstractionTransition.tracing.add(tracing)
                    }
                } else {
                    //Create explicit edge for linked abstractState
                    newAbstractionTransition = AbstractTransition(
                            abstractAction = abstractAction,
                            isImplicit = false,
                            prevWindow = oldAbstractEdge.label.prevWindow,
                            data = oldAbstractEdge.label.data,
                            source = sourceAbstractState,
                            dest = destinationAbstractState
                    )

                    newAbstractionTransition.interactions.add(interaction)
                    newAbstractionTransition.handlers.putAll(oldAbstractEdge.label.handlers)
                    val tracing = atuaMF.interactionsTracing.get(listOf(interaction))
                    if (tracing != null) {
                        newAbstractionTransition.tracing.add(tracing)
                    }
                    newEdge = atuaMF.DSTG.add(
                            sourceAbstractState,
                            destinationAbstractState,
                            newAbstractionTransition
                    )
                    if(!newEdge.label.userInputs.contains(condition))
                        newEdge.label.userInputs.add(condition)

                }
                sourceAbstractState.increaseActionCount2(abstractAction,true)

            }

        }
        if (newAbstractionTransition!=null) {
            // update coverage
            if (atuaMF.guiInteractionCoverage.containsKey(interaction)) {
                val interactionCoverage = atuaMF.guiInteractionCoverage.get(interaction)!!
                interactionCoverage.forEach {
                    newAbstractionTransition.updateUpdateStatementCoverage(it, atuaMF)
                }
            }
            addImplicitAbstractInteraction(destState,newAbstractionTransition)
        }
        return newEdge
    }


    val widget_StaticWidget = HashMap<Window,HashMap<ConcreteId,ArrayList<EWTGWidget>>>()

    private fun ewtg_avm_matching(widget_AttributeValuationMapHashMap: HashMap<Widget, AttributeValuationMap>, guiState: State<*>, wtgWindow: Window): HashMap<AttributeValuationMap, EWTGWidget> {
        val result: HashMap<AttributeValuationMap, EWTGWidget> = HashMap()
        val actionableWidgets = ArrayList<Widget>()
        actionableWidgets.addAll(Helper.getVisibleWidgetsForAbstraction(guiState))
        val unmappedWidgets = actionableWidgets

        val avm_widgets_map = unmappedWidgets.groupBy { widget_AttributeValuationMapHashMap[it] }
                .filter { it.key != null }

        val workingList: LinkedList<AttributeValuationMap> = LinkedList<AttributeValuationMap>()
        avm_widgets_map.keys. forEach {
            workingList.add(it!!)
        }

        val avmId_ewtgWidgets = HashMap<String, EWTGWidget>()
        while (workingList.isNotEmpty()) {
            val avm = workingList.first
            if (avm.parentAttributeValuationMapId!= "" && !avmId_ewtgWidgets.containsKey(avm.parentAttributeValuationMapId)) {
                val parentAVM = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[wtgWindow.classType]!!.get(avm.parentAttributeValuationMapId)
                workingList.addFirst(parentAVM)
                continue
            }
            workingList.removeFirst()
            //avmId_ewtgWidgets.putIfAbsent(avm.avsId, ArrayList())
            if (!result.containsKey(avm)) {
              /*  val staticWidgets =  if (avm_widgets_map.containsKey(avm)) {
                    Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                } else {
                    Helper.getStaticWidgets(avm, wtgWindow, false, avmId_ewtgWidgets)
                }*/

                val staticWidgets =  Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                //if a widgetGroup has more
                if (staticWidgets != null) {
                    result.put(avm, staticWidgets)
                }
            }
        }

     /*   avm_widgets_map.forEach {
                    val attributeValuationSet = it.key!!

                    it.value.forEach { w ->
                        if (!result.containsKey(attributeValuationSet)) {
                            if (mappedStaticWidgets.containsKey(w.id)) {
                                result.put(it.key!!, ArrayList(mappedStaticWidgets.get(w.id)!!))
                            } else {
                                val staticWidgets = Helper.getStaticWidgets(w, guiState, attributeValuationSet, wtgWindow, true, autautMF,avm_ewtgWidgets)
                                //if a widgetGroup has more
                                if (staticWidgets.isNotEmpty()) {
                                    mappedStaticWidgets.put(w.id, ArrayList(staticWidgets))
                                    result.put(attributeValuationSet, ArrayList(staticWidgets))
                                }
                            }
                        }
                    }
                }*/
        return result
    }

    fun addImplicitAbstractInteraction(currentState: State<*>?, abstractTransition: AbstractTransition) {
        //AutAutMF.log.debug("Add implicit abstract interaction")
        var addedCount = 0
        var processedStateCount = 0
        // add implicit back events
        addedCount = 0
        processedStateCount = 0
        val currentAbstractState = abstractTransition.dest
        val prevAbstractState = abstractTransition.source
        val prevWindow = abstractTransition.prevWindow
        if (prevWindow != null && currentState!=null
                && abstractTransition.abstractAction.actionType != AbstractActionType.PRESS_BACK
                && !abstractTransition.abstractAction.isLaunchOrReset()) {
            val implicitBackWindow = computeImplicitBackWindow(currentAbstractState, prevAbstractState, prevWindow)

            //We don't need create implicit back transition in case state unchanged
            if (prevAbstractState != currentAbstractState
                    && implicitBackWindow!=null) {
                val pair = createImplicitPressBackTransition(currentAbstractState, implicitBackWindow, currentState, implicitBackWindow, processedStateCount, prevAbstractState, addedCount)
                addedCount = pair.first
                processedStateCount = pair.second
            }
        }
        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")

        if (abstractTransition.abstractAction.actionType == AbstractActionType.SWIPE
                && abstractTransition.abstractAction.attributeValuationMap != null
                && prevAbstractState != currentAbstractState
        ) {
            //check if the swipe action changed the content
            if (currentAbstractState.attributeValuationMaps.contains(abstractTransition.abstractAction.attributeValuationMap)) {
                val currentWidgetGroup = currentAbstractState.attributeValuationMaps.find { it == abstractTransition.abstractAction.attributeValuationMap }!!
                if (!currentWidgetGroup.havingSameContent(currentAbstractState, abstractTransition.abstractAction.attributeValuationMap!!, prevAbstractState)) {
                    //add implicit sysmetric action
                    createImplictiInverseSwipeTransition(abstractTransition, currentAbstractState, abstractTransition.prevWindow, prevAbstractState)
                }
            }
        }

        if (abstractTransition.abstractAction.actionType == AbstractActionType.ROTATE_UI
                && prevAbstractState != currentAbstractState) {
            createImplicitInverseRotationTransition(currentAbstractState, abstractTransition.prevWindow, prevAbstractState)
        }

        if (abstractTransition.abstractAction.actionType == AbstractActionType.ENABLE_DATA
                || abstractTransition.abstractAction.actionType == AbstractActionType.DISABLE_DATA
        ) {
            return
        }

        /*if (abstractInteraction.abstractAction.actionName == "CloseKeyboard" || abstractInteraction.abstractAction.actionName.isPressBack()) {
            return
        }*/
/*        if (isSwipeScreenGoToAnotherWindow(abstractInteraction.abstractAction,currentAbstractState, prevAbstractState)) {
            return
        }*/
        //add to virtualAbstractState
        val isTargetAction = prevAbstractState.targetActions.contains(abstractTransition.abstractAction)

        val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == prevAbstractState.window }.firstOrNull()


     if (virtualAbstractState != null
                && !abstractTransition.abstractAction.isLaunchOrReset()
             && abstractTransition.abstractAction.actionType != AbstractActionType.PRESS_BACK) {
            createImplicitTransitionForVirtualAbstractState(abstractTransition, virtualAbstractState)
        }

        //do not add implicit transition if this is Launch/Reset/Swipe
       if (!abstractTransition.abstractAction.isLaunchOrReset()) {
            createImplicitTransitionForOtherAbstractStates(prevAbstractState, processedStateCount, abstractTransition, currentAbstractState, prevWindow, isTargetAction, addedCount)
        }

        //update ResetApp && LaunchApp edge
        if (abstractTransition.abstractAction.isLaunchOrReset()) {
            updateResetAndLaunchTransitions(abstractTransition, currentAbstractState)
        }

        //process implicit item action
        val action = abstractTransition.abstractAction.actionType
        if (abstractTransition.abstractAction.isWidgetAction()
                && (action == AbstractActionType.CLICK || action == AbstractActionType.LONGCLICK)
                ) {
            inferItemActionTransitions(action, abstractTransition, prevAbstractState, currentAbstractState)
        }

        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
        //AutAutMF.log.debug("Add implicit back interaction.")

    }

    private fun computeImplicitBackWindow(currentAbstractState: AbstractState, prevAbstractState: AbstractState, prevWindow: Window?): Window? {
        val implicitBackWindow = if (currentAbstractState.isOpeningKeyboard) {
            currentAbstractState.window
        } else if (prevAbstractState.window == currentAbstractState.window) {
            prevWindow
        } else if (currentAbstractState.window == prevWindow) {
            null
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
            val parentAVS = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[prevAbstractState.window.classType]!!.get(parentAttributeValuationSetId)
            if (parentAVS != null) {
                if (prevAbstractState.attributeValuationMaps.contains(parentAVS)) {
                    val itemAbtractAction = AbstractAction(
                            actionType = itemAction,
                            attributeValuationMap = parentAVS
                    )
                    if (parentAVS.actionCount.containsKey(itemAbtractAction)) {
                        prevAbstractState.increaseActionCount2(itemAbtractAction,false)
                        var implicitInteraction =
                                atuaMF.DSTG.edges(prevAbstractState).find {
                                    it.label.isImplicit == true
                                            && it.label.abstractAction == itemAbtractAction
                                            && it.label.prevWindow == abstractTransition.prevWindow
                                            && it.destination?.data == currentAbstractState
                                }?.label
                        if (implicitInteraction == null) {
                            // create new explicit interaction
                            implicitInteraction = AbstractTransition(
                                    abstractAction = itemAbtractAction,
                                    isImplicit = true,
                                    prevWindow = abstractTransition.prevWindow,
                                    source = prevAbstractState,
                                    dest = currentAbstractState
                            )
                            atuaMF.DSTG.add(prevAbstractState, currentAbstractState, implicitInteraction)
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

    private fun updateResetAndLaunchTransitions(abstractTransition: AbstractTransition, currentAbstractState: AbstractState) {
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
    }

    private fun createImplicitTransitionForOtherAbstractStates(prevAbstractState: AbstractState, processedStateCount: Int, abstractTransition: AbstractTransition, currentAbstractState: AbstractState, prevprevWindow: Window?, isTargetAction: Boolean, addedCount: Int) {
        var processedStateCount1 = processedStateCount
        if (abstractTransition.source.window == abstractTransition.dest.window)
            return
        val destVirtualAbstractState = ABSTRACT_STATES.find { it is VirtualAbstractState && it.window == abstractTransition.dest.window }
        if (destVirtualAbstractState == null)
            return

        val otherSameStaticNodeAbStates = getSimilarAbstractStates(prevAbstractState, abstractTransition)

        addImplicitAbstractTransitionToSameWindowAbstractStates(otherSameStaticNodeAbStates, processedStateCount1, abstractTransition, destVirtualAbstractState)
        addImplicitAbstractTransitionToSameWindowAbstractStates(otherSameStaticNodeAbStates, processedStateCount1, abstractTransition, abstractTransition.dest)
    }


    private fun addImplicitAbstractTransitionToSameWindowAbstractStates(otherSameStaticNodeAbStates: List<AbstractState>, processedStateCount1: Int, abstractTransition: AbstractTransition, destVirtualAbstractState: AbstractState) {
        var processedStateCount11 = processedStateCount1
        otherSameStaticNodeAbStates.forEach {
            processedStateCount11 += 1
            var implicitAbstractTransition: AbstractTransition?
            implicitAbstractTransition = it.abstractTransitions.find {
                it.abstractAction == abstractTransition.abstractAction
                        && it.dest == destVirtualAbstractState
                        && it.prevWindow == abstractTransition.prevWindow
                        && it.data == abstractTransition.data
            }
            if (implicitAbstractTransition == null) {
                implicitAbstractTransition = AbstractTransition(
                        abstractAction = abstractTransition.abstractAction,
                        source = it,
                        dest = destVirtualAbstractState,
                        prevWindow = abstractTransition.prevWindow,
                        data = abstractTransition.data,
                        isImplicit = true
                )
            }
            it.abstractTransitions.add(implicitAbstractTransition)
            atuaMF.DSTG.add(it, destVirtualAbstractState, implicitAbstractTransition)
        }
    }

    private fun createImplicitTransitionForVirtualAbstractState(abstractTransition: AbstractTransition, virtualAbstractState: AbstractState) {
        val abstractAction = abstractTransition.abstractAction
        if (abstractTransition.source.window is Dialog && abstractTransition.dest.window is Activity
                && abstractTransition.source.activity == abstractTransition.dest.activity)
            // for the transitions go from a dialog back to an activity
            // we should not create implict transition
            return
/*        if (abstractTransition.source.window == abstractTransition.dest.window)
            return*/
        if (abstractAction.isWidgetAction() && !virtualAbstractState.attributeValuationMaps.contains(abstractAction.attributeValuationMap!!)) {
            virtualAbstractState.attributeValuationMaps.add(abstractAction.attributeValuationMap!!)
        }
        /*val  destVirtualAbstractState = ABSTRACT_STATES.find { it is VirtualAbstractState && it.window == abstractTransition.dest.window}
        if (destVirtualAbstractState == null)
            return*/

        val destVirtualAbstractState = abstractTransition.dest
        val changeEffects: List<ChangeEffect> = computeChanges(abstractTransition)
        val existingVirtualTransitionn =  virtualAbstractState.abstractTransitions.find {
            it.abstractAction == abstractAction
                    && it.dest == destVirtualAbstractState
                    && it.prevWindow == abstractTransition.prevWindow
                    && it.data  == abstractTransition.data
        }
        if (existingVirtualTransitionn == null) {
            val newVirtualTransition = AbstractTransition(
                    abstractAction = abstractAction,
                    source = virtualAbstractState,
                    dest = destVirtualAbstractState,
                    prevWindow = abstractTransition.prevWindow,
                    data = abstractTransition.data,
                    isImplicit = true
            )
            newVirtualTransition.changeEffects.addAll(changeEffects)
            virtualAbstractState.abstractTransitions.add(newVirtualTransition)
            atuaMF.DSTG.add(virtualAbstractState,destVirtualAbstractState,newVirtualTransition)
        } else {
            existingVirtualTransitionn.changeEffects.addAll(changeEffects)
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
            val changeResult = ChangeEffect(AffectElementType.Rotation, null,true)
            return listOf(changeResult)
        } else {
            val changeResult = ChangeEffect(AffectElementType.Rotation,null, false)
            return listOf(changeResult)
        }
        return emptyList()
    }

    private fun createImplicitInverseRotationTransition(currentAbstractState: AbstractState, implicitBackWindow: Window?, prevAbstractState: AbstractState) {
        val inverseAbstractAction = currentAbstractState.getAvailableActions().find {
            it.actionType == AbstractActionType.ROTATE_UI
        }
        if (inverseAbstractAction != null) {
            val inverseAbstractInteraction = AbstractTransition(
                    abstractAction = inverseAbstractAction,
                    prevWindow = implicitBackWindow,
                    isImplicit = true,
                    source = currentAbstractState,
                    dest = prevAbstractState
            )
            atuaMF.DSTG.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            //currentAbstractState.increaseActionCount(inverseAbstractAction)
        }
    }

    private fun createImplictiInverseSwipeTransition(abstractTransition: AbstractTransition, currentAbstractState: AbstractState, implicitBackWindow: Window?, prevAbstractState: AbstractState) {
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
                    prevWindow = implicitBackWindow,
                    isImplicit = true,
                    source = currentAbstractState,
                    dest = prevAbstractState
            )
            atuaMF.DSTG.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            currentAbstractState.increaseActionCount2(inverseAbstractAction,false)
        }
    }

    private fun createImplicitPressBackTransition(currentAbstractState: AbstractState, prevprevWindow: Window?, currentState: State<*>, implicitBackWindow: Window?, processedStateCount: Int, prevAbstractState: AbstractState, addedCount: Int): Pair<Int, Int> {
        var processedStateCount1 = processedStateCount
        var addedCount1 = addedCount
        if (prevAbstractState == currentAbstractState) {
            return Pair(addedCount1, processedStateCount1)
        }
        val backAbstractAction = AbstractAction(actionType = AbstractActionType.PRESS_BACK,
                attributeValuationMap = null)
        //check if there is any pressback action go to another window
        val sameAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == currentAbstractState.window
        }
        var isBackToOtherWindow = false
        for (abstractState in sameAbstractStates) {
            if (abstractState.abstractTransitions.any {
                        it.abstractAction == backAbstractAction
                                && it.prevWindow == prevprevWindow
                                && it.dest.window != implicitBackWindow
                                && !it.isImplicit
                    }) {
                isBackToOtherWindow = true
                break
            }
        }
        if (!isBackToOtherWindow) {
            val backAbstractStates = ArrayList<AbstractState>()
            val lastIndexOfCurrentState = atuaMF.stateList.indexOfLast {
                it == currentState
            }
            var backAbstractState: AbstractState? = null
            atuaMF.stateList.forEachIndexed { index, guiState ->
                val abstractState = guiState_AbstractState_Map[guiState.stateId]!!
                if (index < lastIndexOfCurrentState
                        && abstractState.window == implicitBackWindow
                        && !abstractState.isOpeningKeyboard) {
                    backAbstractState = abstractState
                }
            }

            if (backAbstractState!= null) {
                if (!currentAbstractState.abstractTransitions.any {
                            it.abstractAction == backAbstractAction
                                    && it.prevWindow == prevprevWindow
                                    && it.dest == backAbstractState
                        }) {
                    val backAbstractInteraction = AbstractTransition(
                            abstractAction = backAbstractAction,
                            isImplicit = true,
                            prevWindow = prevprevWindow,
                            source = currentAbstractState,
                            dest = backAbstractState!!
                    )
                    atuaMF.DSTG.add(currentAbstractState, backAbstractState, backAbstractInteraction)
                    currentAbstractState.abstractTransitions.add(backAbstractInteraction)
                    // add edge condition
                    addedCount1 += 1
                }

            }
        }

        return Pair(addedCount1, processedStateCount1)
    }

    private fun updateLaunchTransitions(abstractState: AbstractState, launchAppAction: AbstractAction, currentAbstractState: AbstractState, abstractTransition: AbstractTransition) {
        val existingEdges = atuaMF.DSTG.edges(abstractState).filter {
            it.label.abstractAction == launchAppAction
                    && (( launchAppAction.actionType==AbstractActionType.LAUNCH_APP)
                    || (launchAppAction.actionType == AbstractActionType.RESET_APP))
        }
        if (existingEdges.isNotEmpty()) {
            existingEdges.forEach {
                atuaMF.DSTG.remove(it)
                it.source.data.abstractTransitions.remove(it.label)
            }
        }
        var implicitAbstractInteraction = AbstractTransition(
                abstractAction = launchAppAction,
                isImplicit = true,
                data = abstractTransition.data,
                prevWindow = null,
                source = abstractState,
                dest = currentAbstractState
        )
        atuaMF.DSTG.add(abstractState, currentAbstractState, implicitAbstractInteraction)
    }

    // This should not be implicit added to another abstract states
    private fun isSwipeScreenGoToAnotherWindow(abstractAction: AbstractAction, currentAbstractState: AbstractState, prevAbstractState: AbstractState) =
            (abstractAction.actionType == AbstractActionType.SWIPE && abstractAction.attributeValuationMap == null
                    && currentAbstractState.window != prevAbstractState.window)

    private fun getOrCreateImplicitAbstractInteraction(abstractTransition: AbstractTransition, sourceAbstractState: AbstractState, destinationAbstractState: AbstractState): AbstractTransition? {
        var implicitAbstractTransition: AbstractTransition?

        if (abstractTransition.abstractAction.attributeValuationMap == null) {
            //find existing interaction again
            val existingEdge = atuaMF.DSTG.edges(sourceAbstractState).find {
                it.label.abstractAction == abstractTransition.abstractAction
                        && it.label.prevWindow == abstractTransition.prevWindow
                        && it.label.data == abstractTransition.data
                        && it.destination?.data == destinationAbstractState
            }
            if (existingEdge!=null) {
                return null
            }
            implicitAbstractTransition =
                AbstractTransition(
                        abstractAction = abstractTransition.abstractAction,
                        isImplicit = true,
                        data = abstractTransition.data,
                        prevWindow = abstractTransition.prevWindow,
                        source = sourceAbstractState,
                        dest = destinationAbstractState
                )

        } else {
            //find Widgetgroup
            val widgetGroup = sourceAbstractState.attributeValuationMaps.find {
               it.equals(abstractTransition.abstractAction.attributeValuationMap)
            }
            if (widgetGroup != null) {
                //find existing interaction again
                val existingEdge = atuaMF.DSTG.edges(sourceAbstractState).filter {
                    it.label.abstractAction.equals(abstractTransition.abstractAction)
                            && it.label.prevWindow == abstractTransition.prevWindow
                            && it.label.data == abstractTransition.data
                            && destinationAbstractState == it.destination?.data
                }
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
                            prevWindow = abstractTransition.prevWindow,
                            source = sourceAbstractState,
                            dest = destinationAbstractState
                    )
            } else {
                implicitAbstractTransition = null
            }
            if (implicitAbstractTransition!=null) {
                sourceAbstractState.increaseActionCount2(implicitAbstractTransition.abstractAction,false)
            }
        }
        return implicitAbstractTransition
    }
    fun getPotentialAbstractStates(): List<AbstractState> {
        return ABSTRACT_STATES.filterNot { it is VirtualAbstractState
                || it.window is Launcher
                || it.window is OutOfApp
                || it.attributeValuationMaps.isEmpty()
        }
    }

    fun dump(dstgFolder: Path) {
        val resetAbstractState = getAbstractState(launchStates.get(LAUNCH_STATE.RESET_LAUNCH)!!)!!
        File(dstgFolder.resolve("AbstractStateList.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            ABSTRACT_STATES.filter{ it !is VirtualAbstractState && it.guiStates.isNotEmpty()}.forEach {
                all.newLine()
                val abstractStateInfo = "${it.abstractStateId};${it.activity};${it.window.windowId};${it.rotation};${it.isMenusOpened};" +
                        "${it.isHomeScreen};${it.isRequestRuntimePermissionDialogBox};${it.isAppHasStoppedDialogBox};" +
                        "${it.isOutOfApplication};${it.isOpeningKeyboard};${it.hasOptionsMenu};\"${it.guiStates.map { it.stateId }.joinToString(separator = ";")}\";${it.hashCode};${it==resetAbstractState};${it.modelVersion}"
                all.write(abstractStateInfo)
            }
        }

        val abstractStatesFolder =dstgFolder.resolve("AbstractStates")
        Files.createDirectory(abstractStatesFolder)
        ABSTRACT_STATES.filter{ it !is VirtualAbstractState}.forEach {
            it.dump(abstractStatesFolder)
        }
    }

    private fun header(): String {
        return "[1]abstractStateID;[2]activity;[3]window;[4]rotation;[5]menuOpen;" +
                "[6]isHomeScreen;[7]isRequestRuntimePermissionDialogBox;[8]isAppHasStoppedDialogBox;" +
                "[9]isOutOfApplication;[10]isOpeningKeyboard;[11]hasOptionsMenu;[12]guiStates;[13]hashcode;[14]isInitialState;[15]modelVersion;"
    }

    companion object {
        val instance: AbstractStateManager by lazy {
            AbstractStateManager()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}