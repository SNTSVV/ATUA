package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.autaut.WTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.ContextMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.emptyUUID
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
    val launchAbstractStates: HashMap<LAUNCH_STATE, State<*>> = HashMap()
    lateinit var appResetState: AbstractState
    lateinit var autautMF: AutAutMF
    lateinit var appName: String
    val attrValSetsFrequency = HashMap<Window, HashMap<AttributeValuationSet, Int>>()
    val activity_widget_AttributeValuationSetHashMap = HashMap<String, HashMap<Widget,AttributeValuationSet>>()
    val activity_attrValSetsMap = HashMap<String, ArrayList<AttributeValuationSet>>()

    fun init(regressionTestingMF: AutAutMF, appPackageName: String) {
        this.autautMF = regressionTestingMF
        this.appName = appPackageName

        //create initial abstract state (after App reset)
        appResetState = AppResetAbstractState()

        regressionTestingMF.abstractTransitionGraph = AbstractTransitionGraph()

        WindowManager.instance.allWindows.forEach {
            val virtualAbstractState = VirtualAbstractState(it.activityClass, it, it is Launcher)
            ABSTRACT_STATES.add(virtualAbstractState)
            // regressionTestingMF.abstractStateVisitCount[virtualAbstractState] = 0
        }

        ABSTRACT_STATES.filter { it is VirtualAbstractState }.forEach {
            initAbstractInteractions(it,null)
        }
    }

    fun createVirtualAbstractState(window: Window) {
        val virtualAbstractState = VirtualAbstractState(window.activityClass, window, false)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState,null)
        updateLaunchAndResetAbstractTransitions(virtualAbstractState)
    }

    fun getOrCreateNewAbstractState(guiState: State<*>,
                                    i_activity: String,
                                    rotation: Rotation,
                                    internet: Boolean,
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
        var internetStatus = when (internet) {
            true -> InternetStatus.Enable
            false -> InternetStatus.Disable
        }
        if (guiState.isHomeScreen) {
            var homeState = ABSTRACT_STATES.find { it.isHomeScreen }
            if (homeState != null) {
                abstractState = homeState
                if (!homeState.guiStates.contains(guiState)) {
                    mapGuiStateToAbstractState(homeState,guiState)
                }
            } else {
                abstractState = AbstractState(activity = i_activity,
                        isHomeScreen = true,
                        window = Launcher.instance!!,
                        rotation = Rotation.PORTRAIT,
                        internet = internetStatus)
                if (Launcher.instance!!.activityClass.isBlank()) {
                    Launcher.instance!!.activityClass = activity
                }
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
                        window = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity),
                        rotation = rotation,
                        internet = internetStatus)
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
                var widget_WidgetGroupHashMap = HashMap<Widget, AttributeValuationSet>()
                measureNanoTime {
                    widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity, autautMF.packageName,rotation,autautMF)
                }.let {
                    time1 = it
                }
                if (!activity_widget_AttributeValuationSetHashMap.containsKey(activity)) {
                    activity_widget_AttributeValuationSetHashMap.put(activity, HashMap())
                }
                val widget_AttributeValuationSetHashMap = activity_widget_AttributeValuationSetHashMap[activity]!!
                widget_WidgetGroupHashMap.forEach {
                    widget_AttributeValuationSetHashMap.put(it.key,it.value)
                }
                TextInput.saveSpecificTextInputData(guiState)
                val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
                val matchingTestState = findAbstractState(ABSTRACT_STATES, guiReducedWidgetGroup, activity, rotation, isOpeningKeyboard, internetStatus)
                if (matchingTestState != null) {
                    if (!matchingTestState.guiStates.contains(guiState)) {
                        mapGuiStateToAbstractState(matchingTestState, guiState)
                    }
                    return matchingTestState
                }
                var staticMapping = Pair<Window, HashMap<AttributeValuationSet, ArrayList<EWTGWidget>>>(first = Launcher.instance!!, second = HashMap())
                measureNanoTime {
                    staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, activity, rotation,window)
                    if (staticMapping.first.activityClass.isBlank()) {
                        staticMapping.first.activityClass = activity
                    }
                }.let {
                    time3 = it
                }
                measureNanoTime {
                    val ambigousWidgetGroup = staticMapping.second.filter {
                        it.value.size > 1
                                //In certain cases, static analysis distinguishes same resourceId widgets based on unknown criteria.
                                && !havingSameResourceId(it.value)
                    }
                    if (ambigousWidgetGroup.isEmpty() || ambigousWidgetGroup.isNotEmpty()) {
                        //create new TestState
                        abstractState = AbstractState(activity = activity,
                                attributeValuationSets = ArrayList(guiReducedWidgetGroup),
                                isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                                isOpeningKeyboard = isOpeningKeyboard,
                                EWTGWidgetMapping = staticMapping.second,
                                window = staticMapping.first,
                                rotation = autautMF.currentRotation,
                                isOutOfApplication = staticMapping.first is OutOfApp,
                                internet = internetStatus)
                        if (abstractState!!.window is Dialog || abstractState!!.window is OptionsMenu || abstractState!!.window is ContextMenu) {
                            abstractState!!.hasOptionsMenu = false
                        }
                        ABSTRACT_STATES.add(abstractState!!)
                        mapGuiStateToAbstractState(abstractState!!,guiState)
                        initAbstractInteractions(abstractState!!,guiState)
                    }
                }.let {
                    time4 = it
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
                                  guiReducedAttributeValuationSet: List<AttributeValuationSet>,
                                  activity: String,
                                  rotation: Rotation,
                                  isOpeningKeyboard: Boolean,
                                  internetStatus: InternetStatus): AbstractState? {
        val predictedAbstractStateId =   AbstractState.computeAbstractStateId(guiReducedAttributeValuationSet,activity,rotation,internetStatus)
        return abstractStateList.filter {   it !is VirtualAbstractState
                && it.activity == activity
                &&  rotation == it.rotation
                && it.isOpeningKeyboard == isOpeningKeyboard
                && it.internet == internetStatus }. find {
            it.abstractStateId == predictedAbstractStateId
            //hasSameAVS(guiReducedAttributeValuationSet.toSet(), it.attributeValuationSets.toSet())
        }
    }

    fun refineAbstractState(abstractStates: List<AbstractState>,
                            guiState: State<*>,
                            window: Window,
                            rotation: Rotation,
                            internetStatus: InternetStatus): AbstractState {
        val activity = window.activityClass
        val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
        val isOpeningKeyboard = guiState.visibleTargets.filter { it.isKeyboard }.isNotEmpty()
        val widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity,autautMF.packageName,rotation,autautMF)
        val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
        val matchingTestState = findAbstractState(abstractStates, guiReducedWidgetGroup, activity, rotation, isOpeningKeyboard, internetStatus)
        if (matchingTestState != null) {
            if (!matchingTestState.guiStates.contains(guiState)) {
                mapGuiStateToAbstractState(matchingTestState,guiState)
            }
            return matchingTestState
        }

        val staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, window)
        val abstractState = AbstractState(activity = activity, attributeValuationSets = ArrayList(guiReducedWidgetGroup),
                isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                isOutOfApplication = staticMapping.first is OutOfApp,
                isOpeningKeyboard = isOpeningKeyboard,
                EWTGWidgetMapping = staticMapping.second,
                window = staticMapping.first,
                rotation = rotation,
                internet = internetStatus)
        ABSTRACT_STATES.add(abstractState)
        mapGuiStateToAbstractState(abstractState,guiState)
        initAbstractInteractions(abstractState,guiState)
        return abstractState
    }

    private fun havingSameResourceId(EWTGWidgetList: ArrayList<EWTGWidget>): Boolean {
        if (EWTGWidgetList.isEmpty())
            return false
        var resourceId: String = EWTGWidgetList.first().resourceIdName
        EWTGWidgetList.forEach {
            if (!it.resourceIdName.equals(resourceId)) {
                return false
            }
        }
        return true
    }

     fun initAbstractInteractions(abstractState: AbstractState, guiState: State<*>?) {
        abstractState.initAction()

         val inputs = abstractState.window.inputs
         inputs.forEach {input ->
             if (input.widget == null) {
                 initWindowInputForAbstractState(abstractState, input)
             } else {
                 initWidgetInputForAbstractState(abstractState, input)
             }

         }
         //create implicit non-widget interactions
        val nonTrivialWindowTransitions = autautMF.wtg.edges(abstractState.window).filter { it.label.widget == null && it.label.eventType!=EventType.implicit_back_event && it.source.data == it.destination?.data}
        //create implicit widget interactions from static Node
        val nonTrivialWidgetWindowsTransitions = autautMF.wtg.edges(abstractState.window).filter { it.label.widget != null }.filterNot { it.source.data == it.destination?.data }

        nonTrivialWindowTransitions
                .forEach { windowTransition ->
                    val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label) }.map { it.key}
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
                        val abstractActions = abstractState.inputMappings.filter { it.value.contains(windowTransition.label) }.map { it.key}
                        abstractActions.forEach {abstractAction ->
                           createAbstractTransitionFromWindowTransition(abstractState,abstractAction,windowTransition,destAbstractState)
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
                if (virtualAbstractAction.attributeValuationSet != null) {
                   /* val widgetGroup = abstractState.attributeValuationSets.find { it.avsId == virtualAbstractAction.attributeValuationSet.avsId }
                    if (widgetGroup != null) {
                        existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                attributeValuationSet = widgetGroup,
                                extra = virtualAbstractAction.extra)
                    } else if (guiState!=null){
                        val guiWidget = virtualAbstractAction.attributeValuationSet.getGUIWidgets(guiState).firstOrNull()
                        // guiState.widgets.find { virtualAbstractAction.widgetGroup.isAbstractRepresentationOf(it,guiState) }
                        if (guiWidget != null) {
                            val newAttributePath = activity_widget_AttributeValuationSetHashMap[abstractState.activity]!!.get(guiWidget)!!
                            val newWidgetGroup = newAttributePath
                            existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                    attributeValuationSet = newWidgetGroup,
                                    extra = virtualAbstractAction.extra)
                        }
                    }*/
                } else {
                    existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                            attributeValuationSet = null,
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

        val virtualEdges =  autautMF.abstractTransitionGraph.edges(virtualAbstractState).filter {
            //we will not process any self edge
            it.destination!!.data != virtualAbstractState
        }
        virtualEdges.forEach { edge ->
            val edgeCondition = autautMF.abstractTransitionGraph.edgeConditions[edge]!!
            // initAbstractActionCount
            val virtualAbstractAction = edge.label.abstractAction
            val existingAction = abstractState.getAvailableActions().find {
                it == edge.label.abstractAction
            }
            if (existingAction != null) {
                val actionCount = virtualAbstractState.getActionCount(edge.label.abstractAction)

                val existingEdge = autautMF.abstractTransitionGraph.edges(abstractState).find {
                    it.label.abstractAction == edge.label.abstractAction
                            && it.destination?.data == edge.destination?.data
                            && it.label.prevWindow == edge.label.prevWindow
                            && it.label.dest == edge.destination?.data
                }
                if (existingEdge == null) {
                    val abstractInteraction = AbstractTransition(
                            abstractAction = existingAction,
                            isImplicit = true,
                            prevWindow = edge.label.prevWindow,
                            data = edge.label.data,
                            source = abstractState,
                            dest = edge.destination!!.data)
                    val newEdge = autautMF.abstractTransitionGraph.add(abstractState, edge.destination?.data, abstractInteraction)
                    // add edge condition
                    edgeCondition.forEach {
                        autautMF.abstractTransitionGraph.edgeConditions[newEdge]!!.add(it)
                    }

                }
            }
        }
     }

    private fun createAbstractTransitionFromWindowTransition(abstractState: AbstractState, abstractAction: AbstractAction, windowTransition: Edge<Window, Input>, destAbstractState: AbstractState) {
        val abstractEdge = autautMF.abstractTransitionGraph.edges(abstractState).find {
            it.label.isImplicit
                    && it.label.abstractAction == abstractAction
                    && it.label.data == windowTransition.label.data
                    && it.label.prevWindow == null
                    && it.label.dest == destAbstractState
        }

        var abstractTransition: AbstractTransition
        if (abstractEdge != null) {
            abstractTransition = abstractEdge.label
            if (!abstractState.inputMappings.containsKey(abstractTransition.abstractAction)) {
                abstractState.inputMappings.put(abstractTransition.abstractAction, arrayListOf(windowTransition.label))
            } else {
                if (!abstractState.inputMappings[abstractTransition.abstractAction]!!.contains(windowTransition.label)) {
                    abstractState.inputMappings[abstractTransition.abstractAction]!!.add(windowTransition.label)
                }
            }
        } else {
            abstractTransition = AbstractTransition(abstractAction = abstractAction,
                    isImplicit = true,
                    prevWindow = null,
                    data = windowTransition.label.data,
                    fromWTG = true,
                    source = abstractState,
                    dest = destAbstractState)
            windowTransition.label.modifiedMethods.forEach {
                abstractTransition.modifiedMethods.put(it.key, false)
            }
            abstractState.inputMappings.put(abstractTransition.abstractAction, arrayListOf(windowTransition.label))
        }
        if (autautMF.allTargetInputs.contains(windowTransition.label)) {
            abstractState.targetActions.add(abstractTransition.abstractAction)
        }
        autautMF.abstractTransitionGraph.add(abstractState, destAbstractState, abstractTransition)
    }

    private fun initWidgetInputForAbstractState(abstractState: AbstractState, input: Input) {
        if (input.widget==null)
            return
        val avms = abstractState.EWTGWidgetMapping.filter { m -> m.value.contains(input.widget) }.map { it.key }.toMutableList()
        if (avms.isEmpty() && abstractState is VirtualAbstractState) {
            //create a fake widgetGroup
            val staticWidget = input.widget
            val localAttributes = HashMap<AttributeType, String>()
            localAttributes.put(AttributeType.resourceId, staticWidget.resourceIdName)
            localAttributes.put(AttributeType.className, staticWidget.className)

            val attributePath = AttributePath(localAttributes = localAttributes, activity = abstractState.activity)
            val virtAVM = AttributeValuationSet(attributePath = attributePath, cardinality = Cardinality.ONE, activity = abstractState.activity, attributPath_cardinality = HashMap<UUID, Cardinality>())
            //abstractState.addAttributeValuationSet(virtAVM)
            abstractState.EWTGWidgetMapping.put(virtAVM, arrayListOf(input.widget))
            avms.add(virtAVM)
        }
        avms.forEach { avm ->
            var widgetAbstractAction = abstractState.getAvailableActions().find {
                it.actionType == input.convertToExplorationActionName()
                        && it.attributeValuationSet == avm
            }
            if (widgetAbstractAction == null) {
                if (abstractState is VirtualAbstractState) {
                    widgetAbstractAction = AbstractAction(
                            actionType = input.convertToExplorationActionName(),
                            attributeValuationSet = avm,
                            extra = input.data)
                } else {
                    val actionName = input.convertToExplorationActionName()
                    if (actionName == AbstractActionType.ITEM_CLICK || actionName == AbstractActionType.ITEM_LONGCLICK) {
                        widgetAbstractAction = AbstractAction(
                                actionType = input.convertToExplorationActionName(),
                                attributeValuationSet = avm,
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
        if (avms.isNotEmpty()) {

        }
    }

    private fun initWindowInputForAbstractState(abstractState: AbstractState, input: Input) {
        var abstractAction = abstractState.actionCount.keys.find { it.actionType == input.convertToExplorationActionName() }
        if (abstractAction == null) {
            abstractAction = AbstractAction(
                    actionType = input.convertToExplorationActionName(),
                    extra = input.data)
        } else {
            if (abstractAction.extra == null) {
                abstractAction.extra = input.data
            }
        }
        abstractState.actionCount.put(abstractAction, 0)
        if (!abstractState.inputMappings.containsKey(abstractAction))
            abstractState.inputMappings.put(abstractAction, arrayListOf())
        val inputMapping = abstractState.inputMappings.get(abstractAction)!!
        if (!inputMapping.contains(input))
            inputMapping.add(input)
    }

    fun updateLaunchAndResetAbstractTransitions(abstractState: AbstractState) {
        if (launchAbstractStates.containsKey(LAUNCH_STATE.NORMAL_LAUNCH)) {
            val normalLaunchStates = launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
            val launchAction = AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
            val launchAbstractState = getAbstractState(normalLaunchStates)
            if (launchAbstractState != null) {
                val existingTransition = autautMF.abstractTransitionGraph.edges(abstractState).find {
                    it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP }
                if (existingTransition!=null) {
                    autautMF.abstractTransitionGraph.remove(existingTransition)
                    launchAbstractState.abstractTransitions.remove(existingTransition.label)
                }
                val abstractInteraction = AbstractTransition(abstractAction = launchAction,
                        isImplicit = true, prevWindow = null, source = abstractState, dest = launchAbstractState)

                autautMF.abstractTransitionGraph.add(abstractState, launchAbstractState, abstractInteraction)

            }
        }
        if (launchAbstractStates.containsKey(LAUNCH_STATE.RESET_LAUNCH)) {
            // add reset action
            val resetLaunchStates = launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!
            val resetAction = AbstractAction(
                    actionType = AbstractActionType.RESET_APP
            )
            val resetAbstractState = getAbstractState(resetLaunchStates)
            if (resetAbstractState != null) {
                val existingTransition = autautMF.abstractTransitionGraph.edges(abstractState).find { it.label.abstractAction.actionType == AbstractActionType.RESET_APP }
                if (existingTransition!=null) {
                    autautMF.abstractTransitionGraph.remove(existingTransition)
                    resetAbstractState.abstractTransitions.remove(existingTransition.label)
                }
                val resetAbstractInteraction = AbstractTransition(abstractAction = resetAction,
                        isImplicit = true, prevWindow = null, source = abstractState, dest = resetAbstractState)

                autautMF.abstractTransitionGraph.add(abstractState, resetAbstractState, resetAbstractInteraction)
            }
        }
    }

    val guiState_AbstractState_Map = HashMap<ConcreteId,AbstractState>()
    fun getAbstractState(guiState: State<*>): AbstractState? {
        val abstractState = guiState_AbstractState_Map.get(guiState.stateId)
        return abstractState
    }

    fun hasSameAVS(widgetGroups1: Set<AttributeValuationSet>, widgetGroups2: Set<AttributeValuationSet>): Boolean {
        if (widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun getMatchingStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>
                                 , guiState: State<*>
                                 , activity: String
                                 , rotation: Rotation
                                 , window: Window?): Pair<Window, HashMap<AttributeValuationSet, ArrayList<EWTGWidget>>> {
        //check if the previous state is homescreen
        var bestMatchedNode: Window? = null
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        if (window == null) {

            val allPossibleNodes = ArrayList<Window>()
            if (activity.isBlank()) {
                return Pair(first = OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity), second = HashMap())
            }
            //if the previous state is not homescreen
            //Get candidate nodes
            var activityNode: Window? = Activity.allNodes.find { it.classType == activity }
            if (activityNode == null) {
                val newWTGNode =
                        if (guiState.widgets.any { it.packageName == autautMF.packageName } && !guiState.isRequestRuntimePermissionDialogBox) {
                            Activity.getOrCreateNode(
                                    nodeId = Activity.getNodeId(),
                                    classType = activity
                            )
                        } else {
                            OutOfApp.getOrCreateNode(OutOfApp.getNodeId(), activity)
                        }
                val virtualAbstractState = VirtualAbstractState(newWTGNode.classType, newWTGNode, false)
                ABSTRACT_STATES.add(virtualAbstractState)
                activityNode = newWTGNode
            }

            val optionsMenuNode = autautMF.wtg.getOptionsMenu(activityNode)
            val contextMenuNodes = autautMF.wtg.getContextMenus(activityNode)
            val dialogNodes = ArrayList(autautMF.wtg.getDialogs(activityNode))
            //val dialogNodes = WTGDialogNode.allNodes
            Dialog.allNodes.filter { it.activityClass == activity }.forEach {
                if (!dialogNodes.contains(it)) {
                    dialogNodes.add(it)
                }
            }

            if (optionsMenuNode != null) {
                Helper.mergeOptionsMenuWithActivity(guiState,widget_AttributeValuationSetHashMap, optionsMenuNode, activityNode, autautMF.wtg, autautMF)
            }
            val recentMethods = autautMF.statementMF!!.recentExecutedMethods.map {
                autautMF.statementMF!!.getMethodName(it)
            }
            if (Helper.isSameFullScreenDimension(rotation, guiTreeDimension,autautMF)) {
                bestMatchedNode = activityNode
            } else {
                allPossibleNodes.addAll(dialogNodes)
                allPossibleNodes.addAll(contextMenuNodes.distinct())
                if (optionsMenuNode != null) {
                    allPossibleNodes.add(optionsMenuNode)
                }
            }

            if (bestMatchedNode == null) {
                //Find the most similar node
                //try to calculate the match weight of each node.
                //only at least 1 widget matched is in the return result
                if (allPossibleNodes.size > 0) {
                    val matchWeights = Helper.calculateMatchScoreForEachNode(guiState, allPossibleNodes, appName, activity,widget_AttributeValuationSetHashMap, autautMF)
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
                        if (Helper.isSameFullScreenDimension(rotation, guiTreeDimension,autautMF)) {
                            bestMatchedNode = activityNode
                        } else {
                            if (Helper.isOptionsMenuLayout(guiState)) {
                                val existingOptionsMenu = allPossibleNodes.find { it is OptionsMenu }
                                if (existingOptionsMenu != null) {
                                    bestMatchedNode = existingOptionsMenu
                                } else {
                                    val newOptionsMenu = createOptionsMenu(activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                                    bestMatchedNode = newOptionsMenu
                                }
                            } else {
                                val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                                bestMatchedNode = newWTGDialog
                            }
                        }
                    }
                } else {
                    if (Helper.isOptionsMenuLayout(guiState)) {
                        val existingOptionsMenu = allPossibleNodes.find { it is OptionsMenu }
                        if (existingOptionsMenu != null) {
                            bestMatchedNode = existingOptionsMenu
                        } else {
                            val newOptionsMenu = createOptionsMenu(activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                            bestMatchedNode = newOptionsMenu
                        }
                    } else {
                        val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                        bestMatchedNode = newWTGDialog
                    }
                }
            }
        } else {
            bestMatchedNode = window
        }
        if (isDimensionEmpty(bestMatchedNode!!, rotation, isOpeningKeyboard)) {
            setDimension(bestMatchedNode, rotation, guiTreeDimension, isOpeningKeyboard)
        }
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_AttributeValuationSetHashMap, guiState, bestMatchedNode!!)
        return Pair(first = bestMatchedNode, second = widgetGroup_staticWidgetHashMap)
    }

    private fun createOptionsMenu(activityNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean): OptionsMenu {
        val optionsMenu = OptionsMenu.getOrCreateNode(OptionsMenu.getNodeId(),activityNode.activityClass,fromModel = false)
        setDimension(optionsMenu,rotation,guiTreeDimension,isOpeningKeyboard)
        createVirtualAbstractState(optionsMenu)
        return optionsMenu
    }



    private fun createNewDialog(activity: String, activityNode: Window, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean): Dialog {
        val newWTGDialog = Dialog.getOrCreateNode(Dialog.getNodeId(), activity,false)
        newWTGDialog.activityClass = activity
        //autautMF.wtg.add(activityNode, newWTGDialog, FakeEvent(activityNode))
        setDimension(newWTGDialog, rotation, guiTreeDimension, isOpeningKeyboard)
        // regressionTestingMF.transitionGraph.copyNode(activityNode!!,newWTGDialog)
        createVirtualAbstractState(newWTGDialog)
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


    fun getMatchingStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>, guiState: State<*>, window: Window): Pair<Window, HashMap<AttributeValuationSet, ArrayList<EWTGWidget>>> {
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_AttributeValuationSetHashMap, guiState, window)
        return Pair(first = window, second = widgetGroup_staticWidgetHashMap)
    }
    val REFINEMENT_MAX = 25
    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>, abstractTransition: AbstractTransition): Int {
        val abstractionFunction = AbstractionFunction.INSTANCE
        val actionWidget = guiInteraction.targetWidget
        if (actionWidget == null)
        {
            return 0
        }
        AbstractionFunction.backup(autautMF)

        var refinementGrainCount = 0

        val actionAbstractState = getAbstractState(actionGUIState)!!
        val attributeValuationSet = actionAbstractState.getAttributeValuationSet(guiInteraction.targetWidget!!,actionGUIState)!!
        if (AbstractionFunction.INSTANCE.isAbandonedAttributePath(actionAbstractState.activity,attributeValuationSet,abstractTransition.abstractAction.actionType))
            return 0
        while (!validateModel(guiInteraction, actionGUIState)) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val attributePath = abstractionFunction.reduce(actionWidget, actionGUIState, actionAbstractState.window.activityClass,actionAbstractState.rotation,autautMF, HashMap(), HashMap())

            log.info("Increase refinement")
            if (abstractionFunction.increaseReduceLevel(attributePath, actionAbstractState.window.activityClass, false,guiInteraction.targetWidget!!,actionGUIState)) {
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
            } else {
                break
            }
            /* else {
                //rebuild all related GUI states
                log.info("Restore refinement")
                AbstractionFunction.restore(autautMF)
                refinementGrainCount = 0
                rebuildModel(actionAbstractState.window)
                val actionAbstractState = getAbstractState(actionGUIState)!!
                val attributeValuationSet = actionAbstractState.getAttributeValuationSet(guiInteraction.targetWidget!!,actionGUIState)
                if (attributeValuationSet!=null) {
                    AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Triple(actionAbstractState.activity, attributeValuationSet, abstractTransition.abstractAction.actionType))
                    log.info("Add new abandoned attribute valuation set for: $guiInteraction")
                }
                break
            }*/
            /*if (refinementGrainCount>REFINEMENT_MAX) {
                break
            }*/
        }
        if (refinementGrainCount>0) {
            rebuildModel(actionAbstractState.window)
        }
        //get number of Abstract Interaction
        log.debug("Refinement grain increased count: $refinementGrainCount")
        return refinementGrainCount
    }

    private fun rebuildPartly(guiInteraction: Interaction<*>, actionGUIState: State<*>) {
        val actionAbstractState = getAbstractState(actionGUIState)!!
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val abstractEdge = autautMF.abstractTransitionGraph.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }!!
        abstractEdges.add(abstractEdge)
        val abstractStates = arrayListOf(actionAbstractState)
        //validate going to the same window
        abstractStates.forEach {
            val similarEdges = autautMF.abstractTransitionGraph.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.data == abstractEdge.label.data
                        && it.label.prevWindow == abstractEdge.label.prevWindow
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

        val oldAbstractStates = abstractEdges.map { it.source.data }.distinct()
        rebuildAbstractStates(oldAbstractStates, actionAbstractState.window)
    }

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        if (guiInteraction.targetWidget!=null && guiInteraction.targetWidget!!.isKeyboard)
            return true
        if (guiInteraction.targetWidget!=null && Helper.hasParentWithType(guiInteraction.targetWidget!!,actionGUIState,"WebView"))
            return true
        val actionAbstractState = getAbstractState(actionGUIState)
        if (actionAbstractState==null)
            return true
        val abstractEdge = autautMF.abstractTransitionGraph.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }

        if (abstractEdge == null)
            return true
        if (abstractEdge.destination?.data?.window is OutOfApp) {
            return true
        }
        if (abstractEdge.label.abstractAction.attributeValuationSet == null)
            return true
        if (abstractEdge.label.abstractAction.actionType != AbstractActionType.CLICK)
            return true

        /*val abstractStates = if (guiInteraction.targetWidget == null) {
            ABSTRACT_STATES.filterNot{ it is VirtualAbstractState}. filter { it.window == actionAbstractState.window}
        } else {
            val widgetGroup = actionAbstractState.getWidgetGroup(guiInteraction.targetWidget!!, actionGUIState)
            ABSTRACT_STATES.filterNot { it is VirtualAbstractState }. filter { it.window == actionAbstractState.window
                    && it.widgets.contains(widgetGroup)}
        }*/
        val edgeCondition = autautMF.abstractTransitionGraph.edgeConditions[abstractEdge]!!
        val abstractStates = arrayListOf(actionAbstractState)
        //abstractStates.addAll(getSimilarAbstractStates(actionAbstractState).filter { it.attributeValuationSets.contains(abstractEdge.label.abstractAction.attributeValuationSet!!) })
        //val abstractStates = arrayListOf<AbstractState>(actionAbstractState)
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        abstractEdges.add(abstractEdge)
        //validate going to the same window
        abstractStates.forEach {
            val similarEdges = autautMF.abstractTransitionGraph.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.data == abstractEdge.label.data
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

        abstractEdges.clear()
        abstractStates.forEach {
            val similarExplicitEdges = autautMF.abstractTransitionGraph.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.data == abstractEdge.label.data
                        && it.label.prevWindow == abstractEdge.label.prevWindow
                        && it.label.interactions.isNotEmpty()
                        && it.label.isExplicit()
            }
            similarExplicitEdges.forEach {
                val similarEdgeCondition = autautMF.abstractTransitionGraph.edgeConditions[it]!!
                if (similarEdgeCondition.equals(edgeCondition)) {
                    abstractEdges.add(it)
                }
                abstractEdges.add(it)
            }
        }

        val distinctAbstractInteractions2 = abstractEdges.distinctBy { it.destination?.data }
        if (distinctAbstractInteractions2.size > 1) {
            return false
        }
        return true
    }

    private fun getSimilarAbstractStates(actionAbstractState: AbstractState): List<AbstractState> {
        return ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == actionAbstractState.window
                    && it != actionAbstractState
                    && it.isOpeningKeyboard == actionAbstractState.isOpeningKeyboard
                    && it.rotation == actionAbstractState.rotation
                    && it.internet == actionAbstractState.internet
        }
    }

    fun rebuildModel(staticNode: Window) {
        val affectedWindow = WindowManager.instance.allWindows.filter { it.activityClass == staticNode.activityClass }
        //reset virtual abstract state
        ABSTRACT_STATES.removeIf { it is VirtualAbstractState && it.window.activityClass == staticNode.activityClass }
        affectedWindow.forEach {window ->
            AbstractStateManager.instance.attrValSetsFrequency.remove(window)
            val virtualAbstractState = VirtualAbstractState(window.activityClass, window, window is Launcher)
            ABSTRACT_STATES.add(virtualAbstractState)
            initAbstractInteractions(virtualAbstractState,null)
            val oldAbstractStates = ABSTRACT_STATES.filter {
                it.window.activityClass == window.activityClass && it !is VirtualAbstractState
                        && it.guiStates.isNotEmpty()
            }
            rebuildAbstractStates(oldAbstractStates, window)
        }

        //get all related abstract state

        val allAbstractStates = ABSTRACT_STATES
        val launchState = launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
        val resetState = launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!
        val launchAbstractState = getAbstractState(launchState)!!
        val resetAbstractState = getAbstractState(resetState)!!

        measureTimeMillis {
            allAbstractStates.forEach { abstractState ->
                updateOrCreateLaunchAppTransition(abstractState, launchAbstractState)
                updateOrCreateResetAppTransition(abstractState, resetAbstractState)
            }
        }.let {
            //log.debug("Recompute Launch interactions took $it millis")
        }

    }

    private fun rebuildAbstractStates(oldAbstractStates: List<AbstractState>, staticNode: Window) {
        val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
        val processedGuiState = HashSet<State<*>>()

        val oldGuiStates = HashMap<AbstractState, List<State<*>>>()
        oldAbstractStates.forEach {
            oldGuiStates.put(it, ArrayList(it.guiStates))
            it.guiStates.forEach {
                guiState_AbstractState_Map.remove(it.stateId)
            }
            it.guiStates.clear()
        }

        var computeInteractionsTime: Long = 0
        var computeGuiStateTime: Long = 0
        var getGuiStateTime: Long = 0
        measureTimeMillis {
            //compute new AbstractStates for each old one
            oldAbstractStates.forEach { oldAbstractState ->
                val newAbstractStates = ArrayList<AbstractState>()
                oldGuiStates[oldAbstractState]!!.filterNot { processedGuiState.contains(it) }.forEach { guiState ->
                    processedGuiState.add(guiState)
                    var internet = when (oldAbstractState.internet) {
                        InternetStatus.Enable -> true
                        InternetStatus.Disable -> false
                        else -> true
                    }
                    val abstractState = getOrCreateNewAbstractState(guiState, oldAbstractState.window.activityClass, oldAbstractState.rotation, internet, oldAbstractState.window,true)
                    //val abstractState = refineAbstractState(possibleAbstractStates, guiState, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                    if (!newAbstractStates.contains(abstractState)) {
                        newAbstractStates.add(abstractState)
                        autautMF.abstractStateVisitCount[abstractState] = 1
                    } else {
                        autautMF.abstractStateVisitCount[abstractState] = autautMF.abstractStateVisitCount[abstractState]!! + 1
                    }
                    if (!abstractState.guiStates.contains(guiState)) {
                        mapGuiStateToAbstractState(abstractState,guiState)
                    }
                    autautMF.guiState_AbstractStateMap.put(guiState, abstractState)
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
            PathFindingHelper.allAvailableTransitionPaths.forEach { t, u ->
                val transitionPaths = u.toList()
                transitionPaths.forEach { transitionPath ->
                    if (transitionPath.getVertices().any { toRemoveAbstractStates.contains(it.data) }) {
                        u.remove(transitionPath)
                    }
                }
            }
        }.let {
            //log.debug("Recompute Abstract states took $it millis")
        }
        val processedGUIInteractions = ArrayList<Interaction<Widget>>()
        val newEdges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        val inEdgeMap = HashMap<AbstractState, HashSet<Edge<AbstractState, AbstractTransition>>>()
        old_newAbstractStates.keys.forEach { abstractState ->
            inEdgeMap.put(abstractState, HashSet())
        }
        autautMF.abstractTransitionGraph.edges().forEach {
            if (inEdgeMap.containsKey(it.destination?.data)) {
                inEdgeMap[it.destination?.data]!!.add(it)
            }
        }

        //compute new abstract interactions
        measureTimeMillis {
            old_newAbstractStates.entries.forEach {
                val oldAbstractState = it.key
                val newAbstractStates = it.value
                // process out-edges

                val outAbstractEdges = autautMF.abstractTransitionGraph.edges(oldAbstractState).toMutableList()
                outAbstractEdges.filter{it.label.isImplicit}.forEach {
                    autautMF.abstractTransitionGraph.remove(it)
                }
                val inAbstractEdges = inEdgeMap[oldAbstractState]!!
                inAbstractEdges.filter{it.label.isImplicit}.forEach {
                    autautMF.abstractTransitionGraph.remove(it)
                }

                val explicitOutAbstractEdges = outAbstractEdges.filter { !it.label.isImplicit }

                explicitOutAbstractEdges.forEach { oldAbstractEdge ->
                    if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                        //log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                    } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {

                        // Try to keep the edge
                        val newDestinationAbstractStates = ArrayList<AbstractState>()
                        if (old_newAbstractStates.containsKey(oldAbstractEdge.destination?.data)) {
                            newDestinationAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.destination!!.data)!!)
                        } else {
                            newDestinationAbstractStates.add(oldAbstractEdge.destination!!.data)
                        }
                        newAbstractStates.forEach { source ->
                            newDestinationAbstractStates.forEach { dest ->
                                autautMF.abstractTransitionGraph.add(source, dest, oldAbstractEdge.label)
                            }
                        }
                    } else {
                        val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                        val interactions = oldAbstractEdge.label.interactions.toList()
                        /*val guiEdges = autautMF.stateGraph!!.edges().filter { guiEdge ->
                                oldAbstractEdge.label.interactions.contains(guiEdge.label)
                            }*/
                       /* if (interactions.isEmpty() && oldAbstractEdge.label.abstractAction.isWidgetAction()) {
                            //duplicate the abstract transition
                            newAbstractStates.forEach {
                                val derivedAVM = it.attributeValuationSets.find {
                                    it.isDerivedFrom(oldAbstractEdge.label.abstractAction.attributeValuationSet!!)
                                }
                                if (derivedAVM != null){
                                    val newAbstractAction = AbstractAction(
                                            actionType = oldAbstractEdge.label.abstractAction.actionType,
                                            attributeValuationSet = derivedAVM,
                                            extra = oldAbstractEdge.label.abstractAction.extra
                                    )
                                    val newAbstractTransition = AbstractTransition(
                                            prevWindow = oldAbstractEdge.label.prevWindow,
                                            abstractAction = newAbstractAction,
                                            isImplicit = false,
                                            dest = oldAbstractEdge.label.dest,
                                            source = it,
                                            data = oldAbstractEdge.label.data,
                                            fromWTG = oldAbstractEdge.label.fromWTG
                                    )
                                    autautMF.abstractTransitionGraph.add(it,newAbstractTransition.dest,newAbstractTransition)
                                }
                            }
                        }*/
                        interactions.forEach { interaction ->
                            if (processedGUIInteractions.contains(interaction)) {
                                //log.debug("Processed interaction in refining model")
                            } else {
                                processedGUIInteractions.add(interaction)
                                var sourceState: State<*>? = null
                                var destState: State<*>? = null
                                sourceState = autautMF.stateList.find { it.stateId == interaction.prevState }!!
                                destState = autautMF.stateList.find { it.stateId == interaction.resState }!!

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

                                val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!, destState!!)

                                if (newEdge != null) {
                                    newEdges.add(newEdge)
                                    if (oldAbstractEdge.label != newEdge.label) {
                                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                        autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                                    }
                                }
                            }
                        }
                    }
                }

                // process in-edges

                val explicitInAbstractEdges = inAbstractEdges.filter { !it.label.isImplicit }

                explicitInAbstractEdges.forEach { oldAbstractEdge ->
                    if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                        // log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                    } else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                        // Try to keep the edge
                        val newSourceAbstractStates = ArrayList<AbstractState>()
                        if (old_newAbstractStates.containsKey(oldAbstractEdge.source.data)) {
                            newSourceAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.source.data)!!)
                        } else {
                            newSourceAbstractStates.add(oldAbstractEdge.destination!!.data)
                        }
                        newAbstractStates.forEach { dest ->
                            newSourceAbstractStates.forEach { source ->
                                autautMF.abstractTransitionGraph.add(source, dest, oldAbstractEdge.label)
                            }
                        }
                        //autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                    } else {
                        val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                        val interactions = oldAbstractEdge.label.interactions.toList()
                        /*if (interactions.isEmpty() && oldAbstractEdge.label.abstractAction.isWidgetAction()) {
                            //duplicate the abstract transition
                            val sourceStates: List<AbstractState> = if (old_newAbstractStates.containsKey(oldAbstractEdge.source.data))
                                old_newAbstractStates.get(oldAbstractEdge.source.data)!!
                            else
                                arrayListOf(oldAbstractEdge.source.data)

                            sourceStates.forEach {
                                val derivedAVM = it.attributeValuationSets.find {
                                    it.isDerivedFrom(oldAbstractEdge.label.abstractAction.attributeValuationSet!!)
                                }
                                if (derivedAVM != null) {
                                    newAbstractStates.forEach { dest ->
                                        val newAbstractAction = AbstractAction(
                                                actionType = oldAbstractEdge.label.abstractAction.actionType,
                                                attributeValuationSet = derivedAVM,
                                                extra = oldAbstractEdge.label.abstractAction.extra
                                        )
                                        val newAbstractTransition = AbstractTransition(
                                                prevWindow = oldAbstractEdge.label.prevWindow,
                                                abstractAction = newAbstractAction,
                                                isImplicit = false,
                                                dest = dest,
                                                source = it,
                                                data = oldAbstractEdge.label.data,
                                                fromWTG = oldAbstractEdge.label.fromWTG
                                        )
                                        autautMF.abstractTransitionGraph.add(it, newAbstractTransition.dest, newAbstractTransition)
                                    }
                                }
                            }
                        }*/
                        interactions.filter {
                            val destStateId = it.resState
                            val desState = autautMF.stateGraph!!.getVertices().find { it.data.stateId.equals(destStateId) }?.data
                            if (desState == null) {
                                true
                            } else {
                                val abstractState = getAbstractState(desState)
                                if (abstractState == null)
                                    true
                                else if (abstractState.window == staticNode)
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
                                sourceState = autautMF.stateList.find { it.stateId == interaction.prevState }!!
                                destState = autautMF.stateList.find { it.stateId == interaction.resState }!!

                                var destinationAbstractState = getAbstractState(destState!!)

                                if (destinationAbstractState == null) {
                                    throw Exception("Cannot find new resState' abstract state")
                                }

                                var sourceAbstractState = getAbstractState(sourceState!!)
                                if (sourceAbstractState == null)
                                    throw Exception("Cannot find new prevState' abstract state")


                                //let create new interaction
                                val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!, destState!!)
                                if (newEdge!=null) {
                                    newEdges.add(newEdge)
                                    if (oldAbstractEdge.label != newEdge.label) {
                                        oldAbstractEdge.source.data.abstractTransitions.remove(oldAbstractEdge.label)
                                        autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }.let {
            //log.debug("Recompute abstract interactions took $it millis")
           // log.debug("Recompute interactions took ${computeInteractionsTime / 1000000} millis with ${processedGUIInteractions.size} interactions.")
           // log.debug("Get gui state took ${getGuiStateTime / 1000000} millis")
        }
        oldAbstractStates.forEach {
            if (it.guiStates.isEmpty() && !it.loaded) {
                ABSTRACT_STATES.remove(it)
            }
        }
    }

    private fun updateOrCreateResetAppTransition(abstractState: AbstractState, resetAbstractState: AbstractState) {
        val resetInteraction = autautMF.abstractTransitionGraph.edges(abstractState).find {
            it.label.abstractAction.actionType == AbstractActionType.RESET_APP
        }
        if (resetInteraction != null) {
            autautMF.abstractTransitionGraph.update(abstractState, resetInteraction?.destination?.data, resetAbstractState, resetInteraction.label, resetInteraction.label)
        } else {
            val resetAction = AbstractAction(
                    actionType = AbstractActionType.RESET_APP
            )
            val abstractInteraction = AbstractTransition(abstractAction = resetAction,
                    isImplicit = true, prevWindow = null,source = abstractState,dest = resetAbstractState)
            autautMF.abstractTransitionGraph.add(abstractState, resetAbstractState, abstractInteraction)
        }
    }

    private fun updateOrCreateLaunchAppTransition(abstractState: AbstractState, launchAbstractState: AbstractState): Edge<AbstractState, AbstractTransition>? {
        val launchInteraction = autautMF.abstractTransitionGraph.edges(abstractState).find {
            it.label.abstractAction.actionType == AbstractActionType.LAUNCH_APP
        }
        if (launchInteraction != null) {
            autautMF.abstractTransitionGraph.update(abstractState, launchInteraction?.destination?.data, launchAbstractState, launchInteraction.label, launchInteraction.label)
        } else {
            val launchAction = AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
            val abstractInteraction = AbstractTransition(abstractAction = launchAction,
                    isImplicit = true, prevWindow = null,source = abstractState,dest = launchAbstractState)
            autautMF.abstractTransitionGraph.add(abstractState, launchAbstractState, abstractInteraction)
        }
        return launchInteraction
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
        if (oldAbstractEdge.label.abstractAction.attributeValuationSet == null) {


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
            val existingAbstractEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState, destinationAbstractState)
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
                val tracing = autautMF.interactionsTracing.get(listOf(interaction))
                if (tracing != null) {
                    newAbstractionTransition.tracing.add(tracing)
                }
                newEdge = autautMF.abstractTransitionGraph.add(sourceAbstractState, destinationAbstractState, newAbstractionTransition)
                if (!autautMF.abstractTransitionGraph.containsCondition(newEdge,condition))
                    autautMF.abstractTransitionGraph.addNewCondition(newEdge,condition)
                addImplicitAbstractInteraction(destState,sourceAbstractState, destinationAbstractState, newAbstractionTransition, newAbstractionTransition.prevWindow,condition)

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
                val tracing = autautMF.interactionsTracing.get(listOf(interaction))
                if (tracing != null) {
                    newAbstractionTransition.tracing.add(tracing)
                }
            }
            sourceAbstractState.increaseActionCount(abstractAction,true)
        } else {
            //get widgetgroup
            var newAttributeValuationSet = sourceAbstractState.attributeValuationSets.find { it.isAbstractRepresentationOf(interaction.targetWidget!!, sourceState,false) }
/*            if (newAttributeValuationSet == null) {
                newAttributeValuationSet = oldAbstractEdge.label.abstractAction.attributeValuationSet
            }*/
            if (newAttributeValuationSet == null) {
                val newAttributePath = AbstractionFunction.INSTANCE.reduce(interaction.targetWidget!!, sourceState, sourceAbstractState.window.activityClass,sourceAbstractState.rotation,autautMF, HashMap(), HashMap())
                newAttributeValuationSet = sourceAbstractState.attributeValuationSets.find { it.haveTheSameAttributePath(newAttributePath) }
              /*  newAttributeValuationSet = AttributeValuationSet(newAttributePath, Cardinality.ONE, sourceAbstractState.activity, HashMap())
                activity_widget_AttributeValuationSetHashMap[sourceAbstractState.activity]!!.put(interaction.targetWidget!!,newAttributeValuationSet)*/


                //newWidgetGroup.guiWidgets.add(interaction.targetWidget!!)
                //sourceAbstractState.addWidgetGroup(newWidgetGroup)
            }
            if (newAttributeValuationSet != null) {
                val abstractAction = AbstractAction(
                        actionType = oldAbstractEdge.label.abstractAction.actionType,
                        attributeValuationSet = newAttributeValuationSet,
                        extra = oldAbstractEdge.label.abstractAction.extra
                )
                sourceAbstractState.addAction(abstractAction)
                if (isTarget) {
                    sourceAbstractState.targetActions.add(abstractAction)
                }
                //check if there is exisiting interaction
                val exisitingAbstractEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState, destinationAbstractState).find {
                    it.label.abstractAction == abstractAction
                            && it.label.data == oldAbstractEdge.label.data
                            && it.label.prevWindow == oldAbstractEdge.label.prevWindow
                            && it.label.isImplicit == false
                }
                if (exisitingAbstractEdge != null) {
                    newEdge = exisitingAbstractEdge
                    newAbstractionTransition = exisitingAbstractEdge.label
                    exisitingAbstractEdge.label.interactions.add(interaction)
                    val tracing = autautMF.interactionsTracing.get(listOf(interaction))
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
                    val tracing = autautMF.interactionsTracing.get(listOf(interaction))
                    if (tracing != null) {
                        newAbstractionTransition.tracing.add(tracing)
                    }
                    newEdge = autautMF.abstractTransitionGraph.add(
                            sourceAbstractState,
                            destinationAbstractState,
                            newAbstractionTransition
                    )
                    if (!autautMF.abstractTransitionGraph.containsCondition(newEdge,condition))
                        autautMF.abstractTransitionGraph.addNewCondition(newEdge,condition)
                    addImplicitAbstractInteraction(destState,sourceAbstractState, destinationAbstractState, newAbstractionTransition, newAbstractionTransition.prevWindow,condition)

                }
                sourceAbstractState.increaseActionCount(abstractAction,true)
            }

        }
        if (newAbstractionTransition!=null) {
            // update coverage
            if (autautMF.guiInteractionCoverage.containsKey(interaction)) {
                val interactionCoverage = autautMF.guiInteractionCoverage.get(interaction)!!
                interactionCoverage.forEach {
                    newAbstractionTransition.updateUpdateStatementCoverage(it, autautMF)
                }
            }
            val edgeMethodCoverage = autautMF.abstractTransitionGraph.methodCoverageInfo[oldAbstractEdge]!!
            autautMF.abstractTransitionGraph.methodCoverageInfo.put(newEdge!!, ArrayList(edgeMethodCoverage))
            val edgeStatementCoverage = autautMF.abstractTransitionGraph.statementCoverageInfo[oldAbstractEdge]!!
            autautMF.abstractTransitionGraph.statementCoverageInfo.put(newEdge, ArrayList(edgeStatementCoverage))
        }
        return newEdge
    }


    val widget_StaticWidget = HashMap<Window,HashMap<ConcreteId,ArrayList<EWTGWidget>>>()

    private fun getStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>, guiState: State<*>, staticNode: Window): HashMap<AttributeValuationSet, ArrayList<EWTGWidget>> {
        val result: HashMap<AttributeValuationSet, ArrayList<EWTGWidget>> = HashMap()
        val actionableWidgets = ArrayList<Widget>()
        actionableWidgets.addAll(Helper.getVisibleWidgets(guiState))
        if (actionableWidgets.isEmpty()) {
            actionableWidgets.addAll(guiState.widgets.filterNot { it.isKeyboard })
        }
        val unmappedWidgets = actionableWidgets

        if (!widget_StaticWidget.containsKey(staticNode)) {
            widget_StaticWidget.put(staticNode, HashMap())
        }
        val mappedStaticWidgets = widget_StaticWidget.get(staticNode)!!

        unmappedWidgets.groupBy { widget_AttributeValuationSetHashMap[it] }
                .filter { it.key != null }
                .forEach {
                    val attributeValuationSet = it.key!!

                    it.value.forEach { w ->
                        if (!result.containsKey(attributeValuationSet)) {
                            if (mappedStaticWidgets.containsKey(w.id)) {
                                result.put(it.key!!, ArrayList(mappedStaticWidgets.get(w.id)!!))
                            } else {
                                val staticWidgets = Helper.getStaticWidgets(w, guiState, attributeValuationSet, staticNode, true, autautMF)
                                //if a widgetGroup has more
                                if (staticWidgets.isNotEmpty()) {
                                    mappedStaticWidgets.put(w.id, ArrayList(staticWidgets))
                                    result.put(attributeValuationSet, ArrayList(staticWidgets))
                                }
                            }
                        }
                    }
                }
        return result
    }

    fun addImplicitAbstractInteraction(currentState: State<*>?, prevAbstractState: AbstractState, currentAbstractState: AbstractState, abstractTransition: AbstractTransition, prevWindow: Window?, edgeCondition: HashMap<Widget,String>) {
        //AutAutMF.log.debug("Add implicit abstract interaction")
        var addedCount = 0
        var processedStateCount = 0
        // add implicit back events
        addedCount = 0
        processedStateCount = 0

        if (prevWindow != null && currentState!=null) {
            val implicitBackWindow = if (currentAbstractState.isOpeningKeyboard) {
                currentAbstractState.window
            } else if (prevAbstractState.window == currentAbstractState.window) {
                prevWindow
            } else {
                prevWindow
            }

            //We don't need create implicit back transition in case state unchanged
            if (prevAbstractState != currentAbstractState
                    && !abstractTransition.abstractAction.isLaunchOrReset()) {
                val pair = createImplicitPressBackTransition(currentAbstractState, implicitBackWindow, currentState, implicitBackWindow, processedStateCount, prevAbstractState, addedCount)
                addedCount = pair.first
                processedStateCount = pair.second
            }
        }
        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")

        if (abstractTransition.abstractAction.actionType == AbstractActionType.SWIPE
                && abstractTransition.abstractAction.attributeValuationSet != null
                && prevAbstractState != currentAbstractState
        ) {
            //check if the swipe action changed the content
            if (currentAbstractState.attributeValuationSets.contains(abstractTransition.abstractAction.attributeValuationSet)) {
                val currentWidgetGroup = currentAbstractState.attributeValuationSets.find { it == abstractTransition.abstractAction.attributeValuationSet }!!
                if (!currentWidgetGroup.havingSameContent(currentAbstractState, abstractTransition.abstractAction.attributeValuationSet!!, prevAbstractState)) {
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

    /*   if (virtualAbstractState != null && !prevAbstractState.isOpeningKeyboard
                && !abstractTransition.abstractAction.isLaunchOrReset()) {
            createImplicitTransitionForVirtualAbstractState(abstractTransition, virtualAbstractState, isTargetAction, currentAbstractState, prevAbstractState, prevWindow, edgeCondition)

        }*/

        //do not add implicit transition if this is Launch/Reset/Swipe
       /* if (!abstractTransition.abstractAction.isLaunchOrReset()) {
            createImplicitTransitionForOtherAbstractStates(prevAbstractState, processedStateCount, abstractTransition, currentAbstractState, prevWindow, isTargetAction, edgeCondition, addedCount)
        }*/

        //update ResetApp && LaunchApp edge
        if (abstractTransition.abstractAction.isLaunchOrReset()) {
            updateResetAndLaunchTransitions(abstractTransition, currentAbstractState, prevWindow)
        }

        //process implicit item action
        val action = abstractTransition.abstractAction.actionType
        if (abstractTransition.abstractAction.isWidgetAction()
                && (action == AbstractActionType.CLICK || action == AbstractActionType.LONGCLICK)
                ) {
            inferItemActionTransitions(action, abstractTransition, prevAbstractState, prevWindow, currentAbstractState, currentState, edgeCondition)
        }

        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
        //AutAutMF.log.debug("Add implicit back interaction.")

    }

    private fun inferItemActionTransitions(action: AbstractActionType, abstractTransition: AbstractTransition, prevAbstractState: AbstractState, prevprevWindow: Window?, currentAbstractState: AbstractState, currentState: State<*>?, edgeCondition: HashMap<Widget, String>) {
        val itemAction = when (action) {
            AbstractActionType.CLICK -> AbstractActionType.ITEM_CLICK
            else -> AbstractActionType.ITEM_LONGCLICK
        }
        //val parentWidgetGroups = HashSet<WidgetGroup>()
        var parentAttributeValuationSetId = abstractTransition.abstractAction.attributeValuationSet!!.parentAttributeValuationSetId
        while (parentAttributeValuationSetId != emptyUUID) {
            val parentAVS = AttributeValuationSet.allAttributeValuationSet[prevAbstractState.activity]!!.get(parentAttributeValuationSetId)
            if (parentAVS != null) {
                if (prevAbstractState.attributeValuationSets.contains(parentAVS)) {
                    val itemAbtractAction = AbstractAction(
                            actionType = itemAction,
                            attributeValuationSet = parentAVS
                    )
                    if (parentAVS.actionCount.containsKey(itemAbtractAction)) {
                        prevAbstractState.increaseActionCount(itemAbtractAction, updateSimilarAbstractState = true)
                        var implicitInteraction =
                                autautMF.abstractTransitionGraph.edges(prevAbstractState).find {
                                    it.label.isImplicit == true
                                            && it.label.abstractAction == itemAbtractAction
                                            && it.label.prevWindow == prevprevWindow
                                            && it.destination?.data == currentAbstractState
                                }?.label
                        if (implicitInteraction == null) {
                            // create new explicit interaction
                            implicitInteraction = AbstractTransition(
                                    abstractAction = itemAbtractAction,
                                    isImplicit = true,
                                    prevWindow = prevprevWindow,
                                    source = prevAbstractState,
                                    dest = currentAbstractState
                            )
                            autautMF.abstractTransitionGraph.add(prevAbstractState, currentAbstractState, implicitInteraction)
                        }
                        //addImplicitAbstractInteraction(currentState, prevAbstractState, currentAbstractState, implicitInteraction, prevprevWindow, edgeCondition)
                    }
                }
                parentAttributeValuationSetId = parentAVS.parentAttributeValuationSetId
            } else {
                parentAttributeValuationSetId = emptyUUID
            }

        }
    }

    private fun updateResetAndLaunchTransitions(abstractTransition: AbstractTransition, currentAbstractState: AbstractState, prevprevWindow: Window?) {
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
            updateLaunchTransitions(abstractState, launchAppAction!!, currentAbstractState, abstractTransition, prevprevWindow)
            if (resetAppAction != null) {
                updateLaunchTransitions(abstractState, resetAppAction, currentAbstractState, abstractTransition, prevprevWindow)
            }
        }
    }

    private fun createImplicitTransitionForOtherAbstractStates(prevAbstractState: AbstractState, processedStateCount: Int, abstractTransition: AbstractTransition, currentAbstractState: AbstractState, prevprevWindow: Window?, isTargetAction: Boolean, edgeCondition: HashMap<Widget, String>, addedCount: Int) {
        var processedStateCount1 = processedStateCount
        var addedCount1 = addedCount
        val otherSameStaticNodeAbStates = instance.ABSTRACT_STATES.filter {
            it !is VirtualAbstractState
                    && it.window == prevAbstractState.window
                    && it != prevAbstractState
        }

        otherSameStaticNodeAbStates.forEach {
            processedStateCount1 += 1
            var implicitAbstractTransition: AbstractTransition?
            implicitAbstractTransition = getOrCreateImplicitAbstractInteraction(abstractTransition, it, currentAbstractState, prevprevWindow)
            if (implicitAbstractTransition != null) {
                if (!implicitAbstractTransition.abstractAction.isWidgetAction()) {
                    it.increaseActionCount(implicitAbstractTransition.abstractAction)
                }
                if (isTargetAction) {
                    it.targetActions.add(implicitAbstractTransition.abstractAction)
                }
                if (prevAbstractState != currentAbstractState && prevAbstractState != it) {
                   val edge = autautMF.abstractTransitionGraph.add(it, currentAbstractState, implicitAbstractTransition)
                    // add edge condition
                    if (!autautMF.abstractTransitionGraph.containsCondition(edge, edgeCondition)) {
                        autautMF.abstractTransitionGraph.addNewCondition(edge, edgeCondition)
                    }
                } else if (currentAbstractState == prevAbstractState) {
                    autautMF.abstractTransitionGraph.add(it, it, implicitAbstractTransition)
                }

                //it.increaseActionCount(implicitAbstractTransition.abstractAction)
                addedCount1 += 1
            } else {
                var existingAction = it.getAvailableActions().find {
                    it == abstractTransition.abstractAction
                }
            }
        }
    }

    private fun createImplicitTransitionForVirtualAbstractState(abstractTransition: AbstractTransition, virtualAbstractState: AbstractState, isTargetAction: Boolean, currentAbstractState: AbstractState, prevAbstractState: AbstractState, prevprevWindow: Window?, edgeCondition: HashMap<Widget, String>) {
        val abstractAction = abstractTransition.abstractAction
        // get existing action
        var virtualAbstractAction = virtualAbstractState.getAvailableActions().find {
            it == abstractAction
        }
        if (virtualAbstractAction == null) {
            if (abstractAction.attributeValuationSet != null) {
                val widgetGroup = virtualAbstractState.attributeValuationSets.find { it.haveTheSameAttributePath(abstractAction.attributeValuationSet!!) }
                if (widgetGroup != null) {
                    virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                            attributeValuationSet = widgetGroup,
                            extra = abstractAction.extra)

                } else {
                    val newAttributeValuationSet = abstractAction.attributeValuationSet
                    virtualAbstractState.attributeValuationSets.add(newAttributeValuationSet)
                    //virtualAbstractState.addAttributeValuationSet(newAttributeValuationSet)
                    virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                            attributeValuationSet = newAttributeValuationSet,
                            extra = abstractAction.extra)
                }
            } else {
                virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                        attributeValuationSet = null,
                        extra = abstractAction.extra)
            }
            virtualAbstractState.addAction(virtualAbstractAction)
        }
        if (!virtualAbstractAction.isWidgetAction()) {
            virtualAbstractState.increaseActionCount(virtualAbstractAction)
        }
        if (isTargetAction) {
            virtualAbstractState.targetActions.add(virtualAbstractAction)
        }
        val implicitDestAbstractState = if (currentAbstractState != prevAbstractState) {
            currentAbstractState
        } else {
            virtualAbstractState
        }
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
            if (!autautMF.abstractTransitionGraph.containsCondition(edge, edgeCondition))
                autautMF.abstractTransitionGraph.addNewCondition(edge, edgeCondition)

        }
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
            autautMF.abstractTransitionGraph.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            currentAbstractState.increaseActionCount(inverseAbstractAction)
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
                    && it.attributeValuationSet == abstractTransition.abstractAction.attributeValuationSet
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
            autautMF.abstractTransitionGraph.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
            currentAbstractState.increaseActionCount(inverseAbstractAction)
        }
    }

    private fun createImplicitPressBackTransition(currentAbstractState: AbstractState, prevprevWindow: Window?, currentState: State<*>, implicitBackWindow: Window?, processedStateCount: Int, prevAbstractState: AbstractState, addedCount: Int): Pair<Int, Int> {
        var processedStateCount1 = processedStateCount
        var addedCount1 = addedCount
        val backAbstractAction = AbstractAction(actionType = AbstractActionType.PRESS_BACK,
                attributeValuationSet = null)
        //check if there is any pressback action go to another window
        if (!autautMF.abstractTransitionGraph.edges(currentAbstractState).any {
                    it.destination != null &&
                            it.destination!!.data.window != prevprevWindow &&
                            it.label.prevWindow == prevprevWindow &&
                            it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                }) {
            currentAbstractState.addAction(backAbstractAction)
            val processingAbstractStates = ArrayList<AbstractState>()
            processingAbstractStates.add(currentAbstractState)

            /*val backAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                it.window == implicitBackWindow
                        && !it.isOpeningKeyboard
            }*/
            val backAbstractStates = ArrayList<AbstractState>()
            val lastIndexOfCurrentState = autautMF.stateList.indexOfLast {
                it == currentState
            }
            var backAbstractState: AbstractState? = null
            autautMF.stateList.forEachIndexed { index, guiState ->
                val abstractState = autautMF.guiState_AbstractStateMap[guiState]!!
                if (index < lastIndexOfCurrentState
                        && abstractState.window == implicitBackWindow
                        && !abstractState.isOpeningKeyboard) {
                    backAbstractState = abstractState
                }
            }

            if (backAbstractState == null) {

                //log.debug("Cannot find implicit back abstract state")
            } else {
                backAbstractStates.add(backAbstractState!!)
            }
            backAbstractStates.forEach { abstractState ->
                processedStateCount1 += 1
                val backAbstractInteraction = AbstractTransition(
                        abstractAction = backAbstractAction,
                        isImplicit = true,
                        prevWindow = implicitBackWindow,
                        source = currentAbstractState,
                        dest = abstractState
                )
                val implicitAbstractInteraction = getOrCreateImplicitAbstractInteraction(backAbstractInteraction, currentAbstractState, abstractState, prevprevWindow)
                if (implicitAbstractInteraction != null) {
                    if (prevAbstractState != currentAbstractState) {
                        val edge = autautMF.abstractTransitionGraph.add(currentAbstractState, abstractState, implicitAbstractInteraction)
                        // add edge condition

                    }

                    addedCount1 += 1
                }
            }
        }
        return Pair(addedCount1, processedStateCount1)
    }

    private fun updateLaunchTransitions(abstractState: AbstractState, launchAppAction: AbstractAction, currentAbstractState: AbstractState, abstractTransition: AbstractTransition, prevprevWindow: Window?) {
        val existingEdges = autautMF.abstractTransitionGraph.edges(abstractState).filter {
            it.label.abstractAction == launchAppAction
        }
        if (existingEdges.isNotEmpty()) {
            existingEdges.forEach {
                autautMF.abstractTransitionGraph.update(abstractState, it.destination?.data, currentAbstractState, it.label, it.label)
            }
        } else {
            var implicitAbstractInteraction = AbstractTransition(
                    abstractAction = launchAppAction,
                    isImplicit = true,
                    data = abstractTransition.data,
                    prevWindow = prevprevWindow,
                    source = abstractState,
                    dest = currentAbstractState
            )
            autautMF.abstractTransitionGraph.add(abstractState, currentAbstractState, implicitAbstractInteraction)
        }
    }

    // This should not be implicit added to another abstract states
    private fun isSwipeScreenGoToAnotherWindow(abstractAction: AbstractAction, currentAbstractState: AbstractState, prevAbstractState: AbstractState) =
            (abstractAction.actionType == AbstractActionType.SWIPE && abstractAction.attributeValuationSet == null
                    && currentAbstractState.window != prevAbstractState.window)

    private fun getOrCreateImplicitAbstractInteraction(abstractTransition: AbstractTransition, sourceAbstractState: AbstractState, destinationAbstractState: AbstractState, prevprevWindow: Window?): AbstractTransition? {
        var implicitAbstractTransition: AbstractTransition?

        if (abstractTransition.abstractAction.attributeValuationSet == null) {
            //find existing interaction again
            val existingEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState).find {
                it.label.abstractAction == abstractTransition.abstractAction
                        && it.label.prevWindow == prevprevWindow
                        && it.label.data == abstractTransition.data
                        && it.destination?.data == destinationAbstractState
            }
            if (existingEdge!=null && existingEdge.label.isImplicit) {
                return null
            }
            implicitAbstractTransition = if (existingEdge!=null) {
                existingEdge.label
            } else {
                AbstractTransition(
                        abstractAction = abstractTransition.abstractAction,
                        isImplicit = true,
                        data = abstractTransition.data,
                        prevWindow = prevprevWindow,
                        source = sourceAbstractState,
                        dest = destinationAbstractState
                )
            }

        } else {
            //find Widgetgroup
            val widgetGroup = sourceAbstractState.attributeValuationSets.find { it.equals(abstractTransition.abstractAction.attributeValuationSet) }
            if (widgetGroup != null) {
                //find existing interaction again
                val existingEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState).filter {
                    it.label.abstractAction == abstractTransition.abstractAction
                            && it.label.prevWindow == prevprevWindow
                            && it.label.data == abstractTransition.data
                            && destinationAbstractState == it.destination?.data
                }
                if (existingEdge.isNotEmpty() && existingEdge.any { !it.label.isImplicit }) {
                    return null
                }
                implicitAbstractTransition = if (existingEdge.isNotEmpty()) {
                    existingEdge.first().label
                } else {
                    AbstractTransition(
                            abstractAction = AbstractAction(
                                    actionType = abstractTransition.abstractAction.actionType,
                                    attributeValuationSet = widgetGroup,
                                    extra = abstractTransition.abstractAction.extra
                            ),
                            isImplicit = true,
                            data = abstractTransition.data,
                            prevWindow = prevprevWindow,
                            source = sourceAbstractState,
                            dest = destinationAbstractState
                    )
                }
            } else {
                implicitAbstractTransition = null
            }
        }
        return implicitAbstractTransition
    }
    fun getPotentialAbstractStates(): List<AbstractState> {
        return ABSTRACT_STATES.filterNot { it is VirtualAbstractState
                || it.window is Launcher
                || it.window is OutOfApp
        }
    }

    fun dump(dstgFolder: Path) {
        File(dstgFolder.resolve("AbstractStateList.csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            ABSTRACT_STATES.filter{ it !is VirtualAbstractState}.forEach {
                all.newLine()
                val abstractStateInfo = "${it.abstractStateId};${it.activity};${it.window.windowId};${it.rotation};${it.internet};" +
                        "${it.isHomeScreen};${it.isRequestRuntimePermissionDialogBox};${it.isAppHasStoppedDialogBox};" +
                        "${it.isOutOfApplication};${it.isOpeningKeyboard};${it.hasOptionsMenu};\"${it.guiStates.map { it.stateId }.joinToString(separator = ";")}\""
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
        return "abstractStateID;activity;window;rotation;internetStatus;" +
                "isHomeScreen;isRequestRuntimePermissionDialogBox;isAppHasStoppedDialogBox;" +
                "isOutOfApplication;isOpeningKeyboard;hasOptionsMenu;guiStates"
    }

    companion object {
        val instance: AbstractStateManager by lazy {
            AbstractStateManager()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}