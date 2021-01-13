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
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.WTG.EventType
import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.TransitionPath
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.WindowManager
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import presto.android.gui.clients.regression.informationRetrieval.InformationRetrieval
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.random.Random

class PhaseThreeStrategy(
        autAutTestingStrategy: AutAutTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        autAutTestingStrategy = autAutTestingStrategy,
        scaleFactor = budgetScale,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = false
) {


    val statementMF: StatementCoverageMF
    var remainPhaseStateCount: Int = 0

    var targetWindow: Window? = null
    var allTargetInputs: HashMap<Input, Int> = HashMap()
    var targetEvent: Input? = null
    var targetWindowsCount: HashMap<Window, Int> = HashMap()

    val abstractStatesScores = HashMap<AbstractState, Double>()
    val abstractStateProbability = HashMap<Window, ArrayList<Pair<AbstractState, Double>>>()

    val modifiedMethodWeights = HashMap<String, Double>()
    val modifiedMethodMissingStatements = HashMap<String, HashSet<String>>()
    val appStateModifiedMethodMap = HashMap<AbstractState, HashSet<String>>()
    val windowModifiedMethodMap = HashMap<Window, HashSet<String>>()
    val modifiedMethodTriggerCount = HashMap<String, Int>()
    val windowScores = HashMap<Window, Double> ()
    val windowsProbability = HashMap<Window, Double> ()

    var budgetLeft: Int = 0
    var needResetApp = false
    init {
        phaseState = PhaseState.P3_INITIAL
        autautMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        statementMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        autautMF.updateMethodCovFromLastChangeCount = 0
        autautMF.allTargetWindow_ModifiedMethods.map { it.key }.forEach {
            if (!targetWindowsCount.contains(it)) {
                targetWindowsCount.put(it,0)
            }
        }

    }
    override fun isTargetWindow(window: Window): Boolean {
        if (window == targetWindow)
            return true
        return false
    }

    override fun isTargetState(currentState: State<*>): Boolean {
        return true
    }

    override fun registerTriggeredEvents(abstractAction: AbstractAction, currentState: State<*>) {
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        //val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }

        val staticEvents = abstractState.inputMappings[abstractAction]
        if (staticEvents!=null) {
            staticEvents.forEach {
                if (it == targetEvent) {
                    selectRelatedWindow(currentState,0,0)
                }
            }
        }
    }

    override fun hasNextAction(currentState: State<*>): Boolean {

        if (autautMF.lastUpdatedStatementCoverage == 1.0) {
            return false
        }
        autautMF.allTargetWindow_ModifiedMethods.map { it.key }.forEach {
            if (!targetWindowsCount.contains(it)) {
                targetWindowsCount.put(it,0)
            }
        }
        return true
    }
    override fun nextAction(eContext: ExplorationContext<*, *, *>): ExplorationAction {
        if (autautMF == null)
        {
            autautMF = eContext.findWatcher { it is AutAutMF } as AutAutMF
        }
        var chosenAction:ExplorationAction

        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF.getAbstractState(currentState)!!
        if (targetWindow == null) {
            selectTargetWindow(currentState,0,0)
            selectTargetStaticEvent(currentState)
        }
        if (relatedWindow==null) {
            WindowManager.instance.allMeaningWindows.random()
        }
        log.info("Current abstract state: $currentAppState")
        log.info("Current window: ${currentAppState.window}")
        log.debug("Target: $targetWindow")
        log.debug("To test Window transition: $targetEvent")
        log.debug("Related window: $relatedWindow")
        chooseTask(eContext, currentState)
        if (needResetApp) {
            needResetApp = false
            return eContext.resetApp()
        }
      /*  if (strategyTask is RandomExplorationTask) {
            if (!(strategyTask as RandomExplorationTask).fillingData)   {
              budgetLeft--
            }
        } else if (strategyTask is ExerciseTargetComponentTask) {
            if (!(strategyTask as ExerciseTargetComponentTask).fillingData) {
                budgetLeft--
            }
        }
        else {
            budgetLeft--
        }*/

        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = eContext.resetApp()
        }
        if (strategyTask is RandomExplorationTask
                && (strategyTask as RandomExplorationTask).fillingData != true
                && phaseState == PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW) {
            budgetLeft--
        }
        return chosenAction
    }

    override fun getPathsToVisitedStates(currentState: State<*>,pathType: PathFindingHelper.PathType): List<TransitionPath> {
        if (targetWindow==null)
            return emptyList()
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbState = autautMF.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbState==null)
            return transitionPaths
        val runtimeAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState
                        || it == currentAbState
                        || it.window is Launcher
                        || it.window is OutOfApp
                }
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

        getPathToStatesBasedOnPathType(pathType,transitionPaths,stateByActionCount,currentAbState,currentState)
        return transitionPaths
    }

    override fun getPathsToTargetWindows(currentState: State<*>, pathType: PathFindingHelper.PathType): List<TransitionPath> {
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbState==null)
            return emptyList()
        val targetAppStatesDistribution = HashMap<AbstractState,Pair<Double, Double>>()

        if (!abstractStateProbability.containsKey(targetWindow))
            return emptyList()
        val targetScores = HashMap<AbstractState,Double>()
        abstractStateProbability[targetWindow]!!.filter {
            it.first.inputMappings.filter { it.value.contains(targetEvent) }.isNotEmpty()
        }.forEach {
            targetScores.put(it.first,it.second)
        }
        val transitionPaths = ArrayList<TransitionPath>()
        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>): Set<AbstractAction> {
        val targetEvents = HashMap<Input,List<AbstractAction>>()
        targetEvents.clear()

        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState!!.window == targetWindow)
        {
            val abstractActions = autautMF.validateEvent(targetEvent!!,currentState)
            if (abstractActions.isNotEmpty())
            {
                targetEvents.put(targetEvent!!, abstractActions)
            }
        }
        return targetEvents.map { it.value }.flatten().toSet()
    }

    var relatedWindow: Window? = null
    var exercisedRelatedWindow: Boolean = false
    fun chooseTask(eContext: ExplorationContext<*, *, *>, currentState: State<*>){
        log.debug("Choosing Task")
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(autautMF, autAutTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(autautMF, autAutTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = autautMF!!.getAbstractState(currentState)!!
        if (windowBudgetLeft < 0) {
            computeAppStatesScore()
            selectTargetWindow(currentState,0,0)
            selectTargetStaticEvent(currentState)
            phaseState = PhaseState.P3_INITIAL
        }
        if (budgetLeft > 0 || budgetLeft == -1)
        {
            log.info("Exercise budget left: $budgetLeft")
            if (phaseState == PhaseState.P3_INITIAL)
            {
                nextActionOnInitial(currentAppState, randomExplorationTask, currentState, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P3_GO_TO_RELATED_NODE)
            {
                nextActionOnGoToRelatedWindow(currentState, currentAppState, randomExplorationTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW)
            {
                nextActionOnRandomExplorationInRelatedWindow(randomExplorationTask, currentState, currentAppState, exerciseTargetComponentTask, goToAnotherNode, goToTargetNodeTask)
                return
            }
            if (phaseState == PhaseState.P3_GO_TO_TARGET_NODE) {
                nextActionOnGoToTargetWindow(currentAppState, exerciseTargetComponentTask, currentState, goToTargetNodeTask, randomExplorationTask, goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P3_EXERCISE_TARGET_NODE)
            {
                nextActionOnExerciseTargetWindow(currentState, currentAppState, randomExplorationTask, eContext)
                return
            }
            if (phaseState == PhaseState.P3_GO_TO_EXPLORE_STATE) {
                nextActionOnGoToExploreState(currentState, currentAppState, randomExplorationTask, exerciseTargetComponentTask,goToAnotherNode)
                return
            }
            if (phaseState == PhaseState.P3_RANDOM_EXPLORATION) {
                nextActionOnRandomExploration(randomExplorationTask, currentAppState, currentState, goToAnotherNode, exerciseTargetComponentTask, goToTargetNodeTask)
                return
            }
            log.info("undefined PhaseState.")
            setRandomExploration(randomExplorationTask, currentState)
            return
        }
        if (windowBudgetLeft > 0) {
            selectTargetStaticEvent(currentState)
            phaseState = PhaseState.P3_INITIAL
            chooseTask(eContext, currentState)
        }
        else {
            computeAppStatesScore()
            selectTargetWindow(currentState,0,0)
            selectTargetStaticEvent(currentState)
            phaseState = PhaseState.P3_INITIAL
            chooseTask(eContext, currentState)
            return
        }
    }

    private fun nextActionOnInitial(currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, currentState: State<*>, goToAnotherNode: GoToAnotherWindow) {
        exercisedRelatedWindow = false
        if (currentAppState.window == relatedWindow) {
            setRandomExplorationInRelatedWindow(randomExplorationTask, currentState)
            return
        }
        if (goToAnotherNode.isAvailable(currentState, relatedWindow!!, true, true,false)) {
            setGoToRelatedWindow(goToAnotherNode, currentState)
            return
        }
        if (hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState)
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnGoToRelatedWindow(currentState: State<*>, currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow) {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        if (currentAppState.window == relatedWindow) {
            setRandomExplorationInRelatedWindow(randomExplorationTask, currentState)
            return
        }
        selectTargetStaticEvent(currentState)
        setRandomExploration(randomExplorationTask, currentState)
        phaseState = PhaseState.P3_INITIAL
        return
    }

    private fun nextActionOnRandomExplorationInRelatedWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>, currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, goToAnotherNode: GoToAnotherWindow, goToTargetNodeTask: GoToTargetWindowTask) {
        if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
            // if random can be still run, keep running
            log.info("Continue filling data")
            return
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        exercisedRelatedWindow = true
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetComponentTask.isAvailable(currentState)) {
                setExerciseTarget(exerciseTargetComponentTask, currentState)
                return
            }
        }
        if (currentAppState.window is Dialog || currentAppState.window is OptionsMenu) {
            if (goToAnotherNode.isAvailable(currentState, relatedWindow!!, false, false,false)) {
                setGoToRelatedWindow(goToAnotherNode, currentState)
                return
            }
            setRandomExploration(randomExplorationTask, currentState)
            return
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, false,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (hasUnexploreWidgets(currentState)) {
            setRandomExploration(randomExplorationTask, currentState)
        }
        if (goToAnotherNode.isAvailable(currentState)) {
            setGoToExploreState(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnGoToTargetWindow(currentAppState: AbstractState, exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>, goToTargetNodeTask: GoToTargetWindowTask, randomExplorationTask: RandomExplorationTask, goToAnotherNode: GoToAnotherWindow) {
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetComponentTask.isAvailable(currentState)) {
                setExerciseTarget(exerciseTargetComponentTask, currentState)
                return
            }
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        selectTargetWindow(currentState,0,0)
        setRandomExploration(randomExplorationTask, currentState)
        phaseState = PhaseState.P3_INITIAL
        return
    }

    private fun nextActionOnExerciseTargetWindow(currentState: State<*>, currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, eContext: ExplorationContext<*, *, *>) {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        if (currentAppState.window is Dialog || currentAppState.window is OptionsMenu) {
            setRandomExploration(randomExplorationTask, currentState)
            return
        }
        exercisedRelatedWindow = false
        phaseState = PhaseState.P3_INITIAL
        selectTargetStaticEvent(currentState)
        chooseTask(eContext, currentState)
        return
    }

    private fun nextActionOnGoToExploreState(currentState: State<*>, currentAppState: AbstractState, randomExplorationTask: RandomExplorationTask, exerciseTargetComponentTask: ExerciseTargetComponentTask, goToAnotherNode: GoToAnotherWindow) {
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        if (currentAppState.window == relatedWindow && !exercisedRelatedWindow) {
            setRandomExplorationInRelatedWindow(randomExplorationTask, currentState)
            return
        }
        if (currentAppState.window == targetWindow && exercisedRelatedWindow) {
            if (exerciseTargetComponentTask.isAvailable(currentState)) {
                setExerciseTarget(exerciseTargetComponentTask, currentState)
                return
            }
            if (!strategyTask!!.isTaskEnd(currentState)) {
                log.info("Continue ${strategyTask!!.javaClass.name}")
                return
            }
        }
        if (!exercisedRelatedWindow && goToAnotherNode.isAvailable(currentState, relatedWindow!!, true, false,false)) {
            setGoToRelatedWindow(goToAnotherNode, currentState)
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        return
    }

    private fun nextActionOnRandomExploration(randomExplorationTask: RandomExplorationTask, currentAppState: AbstractState, currentState: State<*>, goToAnotherNode: GoToAnotherWindow, exerciseTargetComponentTask: ExerciseTargetComponentTask, goToTargetNodeTask: GoToTargetWindowTask) {
        if (randomExplorationTask.fillingData || randomExplorationTask.attemptCount == 0) {
            // if random can be still run, keep running
            log.info("Continue filling data")
            return
        }
        if (currentAppState.window == relatedWindow && !exercisedRelatedWindow) {
            setRandomExplorationInRelatedWindow(randomExplorationTask, currentState)
            return
        }
        if (randomExplorationTask.isFullyExploration && !strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        /*if (randomExplorationTask.isFullyExploration && !strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue fully exploration")
                    return
                }*/
        if (!exercisedRelatedWindow && goToAnotherNode.isAvailable(currentState, relatedWindow!!, true, false,false)) {
            setGoToRelatedWindow(goToAnotherNode, currentState)
            return
        }
        if (currentAppState.window == targetWindow) {
            if (exerciseTargetComponentTask.isAvailable(currentState)) {
                setExerciseTarget(exerciseTargetComponentTask, currentState)
                return
            }
        }
        if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!, true, false,false)) {
            setGoToTarget(goToTargetNodeTask, currentState)
            return
        }
        if (!strategyTask!!.isTaskEnd(currentState)) {
            log.info("Continue ${strategyTask!!.javaClass.name}")
            return
        }
        setFullyRandomExploration(randomExplorationTask, currentState)
        phaseState = PhaseState.P3_INITIAL
        selectTargetStaticEvent(currentState)
        return
    }

    private fun setGoToExploreState(goToAnotherNode: GoToAnotherWindow, currentState: State<*>) {
        strategyTask = goToAnotherNode.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
        log.info("Go to explore another window: ${targetWindow.toString()}")
        phaseState = PhaseState.P3_GO_TO_EXPLORE_STATE
    }

    private fun setGoToRelatedWindow(goToAnotherNode: GoToAnotherWindow, currentState: State<*>) {
        log.info("Task chosen: Go to related window: $relatedWindow")
        phaseState = PhaseState.P3_GO_TO_RELATED_NODE
        remainPhaseStateCount = 0
        strategyTask = goToAnotherNode.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
    }

    private fun setGoToTarget(goToTargetNodeTask: GoToTargetWindowTask, currentState: State<*>) {
        log.info("Task chosen: Go to target window: $targetWindow .")
        phaseState = PhaseState.P3_GO_TO_TARGET_NODE
        remainPhaseStateCount = 0
        strategyTask = goToTargetNodeTask.also {
            it.initialize(currentState)
            it.retryTimes = 0
        }
    }

    private fun setExerciseTarget(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>) {
        log.info("Task chosen: Exercise Target Node .")
        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
        remainPhaseStateCount = 0
        strategyTask = exerciseTargetComponentTask.also {
            it.initialize(currentState)
            it.alwaysUseRandomInput = true
            it.environmentChange = true
        }
    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        log.info("Cannot go to target Window. Random Exploration with 10 actions")
        phaseState = PhaseState.P3_RANDOM_EXPLORATION
        remainPhaseStateCount = 0
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.isFullyExploration = true
            it.backAction = true
            it.setMaxiumAttempt((5*scaleFactor).toInt())
            it.environmentChange = true
            it.alwaysUseRandomInput = true
        }
    }


    private fun setRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.lockTargetWindow(targetWindow!!)
            it.setMaxiumAttempt(currentState,(5*scaleFactor).toInt())
            it.backAction = false
            it.environmentChange = true
            it.alwaysUseRandomInput = true
        }
        log.info("This window is a target window but cannot find any target window transition")
        log.info("Random exploration in current window")
        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
    }

    private fun setRandomExplorationInRelatedWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.lockTargetWindow(relatedWindow!!)
            it.environmentChange = true
            it.alwaysUseRandomInput = true
            it.setMaxiumAttempt((5*scaleFactor).toInt())
        }
        log.info("In related window $relatedWindow.")
        log.info("Random exploration in current window")
        phaseState = PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW
    }

    var windowBudgetLeft: Int = 0

    fun selectTargetWindow(currentState: State<*>, numTried: Int, maxTry: Int) {
         computeEventWindowCorrelation()
        if (selectLeastTriedTargetWindow(maxTry, numTried, currentState)) return

        computeTargetEventScores()
        /*if (targetEventScores.isEmpty()) {
            eliminateWindows.add(targetWindow!!)
            selectTargetWindow(currentState, numTried+1, maxTry)
        }*/
        windowBudgetLeft = targetEventScores.size
    }

    private fun computeTargetEventScores() {
        targetModifiedMethods.clear()
        targetModifiedMethods.addAll(windowModifiedMethodMap[targetWindow!!]!!)
        targetEventScores.clear()
        allTargetInputs.filter {
            it.key.sourceWindow == targetWindow
        }.forEach {
            var score: Double = 0.0
            it.key.modifiedMethods.filter { it.value }.forEach {
                val method = it.key
                if (modifiedMethodMissingStatements.containsKey(method)) {
                    val totalStmt = statementMF.getMethodStatements(method).size
                    val missingStmt = modifiedMethodMissingStatements[method]!!.size
                    val coveredStmt = totalStmt - missingStmt
                    score += (modifiedMethodWeights[method]!! * coveredStmt)
                }

            }
            if (score > 0.0)
                targetEventScores.put(it.key, score)
        }

       /*if (targetEventScores.isEmpty()) {
           allTargetInputs.forEach {
                targetEventScores.put(it.key, 1.0)
            }
        }*/
    }

    private fun selectLeastTriedTargetWindow(maxTry: Int, numTried: Int, currentState: State<*>): Boolean {
        val tarqetWindowCandidates = targetWindowsCount.filter {windowModifiedMethodMap.containsKey(it.key) &&  containTargetEvents(it.key) }
        val leastTryTargetWindows: List<Window>
        if (tarqetWindowCandidates.isNotEmpty()) {
            leastTryTargetWindows = tarqetWindowCandidates.map { Pair<Window, Int>(first = it.key, second = it.value) }
                    .groupBy { it.second }.entries
                    .sortedBy { it.key }
                    .first().value
                    .map { it.first }
            if (Random.nextBoolean()) {
                targetWindow = leastTryTargetWindows.random()
            } else if (windowScores.filter { leastTryTargetWindows.contains(it.key) }.isNotEmpty()) {
                val pb = ProbabilityDistribution<Window>(windowsProbability.filter { leastTryTargetWindows.contains(it.key) })
                val targetNode = pb.getRandomVariable()
                targetWindow = targetNode
            } else {
                targetWindow = leastTryTargetWindows.random()
            }
        } else {
            leastTryTargetWindows = targetWindowsCount.filter { !eliminateWindows.contains(it.key) }.map { Pair<Window, Int>(first = it.key, second = it.value) }
                    .groupBy { it.second }.entries
                    .sortedBy { it.key }
                    .first().value
                    .map { it.first }
            if (Random.nextBoolean()) {
                targetWindow = leastTryTargetWindows.random()
            } else {
                if (windowScores.filter { leastTryTargetWindows.contains(it.key) }.isNotEmpty()) {
                    val pb = ProbabilityDistribution<Window>(windowsProbability.filter { leastTryTargetWindows.contains(it.key) })
                    val targetNode = pb.getRandomVariable()
                    targetWindow = targetNode
                } else {
                    targetWindow = leastTryTargetWindows.random()
                }
            }
        }
        targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!! + 1
        var max = if (maxTry == 0)
            leastTryTargetWindows.size / 2 + 1
        else
            maxTry

        if (numTried < max && getPathsToWindowToExplore(currentState, targetWindow!!, PathFindingHelper.PathType.ANY,false).isEmpty()) {
            selectTargetWindow(currentState, numTried + 1, max)
            return true
        }

        return false
    }

    val eliminateWindows = ArrayList<Window>()
    private fun containTargetEvents(window: Window): Boolean {
        return allTargetInputs.any {
            it.key.sourceWindow == window &&
                    eventWindowCorrelation.containsKey(it.key)}
    }

    val targetModifiedMethods = ArrayList<String>()
    val targetEventScores = HashMap<Input, Double>()

    fun selectTargetStaticEvent(currentState: State<*>){
        val windowEvents = getWindowAvailableEvents()
        val leastExerciseEventsCount = windowEvents
                .minBy { it.value }!!.value
        val leastExerciseEvents = windowEvents.filter { it.value == leastExerciseEventsCount }
        val leastExerciseEventScores = targetEventScores.filter { leastExerciseEvents.containsKey(it.key) }
        //pick up randomly a target event
        val pdForTargetEvents = ProbabilityDistribution<Input>(leastExerciseEventScores)
        targetEvent = pdForTargetEvents.getRandomVariable()
        allTargetInputs[targetEvent!!] = allTargetInputs[targetEvent!!]!! + 1
        //select related window
        selectRelatedWindow(currentState,0,0)
        val relatedWindowBudget = if (relatedWindow == null)
            5
        else
            AbstractStateManager.instance.ABSTRACT_STATES.filter { it !is VirtualAbstractState
                    && it.window == relatedWindow}.map {
                it.attributeValuationSets.filter {
                    !it.isUserLikeInput()
                }.size
            }.max()?:1
        budgetLeft = (relatedWindowBudget*scaleFactor).toInt()
        windowBudgetLeft--
        autautMF.updateMethodCovFromLastChangeCount = 0
    }

    private fun getWindowAvailableEvents() : Map<Input,Int> {
        val  events = targetEventScores.filter { eventWindowCorrelation.containsKey(it.key) }
        if (events.isNotEmpty())
            return allTargetInputs.filter { events.containsKey(it.key) }
        return  allTargetInputs.filter { targetEventScores.containsKey(it.key) }
    }

    private fun selectRelatedWindow(currentState: State<*>, numTried: Int, maxTry: Int ) {
        var max = if (maxTry == 0) {
            if (eventWindowCorrelation.containsKey(targetEvent)) {
                eventWindowCorrelation[targetEvent!!]!!.size / 2 + 1
            } else {
                1
            }
        } else {
            maxTry
        }
        if (eventWindowCorrelation.containsKey(targetEvent!!) && eventWindowCorrelation[targetEvent!!]!!.isNotEmpty()) {
            val currentEventWindowCorrelation = eventWindowCorrelation[targetEvent!!]!!.filter {  it.key != targetWindow!! }
            if (currentEventWindowCorrelation.isNotEmpty()) {
                val pdForRelatedWindows = ProbabilityDistribution<Window>(currentEventWindowCorrelation)
                relatedWindow = pdForRelatedWindows.getRandomVariable()
            } else {
                val pdForRelatedWindows = ProbabilityDistribution<Window>(windowScores)
                relatedWindow = pdForRelatedWindows.getRandomVariable()
            }
        } else {
            val candidates = WindowManager.instance.allMeaningWindows.filter { it != targetWindow }
            if (candidates.isNotEmpty()) {
                relatedWindow = candidates.random()
            } else {
                relatedWindow = targetWindow
            }
        }
        if (numTried< max && getPathsToWindowToExplore(currentState, relatedWindow!!,PathFindingHelper.PathType.ANY,false).isEmpty()) {
            selectRelatedWindow(currentState, numTried+1,max)
        }

    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
        }
        log.info("Cannot find path the target node.")
        log.info("Random exploration")
        phaseState = PhaseState.P3_RANDOM_EXPLORATION
    }
    val eventWindowCorrelation = HashMap<Input, HashMap<Window,Double>>()

    fun computeEventWindowCorrelation() {
        eventWindowCorrelation.clear()
        val eventsTerms = autautMF!!.accumulateEventsDependency()
        val ir = InformationRetrieval<Window,String>(autautMF!!.windowTermsHashMap)
        eventsTerms.forEach {
            val result = ir.searchSimilarDocuments(it.value,10)
            val correlation = HashMap<Window, Double>()
            result.forEach {
                correlation.put(it.first,it.second)
            }
            eventWindowCorrelation.put(it.key,correlation)
        }
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
        appStateList.forEach { appState ->
            edges.addAll(autautMF.abstractTransitionGraph.edges(appState).filter { it.label.isExplicit() || it.label.fromWTG })
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
            abstractStateProbability.put(window, appStatesProbab )
            abstractStateList.forEach {
                val pb = abstractStatesScores[it]!!/totalScore
                appStatesProbab.add(Pair(it,pb))
            }
        }

        //calculate staticNode score
        var staticNodeTotalScore = 0.0
        windowScores.clear()
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
            windowModifiedMethodMap.put(n,modifiedMethods)
            modifiedMethods.filter { modifiedMethodWeights.containsKey(it) }. forEach {
                val methodWeight = modifiedMethodWeights[it]!!
                val missingStatementsNumber = modifiedMethodMissingStatements[it]?.size?:0
                weight += (methodWeight*missingStatementsNumber)
            }
            if (weight>0.0)
            {
                windowScores.put(n, weight)
                staticNodeTotalScore+=weight
            }

        }
        allTargetInputs.forEach {
            if (it.eventType != EventType.resetApp
                    && it.eventType != EventType.implicit_launch_event
                    && it.eventType != EventType.implicit_back_event) {
                this.allTargetInputs.put(it,0)
            }
        }
        windowsProbability.clear()
        //calculate staticNode probability
        windowScores.forEach { n, s ->
            val pb = s/staticNodeTotalScore
            windowsProbability.put(n,pb)
        }

    }

    companion object {
        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(PhaseThreeStrategy::class.java) }

        val TEST_BUDGET: Int = 25
    }
}