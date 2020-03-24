package org.droidmate.exploration.modelFeatures.regression.abstractStateElement

import org.droidmate.exploration.modelFeatures.regression.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer.AbstractionFunction
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer.StateReducer
import org.droidmate.exploration.modelFeatures.regression.staticModel.StaticWidget
import org.droidmate.exploration.modelFeatures.regression.staticModel.*
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.LoggerFactory
import java.util.logging.Logger

class AbstractStateManager () {
    val ABSTRACT_STATES: ArrayList<AbstractState> = ArrayList()
    lateinit var appResetState: AbstractState
    lateinit var regressionTestingMF: RegressionTestingMF
    lateinit var appName: String
    fun init(regressionTestingMF: RegressionTestingMF, appPackageName: String)
    {
        this.regressionTestingMF = regressionTestingMF
        this.appName = appPackageName

        //create initial abstract state (after App reset)
        appResetState = AppResetAbstractState()

        WTGNode.allNodes.forEach {
            val virtualAbstractState = VirtualAbstractState(it.classType,it,it is WTGLauncherNode)
            ABSTRACT_STATES.add(virtualAbstractState)
           // regressionTestingMF.abstractStateVisitCount[virtualAbstractState] = 0
        }

    }

    fun getOrCreateNewTestState(guiState: State<*>, i_activity: String, appPackageName: String, isFromLaunch: Boolean, refineEnabled: Boolean = true): AbstractState{
        var activity = i_activity
        if (guiState.isHomeScreen)
        {
            var homeState = ABSTRACT_STATES.find { it.isHomeScreen }
            if (homeState != null)
            {
                if (!homeState.guiStates.contains(guiState))
                {
                    homeState.guiStates.add(guiState)
                }
            }
            else
            {
                homeState = AbstractState(activity="",isHomeScreen = true, staticNode = WTGLauncherNode.instance!!,isFromLaunch = isFromLaunch)
                homeState!!.guiStates.add(guiState)
                ABSTRACT_STATES.add(homeState)
            }
            return homeState
        }
        else if(activity.isBlank() || !guiState.widgets.any { it.packageName == appPackageName
                        || it.packageName == "com.google.android.gms"
                        || it.packageName == "com.android.camera2"
                } || guiState.isRequestRuntimePermissionDialogBox)
        {
            var outOfAppState = ABSTRACT_STATES.find { it.isOutOfApplication }
            if (outOfAppState != null)
            {
                if (!outOfAppState.guiStates.contains(guiState))
                {
                    outOfAppState.guiStates.add(guiState)
                }
            }
            else
            {
                outOfAppState = AbstractState(activity="",isOutOfApplication = true, staticNode = WTGOutScopeNode.getOrCreateNode(),isFromLaunch = isFromLaunch)
                outOfAppState!!.guiStates.add(guiState)
                ABSTRACT_STATES.add(outOfAppState)
            }
            return outOfAppState
        }
        else if (guiState.isAppHasStoppedDialogBox)
        {
            var stopState = ABSTRACT_STATES.find { it.isAppHasStoppedDialogBox }
            if (stopState != null)
            {
                if (!stopState.guiStates.contains(guiState))
                {
                    stopState.guiStates.add(guiState)
                }
            }
            else
            {
                stopState = AbstractState(activity="",isAppHasStoppedDialogBox = true, staticNode = WTGOutScopeNode.getOrCreateNode(),isFromLaunch = isFromLaunch)
                stopState!!.guiStates.add(guiState)
                ABSTRACT_STATES.add(stopState)
            }
            return stopState
        }

        val isRequestRuntimeDialogBox = guiState.isRequestRuntimePermissionDialogBox
        val isOpeningKeyboard = guiState.visibleTargets.filter { it.isKeyboard }.isNotEmpty()
        if (isOpeningKeyboard)
        {
            var openingState = ABSTRACT_STATES.find { it.isOpeningKeyboard &&
                it.activity == activity}
            if (openingState != null)
            {
                if (!openingState.guiStates.contains(guiState))
                {
                    openingState.guiStates.add(guiState)
                }
            }
            else
            {
                openingState = AbstractState(activity=activity,isOpeningKeyboard = true, staticNode = WTGOutScopeNode.getOrCreateNode(),isFromLaunch = isFromLaunch)
                openingState!!.guiStates.add(guiState)
                ABSTRACT_STATES.add(openingState)
            }
            return openingState
        }
        do {
            val widget_WidgetGroupHashMap = StateReducer.reduce(guiState,activity)
            val guiReducedWidgetGroup = widget_WidgetGroupHashMap.map { it.value }.distinct()
            val matchingTestState = ABSTRACT_STATES.find { hasSameWidgetGroups(guiReducedWidgetGroup.toSet() ,it.widgets.toSet())
                    && it.activity == activity && regressionTestingMF.currentRotation == it.rotation}
            if (matchingTestState!=null) {
                if (!matchingTestState.guiStates.contains(guiState))
                {
                    matchingTestState.guiStates.add(guiState)
                }
                return matchingTestState
            }
            val staticMapping = getMatchingStaticWidgets(widget_WidgetGroupHashMap, guiState, activity, isFromLaunch)
            if (activity=="")
            {
                activity = staticMapping.first.classType
            }
            val ambigousWidgetGroup = staticMapping.second.filter { it.value.size > 1
                    //In certain cases, static analysis distinguishes same resourceId widgets based on unknown criteria.
                    && !havingSameResourceId(it.value)}
            if (ambigousWidgetGroup.isEmpty() || !refineEnabled)
            {
                //create new TestState
                val abstractState = AbstractState(activity=activity,widgets = ArrayList(guiReducedWidgetGroup),
                        isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                        isOpeningKeyboard = isOpeningKeyboard,
                        staticWidgetMapping = staticMapping.second,
                        staticNode = staticMapping.first,isFromLaunch = isFromLaunch,
                        rotation = regressionTestingMF.currentRotation)
                ABSTRACT_STATES.add(abstractState)
                abstractState.guiStates.add(guiState)
                initAbstractInteractions(abstractState)
                return abstractState
            }
            else
            {
                var increasedReducerLevelCount = 0
                ambigousWidgetGroup.forEach {
                    if(AbstractionFunction.INSTANCE.increaseReduceLevel(it.key.attributePath,activity,false)){
                        increasedReducerLevelCount += 1
                    }
                }
                if (increasedReducerLevelCount==0)
                {
                    //create new TestState
                    val abstractState = AbstractState(activity=activity,widgets = ArrayList(guiReducedWidgetGroup),
                            isRequestRuntimePermissionDialogBox = isRequestRuntimeDialogBox,
                            isOpeningKeyboard = isOpeningKeyboard,
                            staticWidgetMapping = staticMapping.second,
                            staticNode = staticMapping.first,isFromLaunch = isFromLaunch,
                            rotation = regressionTestingMF.currentRotation)

                    ABSTRACT_STATES.add(abstractState)
                    abstractState.guiStates.add(guiState)
                    initAbstractInteractions(abstractState)
                    return abstractState
                }
            }
        }while (true)
    }

    private fun havingSameResourceId(staticWidgetList: ArrayList<StaticWidget>): Boolean {
        if (staticWidgetList.isEmpty())
            return false
        var resourceId: String = staticWidgetList.first().resourceIdName
        staticWidgetList.forEach {
            if (!it.resourceIdName.equals(resourceId))
            {
                return false
            }
        }
        return true
    }

    private fun initAbstractInteractions(abstractState: AbstractState) {
        //create implicit non-widget interactions
        val nonWidgetStaticEdges = regressionTestingMF.transitionGraph.edges(abstractState.staticNode).filter { it.label.widget == null }
        nonWidgetStaticEdges.forEach {staticEdge->
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.staticNode == destStaticNode && it is VirtualAbstractState}
            if (destAbstractState!=null)
            {
                val abstractAction = AbstractAction(
                        actionName = staticEdge.label.convertToExplorationActionName(),
                        extra = staticEdge.label.data)
                val abstractEdge = regressionTestingMF.abstractTransitionGraph.edges(abstractState).find {
                    it.label.isImplicit && it.label.abstractAction == abstractAction
                }
                var abstractInteraction: AbstractInteraction
                if (abstractEdge!=null)
                {
                    abstractInteraction = abstractEdge.label

                }
                else
                {
                    abstractInteraction = AbstractInteraction(abstractAction = abstractAction,
                            isImplicit = true)
                    abstractState.abstractInteractions.add(abstractInteraction)
                    abstractState.staticEventMapping.put(abstractInteraction, staticEdge.label)

                }
                regressionTestingMF.abstractTransitionGraph.add(abstractState,destAbstractState,abstractInteraction)
            }
        }

        //create implicit widget interactions from static Node
        val widgetStaticEdges = regressionTestingMF.transitionGraph.edges(abstractState.staticNode).filter { it.label.widget != null }
        widgetStaticEdges.forEach {staticEdge ->
            val destStaticNode = staticEdge.destination!!.data
            val destAbstractState = ABSTRACT_STATES.find { it.staticNode == destStaticNode && it is VirtualAbstractState}
            if (destAbstractState!=null)
            {
                val widgetGroups = abstractState.staticWidgetMapping.filter { m-> m.value.contains(staticEdge.label.widget) }.map { it.key }
                widgetGroups.forEach { wg->
                    //TODO: Find existing AbstractInteraction
                    //TODO: Add new AbstractAction to AbstractState
                    val abstractAction = AbstractAction(
                            actionName =  staticEdge.label.convertToExplorationActionName(),
                            widgetGroup = wg,
                            extra = staticEdge.label.data)
                    val abstractEdge = regressionTestingMF.abstractTransitionGraph.edges(abstractState).find {
                        it.label.isImplicit
                                && it.label.abstractAction == abstractAction
                    }
                    var abstractInteraction: AbstractInteraction
                    if (abstractEdge!=null)
                    {
                        abstractInteraction = abstractEdge.label

                    }
                    else
                    {
                        abstractInteraction = AbstractInteraction(abstractAction = abstractAction,
                                isImplicit = true)
                        abstractState.abstractInteractions.add(abstractInteraction)
                        abstractState.staticEventMapping.put(abstractInteraction, staticEdge.label)
                    }
                    regressionTestingMF.abstractTransitionGraph.add(abstractState,destAbstractState,abstractInteraction)
                }

            }
        }
        //create implicit widget interactions from VirtualAbstractState
        val virtualAbstractState = ABSTRACT_STATES.filter { it is VirtualAbstractState && it.staticNode == abstractState.staticNode }.first()
        regressionTestingMF.abstractTransitionGraph.edges(virtualAbstractState).forEach { edge->
            val existingEdge = regressionTestingMF.abstractTransitionGraph.edges(abstractState).find {
                it.label.abstractAction == edge.label.abstractAction
            }
            if (existingEdge == null)
            {
                if (edge.label.abstractAction.widgetGroup==null)
                {
                    val abstractInteraction = AbstractInteraction(
                            abstractAction = edge.label.abstractAction,
                            isImplicit = true
                    )
                    regressionTestingMF.abstractTransitionGraph.add(abstractState,edge.destination?.data,abstractInteraction)
                }
                else
                {
                    val widgetGroup = abstractState.widgets.find { it.attributePath.equals(edge.label.abstractAction.widgetGroup!!.attributePath) }
                    if (widgetGroup!=null)
                    {
                        val abstractInteraction = AbstractInteraction(
                                abstractAction = AbstractAction(actionName = edge.label.abstractAction.actionName,
                                        widgetGroup = widgetGroup,
                                        extra = edge.label.abstractAction.extra),
                                isImplicit = true
                        )
                        regressionTestingMF.abstractTransitionGraph.add(abstractState,edge.destination?.data,abstractInteraction)
                    }
                }
            }

        }
    }

    fun getAbstractState(guiState: State<*>): AbstractState?{
        val activity = regressionTestingMF.getStateActivity(guiState)
        val abstractState = ABSTRACT_STATES.find { it.guiStates.contains(guiState) }
        return abstractState
    }

    fun hasSameWidgetGroups(widgetGroups1: Set<WidgetGroup>, widgetGroups2: Set<WidgetGroup>): Boolean
    {
        if(widgetGroups1.hashCode() == widgetGroups2.hashCode())
            return true
        return false
    }

    fun getMatchingStaticWidgets(widget_WidgetGroupHashMap: HashMap<Widget,WidgetGroup> , guiState: State<*>, activity: String, isFromLaunch: Boolean):Pair<WTGNode, HashMap<WidgetGroup, ArrayList<StaticWidget>>>
    {
        //check if the previous state is homescreen
        val allPossibleNodes = ArrayList<WTGNode>()
        if (isFromLaunch)
        {
            val launchNode = WTGLauncherNode.getOrCreateNode()
            val activityNode = WTGActivityNode.allNodes.first { it.classType == activity }
            allPossibleNodes.add(activityNode)
            val fromLaunchStaticNodes = regressionTestingMF.transitionGraph.edges(launchNode).filter { it.destination!=null }.map { it.destination!!.data }
            fromLaunchStaticNodes.forEach {
                val dialogNodes = regressionTestingMF.transitionGraph.getDialogs(it)
                allPossibleNodes.addAll(dialogNodes)
            }

        }
        else if (activity.isNotBlank())
        {
            //if the previous state is not homescreen
            //Get candidate nodes
            val activityNode = WTGActivityNode.allNodes.first { it.classType == activity }
            val optionsMenuNode = regressionTestingMF.transitionGraph.getOptionsMenu(activityNode)
            val contextMenuNodes = regressionTestingMF.transitionGraph.getContextMenus(activityNode)
            val dialogNodes = regressionTestingMF.transitionGraph.getDialogs(activityNode)
            if (optionsMenuNode != null) {
                if (!Helper.mergeOptionsMenuWithActivity(guiState, optionsMenuNode, activityNode, regressionTestingMF.transitionGraph, regressionTestingMF)) {
                    allPossibleNodes.add(optionsMenuNode)
                }
            }
            allPossibleNodes.add(activityNode)
            allPossibleNodes.addAll(contextMenuNodes.distinct())
            allPossibleNodes.addAll(dialogNodes.distinct())
        } else
        {

        }
        //Find the most similar node
        var  bestMatchedNode: WTGNode
        //try to calculate the match weight of each node.
        //only at least 1 widget matched is in the return result
        if (allPossibleNodes.size == 1) {
            bestMatchedNode = allPossibleNodes.first()
        }
        else {
            val matchWeights = Helper.calculateMatchScoreForEachNode(guiState, allPossibleNodes, appName, regressionTestingMF)
            //sort and get the highest ranking of the match list as best matched node
            val sortedWeight = matchWeights.toSortedMap(compareByDescending { matchWeights[it]!! })
            val topMatchingNodes = matchWeights.filter { it.value == sortedWeight[sortedWeight.firstKey()] }
            if (topMatchingNodes.size == 1) {
                bestMatchedNode = topMatchingNodes.entries.first().key
            } else {
                val sortByPercentage = topMatchingNodes.toSortedMap(compareByDescending { matchWeights[it]!! / it.widgets.size.toDouble() })
                bestMatchedNode = topMatchingNodes.filter { it.value == sortByPercentage[sortByPercentage.firstKey()]!! }.entries.random().key
            }
        }
        val widgetGroup_staticWidgetHashMap = getStaticWidgets(widget_WidgetGroupHashMap,guiState, bestMatchedNode)
        return Pair(first = bestMatchedNode,second =  widgetGroup_staticWidgetHashMap)
    }

    fun refineModel(guiInteraction: Interaction<*>, actionGUIState: State<*>){
        val abstractionFunction = AbstractionFunction.INSTANCE
        val actionWidget = guiInteraction.targetWidget
        AbstractionFunction.backup()

        while(!validateModel(guiInteraction, actionGUIState))
        {
            val actionAbstractState = getAbstractState(actionGUIState)!!
            if (actionWidget!=null) {
                val tempFullAttributePaths= HashMap<Widget,AttributePath>()
                val tempRelativeAttributePaths= HashMap<Widget,AttributePath>()
                val attributePath = abstractionFunction.reduce(actionWidget, actionGUIState,actionAbstractState.activity,tempFullAttributePaths,tempRelativeAttributePaths)
                if (AbstractionFunction.INSTANCE.abandonedAttributePaths.contains(attributePath))
                    break
                if (!abstractionFunction.increaseReduceLevel(attributePath,actionAbstractState.activity,false)) {
                    if(!refineAbstractState(actionAbstractState))
                    {
                        AbstractionFunction.restore()
                        AbstractionFunction.INSTANCE.abandonedAttributePaths.add(attributePath)
                        break
                    }
                    else
                    {

                        rebuildModel(actionAbstractState.staticNode)
                    }
                } else {
                    //rebuild all related GUI states

                    rebuildModel(actionAbstractState.staticNode)

                }
            }
            else
            {
                if (!refineAbstractState(actionAbstractState))
                {
                    AbstractionFunction.restore()
                    break
                }
                else
                {
                    rebuildModel(actionAbstractState.staticNode)
                }
            }
        }
        //get number of Abstract Interaction

    }

    private fun refineAbstractState(actionAbstractState: AbstractState): Boolean {
        var abstractStateRefined: Boolean = false
        val abstractionFunction = AbstractionFunction.INSTANCE
        actionAbstractState.widgets.forEach {
            if (abstractionFunction.increaseReduceLevel(it.attributePath,actionAbstractState.activity,true))
            {
                abstractStateRefined = true
            }
        }
        if (abstractStateRefined)
            return true
        return false
    }

    private fun validateModel(guiInteraction: Interaction<*>, actionGUIState: State<*>): Boolean {
        val actionAbstractState = getAbstractState(actionGUIState)!!
        val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(actionAbstractState).filter { ABSTRACT_STATES.contains(it.destination?.data) }
                .filter { it.label.interactions.contains(guiInteraction) && it.destination?.data !is VirtualAbstractState}
        val distinctAbstractInteractions = abstractInteractions.distinctBy { it.destination }
            if (distinctAbstractInteractions.size > 1)
                return false
        return true
    }

    fun rebuildModel(staticNode: WTGNode)
    {
        //get all related abstract state
        try {
            val oldAbstractStates = ABSTRACT_STATES.filter { it.staticNode == staticNode && it !is VirtualAbstractState }
            val allGUIStates = ArrayList<State<*>>()
            val old_newAbstractStates = HashMap<AbstractState, ArrayList<AbstractState>>()
            oldAbstractStates.forEach { oldAbstractState ->
                allGUIStates.addAll(oldAbstractState.guiStates)
                val newAbstractStates = ArrayList<AbstractState>()
                oldAbstractState.guiStates.forEach {
                    val abstractState = getOrCreateNewTestState(it, oldAbstractState.activity, appName, oldAbstractState.isFromLaunch, false)
                    if (!newAbstractStates.contains(abstractState)) {
                        newAbstractStates.add(abstractState)
                        regressionTestingMF.abstractStateVisitCount[abstractState] = 1
                    } else {
                        regressionTestingMF.abstractStateVisitCount[abstractState] = regressionTestingMF.abstractStateVisitCount[abstractState]!! + 1
                    }
                }
                old_newAbstractStates.put(oldAbstractState, newAbstractStates)
                if (!newAbstractStates.contains(oldAbstractState))
                    ABSTRACT_STATES.remove(oldAbstractState)
            }

            val processedGUIInteractions = ArrayList<Interaction<Widget>>()
            old_newAbstractStates.entries.forEach {
                val oldAbstractState = it.key
                val newAbstractStates = it.value
                val oldAbstractEdges = regressionTestingMF.abstractTransitionGraph.edges(oldAbstractState)
                oldAbstractEdges.forEach { oldAbstractEdge ->
                    val guiEdges = regressionTestingMF.stateGraph!!.edges().filter { guiEdge ->
                        oldAbstractEdge.label.interactions.contains(guiEdge.label)
                    }
                    guiEdges.forEach { guiEdge ->
                        if (processedGUIInteractions.contains(guiEdge.label)) {
                            log.debug("Processed interaction in refining model")
                        } else {
                            processedGUIInteractions.add(guiEdge.label)
                            val sourceAbstractState = newAbstractStates.find { it.guiStates.contains(guiEdge.source.data) }
                            val destinationAbstractState = newAbstractStates.find { it.guiStates.contains(guiEdge.destination?.data) }
                                    ?: oldAbstractEdge.destination!!.data
                            if (sourceAbstractState != null && destinationAbstractState != null) {
                                //let create new interaction
                                if (oldAbstractEdge.label.abstractAction.widgetGroup == null) {
                                    //Reuse Abstract action
                                    val abstractAction = oldAbstractEdge.label.abstractAction
                                    //check if the interaction was created
                                    val abstractEdge = regressionTestingMF.abstractTransitionGraph.edges(sourceAbstractState, destinationAbstractState).find { it.label.abstractAction == abstractAction }
                                    if (abstractEdge == null) {
                                        //Create explicit edge for linked abstractState
                                        val abstractInteraction = AbstractInteraction(
                                                abstractAction = abstractAction,
                                                isImplicit = false
                                        )
                                        sourceAbstractState.abstractInteractions.add(abstractInteraction)
                                        abstractInteraction.interactions.add(guiEdge.label)
                                        regressionTestingMF.abstractTransitionGraph.add(sourceAbstractState, destinationAbstractState, abstractInteraction)
                                        //Create implicit edges for other abstractState
                                        val implicitAbstractInteraction = AbstractInteraction(
                                                abstractAction = abstractAction,
                                                isImplicit = true
                                        )
                                        val otherAbstractStates = newAbstractStates.filterNot { it == sourceAbstractState }
                                        otherAbstractStates.forEach {
                                            regressionTestingMF.abstractTransitionGraph.add(it, destinationAbstractState, implicitAbstractInteraction)
                                        }
                                    } else {
                                        abstractEdge.label.interactions.add(guiEdge.label)
                                    }
                                } else {
                                    //get widgetgroup

                                    val newWidgetGroup = sourceAbstractState.widgets.find { it.isAbstractRepresentationOf(guiEdge.label.targetWidget!!, guiEdge.source.data) }
                                    if (newWidgetGroup != null) {
                                        val abstractAction = AbstractAction(
                                                actionName = oldAbstractEdge.label.abstractAction.actionName,
                                                widgetGroup = newWidgetGroup
                                        )
                                        //check if there is exisiting interaction
                                        val exisitingAbstractEdge = regressionTestingMF.abstractTransitionGraph.edges(sourceAbstractState, destinationAbstractState).find {
                                            it.label.abstractAction == abstractAction
                                        }
                                        if (exisitingAbstractEdge != null) {
                                            exisitingAbstractEdge.label.interactions.add(guiEdge.label)
                                        } else {
                                            //Create explicit edge for linked abstractState
                                            val abstractInteraction = AbstractInteraction(
                                                    abstractAction = abstractAction,
                                                    isImplicit = false
                                            )
                                            sourceAbstractState.abstractInteractions.add(abstractInteraction)

                                            abstractInteraction.interactions.add(guiEdge.label)
                                            regressionTestingMF.abstractTransitionGraph.add(
                                                    sourceAbstractState,
                                                    destinationAbstractState,
                                                    abstractInteraction
                                            )
                                            //Create implicit edges for other abstractState
                                            val implicitAbstractInteraction = AbstractInteraction(
                                                    abstractAction = abstractAction,
                                                    isImplicit = true
                                            )
                                            val otherAbstractStates = newAbstractStates.filterNot { it == sourceAbstractState }
                                            otherAbstractStates.forEach {
                                                if (it.widgets.contains(newWidgetGroup))
                                                    regressionTestingMF.abstractTransitionGraph.add(it, destinationAbstractState, implicitAbstractInteraction)
                                            }

                                        }
                                    }
                                }
                            }
                        }

                    }

                }
            }
        }catch (e: Exception)
        {
            log.info(e.toString())
        }
    }

    private fun getStaticWidgets(widget_WidgetGroupHashMap: HashMap<Widget,WidgetGroup>,guiState: State<*>, staticNode: WTGNode): HashMap<WidgetGroup,ArrayList<StaticWidget>> {
        val result: HashMap<WidgetGroup,ArrayList<StaticWidget>> = HashMap()
        val actionableWidgets = ArrayList<Widget>()
        actionableWidgets.addAll(Helper.getVisibleInteractableWidgets(guiState))
        if (actionableWidgets.isEmpty())
        {
            actionableWidgets.addAll(guiState.widgets.filterNot { it.isKeyboard })
        }
        val unmappedWidgets = actionableWidgets
        val mappedStaticWidgets = ArrayList<StaticWidget>()
        unmappedWidgets.groupBy { widget_WidgetGroupHashMap[it] }.filter{it.key!=null} .forEach {
            it.value.forEach {w->
                val staticWidgets = Helper.getStaticWidgets(w, it.key!!, guiState,staticNode,true, regressionTestingMF)
                //if a widgetGroup has more
                if (staticWidgets.isNotEmpty())
                {
                    result.put(it.key!!,ArrayList(staticWidgets))
                }
            }
        }
        return result
    }


    companion object{
       val instance: AbstractStateManager by lazy {
           AbstractStateManager()
       }
        private val log: org.slf4j.Logger by lazy { LoggerFactory.getLogger(AbstractStateManager::class.java) }
    }
}