package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList

open class GoToAnotherWindow protected constructor(
         regressionWatcher: RegressionTestingMF,
        regressionTestingStrategy: RegressionTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : AbstractStrategyTask(regressionTestingStrategy, regressionWatcher, delay, useCoordinateClicks) {

    private var tryOpenNavigationBar: Boolean = false
    private var tryScroll: Boolean = false
    protected var mainTaskFinished:Boolean = false
    protected var prevState: State<*>?=null
    protected var prevAbState: AbstractState?=null
    protected var randomExplorationTask: RandomExplorationTask = RandomExplorationTask(regressionTestingMF,regressionTestingStrategy,delay,useCoordinateClicks,true,1)

    protected var isFillingText: Boolean = false
    protected var currentEdge: Edge<AbstractState, AbstractInteraction>?=null
    protected var expectedNextAbState: AbstractState?=null
    protected var currentPath: TransitionPath? = null
    protected val possiblePaths = ArrayList<TransitionPath>()

    var targetWindow: WTGNode? = null
    var useInputTargetWindow: Boolean = false

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root.data
        val destination = currentPath!!.getFinalDestination()
        log.debug("Try to reach ${destination.window}")
        mainTaskFinished = false
        isFillingText = false
        tryOpenNavigationBar = false
        tryScroll = false
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (mainTaskFinished)
            return true
        if (currentPath == null)
            return true
        //if app reached the final destination
        val currentAppState = regressionTestingMF.getAbstractState(currentState)!!
        if (currentAppState == currentPath!!.getFinalDestination())
        {
            return true
        }
        //if currentNode is expectedNextNode
        if (expectedNextAbState!=null) {
            if (!isReachExpectedNode(currentState)) {
                log.debug("Fail to reach expected node")
                addIncorrectPath()
                // Try another path if current state is not target node
                if (currentAppState.window != targetWindow) {
                    if (isAvailable(currentState)) {
                        initialize(currentState)
                        return false
                    }
                }
                return true
            }
            else
            {
                return false
            }
        }else
        {
            //something wrong, should end task
            return true
        }
    }

    private fun isReachExpectedNode(currentState: State<*>):Boolean {
        val currentAbState = regressionTestingMF.getAbstractState(currentState)
        if (expectedNextAbState!!.window != currentAbState!!.window)
            return false
        if (expectedNextAbState is VirtualAbstractState) {
            return true
        }
        if (expectedNextAbState == currentAbState)
        {
            return true
        }
        else
        {
            //if next action is feasible
            val nextEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            if (nextEdge == null)
                return false
            val nextEvent = nextEdge.label
            if (nextEvent.abstractAction.widgetGroup == null) {
                if (expectedNextAbState!!.isOpeningKeyboard == currentAbState.isOpeningKeyboard)
                    return true
                return false
            }
              
            val widget = nextEvent.abstractAction.widgetGroup
            if (regressionTestingMF.getRuntimeWidgets(widget, nextEdge.source.data, currentState).isNotEmpty())
                return true
            return false
        }


    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root.data.window == regressionTestingMF.getAbstractState(currentState)!!.window
                && possiblePaths.size > 0) {//still in the source activity
            log.debug("Can change to another option.")
            return true
        }
        return false
    }

    override fun initialize(currentState: State<*>) {

        randomExplorationTask!!.fillData=true
        randomExplorationTask!!.backAction=true
        chooseRandomOption(currentState)
    }

    override fun reset() {
        possiblePaths.clear()
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }

    open fun isAvailable(currentState: State<*>, targetWindow: WTGNode): Boolean {
        log.info("Checking if there is any path to $targetWindow")
        reset()
        this.targetWindow = targetWindow
        this.useInputTargetWindow = true
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }

    open protected fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.clear()
        if (useInputTargetWindow && targetWindow!=null) {
            possiblePaths.addAll(regressionTestingStrategy.phaseStrategy.getPathsToWindow(currentState,targetWindow!!))
        } else {
            possiblePaths.addAll(regressionTestingStrategy.phaseStrategy.getPathsToOtherWindows(currentState))
        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        if (currentEdge!!.label.abstractAction.widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            return regressionTestingMF.getRuntimeWidgets(currentEdge!!.label.abstractAction.widgetGroup!!,currentEdge!!.source.data ,currentState)
        }
    }
    open fun increaseExecutedCount(){
        executedCount++
    }
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        increaseExecutedCount()
        if(currentExtraTask!=null)
            return currentExtraTask!!.chooseAction(currentState)
        var nextNode = expectedNextAbState
        if (!isFillingText)
        {
            prevState = currentState
            prevAbState = expectedNextAbState
        }
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask!!.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination().window}")
        if (expectedNextAbState!!.activity == regressionTestingMF.getAbstractState(currentState)!!.activity) {
            if (!isFillingText)
            {
                currentEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            }
            if (currentEdge != null) {
                regressionTestingMF.setTargetNode(currentEdge!!.destination!!.data)
                nextNode = currentEdge!!.destination!!.data
                expectedNextAbState = nextNode
                log.info("Next expected node: ${nextNode.window}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                if (currentPath!!.edgeConditions.containsKey(currentEdge!!))
                {
                    val actionList = ArrayList<ExplorationAction>()
                    val textInputData = currentPath!!.edgeConditions[currentEdge!!]!!.filter { it.key.attributePath.isInputField() }
                    textInputData.forEach {
                        if (regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).isNotEmpty())
                        {
                            val textInputWidget = regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).random()
                            if (textInputWidget.text!=it.value)
                            {
                                actionList.add(textInputWidget.setText(it.value))
                            }
                        }
                    }
                    if (actionList.isNotEmpty())
                    {
                        isFillingText = true
                        return ActionQueue(actionList,0)
                    }
                }
                isFillingText = false
                if (currentEdge!!.label.abstractAction.actionName.isPressMenu()) {
                    return pressMenuOrClickMoreOption(currentState)
                }
                if (currentEdge!!.label.abstractAction.widgetGroup != null) {
                    val widgets = chooseWidgets(currentState)
                    if (widgets.isNotEmpty()) {
                        tryOpenNavigationBar = false
                        tryScroll = false
                        val candidates = runBlocking { getCandidates(widgets) }
                        val chosenWidget = candidates[random.nextInt(candidates.size)]
                        val actionName = currentEdge!!.label.abstractAction.actionName
                        val actionData = currentEdge!!.label.data
                        return chooseActionWithName(actionName, actionData, chosenWidget, currentState) ?: ExplorationAction.pressBack()
                    } else {
                        // process for some special case
                        // if the current state in the path is VirtualAbstractState
                        expectedNextAbState = prevAbState
                        if (expectedNextAbState is VirtualAbstractState) {
                            //1. if the window has a drawer layout, should try open navigation bar
                            if (!tryOpenNavigationBar && haveOpenNavigationBar(currentState)) {
                                tryOpenNavigationBar = true
                                return clickOnOpenNavigation(currentState)
                            } else {
                                //TODO fix null pointer
                                val scrollableWidgets = currentState.widgets.filter { it.scrollable }
                                if (scrollableWidgets.isNotEmpty()) {
                                    if (!tryScroll) {
                                        scrollAttempt = scrollableWidgets.size
                                        tryScroll = true
                                    }

                                    val scrollActions = scrollableWidgets.random().availableActions(delay, useCoordinateClicks).filter {
                                        it is Swipe
                                                && it.stepSize > 0
                                    }
                                    if (tryScroll && scrollAttempt > 0 && scrollActions.isNotEmpty()) {
                                        scrollAttempt--
                                        return scrollActions.random()

                                    }
                                }
                            }
                        }
                        addIncorrectPath()
                        if (hasAnotherOption(currentState)) {
                            chooseRandomOption(currentState)
                            return chooseAction(currentState)
                        }
                        else
                        {
                            log.debug("Try all options but can not get any widget, finish task.")
                            return randomExplorationTask!!.chooseAction(currentState)
                        }
                    }
                }
                else
                {
                    tryOpenNavigationBar = false
                    val action = currentEdge!!.label.abstractAction.actionName
                    //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
                    if (action == "CallIntent")
                        return chooseActionWithName(action,currentEdge!!.label.data, null, currentState) ?: ExplorationAction.pressBack()
                    else {
                        return chooseActionWithName(action, "", null, currentState) ?: ExplorationAction.pressBack()
                    }
                }
            }
        }
        if(hasAnotherOption(currentState))
        {
            chooseRandomOption(currentState)
            return chooseAction(currentState)
        }
        return randomExplorationTask!!.chooseAction(currentState)
    }

    var scrollAttempt = 0

    protected fun addIncorrectPath() {
        if (currentEdge!=null)
            regressionTestingMF.addDisablePathFromState(currentPath!!,currentEdge!!)

    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherWindow? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: RegressionTestingMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherWindow {
            if (instance == null) {
                instance = GoToAnotherWindow(regressionWatcher, regressionTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}