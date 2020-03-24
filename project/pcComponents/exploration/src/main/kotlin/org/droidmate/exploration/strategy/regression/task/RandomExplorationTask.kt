package org.droidmate.exploration.strategy.regression.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.regression.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.regression.staticModel.Helper
import org.droidmate.exploration.strategy.regression.RegressionTestingStrategy
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
    var backAction = true
    var isFullyExploration: Boolean = false
    var fillData = true
    var recentFillData = false
    var initialExerciseCount = -1
    var currentExerciseCount = -1
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
        maximumAttempt = min(currentState.actionableWidgets.size,attempt)
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
                return unexercisedWidgets.map { it.key }
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
        attemptCount++
        val currentAbstractState = regressionTestingMF.getAbstractState(currentState)
        val executeSystemEvent = random.nextInt(100)/(100*1.0)
        if (executeSystemEvent < 0.05) {
//            val systemActions = ArrayList<EventType>()
//            systemActions.add(EventType.)
            return chooseActionWithName("RotateUI",90,null,currentState)!!
//            else if ()
//            {
//                return ExplorationAction.minimizeMaximize()
//            }
        } else if (executeSystemEvent < 0.1)
        {
            if (haveOpenNavigationBar(currentState))
            {
                val openNavigationWidget = currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") }!!
                return chooseActionWithName("Click",null,openNavigationWidget,currentState)!!
            }
            else
            {
                return chooseActionWithName("PressMenu", null,null,currentState)!!
            }
        }
        else if (executeSystemEvent < 0.15)
        {
            return chooseActionWithName("PressMenu", null,null,currentState)!!
        }else if (executeSystemEvent < 0.20)
        {
            if (!regressionTestingMF.isPressBackCanGoToHomescreen(currentState))
            {
                log.debug("Randomly back")
                return ExplorationAction.closeAndReturn()
            }
            if(clickNavigationUpTask.isAvailable(currentState))
            {
                return clickNavigationUpTask.chooseAction(currentState)
            }
        }
//        if (regressionTestingMF.currentRotation!=0)
//        {
//            return ExplorationAction.rotate(360-regressionTestingMF.currentRotation)
//        }
        if (prevAbState != regressionTestingMF.getAbstractState(currentState))
        {
            recentFillData = false
        }
        if (!recentFillData && fillData && fillDataTask.isAvailable(currentState))
        {
            fillDataTask.initialize(currentState)
            recentFillData = true
            return fillDataTask.chooseAction(currentState)
        }
        val chosenWidgets = ArrayList<Widget>()
        if (currentAbstractState != null)
        {
            prevAbState = currentAbstractState
        }
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
            return ExplorationAction.closeAndReturn().also {
                log.info("Empty widgets --> PressBack")
            }
        val candidates = runBlocking { getCandidates(chosenWidgets)}
        val chosenWidget = candidates[random.nextInt(candidates.size)]
        log.debug("Choose Action for Widget: $chosenWidget")
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
            return chooseActionWithName(action,"", chosenWidget,currentState)?:ExplorationAction.closeAndReturn().also {
                log.info("Cannot get itemClick for ListView --> PressBack")
            }
        }
        var actionList: List<ExplorationAction>
        if (!chosenWidget.clickable && chosenWidget.scrollable)
        {
            actionList = chosenWidget.availableActions(delay, useCoordinateClicks).filter { it is Swipe }
        }
        else
        {
            if (random.nextInt(100)<50 && chosenWidget.longClickable)
            {
                actionList = chosenWidget.availableActions(delay,useCoordinateClicks).filterNot {it is Swipe}.filter {
                    it is LongClick || it is LongClickEvent
                }
            }
            else
            {
                actionList = chosenWidget.availableActions(delay,useCoordinateClicks).filterNot {it is Swipe}.filter {
                    it is Click || it is ClickEvent
                }
            }


        }
        if (actionList.isNotEmpty())
        {
            val maxVal = actionList.size

            assert(maxVal > 0) { "No actions can be performed on the widget $chosenWidget" }

            val randomIdx = random.nextInt(maxVal)
            val randomAction = chooseActionWithName(actionList[randomIdx].name, "" ,chosenWidget, currentState)
            log.info("$randomAction")
            return randomAction?:ExplorationAction.closeAndReturn().also { log.info("Action null -> PressBack") }
        }
        else
        {
            return regressionTestingStrategy.eContext.navigateTo(chosenWidget, {it.click()})?:ExplorationAction.closeAndReturn()
        }
    }

    private fun haveOpenNavigationBar(currentState: State<*>): Boolean {
        if (currentState.widgets.filter { it.isVisible }.find { it.contentDesc.contains("Open navigation") } != null)
        {
            return true
        }
        return false
    }

    override fun reset() {
        attemptCount = 0
        prevAbState=null
        isFullyExploration=false
        initialExerciseCount = -1
        currentExerciseCount = -1
        recentFillData = false
    }

    override fun initialize(currentState: State<*>) {
        reset()
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
       return false
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