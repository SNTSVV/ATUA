package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class PhaseTwoStrategy (
        autAutTestingStrategy: AutAutTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean,
        val unreachableWindow: Set<Window>
):AbstractPhaseStrategy (
    autAutTestingStrategy = autAutTestingStrategy,
        budgetScale = budgetScale,
    delay = delay,
    useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = false
) {
    override fun isTargetWindow(window: Window): Boolean {
        if (window == targetWindow)
            return true
        return false
    }

    override fun isTargetState(currentState: State<*>): Boolean {
        return true
    }


    private var numOfContinousTry: Int =0
    val statementMF: StatementCoverageMF

    var remainPhaseStateCount: Int = 0

    var targetWindow: Window? = null
    var phase2TargetEvents: HashMap<Input, Int> = HashMap()

    var targetWindowsCount: HashMap<Window, Int> = HashMap()

    val abstractStatesScores = HashMap<AbstractState, Double>()
    val abstractStateProbabilityByWindow = HashMap<Window, ArrayList<Pair<AbstractState, Double>>>()

    val modifiedMethodWeights = HashMap<String, Double>()
    val modifiedMethodMissingStatements = HashMap<String, HashSet<String>>()
    val appStateModifiedMethodMap = HashMap<AbstractState, HashSet<String>>()
    val modifiedMethodTriggerCount = HashMap<String, Int>()
    val staticNodeScores = HashMap<Window, Double> ()
    val staticNodesProbability = HashMap<Window, Double> ()
    var attempt: Int = 0
    var budgetLeft: Int = 0
    var needResetApp = false

    init {
        phaseState = PhaseState.P2_INITIAL
        autautMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        statementMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        autautMF.updateMethodCovFromLastChangeCount = 0
        autautMF.allTargetWindow_ModifiedMethods.keys.forEach {
            targetWindowsCount.put(it,0)
        }
        attempt = (targetWindowsCount.size*budgetScale).toInt()
    }

    override fun registerTriggeredEvents(abstractAction: AbstractAction, currentState: State<*>) {
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        //val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }
        if (!abstractState.inputMappings.containsKey(abstractAction)) {
            return
        }
        val staticEvents = abstractState.inputMappings[abstractAction]!!
        if (staticEvents!=null) {
            staticEvents.forEach {
                if (phase2TargetEvents.containsKey(it)) {
                    phase2TargetEvents[it] = phase2TargetEvents[it]!! + 1
                }
            }
        }
    }

    override fun hasNextAction(currentState: State<*>): Boolean {
        if (autautMF.lastUpdatedStatementCoverage == 1.0)
            return false
        //return true
        if (attempt < 0)
            return false
        return true
    }

    override fun nextAction(eContext: ExplorationContext<*, *, *>): ExplorationAction {
        if (autautMF == null)
        {
            autautMF = eContext.findWatcher { it is AutAutMF } as AutAutMF
        }
        var chosenAction:ExplorationAction

        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)

        if (currentAppState == null)
        {
            return eContext.resetApp()
        }

       /* if (targetWindow != null) {
            if (targetWindowsCount.containsKey(currentAppState.window)
                    && targetWindow != currentAppState.window) {
                if (targetWindowsCount[targetWindow!!]!! > targetWindowsCount[currentAppState.window]!!) {
                    targetWindow = currentAppState.window
                    targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!!+1
                }
            }
        }*/
        if (targetWindow == null) {
            computeAppStatesScore()
            selectTargetWindow(currentState,0)
        }

        log.info("Current abstract state: $currentAppState")
        log.info("Current window: ${currentAppState.window}")
        log.info("Target window: $targetWindow")
        chooseTask(eContext, currentState)
        /*if (needResetApp) {
            needResetApp = false
            return eContext.resetApp()
        }*/
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = eContext.resetApp()
        }
        budgetLeft--
        if (strategyTask is RandomExplorationTask && (strategyTask as RandomExplorationTask).fillingData == true) {
            budgetLeft++
        }
        if (strategyTask is ExerciseTargetComponentTask && (strategyTask as ExerciseTargetComponentTask).fillingData) {
            budgetLeft++
        }
        if (strategyTask is GoToAnotherWindow && (strategyTask as GoToAnotherWindow).isFillingText) {
            budgetLeft++
        }
        return chosenAction
    }

    override fun getPathsToTargetWindows(currentState: State<*>, includePressbackEvent: Boolean): List<TransitionPath> {
        //computeAppStatesScore()
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbState==null)
            return emptyList()
        if (!abstractStateProbabilityByWindow.containsKey(targetWindow)) {
            val targetAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }.random()
            val transitionPaths = ArrayList<TransitionPath>()
            val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractTransition,HashMap<AttributeValuationSet,String>>?>()
            childParentMap.put(currentAbState,null)
            PathFindingHelper.findPathToTargetComponent(currentState = currentState
                    , root = currentAbState
                    ,traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<Window>, currentAbState))
                    ,finalTarget = targetAbstractState
                    ,allPaths = transitionPaths
                    ,includeBackEvent = true
                    ,autautMF = autautMF
                    ,includingWTG = useVirtualAbstractState,
                    shortest = false,
                    pathCountLimitation = 10)
            return transitionPaths
        }

        val targetAbstractStatesProbability = abstractStateProbabilityByWindow[targetWindow]!!
        //targetAbstractStatesProbability.removeIf { it.first == currentAbState }
        val targetAbstractStatesPbMap = HashMap<AbstractState,Double>()
        targetAbstractStatesProbability.forEach {
            targetAbstractStatesPbMap.put(it.first,it.second)
        }

        val transitionPaths = ArrayList<TransitionPath>()
        getPathToStates(transitionPaths,targetAbstractStatesPbMap,currentAbState,currentState
                ,false,useVirtualAbstractState,true,true,false,15)
        return transitionPaths
    }

    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>{
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbstractState==null)
            return transitionPaths
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState
                        || it == currentAbstractState
                        || it.window is Launcher
                        || it.window is OutOfApp
                }
        /*val candiateWindows = WTGNode.allMeaningNodes.filter { window -> runtimeAbstractStates.find { it.window == window } != null}
        val candidateVisitedFoundNodes = regressionTestingMF.windowVisitCount
                .filter { candiateWindows.contains(it.key) } as HashMap*/
        val abstratStateCandidates = runtimeAbstractStates

        val stateByActionCount = HashMap<AbstractState,Double>()

        abstratStateCandidates.forEach {
            val weight =  it.computeScore(autautMF)
            if (weight>0.0) {
                stateByActionCount.put(it, weight)
            }
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
        getPathToStates(transitionPaths, stateCandidates, currentAbstractState, currentState
                ,false,false,true,true,false,25)
        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>):  Set<AbstractAction> {
        val targetEvents = HashMap<Input,List<AbstractAction>>()
        targetEvents.clear()

        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState!!.window == targetWindow)
        {
            val availableEvents = abstractState.inputMappings.map { it.value }.flatten()
            val events = HashMap(phase2TargetEvents.filter {
                availableEvents.contains(it.key)
            })
            if (events.isNotEmpty())
            {
                while (targetEvents.isEmpty() && events.isNotEmpty()) {
                    val leastTriggerCount = events.minBy { it.value }!!.value
                    val leastTriggerEvents = events.filter { it.value == leastTriggerCount }
                    leastTriggerEvents.forEach { t, u ->
                        events.remove(t)
                        val abstractActions = autautMF.validateEvent(t,currentState)
                        if (abstractActions.isNotEmpty())
                        {
                            targetEvents.put(t, abstractActions)
                        }
                    }
                }
            }


        }
        return targetEvents.map { it.value }.flatten().toSet()
    }

    var randomInputInTarget = false
    private fun chooseTask(eContext: ExplorationContext<*, *, *>, currentState: State<*>) {
        log.debug("Choosing Task")
        //val fillDataTask = FillTextInputTask.getInstance(regressionTestingMF,this,delay, useCoordinateClicks)
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(autautMF,autAutTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!
        /*if (!setTestBudget && currentAppState.window == targetWindow)
        {
            budgetLeft = (currentAppState.widgets.map { it.getPossibleActions() }.sum()*budgetScale).toInt()
            setTestBudget = true
        }*/
        if (budgetLeft > 0)
        {
            log.info("Exercise budget left: $budgetLeft")
            if (phaseState == PhaseState.P2_INITIAL) {
                nextActionOnInitial(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToTargetNodeTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P2_EXERCISE_TARGET_NODE)
            {
                nextActionOnExerciseTargetWindow(currentState, currentAppState, randomExplorationTask, goToTargetNodeTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P2_RANDOM_IN_EXERCISE_TARGET_NODE) {
                nextActionOnRandomInTargetWindow(randomExplorationTask, currentAppState, currentState, exerciseTargetComponentTask, goToTargetNodeTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P2_GO_TO_TARGET_NODE)
            {
                nextActionOnGoToTargetWindow(currentState, currentAppState, exerciseTargetComponentTask, randomExplorationTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P2_GO_TO_EXPLORE_STATE) {

                nextActionOnGoToExploreState(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToTargetNodeTask)
                return
            }
            if (phaseState == PhaseState.P2_RANDOM_EXPLORATION)
            {
                if (nextActionOnRandomExploration(currentAppState, exerciseTargetComponentTask, currentState, randomExplorationTask, goToTargetNodeTask, goToAnotherNode))
                    return

            }
            //log.info("PhaseState undefined.")
            //setRandomExploration(randomExplorationTask, currentState)
        }
        attempt--
        computeAppStatesScore()
        selectTargetWindow(currentState,0)
        log.info("Phase budget left: $attempt")
        //setTestBudget = false
        //needResetApp = true
        phaseState = PhaseState.P2_INITIAL
        chooseTask(eContext, currentState)
        return
    }

    private fun nextActionOnInitial(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToTargetNodeTask: GoToTargetWindowTask, goToAnotherNode: GoToAnotherWindow) {
        randomInputInTarget = true
        if (currentAppState.window == targetWindow) {
            if (randomInputInTarget) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
            } else {
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
            /*
                    return*/
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() || hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            return
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            strategyTask = goToAnotherNode.also {
                it.initialize(currentState)
                it.retryTimes = 0
            }
            log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
            numOfContinousTry = 0
            phaseState = PhaseState.P2_GO_TO_EXPLORE_STATE
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnExerciseTargetWindow(currentState: State<*>, currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, goToTargetNodeTask: GoToTargetWindowTask, goToAnotherNode: GoToAnotherWindow) {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            //Keep current task
            log.info("Continue exercise target window")
            return
        }
        randomInputInTarget = false
        if (currentAppState.window == targetWindow) {
            setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
            return
        }
        if (currentAppState.window is Dialog) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState, true, lockWindow = false)
        }

        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() || hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            return
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnRandomInTargetWindow(randomExplorationTask: RandomExplorationTask, currentAppState: AbstractState, currentState: State<*>, exerciseTargetComponentTask: ExerciseTargetComponentTask, goToTargetNodeTask: GoToTargetWindowTask, goToAnotherNode: GoToAnotherWindow) {
        if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
            // if random can be still run, keep running
            log.info("Continue filling data")
            return
        }
        if (currentAppState.window == targetWindow) {
            if (randomExplorationTask.fillingData) {
                // if random can be still run, keep running
                log.info("Continue filling data")
                return
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue random in target window")
                return
            }
            randomInputInTarget = true
            if (randomInputInTarget) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
            } else {
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue random in target window")
            return
        }
        randomInputInTarget = true
        if (currentAppState.window is Dialog) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState, true, lockWindow = false)
            return
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnGoToTargetWindow(currentState: State<*>, currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow) {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            //Keep current task
            log.info("Continue go to target window")
            return
        }
        if (currentAppState.window == targetWindow) {
            if (randomInputInTarget) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
            } else {
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() || hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnGoToExploreState(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToTargetNodeTask: GoToTargetWindowTask) {
        if (currentAppState.window == targetWindow) {
            if (randomInputInTarget) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return
                }
            } else {
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return
            }
            return
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            //Keep current task
            log.info("Continue go to the window")
            return
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() || hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnRandomExploration(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, randomExplorationTask: RandomExplorationTask, goToTargetNodeTask: GoToTargetWindowTask, goToAnotherNode: GoToAnotherWindow): Boolean {
        if (currentAppState.window == targetWindow) {
            if (randomInputInTarget) {
                if (exerciseTargetComponentTask.isAvailable(currentState)) {
                    setExerciseTarget(exerciseTargetComponentTask, currentState)
                    return true
                }
            } else {
                setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                return true
            }
        }
        if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
            // if random can be still run, keep running
            log.info("Continue filling data")
            return true
        }

        if (randomExplorationTask.isFullyExploration && !strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue fully exploration")
            return true
        }
        if (currentAppState.window is Dialog && !strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue doing random exploration")
            return true
        }
        if (randomExplorationTask.stopWhenHavingTestPath && currentAppState.window !is Dialog) {
            if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true)) {
                return true
            }
        }
        if (currentAppState.window is Dialog) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState, true, lockWindow = false)
            return true
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, true)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return true
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() && !strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue doing random exploration")
            return true
        }

        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return true
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            //Keep current task
            log.info("Continue doing random exploration")
            return true
        }
        if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() || hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState, currentAppState)
            return true
        }

        if (!randomExplorationTask.isFullyExploration) {
            setFullyRandomExploration(randomExplorationTask, currentState)
            return true
        }
        return false
    }

    private fun setGoToExploreState(goToAnotherNode: GoToAnotherWindow, currentState: State<*>) {
        strategyTask = goToAnotherNode.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
        log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
        phaseState = PhaseState.P2_GO_TO_EXPLORE_STATE
    }

    private fun setGoToTarget(goToTargetNodeTask: GoToTargetWindowTask, currentState: State<*>) {
        log.info("Task chosen: Go to target node .")
        remainPhaseStateCount += 1
        strategyTask = goToTargetNodeTask.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
        phaseState = PhaseState.P2_GO_TO_TARGET_NODE
    }

    private fun setExerciseTarget(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>) {
        log.info("Task chosen: Exercise Target Node .")
        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
        remainPhaseStateCount = 0
        strategyTask = exerciseTargetComponentTask.also {
            it.initialize(currentState)
            it.randomRefillingData = true
            it.environmentChange = true
            it.alwaysUseRandomInput = true
        }
    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        log.info("Task chosen: Fully Random Exploration")
        phaseState = PhaseState.P2_RANDOM_EXPLORATION
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaxiumAttempt((5*budgetScale).toInt())
            it.isFullyExploration = true
            it.environmentChange = true
            it.alwaysUseRandomInput = true
            it.stopWhenHavingTestPath = false
            it.lockTargetWindow(currentAbstractState.window)
        }
    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask,
                                     currentState: State<*>,
                                     currentAbstractState: AbstractState,
                                     stopWhenTestPathIdentified: Boolean = false,
                                     lockWindow: Boolean = true) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.environmentChange = true
            it.alwaysUseRandomInput = true
            it.setMaxiumAttempt((5*budgetScale).toInt())
            if (lockWindow && currentAbstractState.belongToAUT()) {
                it.lockTargetWindow(currentAbstractState.window)
            }
            it.stopWhenHavingTestPath = stopWhenTestPathIdentified
        }
        log.info("Cannot find path the target node.")
        log.info("Random exploration")
        phaseState = PhaseState.P2_RANDOM_EXPLORATION
    }

    private fun setRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaxiumAttempt((2*budgetScale).toInt())
            it.isFullyExploration = true
            it.environmentChange = true
            it.lockTargetWindow(targetWindow!!)
            it.alwaysUseRandomInput = true
        }
        log.info("Random exploration in target window")
        phaseState = PhaseState.P2_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    var setTestBudget = false

    fun selectTargetWindow(currentState: State<*>, numberOfTried: Int, in_maxTried: Int = 0){
        val leastTriedWindows = targetWindowsCount.filter { staticNodeScores.containsKey(it.key) }. map { Pair<Window, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
        val leastTriedWindowScore = staticNodeScores.filter {windowScore -> leastTriedWindows.value.any { it.first == windowScore.key } }
        val maxTried =
        if (in_maxTried == 0) {
            leastTriedWindowScore.size/2+1
        } else {
            in_maxTried
        }
        val pb = ProbabilityDistribution<Window>(staticNodeScores)
        val targetNode = pb.getRandomVariable()
        targetWindow = targetNode
       if (leastTriedWindowScore.isNotEmpty()) {
            val pb = ProbabilityDistribution<Window>(leastTriedWindowScore)
            val targetNode = pb.getRandomVariable()
            targetWindow = targetNode
        } else {
            targetWindow = targetWindowsCount.map { it.key }.random()
        }
        targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!!+1
       if (numberOfTried< maxTried && getPathsToWindowToExplore(currentState, targetWindow!!,true,true).isEmpty()) {
            selectTargetWindow(currentState, numberOfTried+1,maxTried)
            return
        }
        val widgetSize: Int  = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow}.maxBy { it.attributeValuationSets.size }?.attributeValuationSets?.size?:0
        budgetLeft = (TEST_BUDGET * budgetScale).toInt()
        //setTestBudget = false

        autautMF.updateMethodCovFromLastChangeCount = 0
    }



     fun computeAppStatesScore(){
        //Initiate reachable modified methods list
        modifiedMethodMissingStatements.clear()
         modifiedMethodTriggerCount.clear()
         appStateModifiedMethodMap.clear()
         modifiedMethodWeights.clear()
         val allTargetInputs = ArrayList(autautMF.allTargetInputs)

         val triggeredStatements = statementMF.getAllExecutedStatements()
        statementMF.getAllModifiedMethodsId().forEach {
            val methodName = statementMF!!.getMethodName(it)
            if (!autautMF.unreachableModifiedMethods.contains(methodName))
            {
                modifiedMethodTriggerCount.put(it,0)
                val statements = statementMF!!.getMethodStatements(it)
                val missingStatements = statements.filter { !triggeredStatements.contains(it) }
                modifiedMethodMissingStatements.put(it,HashSet(missingStatements))
            }
        }
         allTargetInputs.removeIf {
             it.modifiedMethods.map { it.key }.all {
                  modifiedMethodMissingStatements.containsKey(it) && modifiedMethodMissingStatements[it]!!.size==0
             }
         }
        //get all AppState
        val appStateList = ArrayList<AbstractState>()
        AbstractStateManager.instance.getPotentialAbstractStates().forEach { appStateList.add(it) }

        //get all AppState's edges and appState's modified method
        val edges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        appStateList.forEach {appState ->
            edges.addAll(autautMF.abstractTransitionGraph.edges(appState))
            appStateModifiedMethodMap.put(appState, HashSet())
            appState.abstractTransitions.map { it.modifiedMethods}.forEach { hmap ->
                hmap.forEach { m, v ->
                    if (!appStateModifiedMethodMap[appState]!!.contains(m)) {
                        appStateModifiedMethodMap[appState]!!.add(m)
                    }
                }
            }
        }

        //for each edge, count modified method appearing
        edges.forEach {edge ->
            val coveredMethods = autautMF.abstractTransitionGraph.methodCoverageInfo[edge]
            if (coveredMethods!=null)
                coveredMethods.forEach {
                    if (autautMF.statementMF!!.isModifiedMethod(it)) {
                        if (modifiedMethodTriggerCount.containsKey(it)) {
                            modifiedMethodTriggerCount[it] = modifiedMethodTriggerCount[it]!! + edge.label.interactions.size
                        }
                    }
                }

        }
        //calculate modified method score
        val totalAbstractInteractionCount = edges.size
        modifiedMethodTriggerCount.forEach { m, c ->
            val score = 1-c/totalAbstractInteractionCount.toDouble()
            modifiedMethodWeights.put(m,score)
        }

        //calculate appState score
        appStateList .forEach {
            var appStateScore:Double = 0.0
            if (appStateModifiedMethodMap.containsKey(it))
            {
                appStateModifiedMethodMap[it]!!.forEach {
                    if (!modifiedMethodWeights.containsKey(it))
                        modifiedMethodWeights.put(it,1.0)
                    val methodWeight = modifiedMethodWeights[it]!!
                    if (modifiedMethodMissingStatements.containsKey(it))
                    {
                        val missingStatementNumber = modifiedMethodMissingStatements[it]!!.size
                        appStateScore += (methodWeight * missingStatementNumber)
                    }
                }
                //appStateScore += 1
                abstractStatesScores.put(it,appStateScore)
            }
        }

        //calculate appState probability
        appStateList.groupBy { it.window }. forEach { window, abstractStateList ->
            var totalScore = 0.0
            abstractStateList.forEach {
                totalScore += abstractStatesScores[it]!!
            }

            val appStatesProbab = ArrayList<Pair<AbstractState,Double>>()
            abstractStateProbabilityByWindow.put(window, appStatesProbab )
            abstractStateList.forEach {
                val pb = abstractStatesScores[it]!!/totalScore
                appStatesProbab.add(Pair(it,pb))
            }
        }

        //calculate staticNode score
        var staticNodeTotalScore = 0.0
         staticNodeScores.clear()
        targetWindowsCount.forEach { n, _ ->
            var weight: Double = 0.0
            val modifiedMethods = HashSet<String>()
/*            appStateModifiedMethodMap.filter { it.key.staticNode == n}.map { it.value }.forEach {
                it.forEach {
                    if (!modifiedMethods.contains(it))
                    {
                        modifiedMethods.add(it)
                    }
                }
            }*/
            allTargetInputs.filter { it.sourceWindow == n }. forEach {
                modifiedMethods.addAll(it.modifiedMethods.map { it.key })

            }

            if (autautMF.windowHandlersHashMap.containsKey(n)) {
                autautMF.windowHandlersHashMap[n]!!.forEach { handler ->
                    val methods = autautMF.modifiedMethodTopCallersMap.filter { it.value.contains(handler) }.map { it.key }
                    modifiedMethods.addAll(methods)
                }
            }

            modifiedMethods.filter { modifiedMethodWeights.containsKey(it) }. forEach {
                val methodWeight = modifiedMethodWeights[it]!!
                val missingStatementsNumber = modifiedMethodMissingStatements[it]?.size?:0
                weight += (methodWeight*missingStatementsNumber)
            }
            if (weight>0.0)
            {
                staticNodeScores.put(n, weight)
                staticNodeTotalScore+=weight

                appStateList.map { it.inputMappings }.map { it.values }.flatten().flatten().filter{
                    allTargetInputs.contains(it)
                }.forEach {
                    if (it.eventType != EventType.resetApp
                            && it.eventType != EventType.implicit_launch_event
                            && it.eventType != EventType.press_back
                            && it.eventType != EventType.implicit_back_event) {
                        phase2TargetEvents.put(it,0)
                    }
                }
            }

        }
        staticNodesProbability.clear()
        //calculate staticNode probability
        staticNodeScores.forEach { n, s ->
            val pb = s/staticNodeTotalScore
            staticNodesProbability.put(n,pb)
        }
    }


    companion object {
        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(PhaseTwoStrategy::class.java) }

        val TEST_BUDGET: Int = 25
    }
}