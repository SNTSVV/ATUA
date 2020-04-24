package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.interaction.State
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random

class PhaseTwoStrategy (
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long,
        useCoordinateClicks: Boolean,
        val unreachableWindow: List<WTGNode>
):AbstractPhaseStrategy (
    regressionTestingStrategy = regressionTestingStrategy,
    delay = delay,
    useCoordinateClicks = useCoordinateClicks
) {
    override fun isEnd(): Boolean {
        if (attempt < 0)
            return true
        return false
    }

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

    init {
        phaseState = PhaseState.P2_INITIAL
        regressionTestingMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        statementMF = regressionTestingStrategy.eContext.getOrCreateWatcher()
        regressionTestingMF.modifiedMethodCoverageFromLastChangeCount = 0
        regressionTestingMF.allTargetWindows.filterNot {unreachableWindow.contains(it) }. forEach {
            targetWindowsCount.put(it,0)
        }
        attempt = targetWindowsCount.size
    }

    override fun nextAction(eContext: ExplorationContext<*, *, *>): ExplorationAction {
        if (regressionTestingMF == null)
        {
            regressionTestingMF = eContext.findWatcher { it is RegressionTestingMF } as RegressionTestingMF
        }
        var chosenAction:ExplorationAction

        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)

        if (currentAppState == null)
        {
            return eContext.resetApp()
        }

        if (targetWindow == null) {
            selectTargetStaticNode()
        }

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

    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        //computeAppStatesScore()
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        val prevAbstractState = AbstractStateManager.instance.getAbstractState(regressionTestingMF.appPrevState!!)
        if (currentAbState==null)
            return emptyList()
        if (!abstractStateProbabilityByWindow.containsKey(targetWindow)) {
            val targetAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow }.random()
            val transitionPaths = ArrayList<TransitionPath>()
            val childParentMap = HashMap<AbstractState,Pair<AbstractState, AbstractInteraction>?>()
            childParentMap.put(currentAbState,null)
            findPathToTargetComponentByBFS(currentState = currentState
                    , root = currentAbState
                    ,traversingNodes = listOf(Pair(regressionTestingMF.windowStack.peek(), currentAbState))
                    ,finalTarget = targetAbstractState
                    ,allPaths = transitionPaths
                    ,includeBackEvent = true
                    ,childParentMap = HashMap()
                    ,level = 0)
            return transitionPaths
        }
        val targetAppStatesDistribution = HashMap<AbstractState,Pair<Double, Double>>()
        val targetAbstractStatesProbability = abstractStateProbabilityByWindow[targetWindow]!!
        val targetAbstractStatesPbMap = HashMap<AbstractState,Double>()
        targetAbstractStatesProbability.forEach {
            targetAbstractStatesPbMap.put(it.first,it.second)
        }

        val transitionPaths = ArrayList<TransitionPath>()
        while (transitionPaths.isEmpty() && targetAbstractStatesPbMap.isNotEmpty())
        {
            val pb = ProbabilityDistribution<AbstractState>(targetAbstractStatesPbMap)
            val targetAbstractState = pb.getRandomVariable()
            targetAbstractStatesPbMap.remove(targetAbstractState)
            val existingPaths: List<TransitionPath>?
            existingPaths = regressionTestingMF.allAvailableTransitionPaths[Pair(currentAbState,targetAbstractState)]
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
                        ,traversingNodes = listOf(Pair(prevAbstractState!!.window, currentAbState))
                        ,finalTarget = targetAbstractState
                        ,allPaths = transitionPaths
                        ,includeBackEvent = true
                        ,childParentMap = HashMap()
                        ,level = 0)
            }
        }
        return transitionPaths
    }
    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>{
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
        val randomNode = candiateNodes.random()
        val destinationNodes = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == randomNode }
        destinationNodes.forEach {
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

    override fun getCurrentTargetEvents(currentState: State<*>):  List<AbstractAction> {
        val targetEvents = HashMap<StaticEvent,List<AbstractAction>>()
        targetEvents.clear()

        val abstractState = AbstractStateManager.instance.getAbstractState(currentState)
        if (abstractState!!.window == targetWindow)
        {
            val availableEvent = abstractState.staticEventMapping.map { it.value }
            val events = phase2TargetEvents.filter {
                availableEvent.contains(it.key)
            }
            if (events.isNotEmpty())
            {
                while (targetEvents.isEmpty()) {
                    val leastTriggerCount = events.minBy { it.value }!!.value
                    val leastTriggerEvents = events.filter { it.value == leastTriggerCount }
                    leastTriggerEvents.forEach { t, u ->
                        val abstractActions = regressionTestingMF.validateEvent(t,currentState)
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
        val exerciseTargetComponentTask = ExerciseTargetComponentTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToTargetNodeTask = GoToTargetWindowTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherWindow.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val randomExplorationTask = RandomExplorationTask.getInstance(regressionTestingMF, regressionTestingStrategy,delay, useCoordinateClicks)
        val openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF,regressionTestingStrategy,delay, useCoordinateClicks)
        val currentState = eContext.getCurrentState()
        val currentAppState = regressionTestingMF.getAbstractState(currentState)
        /* if(regressionTestingMF.abstractStateVisitCount[currentAppState] == 1)
         {
             strategyTask = randomExplorationTask.also {
                 it.initialize(currentState)
                 it.backAction = false
                 it.setAttempOnUnexercised(currentState)
             }
             regressionTestingMF.modifiedMethodCoverageFromLastChangeCount=0
             log.info("This node has no target events and is first visited.")
             log.info("Random exploration Task chosen ")
         }*/
        if (!setTestBudget && currentAppState.window == targetWindow)
        {
            budgetLeft = currentAppState.widgets.size
            setTestBudget = true
        }
        if (budgetLeft > 0)
        {
            if (phaseState == PhaseState.P2_INITIAL) {
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also {
                            it.initialize(currentState)
                        }
                    } else {
                        // In case target events not found
                        // Try random exploration
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                        }
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                    }
                } else if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    log.info("Task chosen: Go to target window: ${targetWindow.toString()}")
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    remainPhaseStateCount = 0
                } else {
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                    }
                    log.info("Cannot find path the target window: $targetWindow")
                    log.info("Random exploration")
                    phaseState = PhaseState.P2_RANDOM_EXPLORATION
                }
            }
            else if (phaseState == PhaseState.P2_EXERCISE_TARGET_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue exercise target window")
                }
                else if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState) && strategyTask !is ExerciseTargetComponentTask) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also {
                            it.initialize(currentState)
                        }
                    } else {
                        // In case target events not found
                        // Try random exploration
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                        }
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")
                    }
                }
                else if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!)) {
                    log.info("Task chosen: Go to target node again.")
                    phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                } else {
                    log.info("Task chosen: Random Exploration")
                    phaseState = PhaseState.P2_RANDOM_EXPLORATION
                    remainPhaseStateCount = 0
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                    }
                }
            }
            else if (phaseState == PhaseState.P2_GO_TO_TARGET_NODE)
            {
                if (!strategyTask!!.isTaskEnd(currentState)) {
                    //Keep current task
                    log.info("Continue go to target window")
                } else if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState)) {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also {
                            it.initialize(currentState)
                        }
                    } else {
                        // In case target events not found
                        // Try random exploration
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                        }
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")
                    }
                } else {
                    if (goToTargetNodeTask.isAvailable(currentState,targetWindow!!))
                    {
                        log.info("Task chosen: Go to target node .")
                        remainPhaseStateCount += 1
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    }
                    else
                    {
                        log.info("Task chosen: Random Exploration")
                        phaseState = PhaseState.P2_RANDOM_EXPLORATION
                        remainPhaseStateCount = 0
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.isFullyExploration = true
                        }
                    }
                }
            }
            else if (phaseState == PhaseState.P2_RANDOM_EXPLORATION)
            {
                if (currentAppState.window == targetWindow) {
                    if (exerciseTargetComponentTask.isAvailable(currentState))
                    {
                        log.info("Task chosen: Exercise Target Node .")
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = exerciseTargetComponentTask.also {
                            it.initialize(currentState)
                        }
                    }
                    else {
                        // In case target events not found
                        // Try random exploration
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.lockTargetWindow(targetWindow!!)
                            it.backAction = false
                        }
                        phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                        log.info("This window is a target window but cannot find any target event")
                        log.info("Random exploration in current window")

                    }
                }
                else {
                    if (goToTargetNodeTask.isAvailable(currentState, targetWindow!!))
                    {
                        log.info("Task chosen: Go to target node .")
                        phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    }
                    else
                    {
                        log.info("Task chosen: Full Random Exploration")
                        phaseState = PhaseState.P2_RANDOM_EXPLORATION
                        remainPhaseStateCount = 0
                        strategyTask = randomExplorationTask.also {
                            it.initialize(currentState)
                            it.isFullyExploration = true
                            it.backAction = true
                        }
                    }
                }
            }
        }
        else {
            attempt--
            selectTargetStaticNode()
            setTestBudget = false
            phaseState = PhaseState.P2_INITIAL
            chooseTask(eContext, currentState)
        }
    }

    var setTestBudget = false

    fun selectTargetStaticNode(){
        computeAppStatesScore()
        val leastTriedWindows = targetWindowsCount.map { Pair<WTGNode, Int>(first = it.key, second = it.value) }.groupBy { it.second }.entries.sortedBy { it.key }.first()
        val leastTriedWindowScore = staticNodeScores.filter {windowScore -> leastTriedWindows.value.any { it.first == windowScore.key } }
        val pb = ProbabilityDistribution<WTGNode>(leastTriedWindowScore)
        val targetNode = pb.getRandomVariable()
        targetWindow = targetNode
        targetWindowsCount[targetWindow!!] = targetWindowsCount[targetWindow!!]!!+1
        budgetLeft = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == targetWindow && it is VirtualAbstractState}.first().widgets.size

    }



     fun computeAppStatesScore(){
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
        regressionTestingMF.appStatesMap.forEach { window, abstractStateList ->
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
            regressionTestingMF.allTargetStaticEvents.filter { it.sourceWindow == n }. forEach {
                modifiedMethods.addAll(it.modifiedMethods.map { it.key })
            }

            if (regressionTestingMF.windowHandlersHashMap.containsKey(n)) {
                regressionTestingMF.windowHandlersHashMap[n]!!.forEach {handler ->
                    val methods = regressionTestingMF.modifiedMethodTopCallersMap.filter { it.value.contains(handler) }.map { it.key }
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
                regressionTestingMF.transitionGraph.edges(n).map { it.label }.distinct().filter{ regressionTestingMF.allTargetStaticEvents.contains(it) }.forEach {
                    phase2TargetEvents.put(it,0)
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