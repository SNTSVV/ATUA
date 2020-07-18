package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.staticModel.*
import org.droidmate.exploration.strategy.autaut.RegressionTestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList

open class GoToAnotherWindow protected constructor(
        regressionWatcher: AutAutMF,
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

    var destWindow: WTGNode? = null
    var useInputTargetWindow: Boolean = false

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
        log.debug(currentPath.toString())
        destWindow = currentPath!!.getFinalDestination().window
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
                // Try another path if current state is not target node
                /*if (currentAppState.window != targetWindow && regressionTestingMF.prevAbstractStateRefinement == 0) {
                    log.debug("Fail to reach expected node")
                    addIncorrectPath()
                }*/
                log.debug("Fail to reach expected node")
                addIncorrectPath()
                /*if (isAvailable(currentState)) {
                    initialize(currentState)
                    return false
                }*/
                return true
            }
            else
            {
                if (expectedNextAbState == currentPath!!.getFinalDestination()) {
                    return true
                }
                return false
            }
        } else {
            //something wrong, should end task
            return true
        }
    }

     fun isReachExpectedNode(currentState: State<*>):Boolean {
        val currentAbState = regressionTestingMF.getAbstractState(currentState)
        if (expectedNextAbState!!.window != currentAbState!!.window)
            return false
        if (expectedNextAbState == currentAbState)
        {
            return true
        }
        if (expectedNextAbState == currentPath!!.getFinalDestination() && expectedNextAbState!!.window == currentPath!!.getFinalDestination().window) {
            return true
        }
       /*  if (expectedNextAbState is VirtualAbstractState && expectedNextAbState!!.window == currentAbState.window)
             return true*/
         //if next action is feasible
         val nextEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
         if (nextEdge == null)
             return true
         val nextEvent = nextEdge.label
         if (nextEvent.abstractAction.actionName == AbstractActionType.ROTATE_UI.actionName)
             return false
         if (nextEvent.abstractAction.widgetGroup == null) {
             if (expectedNextAbState is VirtualAbstractState && expectedNextAbState!!.window == currentAbState.window)
                 return true
             if (expectedNextAbState!!.isOpeningKeyboard == currentAbState.isOpeningKeyboard
                     && expectedNextAbState!!.rotation == currentAbState.rotation
             ) {
                 return true
             }
             return false
         }
         val widget = nextEvent.abstractAction.widgetGroup
         if (regressionTestingMF.getRuntimeWidgets(widget, currentAbState, currentState).isNotEmpty())
             return true
         val staticWidget = expectedNextAbState!!.staticWidgetMapping[widget]
         if (staticWidget != null) {
             val availableWidgets = currentAbState!!.staticWidgetMapping.filter { it.value.intersect(staticWidget).isNotEmpty() }
             if (availableWidgets.isNotEmpty()) {
                 return true
             }
         }
         return false


    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root.data == regressionTestingMF.getAbstractState(currentState)!!
                && possiblePaths.size > 0) {//still in the source activity
            log.debug("Can change to another option.")
            return true
        }
        return false
    }

    override fun initialize(currentState: State<*>) {
        randomExplorationTask!!.fillingData=true
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

    open fun isAvailable(currentState: State<*>, destWindow: WTGNode): Boolean {
        log.info("Checking if there is any path to $destWindow")
        reset()
        this.destWindow = destWindow
        this.useInputTargetWindow = true
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        if (this.destWindow is WTGDialogNode || this.destWindow is WTGOptionsMenuNode) {
            val newTarget = WTGActivityNode.allNodes.find { it.classType == this.destWindow!!.classType || it.activityClass == this.destWindow!!.activityClass }
            if (newTarget == null)
                return false
            this.destWindow = newTarget
            initPossiblePaths(currentState)
            if (possiblePaths.size > 0) {
                return true
            }
        }
        return false
    }

    open protected fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.clear()
        if (useInputTargetWindow && destWindow!=null) {
            possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToWindow(currentState,destWindow!!))
        } else {
            possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToOtherWindows(currentState))
           /* if (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathToUnexploredState(currentState))
            }*/

        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val widgetGroup = currentEdge!!.label.abstractAction.widgetGroup
        if (widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            val currentAbstractState = regressionTestingMF.getAbstractState(currentState)!!
            val widgets = regressionTestingMF.getRuntimeWidgets(widgetGroup,currentAbstractState ,currentState)
            if (widgets.isEmpty()) {
                val staticWidget = prevAbState!!.staticWidgetMapping[widgetGroup]
                if (staticWidget == null)
                    return emptyList()
                val correspondentWidgetGroups = currentAbstractState.staticWidgetMapping.filter { it.value.intersect(staticWidget).isNotEmpty() }
                return correspondentWidgetGroups.map { regressionTestingMF.getRuntimeWidgets(it.key,currentAbstractState,currentState) }.flatten()
            }
            return widgets
        }
    }
    open fun increaseExecutedCount(){
        executedCount++
    }
    override fun chooseAction(currentState: State<*>): ExplorationAction {
        increaseExecutedCount()
        val prevEdge = currentEdge
        if(currentExtraTask!=null)
            return currentExtraTask!!.chooseAction(currentState)
        if (expectedNextAbState == null)
        {
            mainTaskFinished = true
            return randomExplorationTask.chooseAction(currentState)
        }
        var nextNode = expectedNextAbState
        val currentAbstractState = regressionTestingMF.getAbstractState(currentState)!!
        if (currentAbstractState.isOpeningKeyboard && !expectedNextAbState!!.isOpeningKeyboard) {
            return  GlobalAction(actionType = ActionType.CloseKeyboard)
        }
        if (!isFillingText)
        {
            prevState = currentState
            prevAbState = expectedNextAbState
        }
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask!!.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == regressionTestingMF.getAbstractState(currentState)!!.window) {
            if (!isFillingText)
            {
                currentEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            }
            if (currentEdge != null) {
                regressionTestingMF.setTargetNode(currentEdge!!.destination!!.data)
                nextNode = currentEdge!!.destination!!.data
                expectedNextAbState = nextNode
                log.info("Next expected node: ${nextNode}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                if (currentPath!!.edgeConditions.containsKey(currentEdge!!) && !isFillingText)
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
                        val actionData = if (currentEdge!!.label.data != null || currentEdge!!.label.data != "") {
                            currentEdge!!.label.data
                        } else
                        {
                            currentEdge!!.label.abstractAction.extra
                        }
                        log.info("Widget: $chosenWidget")

                        return chooseActionWithName(actionName, actionData, chosenWidget, currentState,currentEdge!!.label.abstractAction) ?: ExplorationAction.pressBack()
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

                                    ExplorationTrace.widgetTargets.clear()

                                }
                            }
                        } else {
                            //TODO find widgetGroup 's visible scrollable parent and try scroll until find the widget
                        }
                        if (prevEdge!=null) {
                            addIncorrectPath(prevEdge)
                        }
                        mainTaskFinished = true
                        log.debug("Can not get target widget, finish task.")
                        return randomExplorationTask!!.chooseAction(currentState)
                    }
                }
                else
                {
                    tryOpenNavigationBar = false
                    val action = currentEdge!!.label.abstractAction.actionName
                    //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
                    if (currentEdge!!.label.data != null && currentEdge!!.label.data != "") {
                        return chooseActionWithName(action,currentEdge!!.label.data, null, currentState,currentEdge!!.label.abstractAction) ?: ExplorationAction.pressBack()
                    } else
                    {
                        return chooseActionWithName(action,currentEdge!!.label.abstractAction.extra, null, currentState,currentEdge!!.label.abstractAction) ?: ExplorationAction.pressBack()
                    }
                }
            }
        }
        if (prevEdge!=null) {
            addIncorrectPath(prevEdge)
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    var scrollAttempt = 0

    protected fun addIncorrectPath() {
        if (currentEdge!=null)
            regressionTestingMF.addDisablePathFromState(currentPath!!,currentEdge!!)

    }

    protected fun addIncorrectPath( edge: Edge<AbstractState,AbstractInteraction>) {
        regressionTestingMF.addDisablePathFromState(currentPath!!,edge)
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherWindow? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: AutAutMF,
                        regressionTestingStrategy: RegressionTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherWindow {
            if (instance == null) {
                instance = GoToAnotherWindow(regressionWatcher, regressionTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}