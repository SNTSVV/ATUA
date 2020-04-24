package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticEvent
import org.droidmate.exploration.modelFeatures.autaut.staticModel.TransitionPath
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random

class PhaseThreeStrategy(
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long,
        useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
        regressionTestingStrategy = regressionTestingStrategy,
        delay = delay,
        useCoordinateClicks = useCoordinateClicks
) {
    override fun isEnd(): Boolean {
        return false
    }

    val statementMF: StatementCoverageMF
    var remainPhaseStateCount: Int = 0

    var targetWindow: WTGNode? = null
    var allTargetEvents: HashMap<StaticEvent, Int> = HashMap()
    var targetEvent: StaticEvent? = null
    var targetWindowsCount: HashMap<WTGNode, Int> = HashMap()

    val appStatesScores = HashMap<AbstractState, Double>()
    val appStateProbability = HashMap<WTGNode, ArrayList<Pair<AbstractState, Double>>>()

    val modifiedMethodWeights = HashMap<String, Double>()
    val modifiedMethodMissingStatements = HashMap<String, List<String>>()
    val appStateModifiedMethodMap = HashMap<AbstractState, ArrayList<String>>()
    val windowModifiedMethodMap = HashMap<WTGNode, ArrayList<String>>()
    val modifiedMethodTriggerCount = HashMap<String, Int>()
    val windowScores = HashMap<WTGNode, Double> ()
    val windowsProbability = HashMap<WTGNode, Double> ()

    var budgetLeft: Int = 0

    init {
        phaseState = PhaseState.P3_INITIAL
        regressionTestingMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        statementMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        regressionTestingMF.modifiedMethodCoverageFromLastChangeCount = 0
        WTGNode.allMeaningNodes.filter { it.isStatic() }.forEach {
            if (!targetWindowsCount.contains(it)) {
                targetWindowsCount.put(it,0)
            }
        }

    }

    override fun nextAction(eContext: ExplorationContext<*, *, *>): ExplorationAction {
        if (regressionTestingMF == null)
        {
            regressionTestingMF = eContext.findWatcher { it is RegressionTestingMF } as RegressionTestingMF
        }
        var chosenAction:ExplorationAction

        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)
        chooseTask(eContext, currentState)
        if (strategyTask != null) {
            chosenAction = strategyTask!!.chooseAction(currentState)
        } else {
            log.debug("No task seleted. It might be a bug.")
            chosenAction = ExplorationAction.closeAndReturn()
        }
        budgetLeft--
        return chosenAction
    }

    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath> {
        if (targetWindow==null)
            return emptyList()
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbState = regressionTestingMF.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(regressionTestingMF.appPrevState!!)
        if (currentAbState==null)
            return transitionPaths
        val candiateNodes = ArrayList(WTGNode.allMeaningNodes.filterNot { it != currentAbState.window || it.classType == targetWindow!!.classType})
        //choose random activity
        if (candiateNodes.isEmpty())
            return transitionPaths

        var destinationAbStates: List<AbstractState> = emptyList()
        do {
            val randomOrNot = Random.nextBoolean()
            if (randomOrNot) {
                val randomNode = candiateNodes.random()
                destinationAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == randomNode }
            } else {
                targetEvent = allTargetEvents.filter { regressionTestingMF.eventWindowCorrelation.containsKey(it.key) }.map { it.key }.random()
                val availableNodes = regressionTestingMF.eventWindowCorrelation[targetEvent!!]!!.filter { node -> AbstractStateManager.instance.ABSTRACT_STATES.any { it.window == node.key } }
                if (availableNodes.isNotEmpty()) {
                    val probabilityDistribution = ProbabilityDistribution<WTGNode>(availableNodes)
                    val targetRandomNode = probabilityDistribution.getRandomVariable()
                    destinationAbStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetRandomNode }
                }
            }
        }while (destinationAbStates.isEmpty())

        destinationAbStates.forEach {
            val existingPaths: List<TransitionPath>?
            existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbState,it)]
            if (existingPaths != null && existingPaths.isNotEmpty())
            {
                transitionPaths.addAll(existingPaths)
            }
            else
            {
                val destination = it
                val childParentMap = HashMap<AbstractState,Pair<AbstractState, StaticEvent>?>()
                childParentMap.put(currentAbState,null)
                findPathToTargetComponentByBFS(currentState = currentState
                        , root = currentAbState
                        ,traversingNodes = listOf(Pair(regressionTestingMF.windowStack.peek(), currentAbState))
                        ,finalTarget = it
                        ,allPaths = transitionPaths
                        ,includeBackEvent = true
                        ,childParentMap = HashMap()
                        ,level = 0)
            }
        }
        return transitionPaths
    }

    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(regressionTestingMF.appPrevState!!)
        if (currentAbState==null)
            return emptyList()
        val targetAppStatesDistribution = HashMap<AbstractState,Pair<Double, Double>>()
        if (!appStateProbability.containsKey(targetWindow))
            return emptyList()
        val targetNodesProbability = appStateProbability[targetWindow]!!.filter {
            it.first.staticEventMapping.filter { it.value == targetEvent }.isEmpty()
        }
        var distributionPoint: Double = 0.0
        targetNodesProbability.forEach {
            val begin = distributionPoint
            distributionPoint += it.second
            val end = distributionPoint
            targetAppStatesDistribution.put(it.first,Pair(begin,end))
        }
        val random = Random.nextDouble(0.0,1.0)
        var targetAbState: AbstractState? = null

        for (n in targetAppStatesDistribution)
        {
            if (random>= n.value.first && random <= n.value.second)
            {
                targetAbState = n.key
            }
        }
        if (targetAbState == null)
        {
            targetAbState = targetAppStatesDistribution.entries.random().key
        }

        val transitionPaths = ArrayList<TransitionPath>()
        val existingPaths: List<TransitionPath>?
        existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbState,targetAbState)]
        if (existingPaths != null && existingPaths.isNotEmpty())
        {
            transitionPaths.addAll(existingPaths)
        }
        else
        {
            val childParentMap = HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>()
            childParentMap.put(currentAbState,null)
            findPathToTargetComponentByBFS(currentState = currentState
                    , root = currentAbState
                    ,traversingNodes = listOf(Pair(regressionTestingMF.windowStack.peek(), currentAbState))
                    ,finalTarget = targetAbState
                    ,allPaths = transitionPaths
                    ,includeBackEvent = true
                    ,childParentMap = HashMap()
                    ,level = 0)
        }
        return transitionPaths
    }

    override fun getCurrentTargetEvents(currentState: State<*>): List<AbstractAction> {
        val targetEvents = HashMap<StaticEvent,List<AbstractAction>>()
        targetEvents.clear()

        if (targetWindow == null) {
            selectTargetWindow()
        }
        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState!!.window == targetWindow)
        {
            val abstractActions = regressionTestingMF.validateEvent(targetEvent!!,currentState)
            if (abstractActions.isNotEmpty())
            {
                targetEvents.put(targetEvent!!, abstractActions)
            }
        }
        return targetEvents.map { it.value }.flatMap { it }
    }

    val relatedWindow: WTGNode? = null
    fun chooseTask(eContext: ExplorationContext<*, *, *>, currentState: State<*>){
        log.debug("Choosing Task")
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(regressionTestingMF, regressionTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)
        if (budgetLeft > 0)
        {
            if (phaseState == PhaseState.P3_INITIAL)
            {
                regressionTestingMF.modifiedMethodCoverageFromLastChangeCount = 0
                if (currentAppState.window == relatedWindow) {
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                        it.lockTargetWindow(relatedWindow!!)
                    }
                    log.info("In related window $relatedWindow.")
                    log.info("Random exploration in current window")
                    phaseState = PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW
                } else {
                    if (goToTargetNodeTask.isAvailable(currentState, targetWindow = relatedWindow!!)) {
                        log.info("Task chosen: Go to related window: $relatedWindow")
                        phaseState = PhaseState.P3_GO_TO_RELATED_NODE
                        remainPhaseStateCount = 0
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    } else {
                        log.info("Cannot go to related Window. Random Exploration with 10 actions")
                        phaseState = PhaseState.P3_RANDOM_EXPLORATION
                        remainPhaseStateCount = 0
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.setMaximumAttempt(10)
                            it.isFullyExploration = true
                        }
                    }
                }
            }
            else if (phaseState == PhaseState.P3_GO_TO_RELATED_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue ${strategyTask!!.javaClass.name}")
                } else {
                    if (currentAppState.window == relatedWindow) {
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(relatedWindow!!)
                        }
                        log.info("In related window $relatedWindow.")
                        log.info("Random exploration in current window")
                        phaseState = PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW
                    } else {
                        // change target event
                        selectTargetStaticEvent()
                        chooseTask(eContext, currentState)
                    }
                }
            }
            else if (phaseState == PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue ${strategyTask!!.javaClass.name}")
                }
                else if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    } else if (goToTargetNodeTask.isAvailable(currentState)) {
                        log.info("Task chosen: Go to target window: $targetWindow .")
                        phaseState = PhaseState.P3_GO_TO_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    } else {
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                        }
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")
                        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
                    }
                } else {
                    if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!)) {
                        log.info("Task chosen: Go to target window: $targetWindow .")
                        phaseState = PhaseState.P3_GO_TO_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    } else {
                        log.info("Cannot go to target Window. Random Exploration with 10 actions")
                        phaseState = PhaseState.P3_RANDOM_EXPLORATION
                        remainPhaseStateCount = 0
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.setMaximumAttempt(10)
                            it.isFullyExploration = true
                        }
                    }
                }
            } else if (phaseState == PhaseState.P3_GO_TO_TARGET_NODE)
            {
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    } else if (!strategyTask!!.isTaskEnd(currentState)) {
                        log.info("Continue ${strategyTask!!.javaClass.name}")
                    } else {
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                        }
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")
                        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
                    }
                } else {
                    if (!strategyTask!!.isTaskEnd(currentState)) {
                        log.info("Continue ${strategyTask!!.javaClass.name}")
                    } else {
                        log.info("Cannot go to target Window. Random Exploration with 10 actions")
                        phaseState = PhaseState.P3_EXPLORATION_IN_RELATED_WINDOW
                        remainPhaseStateCount = 0
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.isFullyExploration = true
                            it.backAction = true
                            it.setMaximumAttempt(10)
                        }
                    }
                }
            }
            else if (phaseState == PhaseState.P3_EXERCISE_TARGET_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Continue ${strategyTask!!.javaClass.name}")
                } else if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P3_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                    } else {
                        selectTargetStaticEvent()
                        chooseTask(eContext, currentState)
                    }
                } else {
                    selectTargetStaticEvent()
                    chooseTask(eContext,currentState)
                }
            }
        }
        else {
            if (windowBudgetLeft > 0) {
                selectTargetStaticEvent()
            }
            else {
                selectTargetWindow()
                selectTargetStaticEvent()
            }
            phaseState = PhaseState.P3_INITIAL
            chooseTask(eContext, currentState)
        }
    }

    var windowBudgetLeft: Int = 0

    fun selectTargetWindow() {
        computeScore()
        regressionTestingMF.computeEventWindowCorrelation()
        if (Random.nextBoolean()){
            targetWindow = targetWindowsCount.keys.random()
        }
        else {
            val pb = ProbabilityDistribution<WTGNode>(windowsProbability)
            val targetNode = pb.getRandomVariable()
            targetWindow = targetNode
        }
        targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!!+1
        targetModifiedMethods.clear()
        targetModifiedMethods.addAll(windowModifiedMethodMap[targetWindow!!]!!)
        allTargetEvents.clear()
        allTargetEvents.filter { it.key.sourceWindow == targetWindow }.forEach {
            var score: Double = 0.0
            it.key.modifiedMethods.filter { it.value }. forEach {
                val method = it.key
                val totalStmt = statementMF.getMethodStatements(method).size
                val missingStmt = modifiedMethodMissingStatements[method]!!.size
                val coveredStmt = totalStmt - missingStmt
                score += (modifiedMethodWeights[method]!! * coveredStmt)
            }
            targetEventScores.put(it.key,score)
        }
        windowBudgetLeft = targetEventScores.size
    }

    val targetModifiedMethods = ArrayList<String>()
    val targetEventScores = HashMap<StaticEvent, Double>()

    fun selectTargetStaticEvent(){
        //pick up randomly a target event
        val pdForTargetEvents = ProbabilityDistribution<StaticEvent>(targetEventScores)
        targetEvent = pdForTargetEvents.getRandomVariable()
        windowBudgetLeft--
        budgetLeft = TEST_BUDGET
    }

    fun computeScore(){
        modifiedMethodMissingStatements.clear()
        modifiedMethodTriggerCount.clear()
        modifiedMethodWeights.clear()
        appStateModifiedMethodMap.clear()
        windowModifiedMethodMap.clear()
        appStatesScores.clear()
        appStateProbability.clear()
        windowScores.clear()
        windowsProbability.clear()
        //Initiate reachable modified methods list
        val triggeredStatements = statementMF.getAllExecutedStatements()
        statementMF.getAllModifiedMethodsId().forEach {
            val methodName = statementMF!!.getMethodName(it)
            if (!regressionTestingMF.unreachableModifiedMethods.contains(methodName))
            {
                modifiedMethodTriggerCount.put(it,0)
                val statements = statementMF!!.getMethodStatements(it)
                val missingStatements = statements.filter { !triggeredStatements.contains(it) }
                modifiedMethodMissingStatements.put(it,missingStatements)
            }
        }

        //get all AppState
        val appStateList = ArrayList<AbstractState>()
        regressionTestingMF.appStatesMap.map { it.value }.forEach { appStateList.addAll(it) }

        //get all AppState's edges and appState's modified method
        val edges = ArrayList<Edge<*, *>>()
        appStateList.forEach {appState ->
            edges.addAll(regressionTestingMF.abstractTransitionGraph.edges(appState))
            appStateModifiedMethodMap.put(appState, ArrayList())
            appState.abstractInteractions.map { it.modifiedMethods }.forEach { hmap ->
                hmap.forEach { m, _ ->
                    if (!appStateModifiedMethodMap[appState]!!.contains(m)) {
                        appStateModifiedMethodMap[appState]!!.add(m)
                    }
                }
            }

        }

        //for each edge, count modified method appearing
        edges.forEach {
            val coveredMethods = regressionTestingMF.abstractTransitionGraph.methodCoverageInfo[it]
            if (coveredMethods!=null)
                coveredMethods.forEach {
                    if (modifiedMethodTriggerCount.containsKey(it))
                    {
                        modifiedMethodTriggerCount[it] = modifiedMethodTriggerCount[it]!! + 1
                    }
                }

        }

        //calculate modified method score
        val totalAppStateCount = edges.size
        modifiedMethodTriggerCount.forEach { m, c ->
            val score = 1-c/totalAppStateCount.toDouble()
            modifiedMethodWeights.put(m,score)
        }

        //



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
                appStatesScores.put(it,appStateScore)
            }
        }

        //calculate appState probability
        regressionTestingMF.appStatesMap.forEach { static, appStateList ->
            var totalScore = 0.0
            appStateList.forEach {
                totalScore += appStatesScores[it]!!
            }

            val appStatesProbab = ArrayList<Pair<AbstractState,Double>>()
            appStateProbability.put(static, appStatesProbab )
            appStateList.forEach {
                val pb = appStatesScores[it]!!/totalScore
                appStatesProbab.add(Pair(it,pb))
            }
        }

        //calculate staticNode score
        var staticNodeTotalScore = 0.0
        targetWindowsCount.forEach { n, _ ->
            var weight: Double = 0.0
            val targetEvents = ArrayList<StaticEvent>()
            val modifiedMethods = ArrayList<String>()
            regressionTestingMF.allTargetStaticEvents.filter { it.sourceWindow == n }. forEach {
                modifiedMethods.addAll(it.modifiedMethods.map { it.key })
            }
            if (regressionTestingMF.windowHandlersHashMap.containsKey(n)) {
                regressionTestingMF.windowHandlersHashMap[n]!!.forEach {handler ->
                    val methods = regressionTestingMF.modifiedMethodTopCallersMap.filter { it.value.contains(handler) }.map { it.key }
                    modifiedMethods.addAll(methods)
                }
            }
            windowModifiedMethodMap.put(n,modifiedMethods)
            modifiedMethods.forEach {
                val methodWeight = modifiedMethodWeights[it]!!
                val missingStatementsNumber = modifiedMethodMissingStatements[it]?.size?:0
                weight += (methodWeight*missingStatementsNumber)
            }
            weight+=1
            windowScores.put(n, weight)
            staticNodeTotalScore+=weight
            regressionTestingMF.transitionGraph.edges(n).map { it.label }.distinct().filter{ regressionTestingMF.allTargetStaticEvents.contains(it) }.forEach {
                allTargetEvents.put(it,0)
            }
        }

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