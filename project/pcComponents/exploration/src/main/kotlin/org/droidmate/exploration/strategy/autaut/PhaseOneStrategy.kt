package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.*
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.random.Random

class PhaseOneStrategy(
        regressionTestingStrategy: RegressionTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        regressionTestingStrategy = regressionTestingStrategy,
        budgetScale = budgetScale,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = true
) {
    val untriggeredWidgets = arrayListOf<StaticWidget>()
    val untriggeredTargetEvents = arrayListOf<StaticEvent>()

    var attemps: Int
    var targetWindow: WTGNode? = null
    val flaggedWindows = ArrayList<WTGNode>()
    val unreachableWindows = ArrayList<WTGNode>()
    val fullyCoveredWindows = ArrayList<WTGNode>()
    val targetWindowTryCount: HashMap<WTGNode,Int> = HashMap()
    val windowRandomExplorationBudget: HashMap<WTGNode,Int> = HashMap()
    val windowRandomExplorationBudgetLeft: HashMap<WTGNode, Int> = HashMap()

    init {
        phaseState = PhaseState.P1_INITIAL
        regressionTestingMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        attemps = regressionTestingMF.allTargetWindows.size
        regressionTestingMF.allTargetWindows.forEach {
            targetWindowTryCount.put(it,0)
        }
        regressionTestingMF.allTargetStaticEvents.forEach {
            untriggeredTargetEvents.add(it)
        }
    }

    override fun registerTriggeredEvents( abstractAction: AbstractAction, guiState: State<*>)
    {
        val abstractState = AbstractStateManager.instance.getAbstractState(guiState)!!
        val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }

        val staticEvents = abstractInteractions.filter { abstractState.staticEventMapping[it] != null }.map { abstractState.staticEventMapping[it]!! }.flatten().distinct()
        staticEvents.forEach {
            if (regressionTestingMF!!.targetItemEvents.containsKey(it)) {
                regressionTestingMF!!.targetItemEvents[it]!!["count"] = regressionTestingMF!!.targetItemEvents[it]!!["count"]!! + 1
                if (regressionTestingMF!!.targetItemEvents[it]!!["count"] == regressionTestingMF!!.targetItemEvents[it]!!["max"]!!) {
                    untriggeredTargetEvents.remove(it)
                }
            } else {
                untriggeredTargetEvents.remove(it)
            }
        }
    }

    override fun isEnd(): Boolean {
        if (regressionTestingMF.lastModifiedMethodCoverage == 1.0) {
            return true
        }
        if (targetWindowTryCount.filterNot { flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key) || unreachableWindows.contains(it.key) }.isEmpty()
                || regressionTestingMF.methodCoverageFromLastChangeCount > 50 * budgetScale )
            return true
        return false
    }



    private fun selectTargetNode() {
        val explicitTargetWindows = WTGNode.allMeaningNodes.filter { window ->
            untriggeredTargetEvents.any { it.sourceWindow == window }
        }
        if (targetWindowTryCount.filter { isExplicitCandidateWindow(explicitTargetWindows,it) }.isNotEmpty()) {
            val leastTriedWindow = targetWindowTryCount.filter { isExplicitCandidateWindow(explicitTargetWindows, it) }.map { Pair<WTGNode,Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
            targetWindow = leastTriedWindow.value.random().first
        } else {
            targetWindow = null
        }
        if (targetWindow == null) {
            if (targetWindowTryCount.filter { isCandidateWindow(it) }.isNotEmpty()) {
                val leastTriedWindow = targetWindowTryCount.filter { isCandidateWindow(it) }.map { Pair<WTGNode, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
                targetWindow = leastTriedWindow.value.random().first
            }
        }
        if (targetWindow != null)
            targetWindowTryCount[targetWindow!!] = targetWindowTryCount[targetWindow!!]!! + 1
    }

    private fun isExplicitCandidateWindow(explicitTargetWindows: List<WTGNode>, it: Map.Entry<WTGNode, Int>) =
            explicitTargetWindows.contains(it.key) && !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key) && !unreachableWindows.contains(it.key)

    private fun isCandidateWindow(it: Map.Entry<WTGNode, Int>) =
            !flaggedWindows.contains(it.key) && !fullyCoveredWindows.contains(it.key) && !unreachableWindows.contains(it.key)

    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath> {
        log.debug("getNeareastNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(regressionTestingMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filterNot { it is VirtualAbstractState && it!= currentAbstractState}
        /*val candiateWindows = WTGNode.allMeaningNodes.filter { window -> runtimeAbstractStates.find { it.window == window } != null}
        val candidateVisitedFoundNodes = regressionTestingMF.windowVisitCount
                .filter { candiateWindows.contains(it.key) } as HashMap*/
        val abstratStateCandidates = runtimeAbstractStates

        val stateByActionCount = HashMap<AbstractState,Int>()
        abstratStateCandidates.forEach {
            stateByActionCount.put(it,it.getUnExercisedActions().size)
        }

        var actionLimit = 2
        /*if (candidateVisitedFoundNodes.isEmpty())
            return transitionPaths*/
        //val activityVisitCount = HashMap(this.activityVisitCount)
        while (transitionPaths.isEmpty() && stateByActionCount.isNotEmpty())
        {
            /*val leastVisitedFoundNode = candidateVisitedFoundNodes.minBy { it.value }!!
            val leastVisitedCount = leastVisitedFoundNode.value
            var biasedCandidate =candidateVisitedFoundNodes.filter { it.value == leastVisitedCount }.map { it.key }.random()
//            val topTargetWindow = biasedCandidates.sortedByDescending { it.unexercisedWidgetCount }.first()
//            val targetWindows = biasedCandidates.filter { it.unexercisedWidgetCount == topTargetWindow.unexercisedWidgetCount && it!=currentAbstractState }
            candidateVisitedFoundNodes.remove(biasedCandidate)
            val abstractStates = runtimeAbstractStates.filter { it.window == biasedCandidate }*/
            val maxActionCount = stateByActionCount.maxBy { it.value }!!.value
            val abstractStates = stateByActionCount.filter { it.value == maxActionCount }.keys
            abstractStates.forEach {
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
                            ,traversingNodes = listOf(Pair(regressionTestingMF.windowStack.clone() as Stack<WTGNode>,currentAbstractState))
                            ,finalTarget = it
                            ,allPaths = transitionPaths
                            ,includeBackEvent = true
                            ,childParentMap = HashMap()
                            ,level = 0,
                            useVirtualAbstractState = useVirtualAbstractState)
                }
                stateByActionCount.remove(it)
            }

        }
        return transitionPaths
    }

    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        log.debug("getAllTargetNodePaths")
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(regressionTestingMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val targetStates = getCandidateNodes_P1(currentNode = currentAbstractState)
        while (targetStates.isNotEmpty() && transitionPaths.isEmpty()) {
            val targetState = targetStates.first()
            targetStates.remove(targetState)
            val existingPaths: List<TransitionPath>?
            existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbstractState,targetState)]
            if (existingPaths != null && existingPaths.isNotEmpty())
            {
                transitionPaths.addAll(existingPaths)
            }
            else {
                //check if there is any edge from App State to that node
                val feasibleEdges = regressionTestingMF.abstractTransitionGraph.edges().filter {e ->
                    e.destination?.data!!.window == targetWindow
                }
                if (feasibleEdges.isNotEmpty())
                {
                    val childParentMap = HashMap<AbstractState, Pair<AbstractState, AbstractInteraction>?>()
                    childParentMap.put(currentAbstractState, null)
                    findPathToTargetComponentByBFS(currentState = currentState
                            , root = currentAbstractState
                            , traversingNodes = listOf(Pair(regressionTestingMF.windowStack.clone() as Stack<WTGNode>,currentAbstractState))
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
        if (regressionTestingMF.getAbstractState(currentState)!!.window != targetWindow)
            return emptyList()
        val currentWindowTargetEvents = untriggeredTargetEvents.filter { it.sourceWindow == targetWindow }
        currentWindowTargetEvents.forEach {
            val abstractInteractions = regressionTestingMF.validateEvent(it, currentState)

            if (abstractInteractions.isNotEmpty())
            {
                targetEvents.put(it,abstractInteractions)
            }
        }
        return targetEvents.map { it.value }.flatMap { it }
    }

    var clickedOnKeyboard = false
    override fun nextAction(eContext: ExplorationContext<*,*,*>): ExplorationAction {
        var chosenAction:ExplorationAction
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)!!
        if (targetWindow == null)
            selectTargetNode()
        if (unreachableWindows.contains(currentAppState.window)) {
            unreachableWindows.remove(currentAppState.window)
        }
        if (regressionTestingMF.appPrevState!=null) {
                val prevAppState = regressionTestingMF.getAbstractState(regressionTestingMF.appPrevState!!)!!
            if (currentAppState.window != prevAppState.window && currentAppState.rotation == Rotation.LANDSCAPE) {
                // rotate to portrait
                return ExplorationAction.rotate(-90)
            }

        }

        ExplorationTrace.widgetTargets.clear()
        if (!windowRandomExplorationBudget.containsKey(currentAppState.window)) {
            if (currentAppState.isOpeningKeyboard) {
                log.info("New window but keyboard is open. Close keyboard")
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
            val budget = Helper.getVisibleInteractableWidgets(currentState).filter { !Helper.hasParentWithType(it,currentState,"WebView") }.size*budgetScale
            windowRandomExplorationBudget.put(currentAppState.window, budget.toInt())
            windowRandomExplorationBudgetLeft.put(currentAppState.window, 0)
        }

        if (phaseState != PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE) {
            if (targetWindowTryCount.filterNot {
                        flaggedWindows.contains(it.key) || fullyCoveredWindows.contains(it.key) || unreachableWindows.contains(it.key)
                                || currentAppState.window == targetWindow
                    }.any{it.key == currentAppState.window} ) {
                targetWindow = currentAppState.window
                phaseState = PhaseState.P1_INITIAL
            }
        }


        // check if target window is fully covered
        val targetEvents = untriggeredTargetEvents.filter { it.sourceWindow == targetWindow }
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
        }
        log.info("Target window: $targetWindow")
        chooseTask_P1(eContext, currentState)

        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
            if (windowRandomExplorationBudgetLeft.containsKey(currentAppState.window) && strategyTask !is GoToTargetWindowTask) {
                windowRandomExplorationBudgetLeft[currentAppState.window] = windowRandomExplorationBudgetLeft[currentAppState.window]!! + 1
            }
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
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(regressionTestingMF, regressionTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF,regressionTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)!!

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
        if (targetWindow == null) {
            if (strategyTask!=null) {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    // if random can be still run, keep running
                    log.info("Continue current task: $strategyTask")
                    return
                }
            }
            if (isLoginWindow(currentAppState)&& hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (currentAppState.getUnExercisedActions().isNotEmpty() && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            }
            setRandomExploration(randomExplorationTask, currentState,currentAppState)
            randomExplorationTask.isFullyExploration = true
            return
        }
        if (phaseState == PhaseState.P1_INITIAL) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    log.info("This window has target events.")
                    log.info("Exercise target component task chosen")
                    phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
                    return
                }
                // In case target events not found
                // Try random exploration
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
            if (isLoginWindow(currentAppState)) {
                // Try random login Window
                log.info("Try random in login window")
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
            if (currentAppState.getUnExercisedActions().isNotEmpty() ) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_RANDOM_EXPLORATION
                return
            }
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            randomExplorationTask.reset = true
            return
        }
        if (phaseState == PhaseState.P1_EXERCISE_TARGET_NODE) {
            if (!strategyTask!!.isTaskEnd(currentState)) {
                //Keep current task
                log.info("Continue exercise target window")
                return
            }
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    log.info("This window has target events.")
                    log.info("Exercise target component task chosen")
                    phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
                    return
                }
                // In case target events not found
                // Try random exploration
                if (!hasBudgetLeft(currentAppState.window)) {
                    flaggedWindows.add(targetWindow!!)
                    selectTargetNode()
                    phaseState = PhaseState.P1_INITIAL
                    chooseTask_P1(eContext, currentState)
                    return
                }
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
            if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
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
            }
            if (currentAppState.getUnExercisedActions().isNotEmpty() && hasBudgetLeft(currentAppState.window)) {
                if (hasBudgetLeft(currentAppState.window)) {
                    setRandomExploration(randomExplorationTask, currentState, currentAppState)
                    return
                }
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_GO_TO_TARGET_NODE
                return
            }
            if (hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }
            flaggedWindows.add(currentAppState.window)
            unreachableWindows.add(targetWindow!!)
            selectTargetNode()
            phaseState = PhaseState.P1_INITIAL
            chooseTask_P1(eContext, currentState)
            return
        }
        if (phaseState == PhaseState.P1_GO_TO_TARGET_NODE) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    log.info("This window has target events.")
                    log.info("Exercise target component task chosen")
                    phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue go to target window")
                    return
                }
                // In case target events not found
                // Try random exploration if having budget
                if (hasBudgetLeft(currentAppState.window)) {
                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }
                flaggedWindows.add(targetWindow!!)
                selectTargetNode()
                phaseState = PhaseState.P1_INITIAL
                chooseTask_P1(eContext, currentState)
                return
            }
            if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue ${strategyTask!!}")
                return
            }
            if (currentAppState.getUnExercisedActions().isNotEmpty() && hasBudgetLeft(currentAppState.window)) {
                    setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to explore window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_RANDOM_EXPLORATION
                return
            }
            if (hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState,currentAppState)
                return
            }
            flaggedWindows.add(currentAppState.window)
            unreachableWindows.add(targetWindow!!)
            selectTargetNode()
            phaseState = PhaseState.P1_INITIAL
            chooseTask_P1(eContext, currentState)
            return
        }
        if (phaseState == PhaseState.P1_RANDOM_IN_EXERCISE_TARGET_NODE) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    log.info("This window has target events.")
                    log.info("Exercise target component task chosen")
                    phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue random in target window")
                    return
                }
                if (hasBudgetLeft(currentAppState.window)) {
                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }
                flaggedWindows.add(targetWindow!!)
                selectTargetNode()
                phaseState = PhaseState.P1_INITIAL
                chooseTask_P1(eContext, currentState)
                return
            }
            if (isLoginWindow(currentAppState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (strategyTask==goToTargetNodeTask && !goToTargetNodeTask.isTaskEnd(currentState)) {
                log.info("Continue go to target window")
                return
            }
            if (currentAppState.getUnExercisedActions().isNotEmpty() && hasBudgetLeft(currentAppState.window)) {
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                return
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to explore window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_RANDOM_EXPLORATION
                return
            }
            setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
            randomExplorationTask.isFullyExploration = true
            return
        }
        if (phaseState == PhaseState.P1_RANDOM_EXPLORATION) {
            if (currentAppState.window == targetWindow) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    //Target events found
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    log.info("This window has target events.")
                    log.info("Exercise target component task chosen")
                    phaseState = PhaseState.P1_EXERCISE_TARGET_NODE
                    return
                }
                if (hasBudgetLeft(currentAppState.window)) {
                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }
                flaggedWindows.add(targetWindow!!)
                selectTargetNode()
                phaseState = PhaseState.P1_INITIAL
                chooseTask_P1(eContext, currentState)
                return
            }
            if (isLoginWindow(currentAppState) && strategyTask!!.isTaskEnd(currentState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                strategyTask = goToTargetNodeTask.also {
                    it.initialize(currentState)
                }
                log.info("Go to target window: ${targetWindow.toString()}")
                phaseState= PhaseState.P1_GO_TO_TARGET_NODE
                return
            }
            if (currentAppState.getUnExercisedActions().isNotEmpty() && strategyTask!!.isTaskEnd(currentState) && hasBudgetLeft(currentAppState.window)) {
                // Try random login Window
                log.info("Try random in login window")
                setRandomExploration(randomExplorationTask, currentState, currentAppState)
                return
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                // if random can be still run, keep running
                log.info("Continue exploration")
                return
            }
            if (goToAnotherNode.isAvailable(currentState) && hasBudgetLeft(currentAppState.window)) {
                strategyTask = goToAnotherNode.also {
                    it.initialize(currentState)
                }
                log.info("Go to explore window: ${targetWindow.toString()}")
                phaseState = PhaseState.P1_RANDOM_EXPLORATION
                return
            }

            if (randomExplorationTask.isFullyExploration) {
                unreachableWindows.add(targetWindow!!)
                flaggedWindows.add(currentAppState.window)
                selectTargetNode()
                phaseState = PhaseState.P1_INITIAL
                chooseTask_P1(eContext, currentState)
                return
            }
            setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState,currentAppState)

    }

    private fun hasBudgetLeft(window: WTGNode): Boolean {
        if (!windowRandomExplorationBudgetLeft.containsKey(window))
            return true
        if (windowRandomExplorationBudgetLeft.containsKey(window)) {
            if (windowRandomExplorationBudgetLeft[window]!! < windowRandomExplorationBudget[window]!!) {
                return true
            }
        }
        return false
    }

    private fun isLoginWindow(currentAppState: AbstractState): Boolean {
        val activity = currentAppState.window.classType.toLowerCase()
        return activity.contains("login") || activity.contains("signin")
    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAbstractState: AbstractState) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaximumAttempt(10)
        }
        log.info("Cannot find path the target node.")
        log.info("Random exploration")
        phaseState = PhaseState.P1_RANDOM_EXPLORATION

    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAbstractState: AbstractState) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaximumAttempt(25)
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
            it.setMaximumAttempt(currentState,25)
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
            val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(it).filter { edge ->
                it.staticEventMapping.contains(edge.label)}.map { it.label }
            val staticEvents = it.staticEventMapping.filter { abstractInteractions.contains(it.key) }.map { it.value }
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