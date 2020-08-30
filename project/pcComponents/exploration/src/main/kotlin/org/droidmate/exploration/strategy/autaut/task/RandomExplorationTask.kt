package org.droidmate.exploration.strategy.autaut.task

import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode
import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractAction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
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
    protected var goToTargetWindowTask: GoToTargetWindowTask? = null
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
    private var lockedWindow: WTGNode? = null
    var lastAction: AbstractAction? = null
    var isScrollToEnd = false

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
    }

    override fun initialize(currentState: State<*>) {
        reset()
        setMaximumAttempt(currentState,MAX_ATTEMP_EACH_EXECUTION)
    }
    fun lockTargetWindow (window: WTGNode) {
        lockedWindow = window
        goToTargetWindowTask =  GoToTargetWindowTask(regressionTestingMF,autautStrategy,delay,useCoordinateClicks)
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (attemptCount >= maximumAttempt)
        {
            return true
        }
        if (isFullyExploration)
            return false
        if (lockedWindow != null)
            return false
        if (prevAbState == null)
            return false
        if (regressionTestingMF.getAbstractState(currentState)!!.window != prevAbState!!.window)
            return true
       /* if (initialExerciseCount < currentExerciseCount-1)
            return true*/
        return false
    }

    fun setMaximumAttempt( currentState: State<*>, attempt: Int){
        val inputFieldCount = currentState.widgets.filter { it.isInputField }.size
        val actionBasedAttemp = (regressionTestingMF.getAbstractState(currentState)?.getUnExercisedActions(currentState)?.size?:1)
        maximumAttempt = min(actionBasedAttemp,attempt)
    }

    fun setMaximumAttempt( attempt: Int){
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
        if (!isFullyExploration )
        {
            val unexercisedWidgets = regressionTestingMF.getLeastExerciseWidgets(currentState)
            if (unexercisedWidgets.isNotEmpty())
            {
                if (initialExerciseCount==-1)
                {
                    initialExerciseCount = unexercisedWidgets.entries.first().value.exerciseCount
                }
                currentExerciseCount = unexercisedWidgets.entries.first().value.exerciseCount
               /* if (unexercisedWidgets.size>2 &&
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
                }*/
                return unexercisedWidgets.map { it.key }.filter { !it.checked.isEnabled() }
            }
        }
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

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        if (reset) {
            reset = false
            return autautStrategy.eContext.resetApp()
        }
        val currentAbstractState = regressionTestingMF.getAbstractState(currentState)!!
        val prevAbstractState = if (regressionTestingMF.appPrevState!=null)
            regressionTestingMF.getAbstractState(regressionTestingMF.appPrevState!!)!!
        else
            currentAbstractState

        if (isCameraOpening(currentState)) {
            return dealWithCamera(currentState)
        }
        if (currentAbstractState.isOutOfApplication
                || Helper.getVisibleWidgets(currentState).find { it.resourceId == "android:id/floating_toolbar_menu_item_text" } != null) {

            // Some apps will pop up library activity. Press back is not enough.
            // give a chance to try random
            if (!currentState.widgets.any { it.packageName == regressionTestingMF.packageName }) {
                log.info("App goes to an out of scope node.")
                if (regressionTestingMF.abstractTransitionGraph.edges(currentAbstractState).any {
                            it.label.abstractAction.actionName == ActionType.PressBack.name
                                    && !(it.destination?.data?.isOutOfApplication?:false)
                        }) {
                    log.info("Try press back.")
                    return ExplorationAction.pressBack()
                }
            }
        }

        if (lockedWindow != null && goToTargetWindowTask != null) {
                if (lockedWindow != currentAbstractState.window) {
                // should go back to target Window

                //reset data filling
                dataFilled = false
                if (goToTargetWindowTask!!.isTaskEnd(currentState)) {
                    if (goToTargetWindowTask!!.isAvailable(currentState, lockedWindow!!)) {
                        goToTargetWindowTask!!.initialize(currentState)
                        return goToTargetWindowTask!!.chooseAction(currentState)
                    }
                } else
                    return goToTargetWindowTask!!.chooseAction(currentState)
            }
        }

        if (currentState.widgets.filter { it.isKeyboard }.isNotEmpty()
                && currentState.visibleTargets.filter { it.packageName == autautStrategy.eContext.apk.packageName }.isEmpty()) {
            val searchButtons = currentState.visibleTargets.filter { it.isKeyboard }.filter { it.contentDesc.toLowerCase().contains("search") }
            if (random.nextBoolean()) {
                return chooseActionWithName("RandomKeyboard", "", null, currentState, null)!!
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
                return chooseActionWithName("RandomKeyboard", "", null, currentState, null)!!
            }

        }

        if (currentState.widgets.filter { it.isKeyboard }.isNotEmpty()) {
            return GlobalAction(actionType = ActionType.CloseKeyboard)
        }

        if (currentAbstractState.window.activityClass == "com.oath.mobile.platform.phoenix.core.TrapActivity") {
            if (currentState.visibleTargets.any { it.text == "I agree" || it.text == "Save and continue" }) {
                return currentState.visibleTargets.find { it.text == "I agree"|| it.text == "Save and continue" }!!.click()
            }

        }
        if (currentAbstractState.rotation == Rotation.LANDSCAPE) {
            if (random.nextBoolean())
                return chooseActionWithName("RotateUI", "", null, currentState, null)!!
        }

        if (environmentChange) {
            if (!recentChangedSystemConfiguration) {
                recentChangedSystemConfiguration = true
                if (regressionTestingMF.havingInternetConfiguration(currentAbstractState.window)) {
                    if (random.nextInt(4) < 3)
                        return GlobalAction(ActionType.EnableData).also {
                            regressionTestingMF.internetStatus = true
                        }
                    else
                        return GlobalAction(ActionType.DisableData).also {
                            regressionTestingMF.internetStatus = false
                        }
                } else {
                    return GlobalAction(ActionType.EnableData).also {
                        regressionTestingMF.internetStatus = false
                    }
                }
            } else {
                if (isFullyExploration && regressionTestingMF.havingInternetConfiguration(currentAbstractState.window)) {
                    //20%
                    if (random.nextInt(4) == 0) {
                        if (random.nextInt(4) < 3)
                            return GlobalAction(ActionType.EnableData).also {
                                regressionTestingMF.internetStatus = true
                            }
                        else
                            return GlobalAction(ActionType.DisableData).also {
                                regressionTestingMF.internetStatus = false
                            }
                    }
                }
            }
        }

//        if (currentAbstractState.window is WTGActivityNode) {
//            if (!regressionTestingMF.openNavigationCheck.contains(currentAbstractState)
//                    && openNavigationBarTask.isAvailable(currentState)) {
//                regressionTestingMF.openNavigationCheck.add(currentAbstractState)
//                return openNavigationBarTask.chooseAction(currentState)
//            }
//        }
        if (fillingData && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)
        if (fillingData) {
            fillingData = false
            dataFilled = true
        }
        if (!dataFilled && fillDataTask.isAvailable(currentState)) {
            fillDataTask.initialize(currentState)
            fillingData = true
            return fillDataTask.chooseAction(currentState)
        }
        fillingData = false
        dataFilled = false
        attemptCount++
        var randomAction: AbstractAction? = null
        if (lastAction!=null
                && lastAction!!.actionName == "Swipe"
                && prevAbstractState != currentAbstractState
                && tryLastAction < MAX_TRY_LAST_ACTION
                /*&& isScrollToEnd
                && lastAction!!.extra == "SwipeTillEnd"*/
                && currentAbstractState.getAvailableActions().contains(lastAction!!)
        ) {
            tryLastAction += 1
            randomAction = currentAbstractState.getAvailableActions().find { it == lastAction }

        } else {
            tryLastAction = 0
            val widgetActions = currentAbstractState.getAvailableActions().filter { it.widgetGroup != null }
            val unexercisedActions = currentAbstractState.getUnExercisedActions(currentState)
            if (unexercisedActions.isNotEmpty()) {
                val executeAtLastActions = unexercisedActions.filter {
                    it.actionName.isPressBack()
                            || it.actionName == AbstractActionType.ROTATE_UI.actionName
                            || it.actionName == AbstractActionType.MINIMIZE_MAXIMIZE.actionName
                            || (it.widgetGroup != null &&
                            (it.widgetGroup.attributePath.isCheckable()
                                    || it.widgetGroup.attributePath.isInputField()
                            ))
                }
                if (unexercisedActions.filterNot { executeAtLastActions.contains(it) }.isNotEmpty()) {

                    val toExecuteActions = unexercisedActions.filterNot { executeAtLastActions.contains(it) }
                    randomAction = if (toExecuteActions.any { it.widgetGroup != null }) {
                        //Test: prioritize swipe action to show as many as possible widgets
                       /* val widgetActions = toExecuteActions.filter { it.widgetGroup != null }
                        if (widgetActions.any { it.actionName == "Swipe" }) {
                            widgetActions.filter { it.actionName == "Swipe" }.random()
                        } else {
                            widgetActions.random()
                        }*/

                        // Swipe on widget should be executed by last
                        val widgetActions = toExecuteActions.filter { it.widgetGroup != null }
                        val nonWebViewActions = widgetActions.filterNot { it.widgetGroup!!.attributePath.getClassName().contains("WebView") }
                        val candidateActions = if (nonWebViewActions.isEmpty())
                            widgetActions
                        else
                            nonWebViewActions
                        //widgetActions.random()

                        if (candidateActions.any { it.actionName != "Swipe" }) {
                            val notSwipeActions = candidateActions.filterNot { it.actionName == "Swipe" }
                            //prioritize


                            //prioritize the less frequent widget
                            val actionByWidgetFrequency = HashMap<AbstractAction,Int>()
                            val windowWidgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[currentAbstractState.window]!!
                            notSwipeActions.forEach {
                                val widgetGroup = it.widgetGroup!!
                                if (windowWidgetFrequency.containsKey(widgetGroup)) {
                                    actionByWidgetFrequency.put(it,windowWidgetFrequency[widgetGroup]!!)
                                } else {
                                    actionByWidgetFrequency.put(it,0)
                                }
                            }
                            val sortedActions = actionByWidgetFrequency.map { Pair<AbstractAction,Int>(it.key,it.value) }.sortedBy { it.second }
                            val lessFrequent = sortedActions.filter { it.second == sortedActions.first().second }
                            lessFrequent.random().first
                        } else {
                            candidateActions.random()
                        }

                        /*val clickActions = candidahteActions.filter { it.actionName.isClick() }
                        if (clickActions.isNotEmpty()) {
                            clickActions.random()
                        } else {
                            if (candidateActions.any { it.actionName != "Swipe" }) {
                                candidateActions.filterNot { it.actionName == "Swipe" }.random()
                            } else {
                                candidateActions.random()
                            }
                        }*/
                    } else {
                        toExecuteActions.random()
                    }
                    //randomAction = toExecuteActions.random()
                } else {
                    randomAction = if (unexercisedActions.any { it.widgetGroup != null }) {
                        // Swipe on widget should be executed by last
                        val widgetActions = unexercisedActions.filter { it.widgetGroup != null }
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
                //randomAction = unexercisedActions.random()
            } else {
                val executeSystemEvent = random.nextInt(100) / (100 * 1.0)
                if (executeSystemEvent < 0.1) {
                    randomAction = currentAbstractState.getAvailableActions().filter { it.widgetGroup == null }.random()
                    return chooseActionWithName(randomAction.actionName, randomAction.extra?:"", null, currentState, randomAction)
                            ?: ExplorationAction.pressBack()

                }
                val widgetActions = currentAbstractState.getAvailableActions().filter { it.widgetGroup != null }

                if (widgetActions.isNotEmpty()) {
                    val widgetGroupScore = HashMap<WidgetGroup,Double>()
                    val windowWidgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[currentAbstractState.window]!!
                    widgetActions.forEach {
                        val widgetGroup = it.widgetGroup!!
                        val actionCount = currentAbstractState.getActionCount(it)!!
                        if (windowWidgetFrequency.containsKey(widgetGroup)) {
                            val score = windowWidgetFrequency[widgetGroup]!!+actionCount.toDouble()
                            if (widgetGroupScore.containsKey(widgetGroup)) {
                                val oldScore = widgetGroupScore[widgetGroup]!!
                                widgetGroupScore.put(widgetGroup,oldScore+score)
                            } else {
                                widgetGroupScore.put(widgetGroup, score.toDouble())
                            }
                        } else {
                            if (widgetGroupScore.containsKey(widgetGroup)) {
                                val oldScore = widgetGroupScore[widgetGroup]!!
                                widgetGroupScore.put(widgetGroup,oldScore+actionCount)
                            } else {
                                widgetGroupScore.put(widgetGroup, actionCount.toDouble())
                            }
                        }
                    }
                    val sortedWidgetGroupScore = widgetGroupScore.map { Pair<WidgetGroup,Double>(it.key,it.value) }.sortedBy { it.second }
                    val lessFrequent = sortedWidgetGroupScore.filter { it.second == sortedWidgetGroupScore.first().second }
                    val randomWidgetGroup = lessFrequent.random().first
                    val widgetActionGroup = widgetActions.groupBy { it.widgetGroup }
                    //val randomWidgetGroup = widgetActionGroup.keys.random()
                    val actionGroup = widgetActionGroup.get(randomWidgetGroup)!!.groupBy { it.actionName }
                    val actionName = actionGroup.keys.random()
                    randomAction = actionGroup.get(actionName)!!.random()
                }
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
            if (randomAction.widgetGroup != null) {
                val candidates = randomAction!!.widgetGroup!!.getGUIWidgets(currentState)
                chosenWidget = if (candidates.isEmpty())
                    null
                else
                    candidates.random()
                if (chosenWidget == null) {
                    log.debug("No widget found")
                    // remove action
                    randomAction!!.widgetGroup!!.actionCount.remove(randomAction)
                    isValidAction = false
                } else {
                    log.info(" widget: $chosenWidget")

                }
            }
            if (isValidAction) {
                val actionType = randomAction.actionName
                log.debug("Action: $actionType")
                val chosenAction = when (actionType) {
                    "CallIntent" -> chooseActionWithName(actionType, randomAction.extra, null, currentState, randomAction)
                    "RotateUI" -> chooseActionWithName(actionType, 90, null, currentState, randomAction)
                    else -> chooseActionWithName(actionType, randomAction.extra
                            ?: "", chosenWidget, currentState, randomAction)
                }
                if (chosenAction != null) {
                    return chosenAction.also {
                        if (currentAbstractState != null) {
                            prevAbState = currentAbstractState
                        }
                    }
                } else {
                    // this action should be removed from this abstract state
                    if (randomAction.widgetGroup == null) {
                        currentAbstractState.actionCount.remove(randomAction)
                    } else {
                        randomAction!!.widgetGroup!!.actionCount.remove(randomAction)
                    }
                    return chooseAction(currentState)
                }
            }
        }

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

        val chosenWidgets = ArrayList<Widget>()
        chosenWidgets.addAll(chooseWidgets(currentState))
        //choose randomly 1 widget
        if (chosenWidgets.isEmpty())
            return ExplorationAction.pressBack().also {
                log.info("Empty widgets --> PressBack")
            }
        val candidates = runBlocking { getCandidates(chosenWidgets)}

        val chosenWidget = if (candidates.isEmpty())
                chosenWidgets.random()
        else
            candidates.random()
        log.info("Widget: $chosenWidget")
        if (chosenWidget.className.contains("ListView") ||
                chosenWidget.className.contains("RecyclerView")
                || chosenWidget.className.contains("Gallery"))
        {
            var action: String
            val actionPb = random.nextInt(100)*1.0/100
            if (actionPb<0.33)
            {
                action = "Swipe"
            }
            else if (actionPb<0.66)
            {
                action = "ItemLongClick"
            }
            else
            {
                action = "ItemClick"
            }
            return chooseActionWithName(action,"", chosenWidget,currentState,null)?:ExplorationAction.pressBack().also {
                log.info("Cannot get itemClick for ListView --> PressBack")
            }
        }

        var actionList = ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks))
        val pb = random.nextInt(100)/100.toDouble()
        if (!chosenWidget.clickable && chosenWidget.scrollable)
        {
            actionList.removeIf { it !is Swipe }
        }
        else
        {
            if (pb < 0.5) {
                // Remove LongClick and Scroll
                actionList.removeIf { it is Swipe || it is LongClick || it is LongClickEvent }
            }
        }
        if (actionList.isNotEmpty())
        {
            val maxVal = actionList.size

            assert(maxVal > 0) { "No actions can be performed on the widget $chosenWidget" }

            val randomIdx = random.nextInt(maxVal)
            val randomAction = chooseActionWithName(actionList[randomIdx].name, "" ,chosenWidget, currentState,null)
            log.info("$randomAction")
            return randomAction?:ExplorationAction.pressBack().also { log.info("Action null -> PressBack") }
        }
        else
        {
            ExplorationTrace.widgetTargets.clear()
            return autautStrategy.eContext.navigateTo(chosenWidget, {it.click()})?:ExplorationAction.pressBack()
        }
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