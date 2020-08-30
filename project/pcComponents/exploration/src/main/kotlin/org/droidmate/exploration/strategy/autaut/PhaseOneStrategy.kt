package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ActionQueue
import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.*
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class PhaseOneStrategy(
        autAutTestingStrategy: AutAutTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        autAutTestingStrategy = autAutTestingStrategy,
        budgetScale = budgetScale,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = true
) {
    val untriggeredWidgets = arrayListOf<StaticWidget>()
    val untriggeredTargetEvents = arrayListOf<StaticEvent>()

    var attemps: Int
    var targetWindow: WTGNode? = null
    val flaggedWindows = HashSet<WTGNode>()
    val unreachableWindows = HashSet<WTGNode>()
    val fullyCoveredWindows = HashSet<WTGNode>()
    val targetWindowTryCount: HashMap<WTGNode,Int> = HashMap()
    val windowRandomExplorationBudget: HashMap<WTGNode,Int> = HashMap()
    val windowRandomExplorationBudgetUsed: HashMap<WTGNode, Int> = HashMap()

    init {
        phaseState = PhaseState.P1_INITIAL
        autautMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        attemps = autautMF.allTargetWindows.size
        autautMF.allTargetWindows.keys.forEach {
            targetWindowTryCount.put(it,0)
        }
        autautMF.allTargetStaticEvents.forEach {
            untriggeredTargetEvents.add(it)
        }
    }

    override fun registerTriggeredEvents( abstractAction: AbstractAction, guiState: State<*>)
    {
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        //val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }

        val staticEvents = abstractState.staticEventMapping[abstractAction]
        if (staticEvents != null) {
            staticEvents.forEach {
                if (autautMF.targetItemEvents.containsKey(it)) {
                    autautMF.targetItemEvents[it]!!["count"] = autautMF!!.targetItemEvents[it]!!["count"]!! + 1
                    if (autautMF.targetItemEvents[it]!!["count"] == autautMF!!.targetItemEvents[it]!!["max"]!!) {
                        untriggeredTargetEvents.remove(it)
                    }
                } else {
                    untriggeredTargetEvents.remove(it)
                }
            }
        }
    }

    var tryCount = 0
    override fun isEnd(): Boolean {
        if (autautMF.lastUpdatedMethodCoverage == 1.0) {
            return true
        }
        if (targetWindowTryCount.filterNot { flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key) || unreachableWindows.contains(it.key) }.isEmpty())
            return true
        /*if (autautMF.methodCovFromLastChangeCount > 50 * budgetScale ) {
            if (tryCount < 1) {
                needResetApp = true
                autautMF.methodCovFromLastChangeCount = 0
                tryCount++
                return false
            }
            return true
        }*/
        return false
    }



    private fun selectTargetNode(currentState: State<*>, tried: Int) {
        //Try finding reachable target

        val explicitTargetWindows = WTGNode.allMeaningNodes.filter { window ->
            untriggeredTargetEvents.any { it.sourceWindow == window }
        }
        var candidates = targetWindowTryCount.filter { isExplicitCandidateWindow(explicitTargetWindows,it) }
        if (candidates.isNotEmpty()) {
            val leastTriedWindow = candidates.map { Pair<WTGNode,Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
            targetWindow = leastTriedWindow.value.random().first
        } else {
            targetWindow = null
        }
        if (targetWindow == null) {
            candidates = targetWindowTryCount.filter { isCandidateWindow(it) }
            if (candidates.isNotEmpty()) {
                val leastTriedWindow = candidates.map { Pair<WTGNode, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
                targetWindow = leastTriedWindow.value.random().first
            }
        }
        if (targetWindow != null) {
            val transitionPaths = getPathsToWindow(currentState,targetWindow!!)
            targetWindowTryCount[targetWindow!!] = targetWindowTryCount[targetWindow!!]!! + 1
            if (transitionPaths.isEmpty() && tried <= candidates.size) {
                return selectTargetNode(currentState, tried+1)
            }
        }

        actionCountSinceSelectTarget = 0
    }

    private fun isExplicitCandidateWindow(explicitTargetWindows: List<WTGNode>, it: Map.Entry<WTGNode, Int>) =
            explicitTargetWindows.contains(it.key) && !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key) && !unreachableWindows.contains(it.key)

    private fun isCandidateWindow(it: Map.Entry<WTGNode, Int>) =
            !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key) && !unreachableWindows.contains(it.key)

    override fun  getPathsToOtherWindows(currentState: State<*>): List<TransitionPath> {
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState
                || it == currentAbstractState
                || it.window is WTGLauncherNode
                        || it.window is WTGOutScopeNode }
        /*val candiateWindows = WTGNode.allMeaningNodes.filter { window -> runtimeAbstractStates.find { it.window == window } != null}
        val candidateVisitedFoundNodes = regressionTestingMF.windowVisitCount
                .filter { candiateWindows.contains(it.key) } as HashMap*/
        val abstratStateCandidates = runtimeAbstractStates

        val stateByActionCount = HashMap<AbstractState,Double>()
        abstratStateCandidates.forEach {
            val unExerciseActions = it.getUnExercisedActions(null).filter { it.widgetGroup!=null }
            val widgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[it.window]!!
            var weight = 0.0
            unExerciseActions.forEach { action ->
                val widgetGroup = action.widgetGroup!!
                weight += (1/(widgetFrequency[widgetGroup]?:1).toDouble())
            }
            if (weight>0.0) {
                stateByActionCount.put(it, weight)
            }
            /*val unExercisedActionsSize = it.getUnExercisedActions().filter { it.widgetGroup!=null }.size
            if (unExercisedActionsSize > 0 ) {


                stateByActionCount.put(it, it.getUnExercisedActions().size.toDouble())

            }*/
        }
        val stateCandidates: Map<AbstractState,Double>
        stateCandidates = stateByActionCount
       /* if (stateByActionCount.any { AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH]!!.window == it.key.window }) {
            stateCandidates = stateByActionCount.filter { it.key.window == AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH]!!.window  }
        } else {
            stateCandidates = stateByActionCount
        }*/
        /*if (candidateVisitedFoundNodes.isEmpty())
            return transitionPaths*/
        //val activityVisitCount = HashMap(this.activityVisitCount)
        getPathToStates(transitionPaths, stateCandidates, currentAbstractState, currentState,false,false)
        return transitionPaths
    }



    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        log.debug("getAllTargetNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = getCandidateNodes_P1(currentNode = currentAbstractState)
        while (targetStates.isNotEmpty() && transitionPaths.isEmpty()) {
            val targetState = targetStates.first()
            targetStates.remove(targetState)
            val existingPaths: List<TransitionPath>?
            existingPaths = autautMF.allAvailableTransitionPaths[Pair(currentAbstractState,targetState)]
            if (existingPaths != null && existingPaths.isNotEmpty())
            {
                transitionPaths.addAll(existingPaths)
            }
            else {
                //check if there is any edge from App State to that node
                val feasibleEdges = autautMF.abstractTransitionGraph.edges().filter { e ->
                    e.destination?.data!!.window == targetWindow
                }
                if (feasibleEdges.isNotEmpty())
                {
                    val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<Widget,String>>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<WTGNode>,currentAbstractState))
                            , finalTarget = targetState
                            , allPaths = transitionPaths
                            , includeBackEvent = true
                            , childParentMap = childParentMap
                            , level = 0,
                            useVirtualAbstractState = useVirtualAbstractState)
                }
            }
        }

        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>): List<AbstractAction> {
        val targetEvents = HashMap<StaticEvent,List<AbstractAction>>()
        if (autautMF.getAbstractState(currentState)!!.window != targetWindow)
            return emptyList()
        val currentWindowTargetEvents = untriggeredTargetEvents.filter { it.sourceWindow == targetWindow }
        currentWindowTargetEvents.forEach {
            val abstractInteractions = autautMF.validateEvent(it, currentState)

            if (abstractInteractions.isNotEmpty())
            {
                targetEvents.put(it,abstractInteractions)
            }
        }
        return targetEvents.map { it.value }.flatMap { it }
        /*val currentAppState = regressionTestingMF.getAbstractState(currentState)!!
        val targetActions = currentAppState.targetActions
        val untriggerTargetActions = targetActions.filter {
            if (it.widgetGroup==null) {
                currentAppState.actionCount[it] == 0
            } else {
                it.widgetGroup.actionCount[it] == 0
            }
        }
        return untriggerTargetActions*/
    }

    var clickedOnKeyboard = false
    var needResetApp = false
    var actionCountSinceSelectTarget: Int = 0
    override fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction {
        //TODO Update target windows
        autautMF.allTargetWindows.map { it.key }. forEach {
            if (autautMF.allTargetWindows[it]!!.all { autautMF.allModifiedMethod[it] == true }) {
                if (!flaggedWindows.contains(it)) {
                    flaggedWindows.add(it)
                    if (targetWindow == it) {
                        targetWindow = null
                        strategyTask = null
                    }
                }
            }
            if (!targetWindowTryCount.containsKey(it))
                targetWindowTryCount.put(it,0)
        }

        var chosenAction:ExplorationAction
        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!
        if (autautMF.updateMethodCovFromLastChangeCount > 25 * budgetScale
                && actionCountSinceSelectTarget > 25 * budgetScale) {
            selectTargetNode(currentState,0).also {
                if (targetWindow!=null) {
                    strategyTask = null
                    phaseState = PhaseState.P1_INITIAL
                    autautMF.updateMethodCovFromLastChangeCount = 0
                }
            }
        }
        if (targetWindow == null)
            selectTargetNode(currentState,0).also {
                if (targetWindow!=null) {
                    strategyTask = null
                    phaseState = PhaseState.P1_INITIAL
                }
            }
        if (unreachableWindows.contains(currentAppState.window)) {
            unreachableWindows.remove(currentAppState.window)
        }
/*        if (regressionTestingMF.appPrevState!=null) {
                val prevAppState = regressionTestingMF.getAbstractState(regressionTestingMF.appPrevState!!)!!
            if (currentAppState.window != prevAppState.window && currentAppState.rotation == Rotation.LANDSCAPE) {
                // rotate to portrait
                return ExplorationAction.rotate(-90)
            }
        }*/


        if (!windowRandomExplorationBudget.containsKey(currentAppState.window)) {
            if (currentAppState.isOpeningKeyboard) {
                log.info("New window but keyboard is open. Close keyboard")
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
            val isWebViewBased = true
            val budget = if (isWebViewBased) {
                Helper.getVisibleInteractableWidgets(currentState).map { it.availableActions(100, false) }.flatten().size / 2 * budgetScale
            } else {
                Helper.getVisibleInteractableWidgets(currentState).filter { !Helper.hasParentWithType(it, currentState, "WebView") }.map { it.availableActions(100, false) }.flatten().size / 2 * budgetScale
            }
            windowRandomExplorationBudget.put(currentAppState.window, budget.toInt())
            windowRandomExplorationBudgetUsed.put(currentAppState.window, 0)
        }
        ExplorationTrace.widgetTargets.clear()
        // check if target window is fully covered
/*        val targetEvents = untriggeredTargetEvents.filter { it.sourceWindow == targetWindow }
        if (targetEvents.isEmpty()) {
            if (regressionTestingMF.windowHandlersHashMap.containsKey(targetWindow)) {
                val targetHandlers = regressionTestingMF.windowHandlersHashMap[targetWindow!!]!!.filter { regressionTestingMF.allTargetHandlers.contains(it) }
                if (regressionTestingMF.untriggeredTargetHandlers.intersect(targetHandlers).isEmpty()) {
                    //No need to test this Window
                    fullyCoveredWindows.add(targetWindow!!)
                    selectTargetNode()
                    //TODO relaunch app
                }
            } else {
                if (targetWindow!=null)
                    fullyCoveredWindows.add(targetWindow!!)
                selectTargetNode()
            }
        }*/

        if (targetWindow != currentAppState.window) {
            if (isTargetWindow(currentAppState)) {
                targetWindow = currentAppState.window
                strategyTask = null
                phaseState = PhaseState.P1_INITIAL
            }
        }

        log.info("Target window: $targetWindow")
        chooseTask_P1(eContext, currentState)
        if (needResetApp) {
            needResetApp = false
            autautMF.updateMethodCovFromLastChangeCount = 0
            return eContext.resetApp()
        }
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
            if (isCountAction(chosenAction)
                    && windowRandomExplorationBudgetUsed.containsKey(currentAppState.window)
                    && phaseState!=PhaseState.P1_GO_TO_TARGET_NODE
                    && phaseState!=PhaseState.P1_GO_TO_ANOTHER_NODE
                    && phaseState!=PhaseState.P1_EXERCISE_TARGET_NODE) {

                windowRandomExplorationBudgetUsed[currentAppState.window] = windowRandomExplorationBudgetUsed[currentAppState.window]!! + 1
                if (windowRandomExplorationBudgetUsed[currentAppState.window]!! > windowRandomExplorationBudget[currentAppState.window]!!) {
                    flaggedWindows.add(currentAppState.window)
                }
            }
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = eContext.resetApp()
        }
        actionCountSinceSelectTarget++
        return chosenAction
    }

    private fun isCountAction(chosenAction: ExplorationAction) =
            arrayListOf<String>("Click","ClickEvent","LongClick","LongClickEvent", "Swipe").contains(chosenAction.name)
                    || (chosenAction is ActionQueue && chosenAction.actions.any {
                arrayListOf<String>("Click","ClickEvent","LongClick","LongClickEvent","Swipe").contains(it.name) })

    private fun isTargetWindow(currentAppState: AbstractState): Boolean {
        return targetWindowTryCount.filterNot {
            flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key) || unreachableWindows.contains(it.key)
                    || currentAppState.window == targetWindow
        }.any { it.key == currentAppState.window }
    }

    var numOfContinousTry = 0
    private fun chooseTask_P1(eContext: ExplorationContext<*, *, *>, currentState: State<*>) {
        log.debug("Choosing Task")
        val fillDataTask = PrepareContextTask.getInstance(autautMF,autAutTestingStrategy,delay, useCoordinateClicks)
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(autautMF,autAutTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!

/*        if (strategyTask == null) {
            log.info("No current task selected.")
            log.info("Choose new task.")
            chooseTask_P1(eContext, currentState)
        } else if (phaseState == PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE) {
        } else if (strategyTask != null && strategyTask!!.isTaskEnd(currentState)) {
            log.info("Current task is end.")
            log.info("Choose new task.")
            chooseTask_P1(eContext, currentState)
        } else {
            log.info("Continue ${strategyTask!!.javaClass.name} task.")
        }*/
        log.debug("${currentAppState.window} - Budget: ${windowRandomExplorationBudgetUsed[currentAppState.window]}/${windowRandomExplorationBudget[currentAppState.window]}")
        if (!hasBudgetLeft(currentAppState)) {
            flaggedWindows.add(currentAppState.window)
        }
        if (targetWindow == null) {
            if (strategyTask!=null) {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    // if random can be still run, keep running
                    log.info("Continue current task: $strategyTask")
                    return
                }
            }
            if (isLoginWindow(currentAppState)&& hasBudgetLeft(currentAppState)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                return
            }
            setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
            return
        }
        if (phaseState == PhaseState.P1_INITIAL) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                // In case target events not found
                // Try random exploration
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
                /*else {
                    flaggedWindows.add(targetWindow!!)
                    selectTargetNode()
                    phaseState = PhaseState.P1_INITIAL
                    //needResetApp = true
                    return
                }*/
            }
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                   ) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
/*            if (isLoginWindow(currentAppState)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }*/
            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                numOfContinousTry = 0
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                return
            }
            if (hasBudgetLeft(currentAppState)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }
            setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
            return
        }
        if (phaseState == PhaseState.P1_EXERCISE_TARGET_NODE) {

            if (currentAppState.window == targetWindow) {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue exercise target window")
                    return
                }
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                // In case target events not found
                // Try random exploration
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
            }
/*            if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }*/
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            } else {
                //select new target node and retry
                selectTargetNode(currentState,0)
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window: ${targetWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                    return
                }
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                   ) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                numOfContinousTry = 0
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                return
            }
            /*if (hasBudgetLeft(currentAppState)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }*/
            if (hasBudgetLeft(currentAppState)) {
                setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
                return
            }
        }
        if (phaseState == PhaseState.P1_GO_TO_TARGET_NODE) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue go to target window")
                    return
                }
                // In case target events not found
                // Try random exploration if having budget
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
            }
           /* if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }*/
            if (!strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue ${strategyTask!!}")
                return
            }
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            } else {
                //select new target node and retry
                selectTargetNode(currentState,0)
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window: ${targetWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                    return
                }
            }

            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                numOfContinousTry = 0
                return
            }
            /*if (hasBudgetLeft(currentAppState)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }*/
            if (hasBudgetLeft(currentAppState)) {
                setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
                return
            }
        }
        if (phaseState == PhaseState.P1_GO_TO_ANOTHER_NODE) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                // In case target events not found
                // Try random exploration if having budget
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
            }
            /*if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }*/

            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            } else {
                //select new target node and retry
                selectTargetNode(currentState,0)
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window: ${targetWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                    return
                }
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue ${strategyTask!!}")
                return
            }
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
             if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (!goToAnotherNode.isReachExpectedNode(currentState) && numOfContinousTry < 5) {
                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    numOfContinousTry += 1
                    log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                    return
                }
            }
            /*if (hasBudgetLeft(currentAppState)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }*/
            setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
        }
        if (phaseState == PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE) {
            if (currentAppState.window == targetWindow) {
                if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
                    // if random can be still run, keep running
                    log.info("Continue filling data")
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue random in target window")
                    return
                }
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                /*if (shouldChangeTargetWindow()) {
                    if (goToAnotherNode.isAvailable(currentState)) {
                        strategyTask = goToAnotherNode.also {
                            it.initialize(currentState)
                        }
                        log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")

                        phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                        return
                    }
                }*/
                if (randomExplorationTask.isFullyExploration || shouldChangeTargetWindow() ) {
                    flaggedWindows.add(targetWindow!!)
                    selectTargetNode(currentState,0)
                    phaseState = PhaseState.P1_INITIAL
                    //needResetApp = true
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
            }
            /*if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }*/
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                log.info("Go to target window: ${targetWindow.toString()}")
                return
            } else {
                //select new target node and retry
                selectTargetNode(currentState,0)
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window: ${targetWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                    return
                }
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }

            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                numOfContinousTry = 0
                log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                return
            }
            /*if (hasBudgetLeft(currentAppState)) {
               setRandomExploration(randomExplorationTask, currentState,currentAppState)
               return
           }*/
            if (hasBudgetLeft(currentAppState)) {
                setFullyRandomExploration(randomExplorationTask,currentState,currentAppState)
                return
            }
        }

        if (phaseState == PhaseState.P1_RANDOM_EXPLORATION) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()
                        && autautMF.untriggeredTargetHandlers.intersect(
                                autautMF.windowHandlersHashMap[currentAppState.window]?: emptyList()
                        ).isNotEmpty()) {
                    if (hasBudgetLeft(currentAppState)) {
                        setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                        return
                    }
                    setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
                    randomExplorationTask.lockTargetWindow(currentAppState.window)
                    phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
                    return
                }
                else {
                    flaggedWindows.add(targetWindow!!)
                }
            }
            if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
                // if random can be still run, keep running
                log.info("Continue filling data")
                return
            }
            if (randomExplorationTask.isFullyExploration && !strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue fully exploration")
                return
            }
            if (currentAppState.window is WTGDialogNode) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            } else {
                //select new target node and retry
                selectTargetNode(currentState,0)
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window: ${targetWindow.toString()}")
                    phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                    return
                }
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                // if random can be still run, keep running
                log.info("Continue exploration")
                return
            }
            if (currentAppState.getUnExercisedActions(currentState).isNotEmpty()) {
                // Try random login Window
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                numOfContinousTry = 0
                log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_ANOTHER_NODE
                return
            }

            if (randomExplorationTask.isFullyExploration) {
                unreachableWindows.add(targetWindow!!)
                flaggedWindows.add(currentAppState.window)
                selectTargetNode(currentState,0)
                phaseState = PhaseState.P1_INITIAL
                needResetApp = true
                return
            }
            if ( hasBudgetLeft(currentAppState)) {
                // Try random login Window
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
            return
        }
        unreachableWindows.add(targetWindow!!)
        flaggedWindows.add(currentAppState.window)
        selectTargetNode(currentState,0)
        phaseState = PhaseState.P1_INITIAL
        needResetApp = true
        return

    }

    private fun shouldChangeTargetWindow() = autautMF.updateMethodCovFromLastChangeCount > 25 * budgetScale

    private fun setFullyRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAppState: AbstractState) {
        setFullyRandomExploration(randomExplorationTask, currentState, currentAppState)
        randomExplorationTask.lockTargetWindow(currentAppState.window)
        phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    private fun setExerciseTarget(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>) {
        strategyTask = exerciseTargetComponentTask.also {
            it.initialize(currentState)
        }
        log.info("This window has target events.")
        log.info("Exercise target component task chosen")
        phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
    }

    private fun hasBudgetLeft(abstractState: AbstractState): Boolean {
        val window = abstractState.window
        if (window is WTGDialogNode) {
            //always give budget for dialog
            return true
        }
        if (!windowRandomExplorationBudgetUsed.containsKey(window))
            return true
        if (windowRandomExplorationBudgetUsed.containsKey(window)) {
            if (windowRandomExplorationBudgetUsed[window]!! < windowRandomExplorationBudget[window]!!) {
                return true
            }
        }
        /*if (currentAppState.getUnExercisedActions(currentState).isNotEmpty())
            return true*/
        return false
    }

    private fun isLoginWindow(currentAppState: AbstractState): Boolean {
        val activity = currentAppState.window.classType.toLowerCase()
        return activity.contains("login") || activity.contains("signin")
    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAbstractState: AbstractState) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
        }
        log.info("Random exploration")
        phaseState = PhaseState.P1_RANDOM_EXPLORATION

    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAbstractState: AbstractState) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaximumAttempt((10*budgetScale).toInt())
            it.isFullyExploration = true
        }
        log.info("Cannot find path the target node.")
        log.info("Fully Random exploration")
        phaseState = PhaseState.P1_RANDOM_EXPLORATION

    }

    private fun setRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.lockTargetWindow(targetWindow!!)
        }
        log.info("This window is a target window but cannot find any target event")
        log.info("Random exploration in current window")
        phaseState = PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    fun getCandidateNodes_P1(currentNode: AbstractState): ArrayList<AbstractState>
    {
        val candidates = ArrayList<AbstractState>()
        val excludedNode = currentNode
        // Get all abstract state
        candidates.add(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window == targetWindow && it is VirtualAbstractState }!!)

        //Get all AbstractState contain target events
        AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }. forEach {
            val abstractActions = autautMF.abstractTransitionGraph.edges(it).filter { edge ->
                it.staticEventMapping.contains(edge.label.abstractAction)}.map { it.label.abstractAction }
            val staticEvents = it.staticEventMapping.filter { abstractActions.contains(it.key) }.map { it.value }
            if (staticEvents.find { untriggeredTargetEvents.intersect(it).isNotEmpty() }!=null)
            {
                candidates.add(it)
            }
        }
        return candidates
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(PhaseOneStrategy::class.java) }



    }
}