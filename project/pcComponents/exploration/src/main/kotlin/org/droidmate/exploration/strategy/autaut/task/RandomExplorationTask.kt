package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.staticModel.Helper
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGActivityNode
import org.droidmate.exploration.modelFeatures.autaut.staticModel.WTGNode
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

class RandomExplorationTask constructor(
        regressionTestingMF: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean,
        private var randomScroll: Boolean,
        private var maximumAttempt: Int): AbstractStrategyTask(regressionTestingStrategy,regressionTestingMF,delay, useCoordinateClicks){
    private val MAX_ATTEMP_EACH_EXECUTION=20
    private var prevAbState: AbstractState?=null
    private val BACK_PROB = 0.1
    private val PRESSMENU_PROB = 0.2
    private val ROTATE_PROB = 0.05
    private val SWIPE_PROB = 0.5
    private val clickNavigationUpTask = ClickNavigationUpTask(regressionTestingMF, regressionTestingStrategy, delay, useCoordinateClicks)
    private val fillDataTask = FillTextInputTask.getInstance(regressionTestingMF,regressionTestingStrategy, delay, useCoordinateClicks)
    protected var goToTargetWindowTask: GoToTargetWindowTask? = null
    protected var openNavigationBarTask = OpenNavigationBarTask.getInstance(regressionTestingMF,regressionTestingStrategy,delay, useCoordinateClicks)
    var fillData = true
    private var dataFilled = false
    private var initialExerciseCount = -1
    private var currentExerciseCount = -1

    var backAction = true
    var isFullyExploration: Boolean = false

    private var lockedWindow: WTGNode? = null

    fun lockTargetWindow (window: WTGNode) {
        lockedWindow = window
        goToTargetWindowTask =  GoToTargetWindowTask(regressionTestingMF,regressionTestingStrategy,delay,useCoordinateClicks)
    }
    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (attemptCount >= maximumAttempt)
        {
            return true
        }
        if (initialExerciseCount < currentExerciseCount-1)
            return true
        return false
    }

    fun setMaximumAttempt( currentState: State<*>, attempt: Int){
        val inputFieldCount = currentState.widgets.filter { it.isInputField }.size
        val actionBasedAttemp = (regressionTestingMF.getAbstractState(currentState)?.getUnExercisedActions()?.size?:1)+inputFieldCount
        maximumAttempt = min(actionBasedAttemp,attempt+inputFieldCount)
    }

    fun setMaximumAttempt( attempt: Int){
        maximumAttempt = attempt
    }

    fun setAttempOnUnexercised(currentState: State<*>){
        maximumAttempt = min(regressionTestingMF.getAbstractState(currentState)?.unexercisedWidgetCount?:1,MAX_ATTEMP_EACH_EXECUTION)
    }

    override fun chooseRandomOption(currentState: State<*>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private var attemptCount = 0
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
        if (random.nextInt(100)/100.toDouble()<0.5)
        {
            visibleWidgets = currentState.visibleTargets
        }
        else
        {
            visibleWidgets = Helper.getVisibleInteractableWidgets(currentState)
        }

        if (visibleWidgets.isNotEmpty()) {
            return visibleWidgets
        }
        //when DM2 provides incorrect information
        return currentState.widgets.filterNot { it.isKeyboard}
    }

    override fun chooseAction(currentState: State<*>): ExplorationAction {
        executedCount++
        val currentAbstractState = regressionTestingMF.getAbstractState(currentState)!!
        if (isCameraOpening(currentState))
        {
            return dealWithCamera(currentState)
        }
        if (currentAbstractState.isOutOfApplication
                || Helper.getVisibleWidgets(currentState).find { it.resourceId == "android:id/floating_toolbar_menu_item_text" } != null) {
            log.info("App goes to an out of scope node.")
            log.info("Try press back.")
            return ExplorationAction.pressBack()
        }

        if (lockedWindow != null && goToTargetWindowTask != null) {
            if (lockedWindow != currentAbstractState.window) {
                // should go back to target Window

                //reset data filling
                dataFilled = false
                if (goToTargetWindowTask!!.isTaskEnd(currentState)) {
                    if (goToTargetWindowTask!!.isAvailable(currentState, lockedWindow!!)) {
                        goToTargetWindowTask!!.initialize(currentState)
                    }
                }
                return goToTargetWindowTask!!.chooseAction(currentState)
            }
        }

        if (currentState.visibleTargets.filter { it.isKeyboard }.isNotEmpty() && currentAbstractState.rotation == Rotation.LANDSCAPE) {
            // Need return to portrait
            return chooseActionWithName("RotateUI","",null,currentState)!!
        }


        if (currentState.visibleTargets.filter { it.isKeyboard }.isNotEmpty()
                && currentState.visibleTargets.filter { it.packageName==regressionTestingStrategy.eContext.apk.packageName}.isEmpty()) {
            val searchButtons = currentState.visibleTargets.filter { it.isKeyboard }.filter { it.contentDesc.toLowerCase().contains("search") }
            if (searchButtons.isNotEmpty())
            {
                if (random.nextBoolean()) {
                    dataFilled = false
                    val randomButton = searchButtons.random()
                    log.info("Widget: $random")
                    return randomButton.click()
                } else {
                    return GlobalAction(actionType = ActionType.CloseKeyboard)
                }
            }
            else
            {
                return GlobalAction(actionType = ActionType.CloseKeyboard)
            }
        }
        attemptCount++
        if (currentAbstractState.window is WTGActivityNode) {
            if (!regressionTestingMF.openNavigationCheck.contains(currentAbstractState)
                    && openNavigationBarTask.isAvailable(currentState)) {
                regressionTestingMF.openNavigationCheck.add(currentAbstractState)
                return openNavigationBarTask.chooseAction(currentState)
            }
        }
        if (dataFilled && !fillDataTask.isTaskEnd(currentState))
            return fillDataTask.chooseAction(currentState)

        if (!dataFilled && fillData && fillDataTask.isAvailable(currentState))
        {
            fillDataTask.initialize(currentState)
            dataFilled = true
            return fillDataTask.chooseAction(currentState)
        }

        val unexercisedActions = currentAbstractState.getUnExercisedActions().filter { !it.actionName.isTextInsert() }
        if (unexercisedActions.isNotEmpty()) {
            val randomAction = if (unexercisedActions.any { it.widgetGroup!=null}) {
                // Swipe on widget should be executed by last
                val widgetActions = unexercisedActions.filter { it.widgetGroup!=null}
                if (widgetActions.any { it.actionName != "Swipe" }) {
                    widgetActions.filterNot {it.actionName == "Swipe" }.random()
                } else {
                    widgetActions.random()
                }
            } else {
                unexercisedActions.random()
            }
            var chosenWidget: Widget? = null
            var isValidAction = true
            if (randomAction.widgetGroup!=null)
            {
                val candidates = randomAction.widgetGroup.getGUIWidgets(currentState)
                chosenWidget = if (candidates.isEmpty())
                    null
                else
                    candidates.random()
                if (chosenWidget==null)
                {
                    log.debug("No widget found")
                    // remove action
                    randomAction.widgetGroup.actionCount.remove(randomAction)
                    isValidAction = false
                } else {
                    log.info(" widget: $chosenWidget")

                }
            }
            if (isValidAction) {
                val actionType = randomAction.actionName
                log.debug("Action: $actionType")
                val chosenAction = when (actionType)
                {
                    "CallIntent" -> chooseActionWithName(actionType,randomAction.extra, null, currentState)
                    "RotateUI" -> chooseActionWithName(actionType,90,null,currentState)
                    else -> chooseActionWithName(actionType, randomAction.extra?:"", chosenWidget, currentState)
                }
                if (chosenAction != null)
                {
                    return chosenAction
                } else
                {
                    // this action should be removed from this abstract state
                    if (randomAction.widgetGroup==null) {
                        currentAbstractState.actionCount.remove(randomAction)
                    } else {
                        randomAction.widgetGroup.actionCount.remove(randomAction)
                    }
                }
            }
        }
        val executeSystemEvent = random.nextInt(100)/(100*1.0)
        if (executeSystemEvent < 0.05) {
//            val systemActions = ArrayList<EventType>()
//            systemActions.add(EventType.)
            if (regressionTestingMF.appRotationSupport)
                return chooseActionWithName("RotateUI",90,null,currentState)!!
//            else if ()
//            {
//                return ExplorationAction.minimizeMaximize()
//            }
        } else if (executeSystemEvent < 0.1)
        {
            if (haveOpenNavigationBar(currentState))
            {
                return clickOnOpenNavigation(currentState)
            }
            else
            {
                if (currentAbstractState.hasOptionsMenu)
                    return chooseActionWithName("PressMenu", null,null,currentState)!!
            }
        }
        else if (executeSystemEvent < 0.15)
        {
            return chooseActionWithName("PressMenu", null,null,currentState)!!
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
            return chooseActionWithName("Swipe", "",null,currentState)?:ExplorationAction.pressBack()

        }
//        if (regressionTestingMF.currentRotation!=0)
//        {
//            return ExplorationAction.rotate(360-regressionTestingMF.currentRotation)
//        }

        if (currentAbstractState != null)
        {
            prevAbState = currentAbstractState
        }



        val chosenWidgets = ArrayList<Widget>()
//        if (random.nextInt(100)/100.toDouble()< SWIPE_PROB )
//        {
//            val scrollableWidgets = currentState.widgets.filter {
//                it.isVisible && !it.isKeyboard
//                        && !it.isInputField && it.scrollable && it.childHashes.size>3 }
//            chosenWidgets.addAll(scrollableWidgets)
//            if (chosenWidgets.isNotEmpty())
//            {
//                var chosenWidget: Widget? = null
//                runBlocking {
//                    val candidates = getCandidates(chosenWidgets)
//                    chosenWidget = candidates.random()
//
//                }
//                return chosenWidget?.availableActions(delay, useCoordinateClicks)?.filter { it is Swipe }?.random()?:ExplorationAction.closeAndReturn()
//
//            }
//        }
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
            return chooseActionWithName(action,"", chosenWidget,currentState)?:ExplorationAction.pressBack().also {
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
            val randomAction = chooseActionWithName(actionList[randomIdx].name, "" ,chosenWidget, currentState)
            log.info("$randomAction")
            return randomAction?:ExplorationAction.pressBack().also { log.info("Action null -> PressBack") }
        }
        else
        {
            ExplorationTrace.widgetTargets.clear()
            return regressionTestingStrategy.eContext.navigateTo(chosenWidget, {it.click()})?:ExplorationAction.pressBack()
        }
    }



    override fun reset() {
        attemptCount = 0
        prevAbState=null
        isFullyExploration=false
        initialExerciseCount = -1
        currentExerciseCount = -1
        dataFilled = false
        fillDataTask.reset()
        lockedWindow = null
    }

    override fun initialize(currentState: State<*>) {
        reset()
        setMaximumAttempt(currentState,25)
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
        fun getInstance(regressionTestingMF: RegressionTestingMF,
                regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long,
                        useCoordinateClicks: Boolean,
                        randomScroll: Boolean = true,
                        maximumAttempt: Int = 1): RandomExplorationTask {
            if (instance == null) {
                instance = RandomExplorationTask(regressionTestingMF,regressionTestingStrategy,
                        delay, useCoordinateClicks, randomScroll, maximumAttempt)
            }
            return instance!!
        }
    }

}