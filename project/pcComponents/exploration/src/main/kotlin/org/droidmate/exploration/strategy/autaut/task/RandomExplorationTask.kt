package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGDialogNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGLauncherNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGOutScopeNode
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

class RandomExplorationTask constructor(
        regressionTestingMF: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean,
        private var randomScroll: Boolean,
        private var maximumAttempt: Int): AbstractStrategyTask(autAutTestingStrategy,regressionTestingMF,delay, useCoordinateClicks){
    private val MAX_ATTEMP_EACH_EXECUTION=10
    private var prevAbState: AbstractState?=null
    private val BACK_PROB = 0.1
    private val PRESSMENU_PROB = 0.2
    private val ROTATE_PROB = 0.05
    private val SWIPE_PROB = 0.5
    private val clickNavigationUpTask = ClickNavigationUpTask(regressionTestingMF, autAutTestingStrategy, delay, useCoordinateClicks)
    private val fillDataTask = PrepareContextTask.getInstance(regressionTestingMF,autAutTestingStrategy, delay, useCoordinateClicks)

    protected var goToLockedWindowTask: GoToAnotherWindow? = null
    protected var openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF,autAutTestingStrategy,delay, useCoordinateClicks)
    var fillingData = false
    private var dataFilled = false
    private var initialExerciseCount = -1
    private var currentExerciseCount = -1
    var reset = false
    var backAction = true
    var isFullyExploration: Boolean = false
    var recentChangedSystemConfiguration: Boolean = false
    var environmentChange: Boolean = false
    var alwaysUseRandomInput: Boolean = false
    var stopWhenHavingTestPath: Boolean = false

    private var lockedWindow: WTGNode? = null
    var lastAction: AbstractAction? = null
    var isScrollToEnd = false

    val recentActions = ArrayList<AbstractAction>()
    override fun reset() {
        attemptCount = 0
        prevAbState=null
        isFullyExploration=false
        initialExerciseCount = -1
        currentExerciseCount = -1
        dataFilled = false
        fillingData = false
        fillDataTask.reset()
        lockedWindow = null
        recentChangedSystemConfiguration = false
        environmentChange = false
        lastAction = null
        alwaysUseRandomInput = false
        triedRandomKeyboard = false
        stopWhenHavingTestPath = false
        recentActions.clear()
    }

    override fun initialize(currentState: State<*>) {
        reset()
        setMaxiumAttempt(currentState,MAX_ATTEMP_EACH_EXECUTION)
    }
    fun lockTargetWindow (window: WTGNode) {
        lockedWindow = window

    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (isCameraOpening(currentState))
            return false
        if (attemptCount >= maximumAttempt)
        {
            return true
        }
        val prevState = autautMF.appPrevState
        val prevAppState = autautMF.getAbstractState(prevState!!)!!
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (prevAppState.window != currentAbstractState.window && !currentAbstractState.isOutOfApplication && !prevAppState.isOutOfApplication && lockedWindow == null)
            return true
        /*if (isFullyExploration)
            return false
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (prevAbState == null)
            return false
        if (currentAbstractState.window != prevAbState!!.window && currentAbstractState.window !is WTGOutScopeNode)
        {
            if (lockedWindow != null) {
                val testPaths = autautStrategy.phaseStrategy.getPathsToWindow(currentState,lockedWindow!!,true)
                if (testPaths.isNotEmpty())
                    return false
            }

        }*/
        return false
       /* if (initialExerciseCount < currentExerciseCount-1)
            return true*/
    }

    fun setMaxiumAttempt(currentState: State<*>, attempt: Int){
        val inputFieldCount = currentState.widgets.filter { it.isInputField }.size
        val actionBasedAttempt = (autautMF.getAbstractState(currentState)?.getUnExercisedActions(currentState)?.size?:1)
        maximumAttempt = min(actionBasedAttempt,attempt)
    }

    fun setMaxiumAttempt(attempt: Int){
        maximumAttempt = attempt
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

     var attemptCount = 0
    override fun isAvailable(currentState: State<*>): Boolean {
       return true
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        /*if (!isFullyExploration )
        {
            val unexercisedWidgets = autautMF.getLeastExerciseWidgets(currentState)
            if (unexercisedWidgets.isNotEmpty())
            {
                if (initialExerciseCount==-1)
                {
                    initialExerciseCount = unexercisedWidgets.entries.first().value.exerciseCount
                }
                currentExerciseCount = unexercisedWidgets.entries.first().value.exerciseCount
               *//* if (unexercisedWidgets.size>2 &&
                        unexercisedWidgets.values.find { it.resourceIdName.contains("confirm")
                                || it.resourceIdName.contains("cancel") }!=null)
                {

                    return unexercisedWidgets.filter { !it.value.resourceIdName.contains("confirm")
                            && !it.value.resourceIdName.contains("cancel") }.map { it.key }
                }
                if (unexercisedWidgets.size>1 && unexercisedWidgets.values.find {
                                it.resourceIdName.contains("cancel") }!=null)
                {
                    return unexercisedWidgets.filter {
                        !it.value.resourceIdName.contains("cancel") }.map { it.key }
                }*//*
                return unexercisedWidgets.map { it.key }.filter { !it.checked.isEnabled() }
            }
        }*/
        val visibleWidgets: List<Widget>
        visibleWidgets = Helper.getVisibleInteractableWidgets(currentState)
        if (currentState.widgets.filter { it.className == "android.webkit.WebView" }.isNotEmpty())
        {
            return ArrayList(currentState.widgets.filter { it.className == "android.webkit.WebView"}).also {it.addAll(visibleWidgets) }
        }
        /*if (random.nextInt(100)/100.toDouble()<0.5)
        {
            visibleWidgets = currentState.visibleTargets
        }
        else
        {
            visibleWidgets = Helper.getVisibleInteractableWidgets(currentState)
        }*/

        if (visibleWidgets.isNotEmpty()) {
            return visibleWidgets
        }
        //when DM2 provides incorrect information
        return currentState.widgets.filterNot { it.isKeyboard}
    }

    var tryLastAction = 0
    val MAX_TRY_LAST_ACTION = 10
    var triedRandomKeyboard = false
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        if (reset) {
            reset = false
            return autautStrategy.eContext.resetApp()
        }
         val currentAbstractState = autautMF.getAbstractState(currentState)!!
        val prevState = autautMF.appPrevState!!
        val prevAbstractState = if (autautMF.appPrevState!=null)
            autautMF.getAbstractState(autautMF.appPrevState!!)?:
                    currentAbstractState
        else
            currentAbstractState

        if (isCameraOpening(currentState)) {
            return dealWithCamera(currentState)
        }
        isClickedShutterButton = false
        if (currentAbstractState.isOutOfApplication
                || Helper.getVisibleWidgets(currentState).find { it.resourceId == "android:id/floating_toolbar_menu_item_text" } != null) {

            // Some apps will pop up library activity. Press back is not enough.
            // give a chance to try random
            if (!currentState.widgets.any { it.packageName == autautMF.packageName }) {
                log.info("App goes to an out of scope node.")
                if (autautMF.abstractTransitionGraph.edges(currentAbstractState).any {
                            it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK
                                    && !(it.destination?.data?.isOutOfApplication?:false)
                        }) {
                    log.info("Try press back.")
                    return ExplorationAction.pressBack()
                }
            }
        }

        if (lockedWindow != null && lockedWindow != currentAbstractState.window) {
            if (goToLockedWindowTask != null) {
                // should go back to target Window
                //reset data filling

                if (!goToLockedWindowTask!!.isTaskEnd(currentState)) {
                    return goToLockedWindowTask!!.chooseAction(currentState)
                }
            } else {

                if (currentAbstractState.window !is WTGDialogNode) {
                    dataFilled = false
                    goToLockedWindowTask = GoToAnotherWindow(autAutTestingStrategy = autautStrategy, autautMF = autautMF, delay = delay, useCoordinateClicks = useCoordinateClicks)
                    if (goToLockedWindowTask!!.isAvailable(currentState, lockedWindow!!, true,false)) {
                        goToLockedWindowTask!!.initialize(currentState)
                        return goToLockedWindowTask!!.chooseAction(currentState)
                    }
                }
            }
        }
        goToLockedWindowTask = null
        if (!triedRandomKeyboard) {
            if (currentState.widgets.filter { it.isKeyboard }.isNotEmpty()
                    && Helper.getVisibleInteractableWidgets(currentState).filter { it.packageName == autautStrategy.eContext.apk.packageName }.isEmpty()) {
                val searchButtons = currentState.visibleTargets.filter { it.isKeyboard }.filter { it.contentDesc.toLowerCase().contains("search") }
                if (random.nextBoolean()) {
                    triedRandomKeyboard = true
                    return chooseActionWithName(AbstractActionType.RANDOM_KEYBOARD, "", null, currentState, null)!!
                }
                if (searchButtons.isNotEmpty()) {
                    if (random.nextBoolean()) {
                        dataFilled = false
                        val randomButton = searchButtons.random()
                        log.info("Widget: $random")
                        return randomButton.click()
                    } else {
                        return GlobalAction(actionType = ActionType.CloseKeyboard)
                    }
                } else {
                    return GlobalAction(actionType = ActionType.CloseKeyboard)
                }
            }

            if (currentState.widgets.filter { it.isKeyboard }.isNotEmpty()) {
                // Need return to portrait
                if (random.nextBoolean()) {
                    triedRandomKeyboard = true
                    return chooseActionWithName(AbstractActionType.RANDOM_KEYBOARD, "", null, currentState, null)!!
                }

            }
        }

        if (currentState.widgets.filter { it.isKeyboard }.isNotEmpty() &&
                Helper.getVisibleInteractableWidgets(currentState).filter { it.packageName == autautStrategy.eContext.apk.packageName }.isEmpty() ) {
            return GlobalAction(actionType = ActionType.CloseKeyboard)
        }

       /* if (currentAbstractState.window.activityClass == "com.oath.mobile.platform.phoenix.core.TrapActivity") {
            if (currentState.visibleTargets.any { it.text == "I agree" || it.text == "Save and continue" }) {
                return currentState.visibleTargets.find { it.text == "I agree"|| it.text == "Save and continue" }!!.click()
            }

        }*/
        if (currentAbstractState.rotation == Rotation.LANDSCAPE) {
            if (random.nextBoolean())
                return chooseActionWithName(AbstractActionType.ROTATE_UI, "", null, currentState, null)!!
        }

        if (environmentChange) {
            if (!recentChangedSystemConfiguration) {
                recentChangedSystemConfiguration = true
                if (autautMF.havingInternetConfiguration(currentAbstractState.window)) {
                    if (random.nextInt(4) < 3)
                        return GlobalAction(ActionType.EnableData).also {
                            autautMF.internetStatus = true
                        }
                    else
                        return GlobalAction(ActionType.DisableData).also {
                            autautMF.internetStatus = false
                        }
                } else if (autautMF.internetStatus == false) {
                    return GlobalAction(ActionType.EnableData).also {
                        autautMF.internetStatus = true
                    }
                }
            } /*else {
                if (isFullyExploration && autautMF.havingInternetConfiguration(currentAbstractState.window)) {
                    //20%
                    if (random.nextInt(4) == 0) {
                        if (random.nextInt(4) < 3)
                            return GlobalAction(ActionType.EnableData).also {
                                autautMF.internetStatus = true
                            }
                        else
                            return GlobalAction(ActionType.DisableData).also {
                                autautMF.internetStatus = false
                            }
                    }
                }
            }*/
        }

//        if (currentAbstractState.window is WTGActivityNode) {
//            if (!regressionTestingMF.openNavigationCheck.contains(currentAbstractState)
//                    && openNavigationBarTask.isAvailable(currentState)) {
//                regressionTestingMF.openNavigationCheck.add(currentAbstractState)
//                return openNavigationBarTask.chooseAction(currentState)
//            }
//        }
        if (!dataFilled && !fillingData) {
            if (fillDataTask.isAvailable(currentState,alwaysUseRandomInput)) {
                fillDataTask.initialize(currentState)
                fillingData = true
            }
        }
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)
        if (fillingData) {
            fillingData = false
            dataFilled = true
            /*if (fillDataTask.fillActions.isNotEmpty()  ) {
                dataFilled = false
            } else
                dataFilled = true*/
        }
        attemptCount++
        var randomAction: AbstractAction? = null
        if (lastAction!=null
                && lastAction!!.actionType == AbstractActionType.SWIPE
                && prevAbstractState != currentAbstractState
                && prevAbstractState.window == currentAbstractState.window
                && autautMF.abstractStateVisitCount[currentAbstractState]!! == 1
                && tryLastAction < MAX_TRY_LAST_ACTION
                /*&& isScrollToEnd
                && lastAction!!.extra == "SwipeTillEnd"*/
                && currentAbstractState.getAvailableActions().contains(lastAction!!)
        ) {
            tryLastAction += 1
            randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }

        } else {
            tryLastAction = 0
            //val widgetActions = currentAbstractState.getAvailableActions().filter { it.widgetGroup != null }
            val unexercisedActions = currentAbstractState.getUnExercisedActions(currentState).filterNot { it.isCheckableOrTextInput() }
            val lowestPriorityActions = unexercisedActions.filter {
                        it.attributeValuationSet == null
            }
            if (unexercisedActions.filterNot { lowestPriorityActions.contains(it) } .isNotEmpty()) {
                randomAction = exerciseUnexercisedAbstractActions(unexercisedActions.filterNot { lowestPriorityActions.contains(it) }, randomAction, currentAbstractState)
                //randomAction = unexercisedActions.random()
            }
            else if (unexercisedActions.isNotEmpty()) {
                randomAction = if (unexercisedActions.any { it.attributeValuationSet != null }) {
                    // Swipe on widget should be executed by last
                    val widgetActions = unexercisedActions.filter { it.attributeValuationSet != null }
                    widgetActions.random()
                    /*if (widgetActions.any { it.actionName != "Swipe" }) {
                                if (widgetActions.any { it.actionName == "Click" }) {
                                    widgetActions.filter {it.actionName == "Click"}.random()
                                } else {
                                    widgetActions.filterNot { it.actionName == "Swipe" }.random()
                                }
                            } else {
                                widgetActions.random()
                            }*/
                } else {
                    unexercisedActions.random()
                }
            }
            else if(( Helper.getVisibleInteractableWidgets(currentState)
                            .filterNot { it.isInputField || it.checked.isEnabled()  }.any {
                            runBlocking { autautStrategy.getActionCounter().widgetCntForState(it.uid,currentState.uid) == 0 }
                        }
                    && !isFullyExploration) ) {
                lastAction = null
                val visibleTargets = Helper.getVisibleInteractableWidgets(currentState)
                        .filterNot { it.isInputField || it.checked.isEnabled() }
                val candidates = runBlocking { getCandidates(visibleTargets
                )}

                val chosenWidget = candidates.random()
                log.info("Widget: $chosenWidget")
                var actionList = if (chosenWidget.visibleBounds.width > 200 && chosenWidget.visibleBounds.height > 200 ) {
                    ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks))
                } else {
                    ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks).filterNot { it is Swipe })
                }
                if (actionList.isNotEmpty())
                {
                    val maxVal = actionList.size

                    assert(maxVal > 0) { "No actions can be performed on the widget $chosenWidget" }

                    val randomIdx = random.nextInt(maxVal)
                    val randomAction = chooseActionWithName(AbstractActionType.values().find { it.actionName.equals(actionList[randomIdx].name)}!!, "" ,chosenWidget, currentState,null)
                    log.info("$randomAction")
                    return randomAction?:ExplorationAction.pressBack().also { log.info("Action null -> PressBack") }
                }
                else
                {
                    ExplorationTrace.widgetTargets.clear()
                    return autautStrategy.eContext.navigateTo(chosenWidget, {it.click()})?:ExplorationAction.pressBack()
                }
            } else {
                val allActions = currentAbstractState.getAvailableActions().filterNot {
                            it.actionType == AbstractActionType.ENABLE_DATA
                            || it.actionType == AbstractActionType.DISABLE_DATA
                                    || it.actionType==AbstractActionType.LAUNCH_APP
                                    || it.actionType==AbstractActionType.RESET_APP
                                    || it.actionType==AbstractActionType.ROTATE_UI
                                    || it.isCheckableOrTextInput()
                }

                val interestingActions = ArrayList<AbstractAction>()
                /*val condition = Helper.extractInputFieldAndCheckableWidget(currentState)
                val widgetActions = allActions.filter { it.isWidgetAction() }

                if (condition.isNotEmpty()) {
                    autautMF.abstractTransitionGraph.edges(currentAbstractState)
                            .filter { widgetActions.contains(it.label.abstractAction) }
                            .groupBy { it.label.abstractAction }
                            .forEach { action, edges ->
                                var encounteredCondition = false
                                if (edges.any { autautMF.abstractTransitionGraph.containsCondition(it, HashMap(condition)) }) {
                                    encounteredCondition = true
                                }
                                if (!encounteredCondition) {
                                    interestingActions.add(action)
                                }
                            }
                } */
                if (interestingActions.isEmpty()) {
                    interestingActions.addAll(allActions)
                }

                val actionScore = HashMap<AbstractAction,Double>()

                //getActionScores(currentAbstractState, interestingActions, actionScore, currentState)
                getActionScores2(currentAbstractState, allActions, actionScore, currentState)
                val unexercisedActions = actionScore.filter { !recentActions.contains(it.key) }
                if (unexercisedActions.isNotEmpty()) {
                    randomAction = unexercisedActions.maxBy { it.value }!!.key

                } else {
                    randomAction = actionScore.entries.random().key
                }
                recentActions.add(randomAction)
            }
        }

        if (randomAction != null) {
            if (randomAction.extra == "SwipeTillEnd") {
                isScrollToEnd = true
            } else {
                isScrollToEnd = false
            }
            lastAction = randomAction
            var chosenWidget: Widget? = null
            var isValidAction = true
            if (randomAction.attributeValuationSet != null) {
                val chosenWidgets = randomAction!!.attributeValuationSet!!.getGUIWidgets(currentState)
                if (chosenWidgets.isEmpty()) {
                    chosenWidget = null
                } else {
                    val candidates = runBlocking { getCandidates(chosenWidgets)}
                    chosenWidget = if (candidates.isEmpty())
                        chosenWidgets.random()
                    else
                        candidates.random()
                }

                if (chosenWidget == null) {
                    log.debug("No widget found")
                    // remove action
                    randomAction!!.attributeValuationSet!!.actionCount.remove(randomAction)
                    isValidAction = false
                } else {
                    log.info(" widget: $chosenWidget")
                }
            }
            if (isValidAction) {
                val actionType = randomAction.actionType
                log.debug("Action: $actionType")
                val chosenAction = when (actionType) {
                    AbstractActionType.SEND_INTENT-> chooseActionWithName(actionType, randomAction.extra, null, currentState, randomAction)
                    AbstractActionType.ROTATE_UI -> chooseActionWithName(actionType, 90, null, currentState, randomAction)
                    else -> chooseActionWithName(actionType, randomAction.extra
                            ?: "", chosenWidget, currentState, randomAction)
                }
                if (chosenAction != null) {
                    return chosenAction.also {
                        prevAbState = currentAbstractState
                    }
                } else {
                    // this action should be removed from this abstract state
                    if (randomAction.attributeValuationSet == null) {
                        currentAbstractState.actionCount.remove(randomAction)
                    } else {
                        randomAction.attributeValuationSet!!.actionCount.remove(randomAction)
                    }
                    return chooseAction(currentState)
                }
            }
        }
        return ExplorationAction.pressBack()

/*        if (executeSystemEvent < 0.05) {
            if (regressionTestingMF.appRotationSupport)
                return chooseActionWithName("RotateUI",90,null,currentState,null)!!
        } else if (executeSystemEvent < 0.1)
        {
            if (haveOpenNavigationBar(currentState))
            {
                return clickOnOpenNavigation(currentState)
            }
            else
            {
                if (currentAbstractState.hasOptionsMenu)
                    return chooseActionWithName("PressMenu", null,null,currentState,null)!!
            }
        }
        else if (executeSystemEvent < 0.15)
        {
            return chooseActionWithName("PressMenu", null,null,currentState,null)!!
        }else if (executeSystemEvent < 0.20)
        {
            if (!regressionTestingMF.isPressBackCanGoToHomescreen(currentState) && backAction)
            {
                log.debug("Randomly back")
                return ExplorationAction.pressBack()
            }
            if(clickNavigationUpTask.isAvailable(currentState))
            {
                return clickNavigationUpTask.chooseAction(currentState)
            }
        } else if (executeSystemEvent < 0.25) {
            //Try swipe on unscrollable widget
            return chooseActionWithName("Swipe", "",null,currentState,null)?:ExplorationAction.pressBack()

        }*/


    }

    private fun getActionScores(currentAbstractState: AbstractState, allActions: List<AbstractAction>, actionScore: HashMap<AbstractAction, Double>, currentState: State<*>) {
        val windowWidgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[currentAbstractState.window]!!
        val similarAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == currentAbstractState.window }
        val numboerOfAbstractStates = similarAbstractStates.size
        allActions.forEach { action ->
            val pair = computeActionPotentialScore(action, numboerOfAbstractStates, windowWidgetFrequency, currentAbstractState, similarAbstractStates)
            val widgetGroup = pair.first
            var actionPotentialScore = pair.second

            val actionCount = currentAbstractState.getActionCount(action)
            actionScore.put(action,action.getScore()*actionPotentialScore/actionCount)

            /*if (widgetGroup == null) {
                val score = actionPotentialScore * action.getScore() / (numboerOfAbstractStates * actionCount.toDouble())
                actionScore.put(action, score)
            } else if (windowWidgetFrequency.containsKey(widgetGroup)) {
                val similarWidgetNumbers = widgetGroup.getGUIWidgets(currentState).size
                val score = action.getScore() * actionPotentialScore * similarWidgetNumbers / (windowWidgetFrequency[widgetGroup]!! * actionCount.toDouble())
                actionScore.put(action, score)
            } else {
                val similarWidgetNumbers = widgetGroup.getGUIWidgets(currentState).size
                val score = action.getScore() * actionPotentialScore * similarWidgetNumbers / actionCount.toDouble()
                actionScore.put(action, score)
            }*/
        }
    }

    private fun getActionScores2(currentAbstractState: AbstractState, allActions: List<AbstractAction>, actionScore: HashMap<AbstractAction, Double>, currentState: State<*>) {
        allActions.forEach { action ->
            val pair = computeActionPotentialScore2(action, currentAbstractState)
            var actionPotentialScore = pair.second
            val actionCount = currentAbstractState.getActionCount(action)
            actionScore.put(action,action.getScore()*actionPotentialScore/(actionCount*5))
        }
    }

    private fun computeActionPotentialScore2(action: AbstractAction, currentAbstractState: AbstractState): Pair<AttributeValuationSet?, Double> {
        val widgetGroup = action.attributeValuationSet
        var actionPotentialScore = 1.0

        val goToHomeEdges = autautMF.abstractTransitionGraph.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.destination?.data?.window is WTGLauncherNode
        }

        goToHomeEdges.forEach {
            if (!it.label.isImplicit) {
                actionPotentialScore /= (10* it.label.interactions.size)
            } else
                actionPotentialScore /= 5
        }

/*        val openEdges = autautMF.abstractTransitionGraph.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.source.data != edge.destination?.data
                    && edge.destination?.data !is VirtualAbstractState
                    && edge.destination?.data?.window !is WTGOutScopeNode
                    && edge.destination?.data?.window !is WTGLauncherNode
        }
*//*        if (action.actionType != AbstractActionType.PRESS_BACK && openEdges.isNotEmpty()) {
            if (openEdges.all { it.label.isImplicit }) {
                actionPotentialScore *= (2* openEdges.size)
            } else {
                actionPotentialScore *= (4* openEdges.map { it.label.interactions }.flatten().size)
            }
        }*//*
        val allUnexploredWidgetAction = ArrayList<AbstractAction>()
        openEdges.forEach { edge ->
            val dest = edge.destination?.data
            if (dest != null) {
                val numberOfAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == dest.window }.size
                val visitedCount = autautMF.abstractStateVisitCount[dest]!!
                val widgetGroupFrequences = AbstractStateManager.instance.widgetGroupFrequency[dest.window]!!
                val unexploredWidgetActions = dest.getUnExercisedActions(null).filterNot { it.attributeValuationSet == null }
                unexploredWidgetActions
                        .filterNot { allUnexploredWidgetAction.contains(it) }
                        .forEach {
                            allUnexploredWidgetAction.add(it)
                            if (!widgetGroupFrequences.containsKey(it.attributeValuationSet)) {
                                actionPotentialScore *= (1 + it.getScore() / (numberOfAbstractStates))
                            } else {
                                actionPotentialScore *= (1 + it.getScore() / widgetGroupFrequences[it.attributeValuationSet]!!.toDouble())
                            }
                        }
                actionPotentialScore = actionPotentialScore

            }
        }*/

        val considerDeadEdges = true
        if (considerDeadEdges) {
            val deadEdges =
                autautMF.abstractTransitionGraph.edges(currentAbstractState).filter { edge ->
                    edge.label.abstractAction == action
                            && edge.source.data == edge.destination?.data
                            && edge.destination?.data !is VirtualAbstractState
                            && edge.destination?.data?.window !is WTGOutScopeNode
                }
            deadEdges.forEach {
                if (it.label.isImplicit)
                    actionPotentialScore /= (5)
                if (!it.label.isImplicit)
                    actionPotentialScore/=(10*it.label.interactions.size)

            }
        }

        return Pair(widgetGroup, actionPotentialScore)
    }

    private fun computeActionPotentialScore(action: AbstractAction, numboerOfAbstractStates: Int, windowWidgetFrequency: HashMap<AttributeValuationSet, Int>, currentAbstractState: AbstractState, similarAbstractStates: List<AbstractState>): Pair<AttributeValuationSet?, Double> {
        val widgetGroup = action.attributeValuationSet
        var actionPotentialScore = 1.0

        val goToHomeEdges = autautMF.abstractTransitionGraph.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.destination?.data?.window is WTGLauncherNode
        }
        goToHomeEdges.forEach {
            if (!it.label.isImplicit) {
                actionPotentialScore /= (10* it.label.interactions.size)
            } else
                actionPotentialScore /= 2
        }
        val openEdges = autautMF.abstractTransitionGraph.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.source.data != edge.destination?.data
                    && edge.destination?.data !is VirtualAbstractState
                    && edge.destination?.data?.window !is WTGOutScopeNode
                    && edge.destination?.data?.window !is WTGLauncherNode
        }
/*        if (action.actionType != AbstractActionType.PRESS_BACK && openEdges.isNotEmpty()) {
            if (openEdges.all { it.label.isImplicit }) {
                actionPotentialScore *= (2* openEdges.size)
            } else {
                actionPotentialScore *= (4* openEdges.map { it.label.interactions }.flatten().size)
            }
        }*/
        val unexploredWidgetAction = ArrayList<AbstractAction>()
        openEdges.forEach { edge ->
            val dest = edge.destination?.data
            if (dest != null) {
                val numberOfAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == dest.window }.size
                val visitedCount = autautMF.abstractStateVisitCount[dest]!!
                val widgetGroupFrequences = AbstractStateManager.instance.widgetGroupFrequency[dest.window]!!
                val unexploredWidgetActions = dest.getUnExercisedActions(null).filterNot { it.attributeValuationSet == null }
                unexploredWidgetActions
                        .filterNot { unexploredWidgetAction.contains(it) }
                        .forEach {
                            unexploredWidgetAction.add(it)
                            if (!widgetGroupFrequences.containsKey(it.attributeValuationSet)) {
                                actionPotentialScore *= (1 + it.getScore() / (numberOfAbstractStates))
                            } else {
                                actionPotentialScore *= (1 + it.getScore() / widgetGroupFrequences[it.attributeValuationSet]!!.toDouble())
                            }
                        }
                actionPotentialScore = actionPotentialScore

                val liveEdge = autautMF.abstractTransitionGraph.edges(dest).filter {
                    it.destination != it.source
                            && edge.destination?.data !is VirtualAbstractState
                            && edge.destination?.data?.window !is WTGOutScopeNode
                            && edge.destination?.data?.window !is WTGLauncherNode
                            && it.label.isExplicit()}
                if (liveEdge.size>0) {
                    actionPotentialScore *= (liveEdge.size)
                }
            }
        }




        val considerDeadEdges = true
        if (considerDeadEdges) {
            val deadEdges = similarAbstractStates.map {
                autautMF.abstractTransitionGraph.edges(it).filter { edge ->
                    edge.label.abstractAction == action
                            && edge.source.data == edge.destination?.data
                            && edge.destination?.data !is VirtualAbstractState
                            && edge.destination?.data?.window !is WTGOutScopeNode
                }
            }.flatten()
            deadEdges.forEach {
                if (it.label.isImplicit)
                    actionPotentialScore /= (2)
                if (!it.label.isImplicit)
                    actionPotentialScore/=(10*it.label.interactions.size)

            }
        }
        return Pair(widgetGroup, actionPotentialScore)
    }

    private fun exerciseUnexercisedAbstractActions(unexercisedActions: List<AbstractAction>, randomAction: AbstractAction?, currentAbstractState: AbstractState): AbstractAction? {
        var randomAction1 = randomAction
        randomAction1 = if (unexercisedActions.any { it.attributeValuationSet != null }) {
            //Test: prioritize swipe action to show as many as possible widgets
            /* val widgetActions = toExecuteActions.filter { it.widgetGroup != null }
                    if (widgetActions.any { it.actionName == "Swipe" }) {
                        widgetActions.filter { it.actionName == "Swipe" }.random()
                    } else {
                        widgetActions.random()
                    }*/

            // Swipe on widget should be executed by last
            val widgetActions = unexercisedActions.filter { it.attributeValuationSet != null }
            val nonWebViewActions = widgetActions.filterNot { it.attributeValuationSet!!.attributePath.getClassName().contains("WebView") }
            val candidateActions = if (nonWebViewActions.isEmpty())
                widgetActions
            else
                nonWebViewActions

            //prioritize the less frequent widget
            val actionByScore = HashMap<AbstractAction, Double>()
            val windowWidgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[currentAbstractState.window]!!
            candidateActions.forEach {
                var actionScore = it.getScore()
                val widgetGroup = it.attributeValuationSet!!
                if (windowWidgetFrequency.containsKey(widgetGroup)) {
                    actionByScore.put(it, actionScore.toDouble() / windowWidgetFrequency[widgetGroup]!!)
                } else {
                    actionByScore.put(it, actionScore.toDouble())
                }
            }
            if (actionByScore.isNotEmpty()) {
                actionByScore.maxBy { it.value }!!.key
            } else {
                candidateActions.random()
            }
        } else {
            unexercisedActions.random()
        }
        return randomAction1
    }


    override fun hasAnotherOption(currentState: State<*>): Boolean {
       return false
    }

    var isClickedShutterButton = false
    internal fun dealWithCamera(currentState: State<*>): ExplorationAction {
        val gotItButton = currentState.widgets.find { it.text.toLowerCase().equals("got it") }
        if (gotItButton != null) {
            log.info("Widget: $gotItButton")
            return gotItButton.click()
        }
        if (!isClickedShutterButton){
            val shutterbutton = currentState.actionableWidgets.find { it.resourceId.contains("shutter_button") }
            if (shutterbutton!=null)
            {
                log.info("Widget: $shutterbutton")
                val clickActions = shutterbutton.availableActions(delay, useCoordinateClicks).filter { it.name.isClick()}
                if (clickActions.isNotEmpty()) {
                    isClickedShutterButton = true
                    return clickActions.random()
                }
                ExplorationTrace.widgetTargets.clear()
            }
        }
        val doneButton = currentState.actionableWidgets.find { it.resourceId.contains("done_button") }
        if (doneButton!=null)
        {

            log.info("Widget: $doneButton")
            val clickActions = doneButton.availableActions(delay, useCoordinateClicks).filter { it.name.isClick()}
            if (clickActions.isNotEmpty()) {
                return clickActions.random()
            }
            ExplorationTrace.widgetTargets.clear()
        }
        return ExplorationAction.pressBack()
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount:Int = 0
        var instance: RandomExplorationTask? = null
        fun getInstance(regressionTestingMF: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean,
                        randomScroll: Boolean = true,
                        maximumAttempt: Int = 1): RandomExplorationTask {
            if (instance == null) {
                instance = RandomExplorationTask(regressionTestingMF,autAutTestingStrategy,
                        delay, useCoordinateClicks, randomScroll, maximumAttempt)
            }
            return instance!!
        }
    }

}