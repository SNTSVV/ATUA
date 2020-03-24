package org.droidmate.exploration.strategy.regression

import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.*
import org.droidmate.exploration.modelFeatures.regression.staticModel.*
import org.droidmate.exploration.strategy.regression.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PhaseOneStrategy(
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        regressionTestingStrategy = regressionTestingStrategy,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks
) {
    var attemps: Int
    init {
        phaseState = PhaseState.P1_INITIAL
        regressionTestingMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        attemps = WTGNode.allMeaningNodes.size
    }

    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath> {
        log.debug("getNeareastNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val candiateNodes = AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it.staticNode == currentAbstractState.staticNode
        }
        val candidateVisitedFoundNodes = regressionTestingMF.abstractStateVisitCount
                .filter { candiateNodes.contains(it.key) } as HashMap
        if (candidateVisitedFoundNodes.isEmpty())
            return transitionPaths
        //val activityVisitCount = HashMap(this.activityVisitCount)
        while (transitionPaths.isEmpty() && candidateVisitedFoundNodes.isNotEmpty())
        {
            val leastVisitedFoundNode = candidateVisitedFoundNodes.minBy { it.value }!!
            val leastVisitedCount = leastVisitedFoundNode.value
            var biasedCandidates =candidateVisitedFoundNodes.filter { it.value == leastVisitedCount }.map { it.key }
//            val topTargetWindow = biasedCandidates.sortedByDescending { it.unexercisedWidgetCount }.first()
//            val targetWindows = biasedCandidates.filter { it.unexercisedWidgetCount == topTargetWindow.unexercisedWidgetCount && it!=currentAbstractState }

            biasedCandidates.forEach {
                candidateVisitedFoundNodes.remove(it)
                val existingPaths: List<TransitionPath>?
                existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState,it)]
                if (existingPaths != null && existingPaths.isNotEmpty())
                {
                    transitionPaths.addAll(existingPaths)
                }
                else
                {

                    val childParentMap = HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>()
                    childParentMap.put(currentAbstractState,null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            ,parentNodes = listOf(currentAbstractState)
                            ,finalTarget = it
                            ,allPaths = transitionPaths
                            ,includeBackEvent = true
                            ,childParentMap = HashMap()
                            ,level = 0)
                }
            }
            candidateVisitedFoundNodes.remove(leastVisitedFoundNode.key)
        }
        return transitionPaths
    }

    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        log.debug("getAllTargetNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val candidates = getCandidateNodes_P1(currentAbstractState)
//        val candidatesVisitCount = abstractStateVisitCount.filter { candidates.contains(it.key) } as HashMap
//        if (candidatesVisitCount.isEmpty())
//            return emptyList()
        val staticNodeList = ArrayList(candidates.map { it.staticNode }.distinct())
        while(transitionPaths.isEmpty() && staticNodeList.isNotEmpty())
        {
//            val leastVisitedActivity = activityVisitCount.minBy { it.value } !!
//            val leastVisitedActivities = activityVisitCount.filter { it.value == leastVisitedActivity.value }.map{it.key}
            val chosenNode = staticNodeList.random()
            //val leastVisitedNode = candidatesVisitCount.minBy { it.value }!!
            val biasedCandidates = candidates.filter {
                it.staticNode == chosenNode
            }
            biasedCandidates.forEach {
                val existingPaths: List<TransitionPath>?
                existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState,it)]
                if (existingPaths != null && existingPaths.isNotEmpty())
                {
                    transitionPaths.addAll(existingPaths)
                }
                else {
                    //check if there is any edge from App State to that node
                    val feasibleEdges = regressionTestingMF.abstractTransitionGraph.edges().filter {e ->
                        e.destination?.data == it
                    }
                    if (feasibleEdges.isNotEmpty())
                    {
                        val childParentMap = HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>()
                        childParentMap.put(currentAbstractState, null)
                        findPathToTargetComponentByBFS(currentState = currentState
                                , root = currentAbstractState
                                , parentNodes = listOf(currentAbstractState)
                                , finalTarget = it
                                , allPaths = transitionPaths
                                , includeBackEvent = true
                                , childParentMap = childParentMap
                                , level = 0)
                    }

                }
            }
            staticNodeList.remove(chosenNode)
        }

        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>): List<AbstractAction> {
        val targetEvents = HashMap<StaticEvent,List<AbstractAction>>()
        targetEvents.clear()
        regressionTestingMF.untriggeredTargetEvents.forEach {
            val abstractInteractions = regressionTestingMF.validateEvent(it, currentState)

            if (abstractInteractions.isNotEmpty())
            {
                targetEvents.put(it,abstractInteractions)
            }
        }
        return targetEvents.map { it.value }.flatMap { it }
    }

    override fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction {
        var chosenAction:ExplorationAction
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)
        if (currentAppState == null)
        {
            return eContext.resetApp()
        }
        if (currentState.widgets.any{it.packageName == "com.android.camera2"})
        {
            strategyTask = null
            return dealWithCamera(eContext, currentState)
        }
        if (currentState.visibleTargets.filter { it.isKeyboard }.isNotEmpty() && strategyTask !is FillTextInputTask) {
            log.info("The keyboard is open and Text input filling was executed.")
            log.info("Try close the keyboard.")
            chosenAction = GlobalAction(actionType = ActionType.CloseKeyboard)
            //strategyTask = null
            return chosenAction
        } else if (currentAppState.isOutOfApplication
                || Helper.getVisibleWidgets(currentState).find { it.resourceId == "android:id/floating_toolbar_menu_item_text" } != null) {
            log.info("App goes to an out of scope node.")
            log.info("Try press back.")
            chosenAction = ExplorationAction.pressBack()
            strategyTask = null
            return chosenAction
        } else {
            if (regressionTestingMF.fromLaunch) {
                log.info("This node is reached from AppLaunch.")
                log.info("Reset current task and choose new task.")
                strategyTask = null
                chooseTask_P1(eContext,currentState)
            } else {
                if (strategyTask == null) {
                    log.info("No current task selected.")
                    log.info("Choose new task.")
                    chooseTask_P1(eContext, currentState)
                } else if (strategyTask != null && strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Current task is end.")
                    log.info("Choose new task.")
                    chooseTask_P1(eContext, currentState)
                    //FIXME update AppState Visit count
                    /* if (regressionWatcher.abstractStateVisitCount[currentAppState]!! > 1 && currentAbstractState.hasOptionsMenu && !regressionWatcher.optionsMenuCheck.contains(currentAppState)) {
                         val moreOptionWidget = regressionWatcher.getToolBarMoreOptions(currentState)
                         if (moreOptionWidget != null) {
                             log.info("This node may More Option widget.")
                             log.info("Try click this widget.")
                             regressionWatcher.optionsMenuCheck.add(currentAppState!!)
                             return moreOptionWidget.click()
                         } else {
                             log.info("This node may have Options Menu.")
                             log.info("Try press Options Menu key.")
                             regressionWatcher.optionsMenuCheck.add(currentAppState!!)
                             regressionWatcher.isRecentPressMenu = true
                             return ExplorationAction.pressMenu()
                         }

                     } else {

                     }*/
                } else {
                    log.info("Continue ${strategyTask!!.javaClass.name} task.")
                }
            }
        }
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = ExplorationAction.closeAndReturn()
        }
        return chosenAction
    }

    private fun chooseTask_P1(eContext: ExplorationContext<*, *, *>, currentState: State<*>) {
        log.debug("Choosing Task")
        val fillDataTask = FillTextInputTask.getInstance(regressionTestingMF,regressionTestingStrategy,delay, useCoordinateClicks)
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val reachTargetNodeTask = GoToTargetNodeTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherNode.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(regressionTestingMF, regressionTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF,regressionTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)!!

/*         if(regressionWatcher.abstractStateVisitCount[currentAppState] == 1)
        {
            strategyTask = randomExplorationTask.also {
                it.initialize(currentState)
                it.backAction = false
                it.setAttempOnUnexercised(currentState)
            }
            //regressionWatcher.modifiedMethodCoverageFromLastChangeCount=0
            log.info("This node has no target events and is first visited.")
            log.info("Random exploration Task chosen ")
        }*/
/*        else if(currentState.widgets.find { it.isKeyboard }!=null && fillDataTask.isAvailable(currentState)
                && strategyTask!=fillDataTask)
        {
            strategyTask = fillDataTask.also { it.initialize(currentState) }
            log.info("This node has input fields and need to be filled.")
            log.info("FillDataTask task chosen")
        }*/
        /* else if (regressionWatcher.modifiedMethodCoverageFromLastChangeCount < 30
                 && prevNode!=regressionWatcher.getStateWTGNode(currentState)
                 && strategyTask !is FillDataTask
                 && fillDataTask.isAvailable(currentState))
         {
             strategyTask = fillDataTask.also { it.initialize(currentState) }
             log.debug("FillDataTask task chosen")
         }*/
        if (!regressionTestingMF.openNavigationCheck.contains(currentAppState)
                && openNavigationBarTask.isAvailable(currentState))
        {
            strategyTask = openNavigationBarTask.also { it.initialize(currentState) }
            regressionTestingMF.openNavigationCheck.add(currentAppState!!)
            log.info("This node has navigation bar.")
            log.info("OpenNavigationBar task chosen")
        }
        else if (exerciseTargetComponentTask.isAvailable(currentState)) {
            strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
            log.info("This node has target events.")
            log.info("Exercise target component task chosen")
        }
        else if (regressionTestingMF.modifiedMethodCoverageFromLastChangeCount > 10 &&
                !(strategyTask is RandomExplorationTask && (strategyTask!! as RandomExplorationTask).isFullyExploration))
        {
            //Full random exploration
            strategyTask = randomExplorationTask.also {
                it.initialize(currentState)
                it.setMaximumAttempt(10)
                it.backAction = true
                it.isFullyExploration = true
            }
            attemps--
            log.info("This node has no target events and all exercised.")
            log.info("Modified method coverage has not changed from last 10 actions.")
            log.info("Last task is not fully random exploration.")
            log.info("Fully random exploration Task chosen ")
        }
        else if (strategyTask == null || strategyTask is GoToAnotherNode)
        {
            strategyTask = randomExplorationTask.also {
                it.initialize(currentState)
                it.backAction = true
                it.setMaximumAttempt(10)
            }
            attemps--
            log.info("Random exploration Task chosen ")
        }
        else if (reachTargetNodeTask.isAvailable(currentState)) {
            strategyTask = reachTargetNodeTask.also { it.initialize(currentState) }
            log.info("This node can reach target nodes.")
            log.info("Go to Target Node Task chosen")
        }
/*        else if (currentAppState.unexercisedWidgetCount>0){
            val prevTask = strategyTask
            strategyTask = randomExplorationTask.also {
                it.initialize(currentState)
                it.backAction = true
                if (prevTask !is RandomExplorationTask)
                {
                    it.setAttempOnUnexercised (currentState = currentState)
                }
            }
            log.info("This node has no target events and still has unexercised widgets.")
            log.info("Random exploration Task chosen ")
        }*/
        else if (goToAnotherNode.isAvailable(currentState)){
            strategyTask = goToAnotherNode.also { it.initialize(currentState) }
            log.info("This node cannot reach    any target node.")
            log.info("Go to another node Task chosen")
        }
        else if (randomExplorationTask.isAvailable(currentState)) {
            strategyTask = randomExplorationTask.also {
                it.initialize(currentState)
                it.backAction = true
                it.setMaximumAttempt(10)
            }
            attemps--
            log.info("Not any node can be reached from this node.")
            log.info("Random exploration Task chosen ")
        }
    }

    fun getCandidateNodes_P1(currentNode: AbstractState): ArrayList<AbstractState>
    {
        val candidates = ArrayList<AbstractState>()
        val excludedNode = currentNode
        //Get all VirtualAbstractState can contain target events
        AbstractStateManager.instance.ABSTRACT_STATES.filter {
            it is VirtualAbstractState
                    && it.staticNode !is WTGLauncherNode
                    && it.staticNode !is WTGOutScopeNode
        }.forEach {
            val staticEdge = regressionTestingMF.transitionGraph.edges(it.staticNode).filter { regressionTestingMF.untriggeredTargetEvents.contains(it.label) }
            if (staticEdge.isNotEmpty())
            {
                candidates.add(it)
            }
        }
        //Get all AbstractState contain target events
        AbstractStateManager.instance.ABSTRACT_STATES.forEach {
            if ( it != excludedNode)
            {
                val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(it).filter { edge ->
                    it.staticEventMapping.contains(edge.label)}.map { it.label }
                val staticEvents = it.staticEventMapping.filter { abstractInteractions.contains(it.key) }.map { it.value }
                if (staticEvents.find { regressionTestingMF.untriggeredTargetEvents.contains(it) }!=null)
                {
                    candidates.add(it)
                }
            }
        }
        return candidates
    }

    fun getLeastVisitedWindow_P1(currentState: State<*>): List<TransitionPath>{
        log.debug("getNeareastNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbstractState==null)
            return transitionPaths
        val candiateNodes = AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it==currentAbstractState }
        val candidateVisitedFoundNodes = regressionTestingMF.abstractStateVisitCount
                .filter { candiateNodes.contains(it.key) } as HashMap
        if (candidateVisitedFoundNodes.isEmpty())
            return transitionPaths
        //val activityVisitCount = HashMap(this.activityVisitCount)
        while (transitionPaths.isEmpty() && candidateVisitedFoundNodes.isNotEmpty())
        {
            val leastVisitedFoundNode = candidateVisitedFoundNodes.minBy { it.value }!!
            val leastVisitedCount = leastVisitedFoundNode.value
            var biasedCandidates =candidateVisitedFoundNodes.filter { it.value == leastVisitedCount }.map { it.key }
            val topTargetWindow = biasedCandidates.sortedByDescending { it.unexercisedWidgetCount }.first()
            val targetWindows = biasedCandidates.filter { it.unexercisedWidgetCount == topTargetWindow.unexercisedWidgetCount}

            targetWindows.forEach {
                candidateVisitedFoundNodes.remove(it)
                val existingPaths: List<TransitionPath>?
                existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState,it)]
                if (existingPaths != null && existingPaths.isNotEmpty())
                {
                    transitionPaths.addAll(existingPaths)
                }
                else
                {

                    val childParentMap = HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>()
                    childParentMap.put(currentAbstractState,null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            ,parentNodes = listOf(currentAbstractState)
                            ,finalTarget = it
                            ,allPaths = transitionPaths
                            ,includeBackEvent = true
                            ,childParentMap = HashMap()
                            ,level = 0)
                }
            }
            candidateVisitedFoundNodes.remove(leastVisitedFoundNode.key)
        }
        return transitionPaths

    }
    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(PhaseOneStrategy::class.java) }



    }
}