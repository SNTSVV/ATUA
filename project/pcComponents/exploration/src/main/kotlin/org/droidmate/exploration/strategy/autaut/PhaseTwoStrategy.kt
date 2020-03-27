package org.droidmate.exploration.strategy.autaut

import org.droidmate.deviceInterface.exploration.ActionType
import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.GlobalAction
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.closeAndReturn
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.resetApp
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
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
    useCoordinateClicks: Boolean
):AbstractPhaseStrategy (
    regressionTestingStrategy = regressionTestingStrategy,
    delay = delay,
    useCoordinateClicks = useCoordinateClicks
) {
    val statementMF: StatementCoverageMF

    var remainPhaseStateCount: Int = 0

    var phase2TargetStaticNode: WTGNode? = null
    var phase2TargetEvents: HashMap<StaticEvent, Int> = HashMap()

    var phase2TargetStaticNodeCount: HashMap<WTGNode, Int> = HashMap()

    val appStatesScores = HashMap<AbstractState, Double>()
    val appStateProbability = HashMap<WTGNode, ArrayList<Pair<AbstractState, Double>>>()

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
        WTGNode.allMeaningNodes.filter { it.isStatic() }.forEach {
            if (!phase2TargetStaticNodeCount.contains(it)) {
                phase2TargetStaticNodeCount.put(it,0)
            }
        }
        selectTargetStaticNode()
        attempt = staticNodeScores.size
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
        if (currentState.widgets.any{it.packageName == "com.android.camera2"})
        {
            strategyTask = null
            return dealWithCamera(eContext, currentState)
        }
        if (currentState.visibleTargets.filter { it.isKeyboard }.isNotEmpty() && strategyTask !is FillTextInputTask) {
            log.info("The keyboard is open and Text input filling was executed.")
            log.info("Try close the keyboard.")
            chosenAction = GlobalAction(actionType = ActionType.CloseKeyboard)
            if (strategyTask !is RandomExplorationTask)
                strategyTask = null
        } else if (currentAppState.isOutOfApplication
                || Helper.getVisibleWidgets(currentState).find { it.resourceId == "android:id/floating_toolbar_menu_item_text" } != null) {
            log.info("App goes to an out of scope node.")
            log.info("Try press back.")
            chosenAction = ExplorationAction.pressBack()
            if (strategyTask !is RandomExplorationTask)
                strategyTask = null
        } else {
            if (regressionTestingMF.fromLaunch && strategyTask !is RandomExplorationTask) {
                log.info("This node is reached from AppLaunch.")
                log.info("Reset current task and choose new task.")
                strategyTask = null
                chooseTask(eContext, currentState)
            } else {
                if (strategyTask == null) {
                    log.info("No current task selected.")
                    log.info("Choose new task.")
                    chooseTask(eContext, currentState)
                } else if (strategyTask != null && strategyTask!!.isTaskEnd(currentState)) {
                    log.info("Current task is end.")
                    log.info("Choose new task.")
                    chooseTask(eContext, currentState)
                    /* if (regressionTestingMF.abstractStateVisitCount[currentAppState]!! > 1 && currentWTGNode!!.hasOptionsMenu && !regressionTestingMF.optionsMenuCheck.contains(currentAppState)) {
                         val moreOptionWidget = regressionTestingMF.getToolBarMoreOptions(currentState)
                         if (moreOptionWidget != null) {
                             log.info("This node may More Option widget.")
                             log.info("Try click this widget.")
                             regressionTestingMF.optionsMenuCheck.add(currentAppState!!)
                             return moreOptionWidget.click()
                         } else {
                             log.info("This node may have Options Menu.")
                             log.info("Try press Options Menu key.")
                             regressionTestingMF.optionsMenuCheck.add(currentAppState!!)
                             return ExplorationAction.pressMenu()
                         }
                         regressionTestingMF.isRecentPressMenu = true
                     } else {

                     }*/
                } else {
                    log.info("Continue ${strategyTask!!.javaClass.name} task.")
                }
            }
            if (strategyTask != null) {
                chosenAction = strategyTask!!.chooseAction(currentState)
            } else {
                log.debug("No task seleted. It might be a bug.")
                chosenAction = ExplorationAction.closeAndReturn()
            }
        }
        budgetLeft--
        return chosenAction
    }
    override fun getPathsToTargetWindows(currentState: State<*>): List<TransitionPath> {
        //computeAppStatesScore()
        val currentAbState = AbstractStateManager.instance.getAbstractState(currentState)
        if (currentAbState==null)
            return emptyList()
        val targetAppStatesDistribution = HashMap<AbstractState,Pair<Double, Double>>()
        if (!appStateProbability.containsKey(phase2TargetStaticNode))
            return emptyList()
        val targetNodesProbability = appStateProbability[phase2TargetStaticNode]!!
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
                    ,parentNodes = listOf(currentAbState)
                    ,finalTarget = targetAbState
                    ,allPaths = transitionPaths
                    ,includeBackEvent = true
                    ,childParentMap = HashMap()
                    ,level = 0)
        }
        return transitionPaths
    }
    override fun getPathsToOtherWindows(currentState: State<*>): List<TransitionPath>{
        if (phase2TargetStaticNode==null)
            return emptyList()
        val transitionPaths = ArrayList<TransitionPath>()
        val currentAbState = regressionTestingMF.getAbstractState(currentState)
        if (currentAbState==null)
            return transitionPaths
        val candiateNodes = ArrayList(WTGNode.allMeaningNodes.filterNot { it != currentAbState.staticNode || it.classType == phase2TargetStaticNode!!.classType})
        //choose random activity
        if (candiateNodes.isEmpty())
            return transitionPaths
        val randomNode = candiateNodes.random()
        val destinationNodes = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.staticNode == randomNode }
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
                        ,parentNodes = listOf(currentAbState)
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
        if (abstractState!!.staticNode == phase2TargetStaticNode)
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
        val goToTargetNodeTask = GoToTargetNodeTask.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
        val goToAnotherNode = GoToAnotherNode.getInstance(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
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
        if (budgetLeft > 0)
        {
            if (phaseState == PhaseState.P2_INITIAL)
            {
                //regressionTestingMF.modifiedMethodCoverageFromLastChangeCount = 0
                if(goToAnotherNode.isAvailable(currentState))
                {
                    log.info("Task chosen: Go to another node.")
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    remainPhaseStateCount = 0
                    strategyTask = goToAnotherNode.also { it.initialize(currentState) }
                }
                else if (exerciseTargetComponentTask.isAvailable(currentState))
                {
                    log.info("Task chosen: Exercise Target Node .")
                    phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                }
                else
                {
                    log.info("Task chosen: Random Exploration with 10 actions")
                    phaseState = PhaseState.P2_RANDOM_EXPLORATION
                    remainPhaseStateCount = 0
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                        it.setMaximumAttempt(10)
                    }
                }
            }
            else if (phaseState == PhaseState.P2_GO_TO_ANOTHER_NODE)
            {
                if (currentAppState!!.staticNode == phase2TargetStaticNode
                        && remainPhaseStateCount<=2 && goToAnotherNode.isAvailable(currentState))
                {
                    //Repeat
                    log.info("Task chosen: Go to another node.")
                    remainPhaseStateCount += 1
                    strategyTask = goToAnotherNode.also { it.initialize(currentState) }
                }
                else
                {
                    log.info("Task chosen: Random Exploration with 10 actions")
                    phaseState = PhaseState.P2_RANDOM_EXPLORATION
                    remainPhaseStateCount = 0
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                        it.setMaximumAttempt(10)
                    }
                }

            }
            else if (phaseState == PhaseState.P2_RANDOM_EXPLORATION)
            {
                if (exerciseTargetComponentTask.isAvailable(currentState))
                {
                    log.info("Task chosen: Exercise Target Node .")
                    phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                }
                else
                {
                    if (goToTargetNodeTask.isAvailable(currentState))
                    {
                        log.info("Task chosen: Go to target node .")
                        phaseState = PhaseState.P2_GO_TO_TARGET_NODE
                        remainPhaseStateCount = 0
                        strategyTask = goToTargetNodeTask.also { it.initialize(currentState) }
                    }
                    else
                    {
                        if (remainPhaseStateCount < 2)
                        {
                            log.info("Task chosen: Random Exploration with 10 actions")
                            //phaseState = PhaseState.P2_RANDOM_EXPLORATION
                            remainPhaseStateCount+=1
                            strategyTask = randomExplorationTask.also {
                                it.initialize(currentState)
                                it.setMaximumAttempt(10)
                            }
                        }
                        else
                        {
                            if(goToAnotherNode.isAvailable(currentState))
                            {
                                log.info("Task chosen: Go to another node.")
                                phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                                remainPhaseStateCount = 0
                                strategyTask = goToAnotherNode.also { it.initialize(currentState) }
                            }
                            else
                            {
                                log.info("Task chosen: Random Exploration with 10 actions")
                                phaseState = PhaseState.P2_RANDOM_EXPLORATION
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
                }
            } else if (phaseState == PhaseState.P2_GO_TO_TARGET_NODE)
            {
                if (exerciseTargetComponentTask.isAvailable(currentState))
                {
                    log.info("Task chosen: Exercise Target Node .")
                    phaseState = PhaseState.P2_EXERCISE_TARGET_NODE
                    remainPhaseStateCount = 0
                    strategyTask = exerciseTargetComponentTask.also { it.initialize(currentState) }
                }
                else
                {
                    if (remainPhaseStateCount<=2 && goToTargetNodeTask.isAvailable(currentState))
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
                            it.backAction = true
                            it.setMaximumAttempt(10)
                        }
                    }
                }
            }
            else if (phaseState == PhaseState.P2_EXERCISE_TARGET_NODE)
            {
                if (goToAnotherNode.isAvailable(currentState))
                {
                    log.info("Task chosen: Go to another node.")
                    phaseState = PhaseState.P2_GO_TO_ANOTHER_NODE
                    remainPhaseStateCount = 0
                    strategyTask = goToAnotherNode.also { it.initialize(currentState) }
                }
                else
                {
                    log.info("Task chosen: Random Exploration")
                    phaseState = PhaseState.P2_RANDOM_EXPLORATION
                    remainPhaseStateCount = 0
                    strategyTask = randomExplorationTask.also {
                        it.initialize(currentState)
                        it.setMaximumAttempt(10)
                    }
                }
            }
        }
        else {
            attempt--
            budgetLeft = TEST_BUDGET
            selectTargetStaticNode()
            phaseState = PhaseState.P2_INITIAL
            chooseTask(eContext, currentState)
        }
    }

    fun selectTargetStaticNode(){
        computeAppStatesScore()
        val targetNodesDistribution = HashMap<WTGNode,Pair<Double, Double>>()
        var distributionPoint: Double = 0.0
        staticNodesProbability.forEach {
            val begin = distributionPoint
            distributionPoint += it.value
            val end = distributionPoint
            targetNodesDistribution.put(it.key,Pair(begin,end))
        }
        val random = Random.nextDouble(0.0,1.0)
        var targetNode: WTGNode? = null

        for (n in targetNodesDistribution)
        {
            if (random>= n.value.first && random <= n.value.second)
            {
                targetNode = n.key
            }
        }
        if (targetNode == null)
        {
            targetNode = targetNodesDistribution.entries.random().key
        }

        phase2TargetStaticNode = targetNode
        phase2TargetStaticNodeCount[phase2TargetStaticNode!!] = phase2TargetStaticNodeCount[phase2TargetStaticNode!!]!!+1
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
            appState.staticEventMapping.map { it.value }.map { it.modifiedMethods }.forEach { hmap ->
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
        val totalAppStateCount = appStateList.size
        modifiedMethodTriggerCount.forEach { m, c ->
            val score = 1-c/totalAppStateCount.toDouble()
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
        phase2TargetStaticNodeCount.forEach { n, _ ->
            var weight: Double = 0.0
            val targetEvents = ArrayList<StaticEvent>()
            val modifiedMethods = ArrayList<String>()
            appStateModifiedMethodMap.filter { it.key.staticNode == n}.map { it.value }.forEach {
                it.forEach {
                    if (!modifiedMethods.contains(it))
                    {
                        modifiedMethods.add(it)
                    }
                }
            }
            modifiedMethods.forEach {
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