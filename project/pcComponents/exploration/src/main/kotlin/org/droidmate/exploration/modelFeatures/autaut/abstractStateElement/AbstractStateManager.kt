package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.LoggerFactory
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class AbstractStateManager() {
    val ABSTRACT_STATES: ArrayList<AbstractState> = ArrayList()
    val launchAbstractStates: HashMap<LAUNCH_STATE, State<*>> = HashMap()
    lateinit var appResetState: AbstractState
    lateinit var autautMF: AutAutMF
    lateinit var appName: String
    val widgetGroupFrequency = HashMap<WTGNode, HashMap<AttributeValuationSet, Int>>()

    fun init(regressionTestingMF: AutAutMF, appPackageName: String) {
        this.autautMF = regressionTestingMF
        this.appName = appPackageName

        //create initial abstract state (after App reset)
        appResetState = AppResetAbstractState()

        regressionTestingMF.abstractTransitionGraph = AbstractTransitionGraph()

        WTGNode.allNodes.forEach {
            val virtualAbstractState = VirtualAbstractState(it.activityClass, it, it is WTGLauncherNode)
            ABSTRACT_STATES.add(virtualAbstractState)
            // regressionTestingMF.abstractStateVisitCount[virtualAbstractState] = 0
        }

        ABSTRACT_STATES.filter { it is VirtualAbstractState }.forEach {
            initAbstractInteractions(it,null)
        }
    }

    fun createVirtualAbstractState(window: WTGNode) {
        val virtualAbstractState = VirtualAbstractState(window.activityClass, window, false)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState,null)
    }

    fun getOrCreateNewTestState(guiState: State<*>,
                                i_activity: String,
                                rotation: Rotation,
                                internet: Boolean,
                                window: WTGNode?): AbstractState {
        val exisitingAbstractState = getAbstractState(guiState)
        if (exisitingAbstractState!=null) {
            return exisitingAbstractState
        }
        var abstractState: AbstractState
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
                    homeState.guiStates.add(guiState)
                }
            } else {
                abstractState = AbstractState(activity = i_activity,
                        isHomeScreen = true,
                        window = WTGLauncherNode.instance!!,
                        rotation = Rotation.PORTRAIT,
                        internet = internetStatus)
                if (WTGLauncherNode.instance!!.activityClass.isBlank()) {
                    WTGLauncherNode.instance!!.activityClass = activity
                }
                abstractState.guiStates.add(guiState)
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
                    stopState.guiStates.add(guiState)
                }
            } else {
                stopState = AbstractState(activity = activity,
                        isAppHasStoppedDialogBox = true,
                        window = WTGOutScopeNode.getOrCreateNode(activity),
                        rotation = rotation,
                        internet = internetStatus)
                stopState.guiStates.add(guiState)
                ABSTRACT_STATES.add(stopState)
                abstractState = stopState
            }
        } else {
            //log.info("Activity: $activity")
            val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
            val isOpeningKeyboard = guiState.widgets.any { it.isKeyboard }

            do {
                val widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity, autautMF.packageName)
                TextInput.saveSpecificTextInputData(guiState)
                val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
                val matchingTestState = findAbstractState(ABSTRACT_STATES, guiReducedWidgetGroup, activity, rotation, isOpeningKeyboard, internetStatus)
                if (matchingTestState != null) {
                    if (!matchingTestState.guiStates.contains(guiState)) {
                        matchingTestState.guiStates.add(guiState)
                    }
                    return matchingTestState
                }
                val staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, activity, rotation,window)
                if (staticMapping.first.activityClass.isBlank()) {
                    staticMapping.first.activityClass = activity
                }
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
                            staticWidgetMapping = staticMapping.second,
                            window = staticMapping.first,
                            rotation = autautMF.currentRotation,
                            isOutOfApplication = staticMapping.first is WTGOutScopeNode,
                            internet = internetStatus)
                    if (abstractState.window is WTGDialogNode || abstractState.window is WTGOptionsMenuNode || abstractState.window is WTGContextMenuNode) {
                        abstractState.hasOptionsMenu = false
                    }
                    ABSTRACT_STATES.add(abstractState)
                    abstractState.guiStates.add(guiState)
                    initAbstractInteractions(abstractState,guiState)
                    break
                } /*else {
                    var increasedReducerLevelCount = 0
                    ambigousWidgetGroup.forEach {

                        if (AbstractionFunction.INSTANCE.increaseReduceLevel(it.key.attributePath,activity, false)) {
                            increasedReducerLevelCount += 1
                        }
                    }
                    if (increasedReducerLevelCount == 0) {
                        //create new TestState
                        abstractState = AbstractState(activity = activity, widgets = ArrayList(guiReducedWidgetGroup),
                                isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                                isOpeningKeyboard = isOpeningKeyboard,
                                staticWidgetMapping = staticMapping.second,
                                window = staticMapping.first,
                                rotation = autautMF.currentRotation,
                                isOutOfApplication = staticMapping.first is WTGOutScopeNode,
                                internet = internetStatus)

                        ABSTRACT_STATES.add(abstractState)
                        abstractState.guiStates.add(guiState)
                        initAbstractInteractions(abstractState)
                        break
                    }
                }*/
            } while (true)
        }
        return abstractState
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
        return abstractStateList.find {
            it !is VirtualAbstractState
                    && it.activity == activity
                    && rotation == it.rotation
                    && it.isOpeningKeyboard == isOpeningKeyboard
                    && it.internet == internetStatus
                    && hasSameWidgetGroups(guiReducedAttributeValuationSet.toSet(), it.attributeValuationSets.toSet())

        }
    }

    fun refineAbstractState(abstractStates: List<AbstractState>,
                            guiState: State<*>,
                            window: WTGNode,
                            rotation: Rotation,
                            internetStatus: InternetStatus): AbstractState {
        val activity = window.activityClass
        val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
        val isOpeningKeyboard = guiState.visibleTargets.filter { it.isKeyboard }.isNotEmpty()
        val widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity,autautMF.packageName)
        val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
        val matchingTestState = findAbstractState(abstractStates, guiReducedWidgetGroup, activity, rotation, isOpeningKeyboard, internetStatus)
        if (matchingTestState != null) {
            if (!matchingTestState.guiStates.contains(guiState)) {
                matchingTestState.guiStates.add(guiState)
            }
            return matchingTestState
        }
        //create new TestState
        val staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, window)
        val abstractState = AbstractState(activity = activity, attributeValuationSets = ArrayList(guiReducedWidgetGroup),
                isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                isOutOfApplication = staticMapping.first is WTGOutScopeNode,
                isOpeningKeyboard = isOpeningKeyboard,
                staticWidgetMapping = staticMapping.second,
                window = staticMapping.first,
                rotation = rotation,
                internet = internetStatus)
        ABSTRACT_STATES.add(abstractState)
        abstractState.guiStates.add(guiState)
        initAbstractInteractions(abstractState,guiState)
        return abstractState
    }

    private fun havingSameResourceId(staticWidgetList: ArrayList<StaticWidget>): Boolean {
        if (staticWidgetList.isEmpty())
            return false
        var resourceId: String = staticWidgetList.first().resourceIdName
        staticWidgetList.forEach {
            if (!it.resourceIdName.equals(resourceId)) {
                return false
            }
        }
        return true
    }

    private fun initAbstractInteractions(abstractState: AbstractState, guiState: State<*>?) {
        //create implicit non-widget interactions
        val nonWidgetStaticEdges = autautMF.wtg.edges(abstractState.window).filter { it.label.widget == null }
        //create implicit widget interactions from static Node
        val widgetStaticEdges = autautMF.wtg.edges(abstractState.window).filter { it.label.widget != null }

        nonWidgetStaticEdges
                .filter {
                    it.label.eventType!=EventType.implicit_back_event}
                .filterNot { it.source.data == it.destination?.data }
                .forEach { staticEdge ->
            val isTargetEvent = autautMF.allTargetStaticEvents.contains(staticEdge.label)
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.window == destStaticNode && it is VirtualAbstractState }
            if (destAbstractState != null) {
                var abstractAction = abstractState.actionCount.keys.find { it.actionType == staticEdge.label.convertToExplorationActionName() }
                if (abstractAction == null) {
                    abstractAction = AbstractAction(
                            actionType = staticEdge.label.convertToExplorationActionName(),
                            extra = staticEdge.label.data)
                } else {
                    if (abstractAction.extra == null) {
                        abstractAction.extra = staticEdge.label.data
                    }
                }
                abstractState.actionCount.put(abstractAction, 0)

                val abstractEdge = autautMF.abstractTransitionGraph.edges(abstractState).find {
                    it.label.isImplicit
                            && it.label.abstractAction == abstractAction
                            && it.label.data == staticEdge.label.data
                            && it.label.prevWindow == null
                }

                var abstractInteraction: AbstractInteraction
                if (abstractEdge != null) {
                    abstractInteraction = abstractEdge.label
                    if (!abstractState.staticEventMapping.containsKey(abstractInteraction.abstractAction)) {
                        abstractState.staticEventMapping.put(abstractInteraction.abstractAction, arrayListOf(staticEdge.label))
                    } else {
                        if (!abstractState.staticEventMapping[abstractInteraction.abstractAction]!!.contains(staticEdge.label)) {
                            abstractState.staticEventMapping[abstractInteraction.abstractAction]!!.add(staticEdge.label)
                        }
                    }
                } else {
                    abstractInteraction = AbstractInteraction(abstractAction = abstractAction,
                            isImplicit = true,
                            prevWindow = null,
                            data = staticEdge.label.data,
                            fromWTG = !staticEdge.label.createdAtRuntime
                            )
                    staticEdge.label.modifiedMethods.forEach {
                        abstractInteraction.modifiedMethods.put(it.key, false)
                    }
                    abstractState.abstractInteractions.add(abstractInteraction)
                    abstractState.staticEventMapping.put(abstractInteraction.abstractAction, arrayListOf(staticEdge.label))
                }
                if (autautMF.allTargetStaticEvents.contains(staticEdge.label)) {
                    abstractState.targetActions.add(abstractInteraction.abstractAction)
                }
                autautMF.abstractTransitionGraph.add(abstractState, destAbstractState, abstractInteraction)
            }
        }
        widgetStaticEdges
                .filterNot { it.source.data == it.destination?.data }
                .forEach { staticEdge ->
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.window == destStaticNode && it is VirtualAbstractState }
            if (destAbstractState != null) {
                val widgetGroups = abstractState.staticWidgetMapping.filter { m -> m.value.contains(staticEdge.label.widget) }.map { it.key }
                if (widgetGroups.isEmpty() && abstractState is VirtualAbstractState) {
                    //create a fake widgetGroup
                    val staticWidget = staticEdge.label.widget!!
                    val attributePath = AttributePath()
                    attributePath.localAttributes.put(AttributeType.resourceId, staticWidget.resourceIdName)
                    attributePath.localAttributes.put(AttributeType.className, staticWidget.className)
                    val widgetGroup = AttributeValuationSet(attributePath = attributePath, cardinality = Cardinality.ONE)
                    abstractState.addWidgetGroup(widgetGroup)
                    abstractState.staticWidgetMapping.put(widgetGroup, arrayListOf(staticWidget))
                }
            }
        }
        widgetStaticEdges
                .filterNot { it.source.data == it.destination?.data }
                .forEach { staticEdge ->
            val isTargetEvent = autautMF.allTargetStaticEvents.contains(staticEdge.label)
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.window == destStaticNode && it is VirtualAbstractState }
            if (destAbstractState != null) {
                val widgetGroups = abstractState.staticWidgetMapping.filter { m -> m.value.contains(staticEdge.label.widget) }.map { it.key }
                widgetGroups.forEach { wg ->
                    var widgetAbstractAction = abstractState.getAvailableActions().find {
                        it.actionType == staticEdge.label.convertToExplorationActionName()
                                && it.attributeValuationSet == wg
                    }
                    if (widgetAbstractAction == null) {
                        if (abstractState is VirtualAbstractState) {
                            widgetAbstractAction = AbstractAction(
                                    actionType = staticEdge.label.convertToExplorationActionName(),
                                    attributeValuationSet = wg,
                                    extra = staticEdge.label.data)
                        } else {
                            val actionName = staticEdge.label.convertToExplorationActionName()
                            if (actionName == AbstractActionType.ITEM_CLICK || actionName == AbstractActionType.ITEM_LONGCLICK) {
                                widgetAbstractAction = AbstractAction(
                                        actionType = staticEdge.label.convertToExplorationActionName(),
                                        attributeValuationSet = wg,
                                        extra = staticEdge.label.data)
                            }
                        }
                    } else {
                        if (widgetAbstractAction.extra == null) {
                            widgetAbstractAction.extra = staticEdge.label.data
                        }
                    }
                    if (widgetAbstractAction != null) {
                        if (!abstractState.getAvailableActions().contains(widgetAbstractAction)){
                            abstractState.addAction(widgetAbstractAction)
                        }
                        abstractState.staticEventMapping.put(widgetAbstractAction, arrayListOf(staticEdge.label))
                        val abstractEdge = autautMF.abstractTransitionGraph.edges(abstractState).find {
                            it.label.isImplicit
                                    && it.label.abstractAction == widgetAbstractAction
                                    && it.label.data == staticEdge.label.data
                        }
                        var abstractInteraction: AbstractInteraction
                        if (abstractEdge != null) {
                            abstractInteraction = abstractEdge.label
                            if (!abstractState.staticEventMapping.containsKey(abstractInteraction.abstractAction)) {
                                abstractState.staticEventMapping.put(abstractInteraction.abstractAction, arrayListOf(staticEdge.label))
                            } else {
                                if (!abstractState.staticEventMapping[abstractInteraction.abstractAction]!!.contains(staticEdge.label)) {
                                    abstractState.staticEventMapping[abstractInteraction.abstractAction]!!.add(staticEdge.label)
                                }
                            }
                        } else {
                            abstractInteraction = AbstractInteraction(abstractAction = widgetAbstractAction,
                                    isImplicit = true, prevWindow = null, data = staticEdge.label.data,
                                    fromWTG = !staticEdge.label.createdAtRuntime)
                            abstractState.abstractInteractions.add(abstractInteraction)

                        }
                        if (autautMF.allTargetStaticEvents.contains(staticEdge.label)) {
                            abstractState.targetActions.add(abstractInteraction.abstractAction)
                        }
                        autautMF.abstractTransitionGraph.add(abstractState, destAbstractState, abstractInteraction)
                    }
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
                    val widgetGroup = abstractState.attributeValuationSets.find { it == virtualAbstractAction.attributeValuationSet }
                    if (widgetGroup != null) {
                        existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                attributeValuationSet = widgetGroup,
                                extra = virtualAbstractAction.extra)
                    } else if (guiState!=null){
                        val guiWidget = virtualAbstractAction.attributeValuationSet.getGUIWidgets(guiState).firstOrNull()
                        // guiState.widgets.find { virtualAbstractAction.widgetGroup.isAbstractRepresentationOf(it,guiState) }
                        if (guiWidget != null) {
                            val newAttributePath = AbstractionFunction.INSTANCE.reduce(guiWidget, guiState, abstractState.window.activityClass, HashMap(), HashMap())
                            val newWidgetGroup = AttributeValuationSet(newAttributePath, Cardinality.ONE)
                            existingAction = AbstractAction(actionType = virtualAbstractAction.actionType,
                                    attributeValuationSet = newWidgetGroup,
                                    extra = virtualAbstractAction.extra)
                        }
                    }
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
                val actionCount = virtualAbstractState.getActionCount(virtualAbstractAction)
                abstractState.setActionCount(existingAction, actionCount)
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
                }
                if (existingEdge == null) {
                    val abstractInteraction = AbstractInteraction(
                            abstractAction = existingAction,
                            isImplicit = true,
                            prevWindow = edge.label.prevWindow,
                            data = edge.label.data)
                    abstractState.abstractInteractions.add(abstractInteraction)
                    val newEdge = autautMF.abstractTransitionGraph.add(abstractState, edge.destination?.data, abstractInteraction)
                    // add edge condition
                    edgeCondition.forEach {
                        autautMF.abstractTransitionGraph.edgeConditions[newEdge]!!.add(it)
                    }

                }
            }
        }

        // add launch action
        if (launchAbstractStates.containsKey(LAUNCH_STATE.NORMAL_LAUNCH)) {
            val normalLaunchStates = launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
            val launchAction = AbstractAction(
                    actionType = AbstractActionType.LAUNCH_APP
            )
            val abstractInteraction = AbstractInteraction(abstractAction = launchAction,
                    isImplicit = true, prevWindow = null)
            abstractState.abstractInteractions.add(abstractInteraction)
            val launchAbstractState = getAbstractState(normalLaunchStates)
            autautMF.abstractTransitionGraph.add(abstractState, launchAbstractState, abstractInteraction)
        }
        if (launchAbstractStates.containsKey(LAUNCH_STATE.RESET_LAUNCH)) {
            // add reset action
            val resetLaunchStates = launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!
            val resetAction = AbstractAction(
                    actionType = AbstractActionType.RESET_APP
            )
            val resetAbstractInteraction = AbstractInteraction(abstractAction = resetAction,
                    isImplicit = true, prevWindow = null)
            abstractState.abstractInteractions.add(resetAbstractInteraction)
            val launchAbstractState = getAbstractState(resetLaunchStates)
            autautMF.abstractTransitionGraph.add(abstractState, launchAbstractState, resetAbstractInteraction)
        }


    }

    fun getAbstractState(guiState: State<*>): AbstractState? {
        val activity = autautMF.getStateActivity(guiState)
        val abstractStates = ABSTRACT_STATES.filter { it.guiStates.contains(guiState) }
        if (abstractStates.size == 1) {
            return abstractStates.single()
        } else if (abstractStates.size > 1) {
            log.debug("GUI states belong to more than one Abstract State")
            return abstractStates.last()
        }
        return null
    }

    fun hasSameWidgetGroups(widgetGroups1: Set<AttributeValuationSet>, widgetGroups2: Set<AttributeValuationSet>): Boolean {
        if (widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun getMatchingStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>
                                 , guiState: State<*>
                                 , activity: String
                                 , rotation: Rotation
                                 , window: WTGNode?): Pair<WTGNode, HashMap<AttributeValuationSet, ArrayList<StaticWidget>>> {
        //check if the previous state is homescreen
        var bestMatchedNode: WTGNode? = null
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        if (window == null) {

            val allPossibleNodes = ArrayList<WTGNode>()
            if (activity.isBlank()) {
                return Pair(first = WTGOutScopeNode.getOrCreateNode(activity), second = HashMap())
            }
            //if the previous state is not homescreen
            //Get candidate nodes
            val activityNode = WTGActivityNode.allNodes.find { it.classType == activity }
            if (activityNode == null) {
                val newWTGNode =
                        if (guiState.widgets.any { it.packageName == autautMF.packageName }) {
                            WTGActivityNode.getOrCreateNode(
                                    nodeId = WTGActivityNode.getNodeId(),
                                    classType = activity
                            )
                        } else {
                            WTGOutScopeNode.getOrCreateNode(activity)
                        }
                val virtualAbstractState = VirtualAbstractState(newWTGNode.classType, newWTGNode, false)
                ABSTRACT_STATES.add(virtualAbstractState)
                return Pair(first = newWTGNode, second = HashMap())
            }

            val optionsMenuNode = autautMF.wtg.getOptionsMenu(activityNode)
            val contextMenuNodes = autautMF.wtg.getContextMenus(activityNode)
            val dialogNodes = ArrayList(autautMF.wtg.getDialogs(activityNode))
            //val dialogNodes = WTGDialogNode.allNodes
            WTGDialogNode.allNodes.filter { it.activityClass == activity }.forEach {
                if (!dialogNodes.contains(it)) {
                    dialogNodes.add(it)
                }
            }

            if (optionsMenuNode != null) {
                Helper.mergeOptionsMenuWithActivity(guiState, optionsMenuNode, activityNode, autautMF.wtg, autautMF)
            }
            val recentMethods = autautMF.statementMF!!.recentExecutedMethods.map {
                autautMF.statementMF!!.getMethodName(it)
            }
            if (isSameFullScreenDimension(rotation, guiTreeDimension)) {
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
                    val matchWeights = Helper.calculateMatchScoreForEachNode(guiState, allPossibleNodes, appName, autautMF)
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
                        if (isSameFullScreenDimension(rotation, guiTreeDimension)) {
                            bestMatchedNode = activityNode
                        } else {
                            val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                            bestMatchedNode = newWTGDialog
                        }
                    }
                } else {
                    val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                    bestMatchedNode = newWTGDialog
                }
            }
        } else {
            bestMatchedNode = window
        }
        if (isDimensionEmpty(bestMatchedNode!!, rotation, isOpeningKeyboard)) {
            setDimension(bestMatchedNode, rotation, guiTreeDimension, isOpeningKeyboard)
        }
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_AttributeValuationSetHashMap, guiState, bestMatchedNode!!)
        return Pair(first = bestMatchedNode!!, second = widgetGroup_staticWidgetHashMap)
    }

    private fun isSameFullScreenDimension(rotation: Rotation, guiTreeDimension: Rectangle): Boolean {
        if (rotation == Rotation.PORTRAIT) {
            if (guiTreeDimension.leftX == 0 && guiTreeDimension.width == autautMF.portraitScreenSurface.width) {
                if (guiTreeDimension.height / autautMF.portraitScreenSurface.height.toDouble() > 0.9) {
                    return true
                }
            }
            return false
        }
        if (guiTreeDimension.leftX == 0 && guiTreeDimension.width > 0.9 * autautMF.portraitScreenSurface.height) {
            if (guiTreeDimension.height / autautMF.portraitScreenSurface.width.toDouble() > 0.9) {
                return true
            }
        }
        return false
    }

    private fun createNewDialog(activity: String, activityNode: WTGActivityNode, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean): WTGDialogNode {
        val newWTGDialog = WTGDialogNode.getOrCreateNode(WTGDialogNode.getNodeId(), activity,false)
        newWTGDialog.activityClass = activity
        //autautMF.wtg.add(activityNode, newWTGDialog, FakeEvent(activityNode))
        setDimension(newWTGDialog, rotation, guiTreeDimension, isOpeningKeyboard)
        // regressionTestingMF.transitionGraph.copyNode(activityNode!!,newWTGDialog)
        createVirtualAbstractState(newWTGDialog)
        return newWTGDialog
    }

    private fun setDimension(bestMatchedNode: WTGNode, rotation: Rotation, guiTreeDimension: Rectangle, isOpeningKeyboard: Boolean) {
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

    private fun isSameDimension(window: WTGNode, guiTreeDimension: Rectangle, rotation: Rotation, isOpeningKeyboard: Boolean): Boolean {
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

    private fun isDimensionEmpty(window: WTGNode, rotation: Rotation, isOpeningKeyboard: Boolean): Boolean {
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


    fun getMatchingStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>, guiState: State<*>, window: WTGNode): Pair<WTGNode, HashMap<AttributeValuationSet, ArrayList<StaticWidget>>> {
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_AttributeValuationSetHashMap, guiState, window)
        return Pair(first = window, second = widgetGroup_staticWidgetHashMap)
    }
    val REFINEMENT_MAX = 25
    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>, abstractInteraction: AbstractInteraction): Int {
        val abstractionFunction = AbstractionFunction.INSTANCE
        val actionWidget = guiInteraction.targetWidget

        AbstractionFunction.backup(autautMF)

        var refinementGrainCount = 0
        if (actionWidget == null)
        {
            return 0
        }
        while (!validateModel(guiInteraction, actionGUIState)) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val tempFullAttributePaths = HashMap<Widget, AttributePath>()
            val tempRelativeAttributePaths = HashMap<Widget, AttributePath>()
            val attributePath = abstractionFunction.reduce(actionWidget, actionGUIState, actionAbstractState.window.activityClass, tempFullAttributePaths, tempRelativeAttributePaths)
             if (AbstractionFunction.INSTANCE.abandonedAttributePaths.contains(Pair(attributePath, abstractInteraction)))
                break
            if (abstractionFunction.increaseReduceLevel(attributePath, actionAbstractState.window.activityClass, false,guiInteraction.targetWidget!!,actionGUIState)) {
                /*if (!refineAbstractionFunction(actionAbstractState)) {
                    AbstractionFunction.restore()
                    refinementGrainCount = 0
                    rebuildModel(actionAbstractState.window)
                    AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Pair(attributePath, abstractInteraction))
                    break
                } else {
                }*/
                log.debug("Increase refinement")
                refinementGrainCount += 1
                rebuildModel(actionAbstractState.window)
                //rebuildPartly(guiInteraction,actionGUIState)
            } else {
                //rebuild all related GUI states
                log.debug("Restore refinement")
                AbstractionFunction.restore(autautMF)
                refinementGrainCount = 0
                rebuildModel(actionAbstractState.window)
                AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Pair(attributePath, abstractInteraction))
                break
            }
            if (refinementGrainCount>REFINEMENT_MAX) {
                break
            }
        }
        /*if (refinementGrainCount>0) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            rebuildModel(actionAbstractState.window)
        }*/
        //get number of Abstract Interaction
        log.debug("Refinement grain increased count: $refinementGrainCount")
        return refinementGrainCount
    }

    private fun rebuildPartly(guiInteraction: Interaction<*>, actionGUIState: State<*>) {
        val oldAbstractState = getAbstractState(actionGUIState)!!
        val abstractEdge = autautMF.abstractTransitionGraph.edges(oldAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }
        val newAbstractStates = ArrayList<AbstractState>()
        val possibleAbstractStates = ABSTRACT_STATES
        val processedGuiState = HashSet<State<*>>()

        val oldGuiStates = HashMap<AbstractState, List<State<*>>>()
        oldGuiStates.put(oldAbstractState, ArrayList(oldAbstractState.guiStates))
        oldAbstractState.guiStates.clear()
        measureTimeMillis {
            oldGuiStates[oldAbstractState]!!.filterNot { processedGuiState.contains(it) }.forEach { guiState ->
                processedGuiState.add(guiState)
                var internet = when (oldAbstractState.internet) {
                    InternetStatus.Enable -> true
                    InternetStatus.Disable -> false
                    else -> true
                }
                val abstractState = getOrCreateNewTestState(guiState, oldAbstractState.window.activityClass, oldAbstractState.rotation, internet,oldAbstractState.window)
                //val abstractState = refineAbstractState(possibleAbstractStates, guiState, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                if (!newAbstractStates.contains(abstractState)) {
                    newAbstractStates.add(abstractState)
                    autautMF.abstractStateVisitCount[abstractState] = 1
                } else {
                    autautMF.abstractStateVisitCount[abstractState] = autautMF.abstractStateVisitCount[abstractState]!! + 1
                }
                if (!abstractState.guiStates.contains(guiState)) {
                    abstractState.guiStates.add(guiState)
                }
                autautMF.guiState_AbstractStateMap.put(guiState,abstractState)
            }

            if (!newAbstractStates.contains(oldAbstractState)) {
                ABSTRACT_STATES.remove(oldAbstractState)
            }
        }.let {
            //log.debug("Recompute partly Abstract states took $it millis with ${processedGuiState.size} states")
        }

        val processedGUIInteractions = ArrayList<Interaction<Widget>>()
        val newEdges = ArrayList<Edge<*,*>>()

        //compute new abstract interactions
        measureTimeMillis {
            // process out-edges
            val outAbstractEdges = autautMF.abstractTransitionGraph.edges(oldAbstractState).toMutableList()
            outAbstractEdges.forEach {
                autautMF.abstractTransitionGraph.remove(it)
            }
            val inAbstractEdges = autautMF.abstractTransitionGraph.edges().filter { oldAbstractState == it.destination?.data }.toMutableList()
            inAbstractEdges.forEach {
                autautMF.abstractTransitionGraph.remove(it)
            }
            var explicitAbstractEdges = outAbstractEdges.filter { !it.label.isImplicit }
            explicitAbstractEdges.forEach { oldAbstractEdge ->
                if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                    //log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                }
                else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {

                    // Try to keep the edge
                    val newDestinationAbstractStates = ArrayList<AbstractState>()
                    if (oldAbstractState == oldAbstractEdge.destination?.data) {
                        newDestinationAbstractStates.addAll(newAbstractStates)
                    } else {
                        newDestinationAbstractStates.add(oldAbstractEdge.destination!!.data)
                    }
                    newAbstractStates.forEach { source ->
                        newDestinationAbstractStates.forEach { dest ->
                            autautMF.abstractTransitionGraph.add(source,dest,oldAbstractEdge.label)
                        }
                    }
                } else {

                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val interactions = oldAbstractEdge.label.interactions.toList()

                    /*val guiEdges = autautMF.stateGraph!!.edges().filter { guiEdge ->
                        oldAbstractEdge.label.interactions.contains(guiEdge.label)
                    }*/
                    interactions.forEach { interaction ->
                        if (processedGUIInteractions.contains(interaction)) {
                            //log.debug("Processed interaction in refining model")
                        } else {
                            processedGUIInteractions.add(interaction)
                            var sourceAbstractState =
                                    newAbstractStates.find {
                                        it.guiStates.any {
                                            it.stateId == interaction.prevState
                                        }
                                    }
                            if (sourceAbstractState == null)
                                sourceAbstractState = oldAbstractEdge.source.data

                            var destinationAbstractState = if (oldAbstractEdge.destination!!.data.window == oldAbstractState.window) {
                                newAbstractStates.find {
                                    it.guiStates.any {
                                        it.stateId == interaction.resState
                                    }
                                }
                            } else {
                                oldAbstractEdge.destination!!.data
                            }
                            if (destinationAbstractState == null)
                                destinationAbstractState = oldAbstractEdge.destination!!.data
                            val sourceState = autautMF.abstractStateList.find { it.stateId == interaction.prevState }!!
                            val destState = autautMF.abstractStateList.find { it.stateId == interaction.resState }!!
                            val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState, destinationAbstractState, interaction, sourceState,destState)
                            if (newEdge!=null) {
                                newEdges.add(newEdge)
                            }
                        }
                    }
                }
            }

            // process in-edges
            explicitAbstractEdges = inAbstractEdges.filter { !it.label.isImplicit }
            /*implicitAbstractEdges.filter { it.label.abstractAction.actionName.isPressBack()
                    || it.label.abstractAction.actionName.isLaunchApp()
                    || it.label.abstractAction.actionName == AbstractActionType.SEND_INTENT.actionName
            }.forEach {
                if (old_newAbstractStates.containsKey(it.destination!!.data)) {
                    val newDestAbstractStates = old_newAbstractStates[it.destination!!.data]!!
                    val newSourceAbstractStates = if (it.source.data.window == staticNode
                            && old_newAbstractStates.containsKey(it.source.data)) {
                        old_newAbstractStates[it.source.data]!!
                    } else {
                        arrayListOf(it.source.data)
                    }
                    if (newSourceAbstractStates.isNotEmpty() && newDestAbstractStates.isNotEmpty()) {
                        newSourceAbstractStates.forEach { source ->
                            newDestAbstractStates.forEach { dest ->
                                autautMF.abstractTransitionGraph.add(source, dest, it.label)
                            }
                        }

                    }
                }
            }*/
            explicitAbstractEdges.forEach { oldAbstractEdge ->
                if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                    // log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                }
                else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                    // Try to keep the edge
                    val newSourceAbstractStates = ArrayList<AbstractState>()
                    if (oldAbstractState == oldAbstractEdge.source.data) {
                        newSourceAbstractStates.addAll(newAbstractStates)
                    } else {
                        newSourceAbstractStates.add(oldAbstractEdge.destination!!.data)
                    }
                    newAbstractStates.forEach { dest ->
                        newSourceAbstractStates.forEach { source ->
                            autautMF.abstractTransitionGraph.add(source,dest,oldAbstractEdge.label)
                        }
                    }
                    //autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                } else {
                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val interactions = oldAbstractEdge.label.interactions.toList()
                    interactions.forEach { interaction ->
                        if (processedGUIInteractions.contains(interaction)) {
                            // log.debug("Processed interaction in refining model")
                        } else {
                            processedGUIInteractions.add(interaction)
                            var destinationAbstractState = newAbstractStates.find {
                                it.guiStates.any {
                                    it.stateId == interaction.resState
                                }
                            }
                            if (destinationAbstractState == null)
                                destinationAbstractState = oldAbstractEdge.destination!!.data

                            var sourceAbstractState = if (oldAbstractEdge.source.data.window == oldAbstractState.window) {
                                newAbstractStates.find {
                                    it.guiStates.any {
                                        it.stateId == interaction.prevState
                                    }
                                }
                            } else {
                                oldAbstractEdge.source.data
                            }
                            if (sourceAbstractState == null)
                                sourceAbstractState = oldAbstractEdge.source.data
                            val sourceState = autautMF.abstractStateList.find { it.stateId == interaction.prevState }!!
                            val destState = autautMF.abstractStateList.find { it.stateId == interaction.resState }!!
                            //let create new interaction
                            val newEdge= updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState, destState)
                            if (newEdge!=null) {
                                newEdges.add(newEdge)
                            }
                        }
                    }
                }

            }
        }.let {
            //log.debug("Recompute partly abstract interactions took $it millis with ${processedGUIInteractions.size} interactions")
        }

    }

    /* private fun refineAbstractionFunction(actionAbstractState: AbstractState): Boolean {
         var abstractStateRefined: Boolean = false
         val abstractionFunction = AbstractionFunction.INSTANCE
         actionAbstractState.widgets.forEach {
             if (abstractionFunction.increaseReduceLevel(it.attributePath, actionAbstractState.window.activityClass, false)) {
                 abstractStateRefined = true
             }
         }
         if (abstractStateRefined)
             return true
         return false
     }*/

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        val actionAbstractState = getAbstractState(actionGUIState)
        if (actionAbstractState==null)
            return true
        val abstractEdge = autautMF.abstractTransitionGraph.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }
        if (abstractEdge == null)
            return true
        if (abstractEdge.label.abstractAction.attributeValuationSet == null)
            return true
        if (abstractEdge.label.abstractAction.actionType == AbstractActionType.TEXT_INSERT)
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
        //val abstractStates = arrayListOf<AbstractState>(actionAbstractState)
        val abstractEdges = ArrayList<Edge<AbstractState, AbstractInteraction>>()
        abstractEdges.add(abstractEdge)
        //validate going to the same window
        abstractStates.forEach {
            val similarEdges = autautMF.abstractTransitionGraph.edges(it).filter {
                it != abstractEdge
                        && it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.data == abstractEdge.label.data
                        && it.label.prevWindow == abstractEdge.label.prevWindow
            }
            similarEdges.forEach {
                val similarEdgeCondition = autautMF.abstractTransitionGraph.edgeConditions[it]!!
                if (similarEdgeCondition.equals(edgeCondition)) {
                    abstractEdges.add(it)
                }
            }
        }
        val distinctAbstractInteractions1 = abstractEdges.distinctBy { it.destination?.data?.window?.activityClass }
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
                        && it.label.isImplicit == false
            }
            similarExplicitEdges.forEach {
                val similarEdgeCondition = autautMF.abstractTransitionGraph.edgeConditions[it]!!
                if (similarEdgeCondition.equals(edgeCondition)) {
                    abstractEdges.add(it)
                }
            }
        }

        val distinctAbstractInteractions2 = abstractEdges.distinctBy { it.destination?.data }
        if (distinctAbstractInteractions2.size > 1) {
            return false
        }

        return true
    }

    fun rebuildModel(staticNode: WTGNode) {
        //reset virtual abstract state
        ABSTRACT_STATES.removeIf { it.window == staticNode && it is VirtualAbstractState }
        AbstractStateManager.instance.widgetGroupFrequency.remove(staticNode)
        val virtualAbstractState = VirtualAbstractState(staticNode.activityClass, staticNode, staticNode is WTGLauncherNode)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState,null)
        //get all related abstract state
        val oldAbstractStates = ABSTRACT_STATES.filter { it.window == staticNode && it !is VirtualAbstractState }
        val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
        val possibleAbstractStates = ABSTRACT_STATES
        val processedGuiState = HashSet<State<*>>()

        val oldGuiStates = HashMap<AbstractState, List<State<*>>>()
        oldAbstractStates.forEach {
            oldGuiStates.put(it, ArrayList(it.guiStates))
            it.guiStates.clear()
        }

        var computeInteractionsTime: Long=0
        var computeGuiStateTime: Long=0
        var getGuiStateTime: Long=0
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
                    measureNanoTime {
                        val abstractState = getOrCreateNewTestState(guiState, oldAbstractState.window.activityClass, oldAbstractState.rotation, internet,oldAbstractState.window)
                        //val abstractState = refineAbstractState(possibleAbstractStates, guiState, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                        if (!newAbstractStates.contains(abstractState)) {
                            newAbstractStates.add(abstractState)
                            autautMF.abstractStateVisitCount[abstractState] = 1
                        } else {
                            autautMF.abstractStateVisitCount[abstractState] = autautMF.abstractStateVisitCount[abstractState]!! + 1
                        }
                        if (!abstractState.guiStates.contains(guiState)) {
                            abstractState.guiStates.add(guiState)
                        }
                        autautMF.guiState_AbstractStateMap.put(guiState,abstractState)
                    }.let {
                        computeGuiStateTime+=it
                    }
                }
                old_newAbstractStates.put(oldAbstractState, newAbstractStates)
            }

            oldAbstractStates.forEach { old ->
                if (old_newAbstractStates.values.find { it.contains(old) } == null) {
                    ABSTRACT_STATES.remove(old)
                }
            }
        }.let {
            log.debug("Recompute Abstract states took $it millis")
            log.debug("Recompute gui states took ${computeGuiStateTime/1000000} millis with ${processedGuiState.size} states")

        }
            //update launch states
            //launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!.removeIf { !ABSTRACT_STATES.contains(it) }
            //launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!.removeIf { !ABSTRACT_STATES.contains(it) }*/

            val allNewAbstractStates = old_newAbstractStates.map { it.value }.flatten()
            val stateId_AbstractStateMap = HashMap<ConcreteId, AbstractState>()
            allNewAbstractStates.forEach {abstractState ->
                abstractState.guiStates.forEach {
                    stateId_AbstractStateMap.put(it.stateId,abstractState)
                }

            }
            val processedGUIInteractions = ArrayList<Interaction<Widget>>()
            val newEdges = ArrayList<Edge<AbstractState,AbstractInteraction>>()
            val inEdgeMap = HashMap<AbstractState,HashSet<Edge<AbstractState,AbstractInteraction>>>()
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
                    outAbstractEdges.forEach {
                        autautMF.abstractTransitionGraph.remove(it)
                    }
                    val inAbstractEdges = inEdgeMap[oldAbstractState]!!
                    inAbstractEdges.forEach {
                        autautMF.abstractTransitionGraph.remove(it)
                    }

                    var explicitAbstractEdges = outAbstractEdges.filter { !it.label.isImplicit }

                    explicitAbstractEdges.forEach { oldAbstractEdge ->
                        if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                            //log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                        }
                        else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {

                            // Try to keep the edge
                            val newDestinationAbstractStates = ArrayList<AbstractState>()
                            if (old_newAbstractStates.containsKey(oldAbstractEdge.destination?.data)) {
                                newDestinationAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.destination!!.data)!!)
                            } else {
                                newDestinationAbstractStates.add(oldAbstractEdge.destination!!.data)
                            }
                            newAbstractStates.forEach { source ->
                                newDestinationAbstractStates.forEach { dest ->
                                    autautMF.abstractTransitionGraph.add(source,dest,oldAbstractEdge.label)
                                }
                            }
                        } else {
                            val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                            val interactions = oldAbstractEdge.label.interactions.toList()
                            /*val guiEdges = autautMF.stateGraph!!.edges().filter { guiEdge ->
                                oldAbstractEdge.label.interactions.contains(guiEdge.label)
                            }*/
                            interactions.forEach { interaction ->
                                if (processedGUIInteractions.contains(interaction)) {
                                    //log.debug("Processed interaction in refining model")
                                } else {
                                    processedGUIInteractions.add(interaction)
                                    var sourceAbstractState = stateId_AbstractStateMap[interaction.prevState]

                                    if (sourceAbstractState == null)
                                        sourceAbstractState = oldAbstractEdge.source.data

                                    var destinationAbstractState = if (oldAbstractEdge.destination!!.data.window == staticNode) {
                                        stateId_AbstractStateMap[interaction.resState]
                                    } else {
                                        oldAbstractEdge.destination!!.data
                                    }
                                    if (destinationAbstractState == null)
                                        destinationAbstractState = oldAbstractEdge.destination!!.data
                                    var sourceState: State<*>? = null
                                    var destState: State<*>? = null
                                    measureNanoTime {
                                         sourceState = autautMF.abstractStateList.find { it.stateId == interaction.prevState }!!
                                         destState = autautMF.abstractStateList.find { it.stateId == interaction.resState }!!
                                    }.let {
                                        getGuiStateTime+=it
                                    }

                                    measureNanoTime {
                                        val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!,destState!!)
                                        if (newEdge!=null) {
                                            newEdges.add(newEdge)
                                        }
                                    }.let {
                                        computeInteractionsTime+=it
                                    }

                                }
                            }
                        }
                    }

                    // process in-edges

                    explicitAbstractEdges = inAbstractEdges.filter { !it.label.isImplicit }

                    explicitAbstractEdges.forEach { oldAbstractEdge ->
                        if (oldAbstractEdge.label.abstractAction.isLaunchOrReset()) {
                           // log.debug("LaunchApp or ResetApp interaction. Do nothing.")
                        }
                        else if (oldAbstractEdge.label.abstractAction.isActionQueue()) {
                            // Try to keep the edge
                            val newSourceAbstractStates = ArrayList<AbstractState>()
                            if (old_newAbstractStates.containsKey(oldAbstractEdge.source.data)) {
                                newSourceAbstractStates.addAll(old_newAbstractStates.get(oldAbstractEdge.source.data)!!)
                            } else {
                                newSourceAbstractStates.add(oldAbstractEdge.destination!!.data)
                            }
                            newAbstractStates.forEach { dest ->
                                newSourceAbstractStates.forEach { source ->
                                    autautMF.abstractTransitionGraph.add(source,dest,oldAbstractEdge.label)
                                }
                            }
                            //autautMF.abstractTransitionGraph.remove(oldAbstractEdge)
                        } else {
                            val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                            val interactions = oldAbstractEdge.label.interactions.toList()
                            interactions.forEach { interaction ->
                                if (processedGUIInteractions.contains(interaction)) {
                                   // log.debug("Processed interaction in refining model")
                                } else {
                                    processedGUIInteractions.add(interaction)
                                    var destinationAbstractState = stateId_AbstractStateMap[interaction.resState]

                                    if (destinationAbstractState == null)
                                        destinationAbstractState = oldAbstractEdge.destination!!.data

                                    var sourceAbstractState = if (oldAbstractEdge.source.data.window == staticNode) {
                                        stateId_AbstractStateMap[interaction.prevState]
                                    } else {
                                        oldAbstractEdge.source.data
                                    }
                                    if (sourceAbstractState == null)
                                        sourceAbstractState = oldAbstractEdge.source.data

                                    var sourceState: State<*>? = null
                                    var destState: State<*>? = null
                                    measureNanoTime {
                                        sourceState = autautMF.abstractStateList.find { it.stateId == interaction.prevState }!!
                                        destState = autautMF.abstractStateList.find { it.stateId == interaction.resState }!!
                                    }.let {
                                        getGuiStateTime+=it
                                    }
                                    //let create new interaction
                                    measureNanoTime {
                                        val newEdge = updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState!!, destinationAbstractState!!, interaction, sourceState!!,destState!!)
                                        if (newEdge!=null) {
                                            newEdges.add(newEdge)
                                        }
                                    }.let {
                                        computeInteractionsTime+=it
                                    }
                                }
                            }
                        }
                    }
                }
            }.let {
                log.debug("Recompute abstract interactions took $it millis")
                log.debug("Recompute interactions took ${computeInteractionsTime/1000000} millis with ${processedGUIInteractions.size} interactions.")
                log.debug("Get gui state took ${getGuiStateTime/1000000} millis")
            }


            val allAbstractStates = ABSTRACT_STATES
            val launchState = launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
            val resetState = launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!
            val launchAbstractState = getAbstractState(launchState)!!
            val resetAbstractState = getAbstractState(resetState)!!

            measureTimeMillis {
                allAbstractStates.forEach { abstractState ->
                    val launchInteractions =autautMF.abstractTransitionGraph.edges(abstractState).filter {
                        it.label.abstractAction.isLaunchOrReset()
                    }
                    launchInteractions.forEach {
                        if (it.label.abstractAction.actionType ==AbstractActionType.LAUNCH_APP)
                            autautMF.abstractTransitionGraph.update(abstractState, it.destination?.data, launchAbstractState, it.label, it.label)
                        if (it.label.abstractAction.actionType == AbstractActionType.RESET_APP)
                            autautMF.abstractTransitionGraph.update(abstractState, it.destination?.data, resetAbstractState, it.label, it.label)
                    }
                }
            }.let {
                log.debug("Recompute Launch interactions took $it millis")
            }

    }

    private fun updateAbstractTransition(oldAbstractEdge: Edge<AbstractState, AbstractInteraction>
                                         , isTarget: Boolean
                                         , sourceAbstractState: AbstractState
                                         , destinationAbstractState: AbstractState
                                         , interaction: Interaction<*>
                                         , sourceState: State<*>
                                         , destState: State<*>): Edge<AbstractState, AbstractInteraction>? {
        //Extract text input widget data
        var newAbstractionInteraction: AbstractInteraction?=null
        var newEdge: Edge<AbstractState, AbstractInteraction>? = null
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
                //check if there is an existing abstract interaction
                val existingAbstractInteraction = sourceAbstractState.abstractInteractions.find {
                    it.isImplicit == false &&
                    it.abstractAction == abstractAction &&
                    it.prevWindow == oldAbstractEdge.label.prevWindow &&
                    it.data == oldAbstractEdge.label.data
                }

                if (existingAbstractInteraction != null) {
                    newAbstractionInteraction = existingAbstractInteraction
                } else {
                    //Create explicit edge for linked abstractState
                    newAbstractionInteraction = AbstractInteraction(
                            abstractAction = abstractAction,
                            isImplicit = false,
                            prevWindow = oldAbstractEdge.label.prevWindow,
                            data = oldAbstractEdge.label.data
                    )
                    sourceAbstractState.abstractInteractions.add(newAbstractionInteraction)

                }
                newAbstractionInteraction.interactions.add(interaction)
                newEdge = autautMF.abstractTransitionGraph.add(sourceAbstractState, destinationAbstractState, newAbstractionInteraction)
                if (!autautMF.abstractTransitionGraph.containsCondition(newEdge,condition))
                    autautMF.abstractTransitionGraph.addNewCondition(newEdge,condition)
                addImplicitAbstractInteraction(destState,sourceAbstractState, destinationAbstractState, newAbstractionInteraction, newAbstractionInteraction.prevWindow,condition)

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
                newAbstractionInteraction = existingAbstractEdge.label
                existingAbstractEdge.label.interactions.add(interaction)
            }
            sourceAbstractState.increaseActionCount(abstractAction)
        } else {
            //get widgetgroup
            var newWidgetGroup = sourceAbstractState.attributeValuationSets.find { it.isAbstractRepresentationOf(interaction.targetWidget!!, sourceState) }
            if (newWidgetGroup == null) {
                val newAttributePath = AbstractionFunction.INSTANCE.reduce(interaction.targetWidget!!, sourceState, sourceAbstractState.window.activityClass, HashMap(), HashMap())
                newWidgetGroup = AttributeValuationSet(newAttributePath, Cardinality.ONE)
                //newWidgetGroup.guiWidgets.add(interaction.targetWidget!!)
                //sourceAbstractState.addWidgetGroup(newWidgetGroup)

            }
            if (newWidgetGroup != null) {
                val abstractAction = AbstractAction(
                        actionType = oldAbstractEdge.label.abstractAction.actionType,
                        attributeValuationSet = newWidgetGroup,
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
                    newAbstractionInteraction = exisitingAbstractEdge.label
                    exisitingAbstractEdge.label.interactions.add(interaction)
                } else {
                    val existingAbstractInteraction = sourceAbstractState.abstractInteractions.find {
                        it.isImplicit == false &&
                                it.abstractAction == abstractAction &&
                                it.prevWindow == oldAbstractEdge.label.prevWindow &&
                                it.data == oldAbstractEdge.label.data
                    }

                    if (existingAbstractInteraction != null) {
                        newAbstractionInteraction = existingAbstractInteraction
                    } else {
                        //Create explicit edge for linked abstractState
                        newAbstractionInteraction = AbstractInteraction(
                                abstractAction = abstractAction,
                                isImplicit = false,
                                prevWindow = oldAbstractEdge.label.prevWindow,
                                data = oldAbstractEdge.label.data
                        )
                        sourceAbstractState.abstractInteractions.add(newAbstractionInteraction)

                    }

                    newAbstractionInteraction.interactions.add(interaction)

                    newEdge = autautMF.abstractTransitionGraph.add(
                            sourceAbstractState,
                            destinationAbstractState,
                            newAbstractionInteraction
                    )
                    if (!autautMF.abstractTransitionGraph.containsCondition(newEdge,condition))
                        autautMF.abstractTransitionGraph.addNewCondition(newEdge,condition)

                    addImplicitAbstractInteraction(destState,sourceAbstractState, destinationAbstractState, newAbstractionInteraction, newAbstractionInteraction.prevWindow,condition)

                }
                sourceAbstractState.increaseActionCount(abstractAction)
            }
        }
        if (newAbstractionInteraction != null) {
            // update coverage
            if (autautMF.guiInteractionCoverage.containsKey(interaction)) {
                val interactionCoverage = autautMF.guiInteractionCoverage.get(interaction)!!
                interactionCoverage.forEach {
                    newAbstractionInteraction.updateUpdateStatementCoverage(it,autautMF)
                }
            }
            val edgeMethodCoverage = autautMF.abstractTransitionGraph.methodCoverageInfo[oldAbstractEdge]!!
            autautMF.abstractTransitionGraph.methodCoverageInfo.put(newEdge!!, ArrayList(edgeMethodCoverage))
            val edgeStatementCoverage = autautMF.abstractTransitionGraph.statementCoverageInfo[oldAbstractEdge]!!
            autautMF.abstractTransitionGraph.statementCoverageInfo.put(newEdge!!, ArrayList(edgeStatementCoverage))
        }
        return newEdge
    }



    private fun getStaticWidgets(widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>, guiState: State<*>, staticNode: WTGNode): HashMap<AttributeValuationSet, ArrayList<StaticWidget>> {
        val result: HashMap<AttributeValuationSet, ArrayList<StaticWidget>> = HashMap()
        val actionableWidgets = ArrayList<Widget>()
        actionableWidgets.addAll(Helper.getVisibleWidgets(guiState))
        if (actionableWidgets.isEmpty()) {
            actionableWidgets.addAll(guiState.widgets.filterNot { it.isKeyboard })
        }
        val unmappedWidgets = actionableWidgets
        val mappedStaticWidgets = ArrayList<StaticWidget>()
        unmappedWidgets.groupBy { widget_AttributeValuationSetHashMap[it] }.filter { it.key != null }.forEach {
            it.value.forEach { w ->
                val staticWidgets = Helper.getStaticWidgets(w, guiState, staticNode, true, autautMF)
                //if a widgetGroup has more
                if (staticWidgets.isNotEmpty()) {
                    result.put(it.key!!, ArrayList(staticWidgets))
                }
            }
        }
        return result
    }

    fun addImplicitAbstractInteraction(currentState: State<*>, prevAbstractState: AbstractState, currentAbstractState: AbstractState, abstractInteraction: AbstractInteraction, prevprevWindow: WTGNode?, edgeCondition: HashMap<Widget,String>) {
        //AutAutMF.log.debug("Add implicit abstract interaction")
        var addedCount = 0
        var processedStateCount = 0
        // add implicit back events
        addedCount = 0
        processedStateCount = 0
        val implicitBackWindow = if (currentAbstractState.isOpeningKeyboard) {
            currentAbstractState.window
        } else if (prevAbstractState.window == currentAbstractState.window) {
            prevprevWindow
        } else {
            prevprevWindow
        }

        //We don't need create implicit back transition in case state unchanged
        if (prevAbstractState != currentAbstractState
                &&  !abstractInteraction.abstractAction.isLaunchOrReset()) {
            val backAbstractAction = AbstractAction(actionType = AbstractActionType.PRESS_BACK,
                    attributeValuationSet = null)
            //check if there is any pressback action go to another window
            if (!autautMF.abstractTransitionGraph.edges(currentAbstractState).any {
                        it.destination!= null &&
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
                val lastIndexOfCurrentState = autautMF.abstractStateList.indexOfLast {
                    it == currentState
                }
                var backAbstractState: AbstractState? = null
                autautMF.abstractStateList.forEachIndexed { index, guiState ->
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
                    processedStateCount += 1
                    val backAbstractInteraction = AbstractInteraction(
                            abstractAction = backAbstractAction,
                            isImplicit = true,
                            prevWindow = implicitBackWindow
                    )
                    val implicitAbstractInteraction = getSimilarAbstractInteraction(backAbstractInteraction, currentAbstractState, abstractState, prevprevWindow)
                    if (implicitAbstractInteraction != null) {
                        if (prevAbstractState != currentAbstractState) {
                            val edge = autautMF.abstractTransitionGraph.add(currentAbstractState, abstractState, implicitAbstractInteraction)
                            // add edge condition

                        }

                        addedCount += 1
                    }
                }
            }
        }
        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")

        if (abstractInteraction.abstractAction.actionType == AbstractActionType.SWIPE
                && abstractInteraction.abstractAction.attributeValuationSet != null
                && prevAbstractState != currentAbstractState
        ) {
            //check if the swipe action changed the content
            if (currentAbstractState.attributeValuationSets.contains(abstractInteraction.abstractAction.attributeValuationSet)) {
                val currentWidgetGroup = currentAbstractState.attributeValuationSets.find { it == abstractInteraction.abstractAction.attributeValuationSet }!!
                if (!currentWidgetGroup.havingSameContent(currentAbstractState, abstractInteraction.abstractAction.attributeValuationSet!!, prevAbstractState)) {
                    //add implicit sysmetric action
                    val swipeDirection = abstractInteraction.abstractAction.extra
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
                                && it.attributeValuationSet == abstractInteraction.abstractAction.attributeValuationSet
                                && it.extra == inverseSwipeDirection
                    }
                    if (inverseAbstractAction != null) {
                        val inverseAbstractInteraction = AbstractInteraction(
                                abstractAction = inverseAbstractAction,
                                data = inverseAbstractAction.extra,
                                prevWindow = implicitBackWindow,
                                isImplicit = true
                        )
                        currentAbstractState.abstractInteractions.add(inverseAbstractInteraction)
                        autautMF.abstractTransitionGraph.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
                        currentAbstractState.increaseActionCount(inverseAbstractAction)
                    }
                }

            }
        }
        if (abstractInteraction.abstractAction.actionType == AbstractActionType.ROTATE_UI
                && prevAbstractState != currentAbstractState) {
            val inverseAbstractAction = currentAbstractState.getAvailableActions().find {
                it.actionType == AbstractActionType.ROTATE_UI
            }
            if (inverseAbstractAction != null) {
                val inverseAbstractInteraction = AbstractInteraction(
                        abstractAction = inverseAbstractAction,
                        prevWindow = implicitBackWindow,
                        isImplicit = true
                )
                currentAbstractState.abstractInteractions.add(inverseAbstractInteraction)
                autautMF.abstractTransitionGraph.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
                currentAbstractState.increaseActionCount(inverseAbstractAction)
            }
        }

        if (abstractInteraction.abstractAction.actionType == AbstractActionType.ENABLE_DATA
                || abstractInteraction.abstractAction.actionType == AbstractActionType.DISABLE_DATA
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
        val isTargetAction = prevAbstractState.targetActions.contains(abstractInteraction.abstractAction)

        val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == prevAbstractState.window }.firstOrNull()

        if (virtualAbstractState != null && !prevAbstractState.isOpeningKeyboard
                && !abstractInteraction.abstractAction.isLaunchOrReset()) {
            val abstractAction = abstractInteraction.abstractAction
            // get existing action
            var virtualAbstractAction = virtualAbstractState.getAvailableActions().find {
                it == abstractAction
            }
            if (virtualAbstractAction == null) {
                if (abstractAction.attributeValuationSet != null) {
                    val widgetGroup = virtualAbstractState.attributeValuationSets.find { it == abstractAction.attributeValuationSet }
                    if (widgetGroup != null) {
                        virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                                attributeValuationSet = widgetGroup,
                                extra = abstractAction.extra)

                    } else {
                        val newWidgetGroup = AttributeValuationSet(attributePath = abstractAction.attributeValuationSet.attributePath, cardinality = abstractAction.attributeValuationSet.cardinality)
                        virtualAbstractState.addWidgetGroup(newWidgetGroup)
                        virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                                attributeValuationSet = newWidgetGroup,
                                extra = abstractAction.extra)
                    }
                } else {
                    virtualAbstractAction = AbstractAction(actionType = abstractAction.actionType,
                            attributeValuationSet = null,
                            extra = abstractAction.extra)
                }
                virtualAbstractState.addAction(virtualAbstractAction)
            }
            virtualAbstractState.increaseActionCount(virtualAbstractAction)
            if (isTargetAction) {
                virtualAbstractState.targetActions.add(virtualAbstractAction)
            }
            /*if (prevAbstractState.rotation == Rotation.LANDSCAPE
                    && abstractAction.actionName == AbstractActionType.SWIPE.actionName) {
                //change direction of Swipe action
                if (abstractAction.extra == "SwipeUp") {
                    abstractAction.extra = "SwipeLeft"
                } else if (abstractAction.extra == "SwipeDown") {
                    abstractAction.extra = "SwipeLRight"
                } else if (abstractAction.extra == "SwipeLeft") {
                    abstractAction.extra = "SwipeLRight"
                } else {

                }

            }*/
            val implicitDestAbstractState = if (currentAbstractState!=prevAbstractState) {
                currentAbstractState
            } else {
                virtualAbstractState
            }
            val abstractEdge = autautMF.abstractTransitionGraph.edges(virtualAbstractState)
                    .filter {
                        it.label.abstractAction == virtualAbstractAction
                                && it.label.prevWindow == prevprevWindow
                                && it.destination?.data == implicitDestAbstractState
                                && it.label.data == abstractInteraction.data
                    }
            if (abstractEdge.isEmpty()) {
                val existingAbstractInteraction = autautMF.abstractTransitionGraph.edges(virtualAbstractState).find {
                    it.label.abstractAction == virtualAbstractAction
                            && it.label.prevWindow == prevprevWindow
                            && it.label.data == abstractInteraction.data
                }?.label
                val implicitAbstractInteraction =  if (existingAbstractInteraction!=null) {
                    existingAbstractInteraction
                } else
                    AbstractInteraction(
                            abstractAction = virtualAbstractAction,
                            isImplicit = true,
                            data = abstractInteraction.data,
                            prevWindow = prevprevWindow
                    ).also {
                        virtualAbstractState.abstractInteractions.add(it)
                    }

                val edge = autautMF.abstractTransitionGraph.add(virtualAbstractState, implicitDestAbstractState, implicitAbstractInteraction)
                if (!autautMF.abstractTransitionGraph.containsCondition(edge,edgeCondition))
                    autautMF.abstractTransitionGraph.addNewCondition(edge,edgeCondition)

            }

        }

        //do not add implicit transition if this is Launch/Reset/Swipe
        if (!abstractInteraction.abstractAction.isLaunchOrReset()) {
            val otherSameStaticNodeAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
                it !is VirtualAbstractState
                        && it.window == prevAbstractState.window
                        && it != prevAbstractState
                        && it.isOpeningKeyboard == prevAbstractState.isOpeningKeyboard
            }

            otherSameStaticNodeAbStates.forEach {
                processedStateCount += 1
                var implicitAbstractInteraction: AbstractInteraction?
                implicitAbstractInteraction = getSimilarAbstractInteraction(abstractInteraction, it, currentAbstractState, prevprevWindow)
                if (implicitAbstractInteraction != null) {
                    if (isTargetAction) {
                        it.targetActions.add(implicitAbstractInteraction.abstractAction)
                    }
                    if (prevAbstractState != currentAbstractState && prevAbstractState != it) {
                        /*val oldEdge = autautMF.abstractTransitionGraph.edges(it).find { it.label == implicitAbstractInteraction }
                        if (oldEdge != null) {
                            autautMF.abstractTransitionGraph.update(it, oldEdge.destination?.data, currentAbstractState, implicitAbstractInteraction, implicitAbstractInteraction)
                        } else {
                            autautMF.abstractTransitionGraph.add(it, currentAbstractState, implicitAbstractInteraction)
                        }*/
                        autautMF.abstractTransitionGraph.add(it, currentAbstractState, implicitAbstractInteraction)
                        val edge = autautMF.abstractTransitionGraph.add(it, currentAbstractState, implicitAbstractInteraction)
                        // add edge condition
                        if (!autautMF.abstractTransitionGraph.containsCondition(edge,edgeCondition)) {
                            autautMF.abstractTransitionGraph.addNewCondition(edge,edgeCondition)
                        }
                    } else if (currentAbstractState == prevAbstractState) {
                        autautMF.abstractTransitionGraph.add(it, it, implicitAbstractInteraction)
                        /*val oldEdge = autautMF.abstractTransitionGraph.edges(it).find {
                            it.label == implicitAbstractInteraction }
                        if (oldEdge != null) {
                            autautMF.abstractTransitionGraph.update(it, it, it, implicitAbstractInteraction, implicitAbstractInteraction)
                        } else {
                            autautMF.abstractTransitionGraph.add(it, it, implicitAbstractInteraction)
                        }*/

                    }

                    it.increaseActionCount(implicitAbstractInteraction.abstractAction)
                    addedCount += 1
                } else {
                    var existingAction = it.getAvailableActions().find {
                        it == abstractInteraction.abstractAction
                    }
                    if (existingAction != null) {
                        it.increaseActionCount(existingAction)
                    }
                }
            }
        }

        //update ResetApp && LaunchApp edge
        else if (abstractInteraction.abstractAction.isLaunchOrReset()) {
            val allAbstractStates = ABSTRACT_STATES
            var resetAppAction : AbstractAction?
            var launchAppAction: AbstractAction?
            if (abstractInteraction.abstractAction.actionType == AbstractActionType.RESET_APP) {
                 launchAppAction = AbstractAction.getLaunchAction()
                 resetAppAction = abstractInteraction.abstractAction
            } else {
                launchAppAction = abstractInteraction.abstractAction
                resetAppAction = null
            }

            allAbstractStates.forEach { abstractState ->
                updateLaunchTransitions(abstractState, launchAppAction!!, currentAbstractState, abstractInteraction, prevprevWindow)
                if (resetAppAction!=null) {
                    updateLaunchTransitions(abstractState, resetAppAction, currentAbstractState, abstractInteraction, prevprevWindow)
                }
            }
        }

        //process implicit item action
        val action = abstractInteraction.abstractAction.actionType
        if (abstractInteraction.abstractAction.isWidgetAction()
                && (action == AbstractActionType.CLICK || action == AbstractActionType.LONGCLICK)
                ) {
            val itemAction = when(action) {
                AbstractActionType.CLICK -> AbstractActionType.ITEM_CLICK
                else -> AbstractActionType.ITEM_LONGCLICK
            }
            //val parentWidgetGroups = HashSet<WidgetGroup>()
            var parentAttrPath = abstractInteraction.abstractAction.attributeValuationSet!!.attributePath.parentAttributePath
            while (parentAttrPath!=null) {
                val parentWidgetGroup = prevAbstractState.attributeValuationSets.find { it.attributePath == parentAttrPath }
                if (parentWidgetGroup != null) {
                    val itemAbtractAction = AbstractAction(
                            actionType = itemAction,
                            attributeValuationSet = parentWidgetGroup
                    )
                    parentWidgetGroup.actionCount.get(itemAbtractAction)
                    if (parentWidgetGroup.actionCount.containsKey(itemAbtractAction)) {
                        prevAbstractState.increaseActionCount(itemAbtractAction,updateSimilarAbstractState = true)
                        var implicitInteraction =
                         autautMF.abstractTransitionGraph.edges(prevAbstractState).find {
                            it.label.isImplicit == true
                                    && it.label.abstractAction == itemAbtractAction
                                    && it.label.prevWindow == prevprevWindow
                                    && it.destination?.data == currentAbstractState
                        }?.label
                        if (implicitInteraction == null) {
                            // create new explicit interaction
                            implicitInteraction = AbstractInteraction(
                                    abstractAction = itemAbtractAction,
                                    isImplicit = true,
                                    prevWindow = prevprevWindow
                            )
                            prevAbstractState.abstractInteractions.add(implicitInteraction)
                            autautMF.abstractTransitionGraph.add(prevAbstractState,currentAbstractState,implicitInteraction)
                        }
                        addImplicitAbstractInteraction(currentState, prevAbstractState, currentAbstractState, implicitInteraction, prevprevWindow, edgeCondition)
                    }
                }
                parentAttrPath = parentAttrPath.parentAttributePath
            }
        }

        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
        //AutAutMF.log.debug("Add implicit back interaction.")

    }

    private fun updateLaunchTransitions(abstractState: AbstractState, launchAppAction: AbstractAction, currentAbstractState: AbstractState, abstractInteraction: AbstractInteraction, prevprevWindow: WTGNode?) {
        val existingEdges = autautMF.abstractTransitionGraph.edges(abstractState).filter {
            it.label.abstractAction == launchAppAction
        }
        if (existingEdges.isNotEmpty()) {
            existingEdges.forEach {
                autautMF.abstractTransitionGraph.update(abstractState, it.destination?.data, currentAbstractState, it.label, it.label)
            }
        } else {
            var implicitAbstractInteraction = AbstractInteraction(
                    abstractAction = launchAppAction,
                    isImplicit = true,
                    data = abstractInteraction.data,
                    prevWindow = prevprevWindow
            )
            abstractState.abstractInteractions.add(implicitAbstractInteraction)
            autautMF.abstractTransitionGraph.add(abstractState, currentAbstractState, implicitAbstractInteraction)
        }
    }

    // This should not be implicit added to another abstract states
    private fun isSwipeScreenGoToAnotherWindow(abstractAction: AbstractAction, currentAbstractState: AbstractState, prevAbstractState: AbstractState) =
            (abstractAction.actionType == AbstractActionType.SWIPE && abstractAction.attributeValuationSet == null
                    && currentAbstractState.window != prevAbstractState.window)

    private fun getSimilarAbstractInteraction(abstractInteraction: AbstractInteraction, sourceAbstractState: AbstractState, destinationAbstractState: AbstractState, prevprevWindow: WTGNode?): AbstractInteraction? {
        var implicitAbstractInteraction: AbstractInteraction?

        if (abstractInteraction.abstractAction.attributeValuationSet == null) {
            //find existing interaction again
            val existingEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState).filter {
                it.label.abstractAction == abstractInteraction.abstractAction
                        && it.label.prevWindow == prevprevWindow
                        && it.label.data == abstractInteraction.data
                        && destinationAbstractState == it.destination?.data
            }
            if (existingEdge.isNotEmpty() && existingEdge.all { !it.label.isImplicit }) {
                return null
            }
            implicitAbstractInteraction = if (existingEdge.isNotEmpty()) {
                existingEdge.first().label
            } else {
                AbstractInteraction(
                        abstractAction = abstractInteraction.abstractAction,
                        isImplicit = true,
                        data = abstractInteraction.data,
                        prevWindow = prevprevWindow
                ).also {
                    sourceAbstractState.abstractInteractions.add(it)
                }
            }

        } else {
            //find Widgetgroup
            val widgetGroup = sourceAbstractState.attributeValuationSets.find { it.equals(abstractInteraction.abstractAction.attributeValuationSet) }
            if (widgetGroup != null) {
                //find existing interaction again
                val existingEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState).filter {
                    it.label.abstractAction == abstractInteraction.abstractAction
                            && it.label.prevWindow == prevprevWindow
                            && it.label.data == abstractInteraction.data
                            && destinationAbstractState == it.destination?.data
                }
                if (existingEdge.isNotEmpty() && existingEdge.all { !it.label.isImplicit }) {
                    return null
                }
                implicitAbstractInteraction = if (existingEdge.isNotEmpty()) {
                    existingEdge.first().label
                } else {
                    AbstractInteraction(
                            abstractAction = AbstractAction(
                                    actionType = abstractInteraction.abstractAction.actionType,
                                    attributeValuationSet = widgetGroup,
                                    extra = abstractInteraction.abstractAction.extra
                            ),
                            isImplicit = true,
                            data = abstractInteraction.data,
                            prevWindow = prevprevWindow
                    ).also {
                        sourceAbstractState.abstractInteractions.add(it)
                    }
                }
            } else {
                implicitAbstractInteraction = null
            }
        }
        /*if (implicitAbstractInteraction != null) {
            sourceAbstractState.addAction(implicitAbstractInteraction!!.abstractAction)
        }*/
        return implicitAbstractInteraction
    }
    fun getPotentialAbstractStates(): List<AbstractState> {
        return ABSTRACT_STATES.filterNot { it is VirtualAbstractState
                || it.window is WTGLauncherNode
                || it.window is WTGOutScopeNode}
    }

    companion object {
        val instance: AbstractStateManager by lazy {
            AbstractStateManager()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}