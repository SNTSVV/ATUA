package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PhaseTwoStrategy (
        autAutTestingStrategy: AutAutTestingStrategy,
        budgetScale: Double,
        delay: Long,
        useCoordinateClicks: Boolean,
        val unreachableWindow: Set<WTGNode>
):AbstractPhaseStrategy (
    autAutTestingStrategy = autAutTestingStrategy,
        budgetScale = budgetScale,
    delay = delay,
    useCoordinateClicks = useCoordinateClicks,
        useVirtualAbstractState = false
) {


    private var numOfContinousTry: Int =0
    val statementMF: StatementCoverageMF

    var remainPhaseStateCount: Int = 0

    var targetWindow: WTGNode? = null
    var phase2TargetEvents: HashMap<StaticEvent, Int> = HashMap()

    var targetWindowsCount: HashMap<WTGNode, Int> = HashMap()

    val abstractStatesScores = HashMap<AbstractState, Double>()
    val abstractStateProbabilityByWindow = HashMap<WTGNode, ArrayList<Pair<AbstractState, Double>>>()

    val modifiedMethodWeights = HashMap<String, Double>()
    val modifiedMethodMissingStatements = HashMap<String, List<String>>()
    val appStateModifiedMethodMap = HashMap<AbstractState, ArrayList<String>>()
    val modifiedMethodTriggerCount = HashMap<String, Int>()
    val staticNodeScores = HashMap<WTGNode, Double> ()
    val staticNodesProbability = HashMap<WTGNode, Double> ()
    var attempt: Int = 0
    var budgetLeft: Int = 0
    var needResetApp = false

    init {
        phaseState = PhaseState.P2_INITIAL
        autautMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        statementMF = autAutTestingStrategy.eContext.getOrCreateWatcher()
        autautMF.updateMethodCovFromLastChangeCount = 0
        autautMF.allTargetWindows.keys.filterNot {unreachableWindow.contains(it) }. forEach {
            targetWindowsCount.put(it,0)
        }
        attempt = (targetWindowsCount.size*budgetScale).toInt()
    }

    override fun registerTriggeredEvents(abstractAction: AbstractAction, currentState: State<*>) {
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)!!
        //val abstractInteractions = regressionTestingMF.abstractTransitionGraph.edges(abstractState).filter { it.label.abstractAction.equals(abstractAction) }.map { it.label }
        if (!abstractState.staticEventMapping.containsKey(abstractAction)) {
            return
        }
        val staticEvents = abstractState.staticEventMapping[abstractAction]!!
        if (staticEvents!=null) {
            staticEvents.forEach {
                if (phase2TargetEvents.containsKey(it)) {
                    phase2TargetEvents[it] = phase2TargetEvents[it]!! + 1
                }
            }
        }
    }

    override fun isEnd(): Boolean {
        if (attempt < 0)
            return true
        if (autautMF.lastUpdatedStatementCoverage == 1.0)
            return true
        return false
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
            selectTargetStaticNode(currentState,0)
        }

        log.info("Target window: $targetWindow")
        chooseTask(eContext, currentState)
        if (needResetApp) {
            needResetApp = false
            return eContext.resetApp()
        }
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = eContext.resetApp()
        }
        budgetLeft--
        return chosenAction
    }

    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        //computeAppStatesScore()
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(autautMF.appPrevState!!)
        if (currentAbState==null)
            return emptyList()
        if (!abstractStateProbabilityByWindow.containsKey(targetWindow)) {
            val targetAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }.random()
            val transitionPaths = ArrayList<TransitionPath>()
            val childParentMap = HashMap<AbstractState, Triple<AbstractState, AbstractInteraction,HashMap<WidgetGroup,String>>?>()
            childParentMap.put(currentAbState,null)
            findPathToTargetComponentByBFS(currentState = currentState
                    , root = currentAbState
                    ,traversingNodes = listOf(Pair(autautMF.windowStack.clone() as Stack<WTGNode>, currentAbState))
                    ,finalTarget = targetAbstractState
                    ,allPaths = transitionPaths
                    ,includeBackEvent = true
                    ,childParentMap = HashMap()
                    ,level = 0,
                    useVirtualAbstractState = useVirtualAbstractState)
            return transitionPaths
        }
        val targetAppStatesDistribution = HashMap<AbstractState,Pair<Double, Double>>()
        val targetAbstractStatesProbability = abstractStateProbabilityByWindow[targetWindow]!!
        targetAbstractStatesProbability.removeIf { it.first == currentAbState }
        val targetAbstractStatesPbMap = HashMap<AbstractState,Double>()
        targetAbstractStatesProbability.forEach {
            targetAbstractStatesPbMap.put(it.first,it.second)
        }

        val transitionPaths = ArrayList<TransitionPath>()
        getPathToStates(transitionPaths,targetAbstractStatesPbMap,currentAbState,currentState,false,useVirtualAbstractState)
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
                        || it.window is WTGLauncherNode
                        || it.window is WTGOutScopeNode }
        /*val candiateWindows = WTGNode.allMeaningNodes.filter { window -> runtimeAbstractStates.find { it.window == window } != null}
        val candidateVisitedFoundNodes = regressionTestingMF.windowVisitCount
                .filter { candiateWindows.contains(it.key) } as HashMap*/
        val abstratStateCandidates = runtimeAbstractStates

        val stateByActionCount = HashMap<AbstractState,Double>()
        abstratStateCandidates.forEach {
            val unExercisedActionsSize = it.getUnExercisedActions(null).filter { it.widgetGroup!=null }.size
            if (unExercisedActionsSize > 0 )
                stateByActionCount.put(it,it.getUnExercisedActions(null).size.toDouble())
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
        /*if (transitionPaths.isEmpty()) {
            val virtualAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
                    .filter { it is VirtualAbstractState && !it.isHomeScreen }
            val stateByActionCount = HashMap<AbstractState,Double>()
            virtualAbstractStates.forEach {
                val unExercisedActionsSize = it.getUnExercisedActions(null).size
                if (unExercisedActionsSize > 0 )
                    stateByActionCount.put(it,it.getUnExercisedActions(null).size.toDouble())
            }
            getPathToStates(transitionPaths, stateByActionCount, currentAbstractState, currentState,true,true)
        }*/
        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>):  List<AbstractAction> {
        val targetEvents = HashMap<StaticEvent,List<AbstractAction>>()
        targetEvents.clear()

        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState!!.window == targetWindow)
        {
            val availableEvents = abstractState.staticEventMapping.map { it.value }.flatten()
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
        return targetEvents.map { it.value }.flatMap { it }
    }

    private fun chooseTask(eContext: ExplorationContext<*, *, *>, currentState: State<*>) {
        log.debug("Choosing Task")
        //       val fillDataTask = FillTextInputTask.getInstance(regressionTestingMF,this,delay, useCoordinateClicks)
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
            if (phaseState == PhaseState.P2_INITIAL) {
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }
                    /*setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return*/
                }
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    log.info("Task chosen: Go to target window: ${targetWindow.toString()}")
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    remainPhaseStateCount = 0
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                    setRandomExploration(randomExplorationTask, currentState)
                    return
                }
                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                    numOfContinousTry = 0
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    return
                }
                setFullyRandomExploration(randomExplorationTask,currentState)
                return
            }
            if (phaseState == PhaseState.P2_EXERCISE_TARGET_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue exercise target window")
                    return
                }
                if (currentAppState.window == targetWindow) {
                /*    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }*/
                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    log.info("Task chosen: Go to target node again.")
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                    setRandomExploration(randomExplorationTask, currentState)
                    return
                }
                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                    numOfContinousTry = 0
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    return
                }
                setFullyRandomExploration(randomExplorationTask,currentState)
                return
            }
            if (phaseState == PhaseState.P2_RANDOM_IN_EXERCISE_TARGET_NODE) {
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
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        //Target events found
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }
                }
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    strategyTask = goToTargetNodeTask.also {
                        it.initialize(currentState)
                    }
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    log.info("Go to target window: ${targetWindow.toString()}")
                    return
                }
                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to explore window: ${goToAnotherNode.destWindow.toString()}")
                    numOfContinousTry = 0
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    return
                }
                setFullyRandomExploration(randomExplorationTask, currentState)
                return
            }
            if (phaseState == PhaseState.P2_GO_TO_TARGET_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue go to target window")
                    return
                }
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                    setRandomExploration(randomExplorationTask, currentState)
                    return
                }
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!))
                {
                    log.info("Task chosen: Go to target node .")
                    remainPhaseStateCount += 1
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    return
                }

                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                    numOfContinousTry = 0
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    return
                }
                setFullyRandomExploration(randomExplorationTask, currentState)
                return
            }
            if (phaseState == PhaseState.P2_GO_TO_ANOTHER_NODE) {

                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }
                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }
                if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!))
                {
                    log.info("Task chosen: Go to target node .")
                    remainPhaseStateCount += 1
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue go to the window")
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                    setRandomExploration(randomExplorationTask, currentState)
                    return
                }
                if (!goToAnotherNode.isReachExpectedNode(currentState) && numOfContinousTry < 5) {
                     if (goToAnotherNode.isAvailable(currentState)) {
                         strategyTask = goToAnotherNode.also {
                             it.initialize(currentState)
                         }
                         log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                         numOfContinousTry +=1
                         phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                         return
                     }
                }
                setFullyRandomExploration(randomExplorationTask, currentState)
                return
            }
            if (phaseState == PhaseState.P2_RANDOM_EXPLORATION)
            {
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState))
                    {
                        setExerciseTarget(exerciseTargetComponentTask, currentState)
                        return
                    }

                    setRandomExplorationInTargetWindow(randomExplorationTask, currentState)
                    return
                }

                if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!))
                {
                    log.info("Task chosen: Go to target node .")
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    return
                }
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue doing random exploration")
                    return
                }
                if (currentAppState.getUnExercisedActions(currentState).isNotEmpty() ) {
                    setRandomExploration(randomExplorationTask, currentState)
                    return
                }
                if (goToAnotherNode.isAvailable(currentState)) {
                    strategyTask = goToAnotherNode.also {
                        it.initialize(currentState)
                    }
                    log.info("Go to target window by visiting another window: ${targetWindow.toString()}")
                    numOfContinousTry = 0
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    return
                }
                if (!randomExplorationTask.isFullyExploration) {
                    setFullyRandomExploration(randomExplorationTask, currentState)
                    return
                }

            }
            //log.info("PhaseState undefined.")
            //setRandomExploration(randomExplorationTask, currentState)
        }
        attempt--
        selectTargetStaticNode(currentState,0)
        //setTestBudget = false
        needResetApp = true
        phaseState = PhaseState.P2_INITIAL
        return
    }

    private fun setExerciseTarget(exerciseTargetComponentTask: ExerciseTargetComponentTask, currentState: State<*>) {
        log.info("Task chosen: Exercise Target Node .")
        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
        remainPhaseStateCount = 0
        strategyTask = exerciseTargetComponentTask.also {
            it.initialize(currentState)
            it.randomRefillingData = true
            it.environmentChange = true
        }
    }

    private fun setFullyRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        log.info("Task chosen: Fully Random Exploration")
        phaseState = PhaseState.P2_RANDOM_EXPLORATION
        remainPhaseStateCount = 0
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.setMaximumAttempt((10*budgetScale).toInt())
            it.isFullyExploration = true
            it.environmentChange = true
        }
    }

    private fun setRandomExploration(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.environmentChange = true
        }
        log.info("Cannot find path the target node.")
        log.info("Random exploration")
        phaseState = PhaseState.P2_RANDOM_EXPLORATION
    }

    private fun setRandomExplorationInTargetWindow(randomExplorationTask: RandomExplorationTask, currentState: State<*>) {
        strategyTask = randomExplorationTask.also {
            it.initialize(currentState)
            it.isFullyExploration = true
            it.environmentChange = true
            it.lockTargetWindow(targetWindow!!)
        }
        log.info("This window is a target window but cannot find any target event")
        log.info("Random exploration in current window")
        phaseState = PhaseState.P2_RANDOM_IN_EXERCISE_TARGET_NODE
    }

    var setTestBudget = false

    fun selectTargetStaticNode(currentState: State<*>, numberOfTried: Int){
        val leastTriedWindows = targetWindowsCount.map { Pair<WTGNode, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
        val leastTriedWindowScore = staticNodeScores.filter {windowScore -> leastTriedWindows.value.any { it.first == windowScore.key } }
        if (leastTriedWindowScore.isNotEmpty()) {
            val pb = ProbabilityDistribution<WTGNode>(leastTriedWindowScore)
            val targetNode = pb.getRandomVariable()
            targetWindow = targetNode
        } else {
            targetWindow = targetWindowsCount.map { it.key }.random()
        }
        targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!!+1
        if (numberOfTried< targetWindowsCount.size/2+1 && getPathsToWindow(currentState, targetWindow!!).isEmpty()) {
            selectTargetStaticNode(currentState, numberOfTried+1)
            return
        }
        val widgetSize: Int  = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow}.maxBy { it.widgets.size }?.widgets?.size?:0
        budgetLeft = (TEST_BUDGET * budgetScale).toInt()
        //setTestBudget = false

        autautMF.updateMethodCovFromLastChangeCount = 0
    }



     fun computeAppStatesScore(){
        //Initiate reachable modified methods list
        val triggeredStatements = statementMF.getAllExecutedStatements()
        statementMF.getAllModifiedMethodsId().forEach {
            val methodName = statementMF!!.getMethodName(it)
            if (!autautMF.unreachableModifiedMethods.contains(methodName))
            {
                modifiedMethodTriggerCount.put(it,0)
                val statements = statementMF!!.getMethodStatements(it)
                val missingStatements = statements.filter { !triggeredStatements.contains(it) }
                modifiedMethodMissingStatements.put(it,missingStatements)
            }
        }

        //get all AppState
        val appStateList = ArrayList<AbstractState>()
        autautMF.appStatesMap.map { it.value }.forEach { appStateList.addAll(it) }

        //get all AppState's edges and appState's modified method
        val edges = ArrayList<Edge<*, *>>()
        appStateList.forEach {appState ->
            edges.addAll(autautMF.abstractTransitionGraph.edges(appState))
            appStateModifiedMethodMap.put(appState, ArrayList())
            appState.abstractInteractions.map { it.modifiedMethods}.forEach { hmap ->
                hmap.forEach { m, v ->
                    if (!appStateModifiedMethodMap[appState]!!.contains(m)) {
                        appStateModifiedMethodMap[appState]!!.add(m)
                    }
                }
            }
        }

        //for each edge, count modified method appearing
        edges.forEach {
            val coveredMethods = autautMF.abstractTransitionGraph.methodCoverageInfo[it]
            if (coveredMethods!=null)
                coveredMethods.forEach {
                    if (modifiedMethodTriggerCount.containsKey(it))
                    {
                        modifiedMethodTriggerCount[it] = modifiedMethodTriggerCount[it]!! + 1
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
        appStateList.forEach {
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
                appStateScore += 1
                abstractStatesScores.put(it,appStateScore)
            }
        }

        //calculate appState probability
        autautMF.appStatesMap.forEach { window, abstractStateList ->
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
        targetWindowsCount.forEach { n, _ ->
            var weight: Double = 0.0
            val targetEvents = ArrayList<StaticEvent>()
            val modifiedMethods = ArrayList<String>()
/*            appStateModifiedMethodMap.filter { it.key.staticNode == n}.map { it.value }.forEach {
                it.forEach {
                    if (!modifiedMethods.contains(it))
                    {
                        modifiedMethods.add(it)
                    }
                }
            }*/
            autautMF.allTargetStaticEvents.filter { it.sourceWindow == n }. forEach {
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

                autautMF.wtg.edges(n).map { it.label }.distinct().filter{ autautMF.allTargetStaticEvents.contains(it) }.forEach {
                    if (it.eventType != EventType.resetApp && it.eventType != EventType.implicit_launch_event) {
                        phase2TargetEvents.put(it,0)
                    }

                }
            }

        }

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