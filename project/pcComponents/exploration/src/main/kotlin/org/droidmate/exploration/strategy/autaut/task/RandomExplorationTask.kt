package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.dstg.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper
import org.droidmate.exploration.modelFeatures.atua.ewtg.WindowManager
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Activity
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.helper.ProbabilityDistribution
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max

class RandomExplorationTask constructor(
        regressionTestingMF: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean,
        private var randomScroll: Boolean,
        private var maximumAttempt: Int) : AbstractStrategyTask(atuaTestingStrategy, regressionTestingMF, delay, useCoordinateClicks) {
    private val MAX_ATTEMP_EACH_EXECUTION = (5*atuaTestingStrategy.scaleFactor).toInt()
    private var prevAbState: AbstractState? = null
    private val BACK_PROB = 0.1
    private val PRESSMENU_PROB = 0.2
    private val ROTATE_PROB = 0.05
    private val SWIPE_PROB = 0.5
    private val clickNavigationUpTask = ClickNavigationUpTask(regressionTestingMF, atuaTestingStrategy, delay, useCoordinateClicks)
    private val fillDataTask = PrepareContextTask(regressionTestingMF, atuaTestingStrategy, delay, useCoordinateClicks)
    private var qlearningRunning = false
    private var qlearningSteps = 0
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
    var forcingEndTask: Boolean = false

    var lockedWindow: Window? = null
    var lastAction: AbstractAction? = null
    var isScrollToEnd = false

    val recentActions = ArrayList<AbstractAction>()

    init {
        reset()
    }
    override fun reset() {
        forcingEndTask = false
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
        qlearningRunning = false
        qlearningSteps = 0
        goToLockedWindowTask = null
    }

    override fun initialize(currentState: State<*>) {
        reset()
        setMaxiumAttempt(currentState, MAX_ATTEMP_EACH_EXECUTION)
    }

    fun lockTargetWindow(window: Window) {
        lockedWindow = window

    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (forcingEndTask)
            return true
        if (isCameraOpening(currentState))
            return false
        if (fillingData == true) {
            return false
        }
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (currentAbstractState.isOpeningKeyboard)
            return false
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

    fun setMaxiumAttempt(currentState: State<*>, minAttempt: Int) {
        val actionBasedAttempt = (atuaMF.getAbstractState(currentState)?.getUnExercisedActions(currentState,atuaMF)?.size
                ?: 1)
        maximumAttempt = max(actionBasedAttempt, minAttempt)
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
    var actionOnOutOfAppCount = 0
    override fun chooseAction(currentState: State<*>): ExplorationAction? {
        executedCount++
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (shouldRandomExplorationOutOfApp(currentAbstractState,currentState)) {
            actionOnOutOfAppCount = 0
        }
        val prevAbstractState = if (atuaMF.appPrevState != null)
            atuaMF.getAbstractState(atuaMF.appPrevState!!) ?: currentAbstractState
        else
            currentAbstractState
        if (goToLockedWindowTask != null) {
            // should go back to target Window
            //reset data filling
            if (!goToLockedWindowTask!!.isTaskEnd(currentState)) {
                if (recentGoToExploreState || lockedWindow != currentAbstractState.window)
                    return goToLockedWindowTask!!.chooseAction(currentState)
            }
        } else {
            if (isCameraOpening(currentState)) {
                return dealWithCamera(currentState)
            }
            if (lockedWindow != null
                    && lockedWindow != currentAbstractState.window) {
                dataFilled = false
                if (!currentAbstractState.isRequireRandomExploration() || actionOnOutOfAppCount >= 5) {

                    goToLockedWindowTask = GoToAnotherWindow(atuaTestingStrategy = atuaStrategy, autautMF = atuaMF, delay = delay, useCoordinateClicks = useCoordinateClicks)
                    if (goToLockedWindowTask!!.isAvailable(currentState, lockedWindow!!, true, false, false)) {
                        goToLockedWindowTask!!.initialize(currentState)
                        return goToLockedWindowTask!!.chooseAction(currentState)
                    }
                    else if (actionOnOutOfAppCount >= 11) {
                        return atuaStrategy.eContext.resetApp()
                    } else if (actionOnOutOfAppCount >= 10) {
                        return atuaStrategy.eContext.launchApp()
                    }
                    else if (actionOnOutOfAppCount >= 5|| !shouldRandomExplorationOutOfApp(currentAbstractState,currentState)){
                        return ExplorationAction.pressBack()
                    } else {
                        if (currentState.widgets.any { it.isKeyboard }) {
                            return GlobalAction(actionType = ActionType.CloseKeyboard)
                        }
                    }
                }
            } else {
                if (actionOnOutOfAppCount >= 11) {
                    return atuaStrategy.eContext.resetApp()
                }
                if (actionOnOutOfAppCount >= 10) {
                    return atuaStrategy.eContext.launchApp()
                }
                if (actionOnOutOfAppCount >= 5 || !shouldRandomExplorationOutOfApp(currentAbstractState,currentState)) {
                    return ExplorationAction.pressBack()
                }

            }
        }
        goToLockedWindowTask = null

        isClickedShutterButton = false
        val lastActionType = atuaStrategy.eContext.getLastActionType()
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
        var action: ExplorationAction? = null
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            action = fillDataTask.chooseAction(currentState)
        else
            fillingData = false
        if (action != null) {
            return action
        }
        if (dataFilled) {
            val userlikedInputs= currentAbstractState.attributeValuationMaps.filter { it.isUserLikeInput() }
            val userlikedInputsEWidget = userlikedInputs.map { currentAbstractState.EWTGWidgetMapping.get(it) }
            val previousStateWidgets = prevAbstractState.EWTGWidgetMapping.values
            if (userlikedInputsEWidget.isNotEmpty() && userlikedInputsEWidget.intersect(previousStateWidgets).isEmpty()) {
                dataFilled = false
            }
        }
        if (!dataFilled) {
            val lastAction = atuaStrategy.eContext.getLastAction()
            if (!lastAction.actionType.isTextInsert()) {
                if (fillDataTask.isAvailable(currentState, alwaysUseRandomInput)) {
                    fillDataTask.initialize(currentState)
                    fillingData = true
                    dataFilled = true
                    val action = fillDataTask.chooseAction(currentState)
                    if (action != null)
                        return action
                }
            } else {
                dataFilled = true
            }
        }

        attemptCount++
        if (currentAbstractState.window is OutOfApp) {
            actionOnOutOfAppCount += 1
        }
        var randomAction: AbstractAction? = null
        if (qlearningRunning) {
            qlearningSteps-=1
            if (qlearningSteps==0) {
                qlearningRunning = false
            }
            val bestCandidates = atuaMF.getCandidateAction(currentState, delay, useCoordinateClicks)
            if (bestCandidates.any { it.key==null }) {
                if (random.nextBoolean())
                    return bestCandidates.filter { it.key==null }.values.flatten().random()
            }
            val widgetActionCandidates = bestCandidates.filter { it.key!=null }
            val widgetCandidates = runBlocking {getCandidates(widgetActionCandidates.keys.toList() as List<Widget>)  }
            val selectedWidget = widgetCandidates.random()
            val selectedAction = widgetActionCandidates[selectedWidget]!!.random()
            ExplorationTrace.widgetTargets.add(selectedWidget)
            log.info("Widget: ${selectedWidget}")
            return selectedAction
        } else if (!isPureRandom && lastAction != null
                && lastAction!!.actionType == AbstractActionType.SWIPE
                && lastAction!!.attributeValuationMap!=null
                && listOf<String>("ListView","RecyclerView").any { lastAction!!.attributeValuationMap!!.getClassName().contains(it) } ) {
                if ( prevAbstractState != currentAbstractState
                        && prevAbstractState.window == currentAbstractState.window
                        && atuaMF.abstractStateVisitCount[currentAbstractState]!! == 1
                        /*&& isScrollToEnd
                        && lastAction!!.extra == "SwipeTillEnd"*/
                        && currentAbstractState.getAvailableActions().contains(lastAction!!)) {
                tryLastAction = 1
                maximumAttempt += 1
                randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }
            } /*else if (atuaMF.appPrevState!!.stateId != currentState.stateId
                    && atuaMF.stateVisitCount[currentState] == 1
                    && tryLastAction < MAX_TRY_LAST_ACTION
                    && currentAbstractState.getAvailableActions().contains(lastAction!!)
                    ) {
                tryLastAction += 1
                maximumAttempt += 1
                randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }
            }*/
        }
        if(randomAction==null) {
            tryLastAction = 0
            //val widgetActions = currentAbstractState.getAvailableActions().filter { it.widgetGroup != null }
            val unexercisedActions = currentAbstractState.getUnExercisedActions(currentState,atuaMF)
            val lowestPriorityActions = unexercisedActions.filter {
                it.attributeValuationMap == null
            }
            val priotizeActions = unexercisedActions.filterNot { lowestPriorityActions.contains(it) }
            if (priotizeActions.isNotEmpty()) {
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
                if (!isPureRandom && !currentAbstractState.isRequireRandomExploration() && !recentGoToExploreState ) {
                    var targetStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                        it.window == currentAbstractState.window
                                && it != currentAbstractState
                                && it !is VirtualAbstractState
                                && it.guiStates.isNotEmpty()
                                && it.attributeValuationMaps.isNotEmpty()
                                && it.getUnExercisedActions(null,atuaMF).filter { it.isWidgetAction() && !it.attributeValuationMap!!.getClassName().contains("WebView")}.isNotEmpty()
                    }.toHashSet()
                    if (targetStates.isNotEmpty()) {
                        goToLockedWindowTask = GoToAnotherWindow(atuaTestingStrategy = atuaStrategy, autautMF = atuaMF, delay = delay, useCoordinateClicks = useCoordinateClicks)
                        if (goToLockedWindowTask!!.isAvailable(currentState, currentAbstractState.window, true, false, true)) {
                            recentGoToExploreState = true
                            goToLockedWindowTask!!.initialize(currentState)
                            return goToLockedWindowTask!!.chooseAction(currentState)
                        }
                    }
                }
                goToLockedWindowTask = null
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
                    val unexploredWidgets = atuaMF.actionCount.getUnexploredWidget(currentState).filter {
                        /*it.clickable || it.scrollable || (!it.clickable && it.longClickable)*/
                        it.clickable
                    }.filterNot { Helper.isUserLikeInput(it) }
                    if (unexploredWidgets.isNotEmpty() /*|| isPureRandom*/ ) {
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
                    }  else {
                        val notUserlikeInputs = visibleTargets.filter {
                            it.clickable || it.longClickable || it.scrollable
                        }.filterNot { Helper.isUserLikeInput(it) }

                        val candidates =  if (notUserlikeInputs.isNotEmpty() && random.nextBoolean()) {
                            notUserlikeInputs
                        } else {
                            visibleTargets
                        }
                        if (random.nextDouble()<0.2) {
                            val chosenWidget = candidates.random()
                            log.info("Widget: $chosenWidget")
                            return doRandomActionOnWidget(chosenWidget, currentState)
                        }
                        val lessExercisedWidgets = runBlocking {
                            ArrayList(getCandidates(candidates
                            ))
                        }
                        if (lessExercisedWidgets.isNotEmpty()) {
                            val chosenWidget = lessExercisedWidgets.random()
                            log.info("Widget: $chosenWidget")
                            return doRandomActionOnWidget(chosenWidget, currentState)
                        } else {
                            if (candidates.isNotEmpty()) {
                                val chosenWidget = candidates.random()
                                log.info("Widget: $chosenWidget")
                                return doRandomActionOnWidget(chosenWidget, currentState)
                            } else {
                                val chosenWidget = currentState.visibleTargets.random()
                                log.info("Widget: $chosenWidget")
                                return doRandomActionOnWidget(chosenWidget, currentState)
                            }
                        }
                    }
                    /*else {
                        qlearningRunning = true
                        qlearningSteps = 5*atuaStrategy.scaleFactor.toInt()
                        qlearningSteps-=1
                        val bestCandidates = atuaMF.getCandidateAction(currentState, delay, useCoordinateClicks)
                        if (bestCandidates.any { it.key==null }) {
                            if (random.nextBoolean())
                                return bestCandidates.filter { it.key==null }.values.flatten().random()
                        }
                        val widgetActionCandidates = bestCandidates.filter { it.key!=null }
                        if (widgetActionCandidates.isNotEmpty()) {
                            val widgetCandidates = runBlocking { getCandidates(widgetActionCandidates.keys.toList() as List<Widget>) }
                            val selectedWidget = widgetCandidates.random()
                            val selectedAction = widgetActionCandidates[selectedWidget]!!.random()
                            ExplorationTrace.widgetTargets.add(selectedWidget)
                            log.info("Widget: ${selectedWidget}")
                            return selectedAction
                        } else {
                            return bestCandidates.values.random().random()
                        }
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

    private fun shouldRandomExplorationOutOfApp(currentAbstractState: AbstractState,currentState: State<*>): Boolean {
        if (isCameraOpening(currentState)) {
            return true
        }
        if (currentAbstractState.window is OutOfApp)
            return false
        if (currentAbstractState.window is Dialog) {
            if (WindowManager.instance.updatedModelWindows.filter { it is OutOfApp }.map { it.classType }.contains(currentAbstractState.activity) ){
                return false
            }
        }
        return true
    }

    private fun isTrapActivity(currentAbstractState: AbstractState) =
            currentAbstractState.window.classType == "com.oath.mobile.platform.phoenix.core.TrapActivity"
                    || currentAbstractState.window.classType == "com.yahoo.mobile.client.share.account.controller.activity.TrapsActivity"


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
            val windowWidgetFrequency = AbstractStateManager.INSTANCE.attrValSetsFrequency[currentAbstractState.window]!!
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