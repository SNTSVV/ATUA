package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.reducer.WidgetReducer
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.inputRepo.textInput.TextInput
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.LoggerFactory

class AbstractStateManager() {
    val ABSTRACT_STATES: ArrayList<AbstractState> = ArrayList()
    val launchAbstractStates: HashMap<LAUNCH_STATE, HashSet<AbstractState>> = HashMap()
    lateinit var appResetState: AbstractState
    lateinit var autautMF: AutAutMF
    lateinit var appName: String

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
            initAbstractInteractions(it)
        }

        launchAbstractStates.put(LAUNCH_STATE.NORMAL_LAUNCH, HashSet())
        launchAbstractStates.put(LAUNCH_STATE.RESET_LAUNCH, HashSet())
    }

    fun createVirtualAbstractState(window: WTGNode) {
        val virtualAbstractState = VirtualAbstractState(window.activityClass, window, false)
        ABSTRACT_STATES.add(virtualAbstractState)
        initAbstractInteractions(virtualAbstractState)
    }

    fun getOrCreateNewTestState(guiState: State<*>,
                                i_activity: String,
                                rotation: Rotation,
                                internet: Boolean,
                                prevWindow: WTGNode): AbstractState {
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
        } else if (activity.isBlank() || guiState.isRequestRuntimePermissionDialogBox) {
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
        } else if (guiState.isAppHasStoppedDialogBox) {
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
            log.info("Activity: $activity")
            val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
            val isOpeningKeyboard = guiState.visibleTargets.filter { it.isKeyboard }.isNotEmpty()
/*        if (isOpeningKeyboard)
        {
            var openingKeyboardState = ABSTRACT_STATES.find { it.isOpeningKeyboard &&
                it.activity == activity}
            if (openingKeyboardState != null)
            {
                if (!openingKeyboardState.guiStates.contains(guiState))
                {
                    openingKeyboardState.guiStates.add(guiState)
                }
            }
            else
            {
                openingKeyboardState = AbstractState(activity=activity,isOpeningKeyboard = true, window = WTGOutScopeNode.getOrCreateNode(),launchState = launchState, rotation = rotation)
                openingKeyboardState!!.guiStates.add(guiState)
                ABSTRACT_STATES.add(openingKeyboardState)
            }
            return openingKeyboardState
        }*/
            do {
                val widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity)
                TextInput.saveSpecificTextInputData(guiState)
                val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
                val matchingTestState = findAbstractState(ABSTRACT_STATES, guiReducedWidgetGroup, activity, rotation, isOpeningKeyboard, internetStatus)
                if (matchingTestState != null) {
                    if (!matchingTestState.guiStates.contains(guiState)) {
                        matchingTestState.guiStates.add(guiState)
                    }
                    return matchingTestState
                }
                val staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, activity, rotation)
                if (staticMapping.first.activityClass.isBlank()) {
                    staticMapping.first.activityClass = activity
                }
                val ambigousWidgetGroup = staticMapping.second.filter {
                    it.value.size > 1
                            //In certain cases, static analysis distinguishes same resourceId widgets based on unknown criteria.
                            && !havingSameResourceId(it.value)
                }
                if (ambigousWidgetGroup.isEmpty()) {
                    //create new TestState
                    abstractState = AbstractState(activity = activity,
                            widgets = ArrayList(guiReducedWidgetGroup),
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
                    initAbstractInteractions(abstractState)
                    break
                } else {
                    var increasedReducerLevelCount = 0
                    ambigousWidgetGroup.forEach {

                        if (AbstractionFunction.INSTANCE.increaseReduceLevel(it.key.attributePath, activity, false)) {
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
                }
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
                                  guiReducedWidgetGroup: List<WidgetGroup>,
                                  activity: String,
                                  rotation: Rotation,
                                  isOpeningKeyboard: Boolean,
                                  internetStatus: InternetStatus): AbstractState? {
        return abstractStateList.filterNot { it is VirtualAbstractState }.find {
            hasSameWidgetGroups(guiReducedWidgetGroup.toSet(), it.widgets.toSet())
                    && it.activity == activity
                    && rotation == it.rotation
                    && it.isOpeningKeyboard == isOpeningKeyboard
                    && it.internet == internetStatus
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
        val widget_WidgetGroupHashMap = StateReducer.reduce(guiState, activity)
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
        val abstractState = AbstractState(activity = activity, widgets = ArrayList(guiReducedWidgetGroup),
                isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                isOpeningKeyboard = isOpeningKeyboard,
                staticWidgetMapping = staticMapping.second,
                window = staticMapping.first,
                rotation = rotation,
                internet = internetStatus)
        ABSTRACT_STATES.add(abstractState)
        abstractState.guiStates.add(guiState)
        initAbstractInteractions(abstractState)
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

    private fun initAbstractInteractions(abstractState: AbstractState) {
        //create implicit non-widget interactions
        val nonWidgetStaticEdges = autautMF.wtg.edges(abstractState.window).filter { it.label.widget == null }
        //create implicit widget interactions from static Node
        val widgetStaticEdges = autautMF.wtg.edges(abstractState.window).filter { it.label.widget != null }

        nonWidgetStaticEdges.filter{it.label.eventType!=EventType.implicit_back_event}.forEach { staticEdge ->
            val isTargetEvent = autautMF.allTargetStaticEvents.contains(staticEdge.label)
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.window == destStaticNode && it is VirtualAbstractState }
            if (destAbstractState != null) {
                var abstractAction = abstractState.actionCount.keys.find { it.actionName == staticEdge.label.convertToExplorationActionName() }
                if (abstractAction == null) {
                    abstractAction = AbstractAction(
                            actionName = staticEdge.label.convertToExplorationActionName(),
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
                            isImplicit = true, prevWindow = null, data = staticEdge.label.data)
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
        widgetStaticEdges.forEach { staticEdge ->
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
                    val widgetGroup = WidgetGroup(attributePath = attributePath, cardinality = Cardinality.ONE)
                    abstractState.widgets.add(widgetGroup)
                    abstractState.staticWidgetMapping.put(widgetGroup, arrayListOf(staticWidget))
                }
            }
        }
        widgetStaticEdges.forEach { staticEdge ->
            val isTargetEvent = autautMF.allTargetStaticEvents.contains(staticEdge.label)
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.window == destStaticNode && it is VirtualAbstractState }
            if (destAbstractState != null) {
                val widgetGroups = abstractState.staticWidgetMapping.filter { m -> m.value.contains(staticEdge.label.widget) }.map { it.key }
                widgetGroups.forEach { wg ->
                    var widgetAbstractAction = abstractState.getAvailableActions().find {
                        it.actionName == staticEdge.label.convertToExplorationActionName()
                                && it.widgetGroup == wg
                    }
                    if (widgetAbstractAction == null) {
                        widgetAbstractAction = AbstractAction(
                                actionName = staticEdge.label.convertToExplorationActionName(),
                                widgetGroup = wg,
                                extra = staticEdge.label.data)
                    } else {
                        if (widgetAbstractAction.extra == null) {
                            widgetAbstractAction.extra = staticEdge.label.data
                        }
                    }
                    wg.actionCount.put(widgetAbstractAction, 0)
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
                                isImplicit = true, prevWindow = null, data = staticEdge.label.data)
                        abstractState.abstractInteractions.add(abstractInteraction)
                        abstractState.staticEventMapping.put(widgetAbstractAction, arrayListOf(staticEdge.label))
                    }
                    if (autautMF.allTargetStaticEvents.contains(staticEdge.label)) {
                        abstractState.targetActions.add(abstractInteraction.abstractAction)
                    }
                    autautMF.abstractTransitionGraph.add(abstractState, destAbstractState, abstractInteraction)
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

        val virtualEdges =  autautMF.abstractTransitionGraph.edges(virtualAbstractState).filter {
            //we will not process any self edge
            it.destination!!.data != virtualAbstractState
        }
        virtualEdges.forEach { edge ->
            val isTarget = virtualAbstractState.targetActions.contains(edge.label.abstractAction)
            // initAbstractActionCount
            val virtualAbstractAction = edge.label.abstractAction
            var existingAction = abstractState.getAvailableActions().find {
                it == edge.label.abstractAction
            }
            if (existingAction == null) {
                if (virtualAbstractAction.widgetGroup != null) {
                    val widgetGroup = abstractState.widgets.find { it == virtualAbstractAction.widgetGroup }
                    if (widgetGroup != null) {
                        existingAction = AbstractAction(actionName = edge.label.abstractAction.actionName,
                                widgetGroup = widgetGroup,
                                extra = edge.label.abstractAction.extra)
                    }
                } else {
                    existingAction = AbstractAction(actionName = edge.label.abstractAction.actionName,
                            widgetGroup = null,
                            extra = edge.label.abstractAction.extra)
                }
            }
            if (existingAction != null) {
                if (edge.label.abstractAction.actionName != "Swipe") {

                }
                val actionCount = virtualAbstractState.getActionCount(edge.label.abstractAction)
                abstractState.setActionCount(existingAction, actionCount)

                if (isTarget) {
                    abstractState.targetActions.add(existingAction)
                }

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
                    autautMF.abstractTransitionGraph.add(abstractState, edge.destination?.data, abstractInteraction)
                }
            }
        }
        // add launch action
        val normalLaunchStates = launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!
        val launchAction = AbstractAction(
                actionName = "LaunchApp"
        )
        val abstractInteraction = AbstractInteraction(abstractAction = launchAction,
                isImplicit = true, prevWindow = null)
        abstractState.abstractInteractions.add(abstractInteraction)

        normalLaunchStates.forEach { target ->
            autautMF.abstractTransitionGraph.add(abstractState, target, abstractInteraction)
        }
        // add reset action
        val resetLaunchStates = launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!
        val resetAction = AbstractAction(
                actionName = "ResetApp"
        )
        val resetAbstractInteraction = AbstractInteraction(abstractAction = resetAction,
                isImplicit = true, prevWindow = null)
        abstractState.abstractInteractions.add(resetAbstractInteraction)

        resetLaunchStates.forEach { target ->
            autautMF.abstractTransitionGraph.add(abstractState, target, resetAbstractInteraction)
        }


    }

    fun getAbstractState(guiState: State<*>): AbstractState? {
        val activity = autautMF.getStateActivity(guiState)
        val abstractState = ABSTRACT_STATES.find { it.guiStates.contains(guiState) }
        return abstractState
    }

    fun hasSameWidgetGroups(widgetGroups1: Set<WidgetGroup>, widgetGroups2: Set<WidgetGroup>): Boolean {
        if (widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun getMatchingStaticWidgets(widget_WidgetGroupHashMap: HashMap<Widget, WidgetGroup>, guiState: State<*>, activity: String, rotation: Rotation): Pair<WTGNode, HashMap<WidgetGroup, ArrayList<StaticWidget>>> {
        //check if the previous state is homescreen
        val guiTreeDimension = Helper.computeGuiTreeDimension(guiState)
        val isOpeningKeyboard = guiState.visibleTargets.any { it.isKeyboard }
        val allPossibleNodes = ArrayList<WTGNode>()
        if (activity.isBlank()) {
            return Pair(first = WTGOutScopeNode.getOrCreateNode(activity), second = HashMap())
        }
        //if the previous state is not homescreen
        //Get candidate nodes
        val activityNode = WTGActivityNode.allNodes.find { it.classType == activity }
        if (activityNode == null) {
            val newOutAppWindow = WTGOutScopeNode.getOrCreateNode(activity)
            val virtualAbstractState = VirtualAbstractState(newOutAppWindow.classType, newOutAppWindow, false)
            ABSTRACT_STATES.add(virtualAbstractState)

            return Pair(first = newOutAppWindow, second = HashMap())
        }

        val optionsMenuNode = autautMF.wtg.getOptionsMenu(activityNode)
        val contextMenuNodes = autautMF.wtg.getContextMenus(activityNode)
        val dialogNodes = ArrayList(autautMF.wtg.getDialogs(activityNode))
        WTGDialogNode.allNodes.filter { it.activityClass == activity }.forEach {
            if (!dialogNodes.contains(it)) {
                dialogNodes.add(it)
            }
        }

        if (optionsMenuNode != null) {
            Helper.mergeOptionsMenuWithActivity(guiState, optionsMenuNode, activityNode, autautMF.wtg, autautMF)
        }

        if (isSameFullScreenDimension(rotation, guiTreeDimension)) {
            allPossibleNodes.add(activityNode)
        } else {
            allPossibleNodes.addAll(contextMenuNodes.distinct())
            allPossibleNodes.addAll(dialogNodes.distinct())
            if (optionsMenuNode != null) {
                allPossibleNodes.add(optionsMenuNode)
            }
        }


        //Find the most similar node
        var bestMatchedNode: WTGNode
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
                    bestMatchedNode = topMatchingNodes.filter { it.value == sortByPercentage[sortByPercentage.firstKey()]!! }.entries.first().key
                }
            } else {
                if (guiTreeDimension == autautMF.portraitScreenSurface ||
                        guiTreeDimension == autautMF.landscapeScreenSurface) {
                    bestMatchedNode = activityNode!!
                } else {
                    val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                    bestMatchedNode = newWTGDialog
                    val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_WidgetGroupHashMap, guiState, bestMatchedNode)
                    return Pair(first = bestMatchedNode, second = widgetGroup_staticWidgetHashMap)
                }
            }
        } else {
            val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
            bestMatchedNode = newWTGDialog
            val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_WidgetGroupHashMap, guiState, bestMatchedNode)
            return Pair(first = bestMatchedNode, second = widgetGroup_staticWidgetHashMap)
        }

        if (isDimensionEmpty(bestMatchedNode, rotation, isOpeningKeyboard)) {
            setDimension(bestMatchedNode, rotation, guiTreeDimension, isOpeningKeyboard)
            /* if (bestMatchedNode is WTGOptionsMenuNode) {
                setDimension(bestMatchedNode,rotation, guiTreeDimension,isOpeningKeyboard)
            }
            else if (bestMatchedNode !is WTGActivityNode && !isOpeningKeyboard && guiTreeDimension.leftX > 0) {
                setDimension(bestMatchedNode,rotation, guiTreeDimension,isOpeningKeyboard)
            } else if (bestMatchedNode is WTGActivityNode && guiTreeDimension.leftX==0) {
                setDimension(bestMatchedNode, rotation, guiTreeDimension,isOpeningKeyboard)
            } else {
                // it can be assumed that a new dialog is popup
                // create new WTGDialog Window
                val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension,isOpeningKeyboard)
                bestMatchedNode = newWTGDialog
            }*/
        } else {
            // check if guistate is not in another rotation
            if (!isSameDimension(bestMatchedNode, guiTreeDimension, rotation, isOpeningKeyboard)) {
                // it can be assumed that a new dialog is popup
                // create new WTGDialog Window
                val newWTGDialog = createNewDialog(activity, activityNode, rotation, guiTreeDimension, isOpeningKeyboard)
                autautMF.wtg.mergeNode(bestMatchedNode, newWTGDialog)
                bestMatchedNode = newWTGDialog

            }
        }

        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_WidgetGroupHashMap, guiState, bestMatchedNode)
        return Pair(first = bestMatchedNode, second = widgetGroup_staticWidgetHashMap)
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
        val newWTGDialog = WTGDialogNode.getOrCreateNode(WTGDialogNode.getNodeId(), activity)
        newWTGDialog.activityClass = activity
        autautMF.wtg.add(activityNode, newWTGDialog, FakeEvent(activityNode))
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


    fun getMatchingStaticWidgets(widget_WidgetGroupHashMap: HashMap<Widget, WidgetGroup>, guiState: State<*>, window: WTGNode): Pair<WTGNode, HashMap<WidgetGroup, ArrayList<StaticWidget>>> {
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_WidgetGroupHashMap, guiState, window)
        return Pair(first = window, second = widgetGroup_staticWidgetHashMap)
    }

    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>, abstractInteraction: AbstractInteraction): Int {
        val abstractionFunction = AbstractionFunction.INSTANCE
        val actionWidget = guiInteraction.targetWidget

        AbstractionFunction.backup()

        var refinementGrainCount = 0
        if (actionWidget == null)
        {
            return 0
        }
        while (!validateModel(guiInteraction, actionGUIState)) {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            val tempFullAttributePaths = HashMap<Widget, AttributePath>()
            val tempRelativeAttributePaths = HashMap<Widget, AttributePath>()
            val attributePath = abstractionFunction.reduce(actionWidget, actionGUIState, actionAbstractState.activity, tempFullAttributePaths, tempRelativeAttributePaths)
            if (AbstractionFunction.INSTANCE.abandonedAttributePaths.contains(Pair(attributePath, abstractInteraction)))
                break
            if (!abstractionFunction.increaseReduceLevel(attributePath, actionAbstractState.activity, false)) {
                /*if (!refineAbstractionFunction(actionAbstractState)) {
                    AbstractionFunction.restore()
                    refinementGrainCount = 0
                    rebuildModel(actionAbstractState.window)
                    AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Pair(attributePath, abstractInteraction))
                    break
                } else {
                }*/
                refinementGrainCount += 1
                rebuildModel(actionAbstractState.window)
            } else {
                //rebuild all related GUI states
                AbstractionFunction.restore()
                refinementGrainCount = 0
                rebuildModel(actionAbstractState.window)
                AbstractionFunction.INSTANCE.abandonedAttributePaths.add(Pair(attributePath, abstractInteraction))
                break
            }
        }
        //get number of Abstract Interaction
        log.debug("Refinement grain increased count: $refinementGrainCount")
        return refinementGrainCount
    }

    private fun refineAbstractionFunction(actionAbstractState: AbstractState): Boolean {
        var abstractStateRefined: Boolean = false
        val abstractionFunction = AbstractionFunction.INSTANCE
        actionAbstractState.widgets.forEach {
            if (abstractionFunction.increaseReduceLevel(it.attributePath, actionAbstractState.activity, false)) {
                abstractStateRefined = true
            }
        }
        if (abstractStateRefined)
            return true
        return false
    }

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        val actionAbstractState = getAbstractState(actionGUIState)!!
        val abstractEdge = autautMF.abstractTransitionGraph.edges(actionAbstractState).find {
            it.label.interactions.contains(guiInteraction)
                    && !it.label.isImplicit
        }
        if (abstractEdge == null)
            return true
        if (abstractEdge.label.abstractAction.widgetGroup == null)
            return true
        if (abstractEdge.label.abstractAction.actionName == AbstractActionType.TEXT_INSERT.actionName)
            return true
        /*val abstractStates = if (guiInteraction.targetWidget == null) {
            ABSTRACT_STATES.filterNot{ it is VirtualAbstractState}. filter { it.window == actionAbstractState.window}
        } else {
            val widgetGroup = actionAbstractState.getWidgetGroup(guiInteraction.targetWidget!!, actionGUIState)
            ABSTRACT_STATES.filterNot { it is VirtualAbstractState }. filter { it.window == actionAbstractState.window
                    && it.widgets.contains(widgetGroup)}
        }*/
        val abstractStates = arrayListOf(actionAbstractState)
        //val abstractStates = arrayListOf<AbstractState>(actionAbstractState)
        val abstractInteractions = ArrayList<Edge<AbstractState, AbstractInteraction>>()
        abstractStates.forEach {
            val similarExplicitEdges = autautMF.abstractTransitionGraph.edges(it).filter {
                it.label.abstractAction == abstractEdge.label.abstractAction
                        && it.label.data == abstractEdge.label.data
                        && !it.label.isImplicit
            }
            abstractInteractions.addAll(similarExplicitEdges)
        }

        val distinctAbstractInteractions = abstractInteractions.distinctBy { it.destination?.data }
        if (distinctAbstractInteractions.size > 1)
            return false
        return true
    }

    fun rebuildModel(staticNode: WTGNode) {
        //get all related abstract state
        try {
            val oldAbstractStates = ABSTRACT_STATES.filter { it.window == staticNode && it !is VirtualAbstractState }
            val virtualAbstractState = ABSTRACT_STATES.find { it.window == staticNode && it is VirtualAbstractState }!!
            val allGUIStates = ArrayList<State<*>>()
            val possibleAbstractStates = ABSTRACT_STATES
            val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
            //compute new AbstractStates for each old one
            oldAbstractStates.forEach { oldAbstractState ->
                allGUIStates.addAll(oldAbstractState.guiStates)
                val newAbstractStates = ArrayList<AbstractState>()
                oldAbstractState.guiStates.forEach {
                    val abstractState = refineAbstractState(possibleAbstractStates, it, oldAbstractState.window, oldAbstractState.rotation, oldAbstractState.internet)
                    if (!newAbstractStates.contains(abstractState)) {
                        newAbstractStates.add(abstractState)
                        autautMF.abstractStateVisitCount[abstractState] = 1
                    } else {
                        autautMF.abstractStateVisitCount[abstractState] = autautMF.abstractStateVisitCount[abstractState]!! + 1
                    }
                }
                if (newAbstractStates.size > 1 || newAbstractStates.first() != oldAbstractState) {
                    old_newAbstractStates.put(oldAbstractState, newAbstractStates)
                    if (!newAbstractStates.contains(oldAbstractState))
                        ABSTRACT_STATES.remove(oldAbstractState)
                }
            }
            //update launch states
            launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!.removeIf { !ABSTRACT_STATES.contains(it) }
            launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!.removeIf { !ABSTRACT_STATES.contains(it) }

            val allNewAbstractStates = old_newAbstractStates.map { it.value }.flatten()
            val processedGUIInteractions = ArrayList<Edge<State<Widget>, Interaction<Widget>>>()

            //compute new abstract interactions
            old_newAbstractStates.entries.forEach {
                val oldAbstractState = it.key
                val newAbstractStates = it.value
                // process out-edges
                val oldAbstractEdges = autautMF.abstractTransitionGraph.edges(oldAbstractState)
                var implicitAbstractEdges = oldAbstractEdges.filter { it.label.isImplicit }
                var explicitAbstractEdges = oldAbstractEdges.filter { !it.label.isImplicit }
                /*implicitAbstractEdges.filter { it.label.abstractAction.actionName.isPressBack()
                        || it.label.abstractAction.actionName.isLaunchApp()
                        || it.label.abstractAction.actionName == AbstractActionType.SEND_INTENT.actionName}.forEach {
                    if (old_newAbstractStates.containsKey(it.source.data)) {
                        val newSourceAbstractStates = old_newAbstractStates[it.source.data]!!
                        val newDestAbstractStates = if (it.destination!!.data.window == staticNode
                                && old_newAbstractStates.containsKey(it.destination!!.data)) {
                            old_newAbstractStates[it.destination?.data]!!
                        } else {
                            arrayListOf(it.destination!!.data)
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
                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val guiEdges = autautMF.stateGraph!!.edges().filter { guiEdge ->
                        oldAbstractEdge.label.interactions.contains(guiEdge.label)
                    }
                    guiEdges.forEach { guiEdge ->
                        if (processedGUIInteractions.contains(guiEdge)) {
                            log.debug("Processed interaction in refining model")
                        } else {
                            processedGUIInteractions.add(guiEdge)
                            val sourceAbstractState = allNewAbstractStates.find {
                                it.guiStates.any {
                                    it.stateId == guiEdge.label.prevState
                                            || it == guiEdge.source?.data
                                }
                            } ?: oldAbstractEdge.source.data
                            val destinationAbstractState = allNewAbstractStates.find { it.guiStates.contains(guiEdge.destination?.data) }
                                    ?: oldAbstractEdge.destination!!.data
                            if (sourceAbstractState != null && destinationAbstractState != null) {
                                //let create new interaction
                                updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState, destinationAbstractState, guiEdge)
                            } else {
                                log.debug("Source Abstract State: " + sourceAbstractState)
                                log.debug("Destination Abstract State: " + destinationAbstractState)
                            }
                        }
                    }
                }
                oldAbstractEdges.toMutableList().forEach {
                    autautMF.abstractTransitionGraph.remove(it)
                }
                // process in-edges
                val inAbstractEdges = autautMF.abstractTransitionGraph.edges().filter { oldAbstractState == it.destination?.data }
                implicitAbstractEdges = inAbstractEdges.filter { it.label.isImplicit }
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
                    val isTarget = oldAbstractState.targetActions.contains(oldAbstractEdge.label.abstractAction)
                    val guiEdges = autautMF.stateGraph!!.edges().filter { guiEdge ->
                        oldAbstractEdge.label.interactions.contains(guiEdge.label)
                    }
                    guiEdges.forEach { guiEdge ->
                        if (processedGUIInteractions.contains(guiEdge)) {
                            log.debug("Processed interaction in refining model")
                        } else {
                            processedGUIInteractions.add(guiEdge)
                            val destinationAbstractState = allNewAbstractStates.find {
                                it.guiStates.any {
                                    it.stateId == guiEdge.label.resState
                                            || it == guiEdge.destination?.data
                                }
                            } ?: oldAbstractEdge.destination?.data
                            val sourceAbstractState = allNewAbstractStates.find { it.guiStates.contains(guiEdge.source.data) }
                                    ?: oldAbstractEdge.source.data
                            if (sourceAbstractState != null && destinationAbstractState != null) {
                                //let create new interaction
                                updateAbstractTransition(oldAbstractEdge, isTarget, sourceAbstractState, destinationAbstractState, guiEdge)
                            } else {
                                log.debug("Source Abstract State: " + sourceAbstractState)
                                log.debug("Destination Abstract State: " + destinationAbstractState)
                            }
                        }

                    }

                }
                inAbstractEdges.toMutableList().forEach {
                    autautMF.abstractTransitionGraph.remove(it)
                }
            }


        } catch (e: Exception) {
            log.info(e.toString())
        }
    }

    private fun updateAbstractTransition(oldAbstractEdge: Edge<AbstractState, AbstractInteraction>, isTarget: Boolean, sourceAbstractState: AbstractState, destinationAbstractState: AbstractState, guiEdge: Edge<State<*>, Interaction<*>>) {
        if (oldAbstractEdge.label.abstractAction.widgetGroup == null) {
            //Reuse Abstract action
            val abstractAction = oldAbstractEdge.label.abstractAction
            if (isTarget) {
                sourceAbstractState.targetActions.add(abstractAction)
            }
            //Update launch destination
            when (abstractAction.actionName) {
                "LaunchApp" -> {
                    launchAbstractStates[LAUNCH_STATE.NORMAL_LAUNCH]!!.add(destinationAbstractState)
                }
                "ResetApp" -> {
                    launchAbstractStates[LAUNCH_STATE.RESET_LAUNCH]!!.add(destinationAbstractState)
                }
            }

            //check if the interaction was created
            val abstractEdge = autautMF.abstractTransitionGraph.edges(sourceAbstractState, destinationAbstractState)
                    .find {
                        it.label.abstractAction == abstractAction && it.label.data == oldAbstractEdge.label.data
                                && it.label.prevWindow == oldAbstractEdge.label.prevWindow
                    }
            if (abstractEdge == null) {
                //Create explicit edge for linked abstractState
                val abstractInteraction = AbstractInteraction(
                        abstractAction = abstractAction,
                        isImplicit = false,
                        prevWindow = oldAbstractEdge.label.prevWindow,
                        data = oldAbstractEdge.label.data
                )
                sourceAbstractState.increaseActionCount(abstractAction)
                sourceAbstractState.abstractInteractions.add(abstractInteraction)
                abstractInteraction.interactions.add(guiEdge.label)
                autautMF.abstractTransitionGraph.add(sourceAbstractState, destinationAbstractState, abstractInteraction)
                addImplicitAbstractInteraction(sourceAbstractState, destinationAbstractState, abstractInteraction, abstractInteraction.prevWindow)
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
                abstractEdge.label.interactions.add(guiEdge.label)
            }
        } else {
            //get widgetgroup
            val newWidgetGroup = sourceAbstractState.widgets.find { it.isAbstractRepresentationOf(guiEdge.label.targetWidget!!, guiEdge.source.data) }
            if (newWidgetGroup == null) {
                // for debug
                assert(guiEdge.source.data.equals(guiEdge.label.prevState))
                val attributePath = WidgetReducer.reduce(guiEdge.label.targetWidget!!, guiEdge.source.data, sourceAbstractState.window.activityClass, HashMap<Widget, AttributePath>(), HashMap<Widget, AttributePath>())
                assert(sourceAbstractState.widgets.any { it.attributePath == attributePath })

            }
            if (newWidgetGroup != null) {
                val abstractAction = AbstractAction(
                        actionName = oldAbstractEdge.label.abstractAction.actionName,
                        widgetGroup = newWidgetGroup,
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
                    exisitingAbstractEdge.label.interactions.add(guiEdge.label)
                } else {
                    //Create explicit edge for linked abstractState
                    val abstractInteraction = AbstractInteraction(
                            abstractAction = abstractAction,
                            isImplicit = false,
                            prevWindow = oldAbstractEdge.label.prevWindow,
                            data = oldAbstractEdge.label.data

                    )
                    sourceAbstractState.increaseActionCount(abstractAction)
                    sourceAbstractState.abstractInteractions.add(abstractInteraction)
                    abstractInteraction.interactions.add(guiEdge.label)
                    autautMF.abstractTransitionGraph.add(
                            sourceAbstractState,
                            destinationAbstractState,
                            abstractInteraction
                    )
                    addImplicitAbstractInteraction(sourceAbstractState, destinationAbstractState, abstractInteraction, abstractInteraction.prevWindow)
                    /*//Create implicit edges for other abstractState
                                            val implicitAbstractInteraction = AbstractInteraction(
                                                    abstractAction = abstractAction,
                                                    isImplicit = true,
                                                    prevWindow = null
                                            )
                                            val otherAbstractStates = newAbstractStates.filterNot { it == sourceAbstractState }
                                            otherAbstractStates.forEach {
                                                if (it.widgets.contains(newWidgetGroup)) {
                                                    autautMF.abstractTransitionGraph.add(it, destinationAbstractState, implicitAbstractInteraction)
                                                    it.increaseActionCount(abstractAction)
                                                }
                                            }*/

                }
            }

            /* //update AbstractInteraction and Transition in VirtualAbstractState
                                     autautMF.abstractTransitionGraph.edges(virtualAbstractState).filter {
                                         it.label.abstractAction == oldAbstractEdge.label.abstractAction
                                     }*/
        }
    }

    private fun getStaticWidgets(widget_WidgetGroupHashMap: HashMap<Widget, WidgetGroup>, guiState: State<*>, staticNode: WTGNode): HashMap<WidgetGroup, ArrayList<StaticWidget>> {
        val result: HashMap<WidgetGroup, ArrayList<StaticWidget>> = HashMap()
        val actionableWidgets = ArrayList<Widget>()
        actionableWidgets.addAll(Helper.getVisibleWidgets(guiState))
        if (actionableWidgets.isEmpty()) {
            actionableWidgets.addAll(guiState.widgets.filterNot { it.isKeyboard })
        }
        val unmappedWidgets = actionableWidgets
        val mappedStaticWidgets = ArrayList<StaticWidget>()
        unmappedWidgets.groupBy { widget_WidgetGroupHashMap[it] }.filter { it.key != null }.forEach {
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

    fun addImplicitAbstractInteraction(prevAbstractState: AbstractState, currentAbstractState: AbstractState, abstractInteraction: AbstractInteraction, prevprevWindow: WTGNode?) {
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


        val backAbstractAction = AbstractAction(actionName = ActionType.PressBack.name,
                widgetGroup = null)
        currentAbstractState.addAction(backAbstractAction)
        val processingAbstractStates = ArrayList<AbstractState>()
        processingAbstractStates.add(currentAbstractState)

        val backAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it.window == implicitBackWindow
                    && !it.isOpeningKeyboard
        }

        backAbstractState.forEach { abstractState ->
            processedStateCount += 1

            val implicitAbstractInteraction = AbstractInteraction(
                    abstractAction = backAbstractAction,
                    isImplicit = true,
                    prevWindow = implicitBackWindow
            )
            processingAbstractStates.forEach {
                autautMF.abstractTransitionGraph.add(it, abstractState, implicitAbstractInteraction)
                addedCount += 1
            }
        }
        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")

        if (abstractInteraction.abstractAction.actionName == AbstractActionType.SWIPE.actionName
                && abstractInteraction.abstractAction.widgetGroup != null
        ) {
            //check if the swipe action changed the content
            if (currentAbstractState.widgets.contains(abstractInteraction.abstractAction.widgetGroup)) {
                val currentWidgetGroup = currentAbstractState.widgets.find { it == abstractInteraction.abstractAction.widgetGroup }!!
                if (!currentWidgetGroup.havingSameContent(currentAbstractState, abstractInteraction.abstractAction.widgetGroup!!, prevAbstractState)) {
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
                        it.actionName == "Swipe"
                                && it.widgetGroup == abstractInteraction.abstractAction.widgetGroup
                                && it.extra == inverseSwipeDirection
                    }
                    if (inverseAbstractAction != null) {
                        val inverseAbstractInteraction = AbstractInteraction(
                                abstractAction = inverseAbstractAction,
                                data = inverseAbstractAction.extra,
                                prevWindow = implicitBackWindow,
                                isImplicit = true
                        )
                        autautMF.abstractTransitionGraph.add(currentAbstractState, prevAbstractState, inverseAbstractInteraction)
                        //currentAbstractState.increaseActionCount(inverseAbstractAction)
                    }
                    return
                }

            }
        }
        if (abstractInteraction.abstractAction.actionName == AbstractActionType.ROTATE_UI.actionName
                || abstractInteraction.abstractAction.actionName == "EnableData"
                || abstractInteraction.abstractAction.actionName == "ResetData"
                || abstractInteraction.abstractAction.actionName == AbstractActionType.MINIMIZE_MAXIMIZE.actionName
        ) {
            /*if (currentAbstractState.rotation != prevAbstractState.rotation) {
                val rotateAction = currentAbstractState.getAvailableActions().find { it.actionName == AbstractActionType.ROTATE_UI.actionName }
                if (rotateAction != null) {
                    val rotateAbstractInteraction = AbstractInteraction (
                            abstractAction= rotateAction,
                            isImplicit = true,
                            prevWindow = prevprevWindow
                    )
                    autautMF.abstractTransitionGraph.add(currentAbstractState,prevAbstractState,rotateAbstractInteraction)
                }
            }*/
            return
        }
        //add to virtualAbstractState
        val isTargetAction = prevAbstractState.targetActions.contains(abstractInteraction.abstractAction)
        val virtualAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == prevAbstractState.window }.firstOrNull()

        if (virtualAbstractState != null) {
            val abstractAction = abstractInteraction.abstractAction
            // get existing action
            var virtualAbstractAction = virtualAbstractState.getAvailableActions().find {
                it == abstractAction
            }
            if (virtualAbstractAction == null) {
                if (abstractAction.widgetGroup != null) {
                    val widgetGroup = virtualAbstractState.widgets.find { it == abstractAction.widgetGroup }
                    if (widgetGroup != null) {
                        virtualAbstractAction = AbstractAction(actionName = abstractAction.actionName,
                                widgetGroup = widgetGroup,
                                extra = abstractAction.extra)

                    } else {
                        val newWidgetGroup = WidgetGroup(attributePath = abstractAction.widgetGroup.attributePath, cardinality = abstractAction.widgetGroup.cardinality)
                        virtualAbstractState.widgets.add(newWidgetGroup)
                        virtualAbstractAction = AbstractAction(actionName = abstractAction.actionName,
                                widgetGroup = newWidgetGroup,
                                extra = abstractAction.extra)
                    }
                } else {
                    virtualAbstractAction = AbstractAction(actionName = abstractAction.actionName,
                            widgetGroup = null,
                            extra = abstractAction.extra)
                }
                virtualAbstractState.addAction(virtualAbstractAction)
            }
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
            val abstractEdge = autautMF.abstractTransitionGraph.edges(virtualAbstractState)
                    .filter {
                        it.label.abstractAction == virtualAbstractAction
                                && it.label.prevWindow == prevprevWindow
                                && it.destination?.data == currentAbstractState
                                && it.label.data == abstractInteraction.data
                    }
            if (abstractEdge.isEmpty()) {
                val existingAbstractInteraction = autautMF.abstractTransitionGraph.edges(virtualAbstractState).find {
                    it.label.abstractAction == virtualAbstractAction
                            && it.label.prevWindow == prevprevWindow
                            && it.label.data == abstractInteraction.data
                }?.label
                val implicitAbstractInteraction = existingAbstractInteraction ?: AbstractInteraction(
                        abstractAction = virtualAbstractAction,
                        isImplicit = true,
                        data = abstractInteraction.data,
                        prevWindow = prevprevWindow
                )
                autautMF.abstractTransitionGraph.add(virtualAbstractState, currentAbstractState, implicitAbstractInteraction)
                virtualAbstractState.increaseActionCount(implicitAbstractInteraction.abstractAction)
            }
        }

        val otherSameStaticNodeAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it !is VirtualAbstractState && it.window == prevAbstractState.window
                    && it != prevAbstractState && it.isOpeningKeyboard == prevAbstractState.isOpeningKeyboard
        }

        otherSameStaticNodeAbStates.forEach {
            processedStateCount += 1
            var implicitAbstractInteraction: AbstractInteraction?
            implicitAbstractInteraction = getSimilarAbstractInteraction(abstractInteraction, it, prevprevWindow)
            if (implicitAbstractInteraction != null) {
                if (isTargetAction) {
                    it.targetActions.add(implicitAbstractInteraction.abstractAction)
                }
                autautMF.abstractTransitionGraph.add(it, currentAbstractState, implicitAbstractInteraction)
                it.increaseActionCount(implicitAbstractInteraction.abstractAction)
                addedCount += 1
            }
        }

        //AutAutMF.log.debug("Processed $processedStateCount abstract state - Added $addedCount abstract interaction.")
        //AutAutMF.log.debug("Add implicit back interaction.")

    }

    private fun getSimilarAbstractInteraction(abstractInteraction: AbstractInteraction, abstractState: AbstractState, prevprevWindow: WTGNode?): AbstractInteraction? {
        var implicitAbstractInteraction: AbstractInteraction?

        if (abstractInteraction.abstractAction.widgetGroup == null) {
            //find existing interaction again
            val existingEdge = autautMF.abstractTransitionGraph.edges(abstractState).filter {
                it.label.abstractAction == abstractInteraction.abstractAction
                        && it.label.prevWindow == prevprevWindow
                        && it.label.data == abstractInteraction.data
            }
            implicitAbstractInteraction = if (existingEdge.isNotEmpty()) {
                existingEdge.first().label
            } else {
                AbstractInteraction(
                        abstractAction = abstractInteraction.abstractAction,
                        isImplicit = true,
                        data = abstractInteraction.data,
                        prevWindow = prevprevWindow
                )
            }

        } else {
            //find Widgetgroup
            val widgetGroup = abstractState.widgets.find { it.equals(abstractInteraction.abstractAction.widgetGroup) }
            if (widgetGroup != null) {
                //find existing interaction again
                val existingEdge = autautMF.abstractTransitionGraph.edges(abstractState).filter {
                    it.label.abstractAction == abstractInteraction.abstractAction
                            && it.label.prevWindow == prevprevWindow
                }
                implicitAbstractInteraction = if (existingEdge.isNotEmpty()) {
                    existingEdge.first().label
                } else {
                    AbstractInteraction(
                            abstractAction = AbstractAction(
                                    actionName = abstractInteraction.abstractAction.actionName,
                                    widgetGroup = widgetGroup,
                                    extra = abstractInteraction.abstractAction.extra
                            ),
                            isImplicit = true,
                            data = abstractInteraction.data,
                            prevWindow = prevprevWindow
                    )
                }
            } else {
                implicitAbstractInteraction = null
            }
        }
        if (implicitAbstractInteraction != null) {
            abstractState.addAction(implicitAbstractInteraction!!.abstractAction)
        }
        return implicitAbstractInteraction
    }

    companion object {
        val instance: AbstractStateManager by lazy {
            AbstractStateManager()
        }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}