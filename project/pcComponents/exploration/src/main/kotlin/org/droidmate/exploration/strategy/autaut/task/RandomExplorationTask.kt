package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Activity
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.helper.ProbabilityDistribution
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

class RandomExplorationTask constructor(
        regressionTestingMF: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean,
        private var randomScroll: Boolean,
        private var maximumAttempt: Int) : AbstractStrategyTask(atuaTestingStrategy, regressionTestingMF, delay, useCoordinateClicks) {
    private val MAX_ATTEMP_EACH_EXECUTION = 10
    private var prevAbState: AbstractState? = null
    private val BACK_PROB = 0.1
    private val PRESSMENU_PROB = 0.2
    private val ROTATE_PROB = 0.05
    private val SWIPE_PROB = 0.5
    private val clickNavigationUpTask = ClickNavigationUpTask(regressionTestingMF, atuaTestingStrategy, delay, useCoordinateClicks)
    private val fillDataTask = PrepareContextTask(regressionTestingMF, atuaTestingStrategy, delay, useCoordinateClicks)

    var goToLockedWindowTask: GoToAnotherWindow? = null
    protected var openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF, atuaTestingStrategy, delay, useCoordinateClicks)
    var fillingData = false
    private var dataFilled = false
    private var initialExerciseCount = -1
    private var currentExerciseCount = -1
    var reset = false
    var backAction = true
    var isPureRandom: Boolean = false
    var recentChangedSystemConfiguration: Boolean = false
    var environmentChange: Boolean = false
    var alwaysUseRandomInput: Boolean = false
    var stopWhenHavingTestPath: Boolean = false

    private var lockedWindow: Window? = null
    var lastAction: AbstractAction? = null
    var isScrollToEnd = false

    val recentActions = ArrayList<AbstractAction>()

    init {
        reset()
    }
    override fun reset() {
        attemptCount = 0
        prevAbState = null
        isPureRandom = false
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
        recentGoToExploreState = false
    }

    override fun initialize(currentState: State<*>) {
        reset()
        setMaxiumAttempt(currentState, MAX_ATTEMP_EACH_EXECUTION)
    }

    fun lockTargetWindow(window: Window) {
        lockedWindow = window

    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (isCameraOpening(currentState))
            return false
        if (fillingData == true) {
            return false
        }
        if (attemptCount >= maximumAttempt) {
            return true
        }
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

    fun setMaxiumAttempt(currentState: State<*>, attempt: Int) {
        val actionBasedAttempt = (atuaMF.getAbstractState(currentState)?.getUnExercisedActions(currentState,atuaMF)?.size
                ?: 1)
        maximumAttempt = min(actionBasedAttempt, attempt)
    }

    fun setMaxiumAttempt(attempt: Int) {
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
        visibleWidgets = Helper.getActionableWidgetsWithoutKeyboard(currentState)
        if (currentState.widgets.filter { it.className == "android.webkit.WebView" }.isNotEmpty()) {
            return ArrayList(currentState.widgets.filter { it.className == "android.webkit.WebView" }).also { it.addAll(visibleWidgets) }
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
        return emptyList()
    }

    var tryLastAction = 0
    val MAX_TRY_LAST_ACTION = 3
    var triedRandomKeyboard = false
    var recentGoToExploreState = false
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        val prevAbstractState = if (atuaMF.appPrevState != null)
            atuaMF.getAbstractState(atuaMF.appPrevState!!) ?: currentAbstractState
        else
            currentAbstractState

        if (isCameraOpening(currentState)) {
            return dealWithCamera(currentState)
        }
        isClickedShutterButton = false
        if (goToLockedWindowTask != null) {
            // should go back to target Window
            //reset data filling

            if (!goToLockedWindowTask!!.isTaskEnd(currentState)) {
                return goToLockedWindowTask!!.chooseAction(currentState)
            }
        } else {
            if (lockedWindow != null && lockedWindow != currentAbstractState.window) {
                if (currentAbstractState.window !is Dialog && currentAbstractState.window !is OptionsMenu && currentAbstractState.window !is OutOfApp) {
                    dataFilled = false
                    goToLockedWindowTask = GoToAnotherWindow(atuaTestingStrategy = autautStrategy, autautMF = atuaMF, delay = delay, useCoordinateClicks = useCoordinateClicks)
                    if (goToLockedWindowTask!!.isAvailable(currentState, lockedWindow!!, true, false, false)) {
                        goToLockedWindowTask!!.initialize(currentState)
                        return goToLockedWindowTask!!.chooseAction(currentState)
                    }
                }
            }
        }

        goToLockedWindowTask = null
        val lastActionType = autautStrategy.eContext.getLastActionType()
/*        if (lastActionType != "ResetApp" && lastActionType != "LaunchApp") {
            val allActions = autautStrategy.eContext.explorationTrace.getActions()
            val distantFromLaunch = allActions.size - allActions
                    .indexOfLast { it.actionType == "ResetApp" || it.actionType == "LaunchApp" }
            if (distantFromLaunch > 50*autautStrategy.budgetScale) {
                return autautStrategy.eContext.launchApp()
            }
        }*/
        if (currentState.widgets.any { it.isKeyboard }) {
            if (random.nextBoolean()) {
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
            val actionableWidgets = Helper.getActionableWidgetsWithoutKeyboard(currentState)
            if (actionableWidgets.isEmpty()) {
                //if keyboard is openning but there 's no special actions
                //give a 50/50 chance to do a random click on keyboard
                if (random.nextBoolean()) {
                    return doRandomKeyboard(currentState, null)!!
                }
                //find search button
                val searchButtons = currentState.visibleTargets.filter { it.isKeyboard }.filter { it.contentDesc.toLowerCase().contains("search") }
                if (searchButtons.isNotEmpty()) {
                    //Give a 50/50 chance to click on the search button
                    if (random.nextBoolean()) {
                        dataFilled = false
                        val randomButton = searchButtons.random()
                        log.info("Widget: $random")
                        return randomButton.click()
                    }
                }
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
        }
        if (isTrapActivity(currentAbstractState)) {
            if (currentState.visibleTargets.any { it.text == "I agree" || it.text == "Save and continue" || it.text == "Accept all" || it.text.contains("Go to end")}) {
                val candidates = currentState.visibleTargets.filter { it.text == "I agree" || it.text == "Save and continue" || it.text == "Accept all" || it.text.contains("Go to end")}
                val choosenWidget = runBlocking {  getCandidates(candidates).random() }
                return choosenWidget.click()
            }
        }
/*        if (currentAbstractState.rotation == Rotation.LANDSCAPE) {
            if (random.nextBoolean())
                return chooseActionWithName(AbstractActionType.ROTATE_UI, "", null, currentState, null)!!
        }*/

        if (environmentChange) {
            if (!recentChangedSystemConfiguration) {
                recentChangedSystemConfiguration = true
                if (atuaMF.havingInternetConfiguration(currentAbstractState.window)) {
                    if (random.nextInt(4) < 3)
                        return GlobalAction(ActionType.EnableData).also {
                            atuaMF.internetStatus = true
                        }
                    else
                        return GlobalAction(ActionType.DisableData).also {
                            atuaMF.internetStatus = false
                        }
                } else if (atuaMF.internetStatus == false) {
                    return GlobalAction(ActionType.EnableData).also {
                        atuaMF.internetStatus = true
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

//      if (currentAbstractState.window is WTGActivityNode) {
//            if (!regressionTestingMF.openNavigationCheck.contains(currentAbstractState)
//                    && openNavigationBarTask.isAvailable(currentState)) {
//                regressionTestingMF.openNavigationCheck.add(currentAbstractState)
//                return openNavigationBarTask.chooseAction(currentState)
//            }
//        }
        if (prevAbstractState!=null && prevAbstractState.window!=currentAbstractState.window
                && prevAbstractState.window is Activity
                && currentAbstractState.window is Activity) {
            dataFilled = false
            fillingData = false
        }
        if (!dataFilled && !fillingData) {
            val lastAction = autautStrategy.eContext.getLastAction()
            if (!lastAction.actionType.isTextInsert()) {
                if (fillDataTask.isAvailable(currentState, alwaysUseRandomInput)) {
                        fillDataTask.initialize(currentState)
                    fillingData = true
                } else {
                    dataFilled = true
                }
            } else {
                dataFilled = true
            }
        }
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)
        if (fillingData) {
            fillingData = false
            dataFilled = true
        }

        attemptCount++
        var randomAction: AbstractAction? = null
        if (!isPureRandom && lastAction != null
                && lastAction!!.actionType == AbstractActionType.SWIPE
                && prevAbstractState != currentAbstractState
                && prevAbstractState.window == currentAbstractState.window
                && atuaMF.abstractStateVisitCount[currentAbstractState]!! == 1
                /*&& isScrollToEnd
                && lastAction!!.extra == "SwipeTillEnd"*/
                && currentAbstractState.getAvailableActions().contains(lastAction!!)
        ) {
            tryLastAction = 1
            maximumAttempt += 1
            randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }
        } else if (!isPureRandom && lastAction != null
                && lastAction!!.actionType == AbstractActionType.SWIPE
                && atuaMF.appPrevState!!.stateId.uid != currentState.stateId.uid
                && (prevAbstractState == currentAbstractState ||
                        (prevAbstractState != currentAbstractState
                                && prevAbstractState.window == currentAbstractState.window
                                && atuaMF.abstractStateVisitCount[currentAbstractState]!! > 1)
                        )
                && tryLastAction < MAX_TRY_LAST_ACTION
                && currentAbstractState.getAvailableActions().contains(lastAction!!)) {
            tryLastAction += 1
            maximumAttempt += 1
            randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }
        } else {
            tryLastAction = 0
            //val widgetActions = currentAbstractState.getAvailableActions().filter { it.widgetGroup != null }
            val unexercisedActions = currentAbstractState.getUnExercisedActions(currentState,atuaMF)
            val lowestPriorityActions = unexercisedActions.filter {
                it.attributeValuationMap == null
            }
            if (unexercisedActions.filterNot { lowestPriorityActions.contains(it) }.isNotEmpty()) {
                randomAction = exerciseUnexercisedWidgetAbstractActions(unexercisedActions.filterNot { lowestPriorityActions.contains(it) }, randomAction, currentAbstractState)
                //randomAction = unexercisedActions.random()
            } else if (unexercisedActions.isNotEmpty()) {
                randomAction = if (unexercisedActions.any { it.attributeValuationMap != null }) {
                    // Swipe on widget should be executed by last
                    val widgetActions = unexercisedActions.filter { it.attributeValuationMap != null }
                    widgetActions.random()
                } else {
                    unexercisedActions.random()
                }
            } else {
                if (!isPureRandom && currentAbstractState.window !is Dialog && currentAbstractState.window !is OptionsMenu) {
                    goToLockedWindowTask = GoToAnotherWindow(atuaTestingStrategy = autautStrategy, autautMF = atuaMF, delay = delay, useCoordinateClicks = useCoordinateClicks)
                    if (!recentGoToExploreState && goToLockedWindowTask!!.isAvailable(currentState, currentAbstractState.window, true, false, true)) {
                        recentGoToExploreState = true
                        goToLockedWindowTask!!.initialize(currentState)
                        return goToLockedWindowTask!!.chooseAction(currentState)
                    }
                    goToLockedWindowTask = null
                }

                recentGoToExploreState = false
                lastAction = null
                if (random.nextDouble() < 0.1) {
                    val abstractActions = currentAbstractState.getAvailableActions().filter {
                        !it.isWidgetAction() && !recentActions.contains(it) && !it.isLaunchOrReset()
                    }
                    if (abstractActions.isNotEmpty()) {
                        randomAction = abstractActions.random()
                    }
                } else {
                    val visibleTargets = ArrayList(Helper.getActionableWidgetsWithoutKeyboard(currentState))

                    val unexploredWidgets = atuaMF.getUnexploredWidget(currentState).filter {
                        it.clickable || it.scrollable || (!it.clickable && it.longClickable)
                    }.filterNot { Helper.isUserLikeInput(it) }
                    if (unexploredWidgets.isNotEmpty() || isPureRandom || true) {

                        val notUserlikeInputs = visibleTargets.filter {
                            it.clickable || it.longClickable || it.scrollable
                        }.filterNot { Helper.isUserLikeInput(it) }

                        val candidates =  if (notUserlikeInputs.isNotEmpty() && random.nextBoolean()) {
                            notUserlikeInputs
                        } else {
                            visibleTargets
                        }
                        val lessExercisedWidgets = runBlocking {
                            ArrayList(getCandidates(candidates
                            ))
                        }
                        val chosenWidget = if (unexploredWidgets.any { lessExercisedWidgets.contains(it) })
                            unexploredWidgets.filter { lessExercisedWidgets.contains(it) }.random()
                        else
                            lessExercisedWidgets.random()
                        log.info("Widget: $chosenWidget")
                        return doRandomActionOnWidget(chosenWidget, currentState)
                    } /*else {
                        val bestCandidate = atuaMF.getCandidateAction(currentState, delay, useCoordinateClicks)
                        log.info("Widget: ${bestCandidate.second}")
                        return bestCandidate.first
                    }*/

                    /*val attributeValuationSet = currentAbstractState.getAttributeValuationSet(chosenWidget, currentState)
                    if (attributeValuationSet == null) {
                        return doRandomActionOnWidget(chosenWidget, currentState)
                    }
                    val availableActions = attributeValuationSet.actionCount.map { it.key }
                    if (availableActions.any { !recentActions.contains(it) }) {
                        randomAction = availableActions.filter { !recentActions.contains(it) }.random()
                    }*/
                }
            }
            /*    else if(( Helper.getVisibleInteractableWidgets(currentState)
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
                    *//*val condition = Helper.extractInputFieldAndCheckableWidget(currentState)
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
                } *//*
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
            }*/
        }

        if (randomAction != null) {
            if (randomAction.extra == "SwipeTillEnd") {
                isScrollToEnd = true
            } else {
                isScrollToEnd = false
            }
            lastAction = randomAction
            recentActions.add(randomAction)
            var chosenWidget: Widget? = null
            var isValidAction = true
            if (randomAction.attributeValuationMap != null) {
                val chosenWidgets = randomAction.attributeValuationMap!!.getGUIWidgets(currentState)
                if (chosenWidgets.isEmpty()) {
                    chosenWidget = null
                } else {
                    val candidates = runBlocking { getCandidates(chosenWidgets) }
                    chosenWidget = if (candidates.isEmpty())
                        chosenWidgets.random()
                    else
                        candidates.random()
                }

                if (chosenWidget == null) {
                    log.debug("No widget found")
                    // remove action
                    randomAction.attributeValuationMap!!.actionCount.remove(randomAction)
                    isValidAction = false
                } else {
                    log.info(" widget: $chosenWidget")
                }
            }
            if (isValidAction) {
                val actionType = randomAction.actionType
                log.debug("Action: $actionType")
                val chosenAction = when (actionType) {
                    AbstractActionType.SEND_INTENT -> chooseActionWithName(actionType, randomAction.extra, null, currentState, randomAction)
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
                    if (randomAction.attributeValuationMap == null) {
                        currentAbstractState.actionCount.remove(randomAction)
                    } else {
                        randomAction.attributeValuationMap!!.actionCount.remove(randomAction)
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

    private fun isTrapActivity(currentAbstractState: AbstractState) =
            currentAbstractState.window.classType == "com.oath.mobile.platform.phoenix.core.TrapActivity"
                    || currentAbstractState.window.classType == "com.yahoo.mobile.client.share.account.controller.activity.TrapsActivity"


    private fun getActionScores(currentAbstractState: AbstractState, allActions: List<AbstractAction>, actionScore: HashMap<AbstractAction, Double>, currentState: State<*>) {
        val windowWidgetFrequency = AbstractStateManager.instance.attrValSetsFrequency[currentAbstractState.window]!!
        val similarAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == currentAbstractState.window }
        val numboerOfAbstractStates = similarAbstractStates.size
        allActions.forEach { action ->
            val pair = computeActionPotentialScore(action, numboerOfAbstractStates, windowWidgetFrequency, currentAbstractState, similarAbstractStates)
            val widgetGroup = pair.first
            var actionPotentialScore = pair.second

            val actionCount = currentAbstractState.getActionCount(action)
            actionScore.put(action, action.getScore() * actionPotentialScore / actionCount)

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
            actionScore.put(action, action.getScore() * actionPotentialScore / (actionCount * 5))
        }
    }

    private fun computeActionPotentialScore2(action: AbstractAction, currentAbstractState: AbstractState): Pair<AttributeValuationMap?, Double> {
        val widgetGroup = action.attributeValuationMap
        var actionPotentialScore = 1.0

        val goToHomeEdges = atuaMF.DSTG.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.destination?.data?.window is Launcher
        }

        goToHomeEdges.forEach {
            if (!it.label.isImplicit) {
                actionPotentialScore /= (10 * it.label.interactions.size)
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
                    atuaMF.DSTG.edges(currentAbstractState).filter { edge ->
                        edge.label.abstractAction == action
                                && edge.source.data == edge.destination?.data
                                && edge.destination?.data !is VirtualAbstractState
                                && edge.destination?.data?.window !is OutOfApp
                    }
            deadEdges.forEach {
                if (it.label.isImplicit)
                    actionPotentialScore /= (5)
                if (!it.label.isImplicit)
                    actionPotentialScore /= (10 * it.label.interactions.size)

            }
        }

        return Pair(widgetGroup, actionPotentialScore)
    }

    private fun computeActionPotentialScore(action: AbstractAction, numboerOfAbstractStates: Int, windowWidgetFrequency: HashMap<AttributeValuationMap, Int>, currentAbstractState: AbstractState, similarAbstractStates: List<AbstractState>): Pair<AttributeValuationMap?, Double> {
        val widgetGroup = action.attributeValuationMap
        var actionPotentialScore = 1.0

        val goToHomeEdges = atuaMF.DSTG.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.destination?.data?.window is Launcher
        }
        goToHomeEdges.forEach {
            if (!it.label.isImplicit) {
                actionPotentialScore /= (10 * it.label.interactions.size)
            } else
                actionPotentialScore /= 2
        }
        val openEdges = atuaMF.DSTG.edges(currentAbstractState).filter { edge ->
            edge.label.abstractAction == action
                    && edge.source.data != edge.destination?.data
                    && edge.destination?.data !is VirtualAbstractState
                    && edge.destination?.data?.window !is OutOfApp
                    && edge.destination?.data?.window !is Launcher
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
                val visitedCount = atuaMF.abstractStateVisitCount[dest]!!
                val widgetGroupFrequences = AbstractStateManager.instance.attrValSetsFrequency[dest.window]!!
                val unexploredWidgetActions = dest.getUnExercisedActions(null,atuaMF).filterNot { it.attributeValuationMap == null }
                unexploredWidgetActions
                        .filterNot { unexploredWidgetAction.contains(it) }
                        .forEach {
                            unexploredWidgetAction.add(it)
                            if (!widgetGroupFrequences.containsKey(it.attributeValuationMap)) {
                                actionPotentialScore *= (1 + it.getScore() / (numberOfAbstractStates))
                            } else {
                                actionPotentialScore *= (1 + it.getScore() / widgetGroupFrequences[it.attributeValuationMap]!!.toDouble())
                            }
                        }
                actionPotentialScore = actionPotentialScore

                val liveEdge = atuaMF.DSTG.edges(dest).filter {
                    it.destination != it.source
                            && edge.destination?.data !is VirtualAbstractState
                            && edge.destination?.data?.window !is OutOfApp
                            && edge.destination?.data?.window !is Launcher
                            && it.label.isExplicit()
                }
                if (liveEdge.size > 0) {
                    actionPotentialScore *= (liveEdge.size)
                }
            }
        }


        val considerDeadEdges = true
        if (considerDeadEdges) {
            val deadEdges = similarAbstractStates.map {
                atuaMF.DSTG.edges(it).filter { edge ->
                    edge.label.abstractAction == action
                            && edge.source.data == edge.destination?.data
                            && edge.destination?.data !is VirtualAbstractState
                            && edge.destination?.data?.window !is OutOfApp
                }
            }.flatten()
            deadEdges.forEach {
                if (it.label.isImplicit)
                    actionPotentialScore /= (2)
                if (!it.label.isImplicit)
                    actionPotentialScore /= (10 * it.label.interactions.size)

            }
        }
        return Pair(widgetGroup, actionPotentialScore)
    }

    private fun exerciseUnexercisedWidgetAbstractActions(unexercisedActions: List<AbstractAction>, randomAction: AbstractAction?, currentAbstractState: AbstractState): AbstractAction? {
        var randomAction1 = randomAction
        randomAction1 = if (unexercisedActions.any { it.attributeValuationMap != null }) {
            // Swipe on widget should be executed by last
            val widgetActions = unexercisedActions.filter { it.attributeValuationMap != null }
            val nonWebViewActions = widgetActions.filterNot { it.attributeValuationMap!!.getClassName().contains("WebView") }
            val candidateActions = if (nonWebViewActions.isEmpty())
                ArrayList(widgetActions)
            else
                ArrayList(nonWebViewActions)

            //prioritize the less frequent widget
            val actionByScore = HashMap<AbstractAction, Double>()
            val windowWidgetFrequency = AbstractStateManager.instance.attrValSetsFrequency[currentAbstractState.window]!!
            candidateActions.forEach {
                var actionScore = it.getScore()
                val widgetGroup = it.attributeValuationMap!!
                if (windowWidgetFrequency.containsKey(widgetGroup)) {
                    actionByScore.put(it, actionScore.toDouble() / windowWidgetFrequency[widgetGroup]!!)
                } else {
                    actionByScore.put(it, actionScore.toDouble())
                }
            }

            if (actionByScore.isNotEmpty()) {
                val pb = ProbabilityDistribution<AbstractAction>(actionByScore)
                pb.getRandomVariable()
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


    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }
        var executedCount: Int = 0
        var instance: RandomExplorationTask? = null
        fun getInstance(regressionTestingMF: ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean,
                        randomScroll: Boolean = true,
                        maximumAttempt: Int = 1): RandomExplorationTask {
            if (instance == null) {
                instance = RandomExplorationTask(regressionTestingMF, atuaTestingStrategy,
                        delay, useCoordinateClicks, randomScroll, maximumAttempt)
            }
            return instance!!
        }
    }

}