package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.exploration.strategy.autaut.AutAutTestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList

open class GoToAnotherWindow constructor(
        autautMF: AutAutMF,
        autAutTestingStrategy: AutAutTestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : AbstractStrategyTask(autAutTestingStrategy, autautMF, delay, useCoordinateClicks) {

    private var tryOpenNavigationBar: Boolean = false
    private var tryScroll: Boolean = false
    protected var mainTaskFinished:Boolean = false
    protected var prevState: State<*>?=null
    protected var prevAbState: AbstractState?=null
    protected var randomExplorationTask: RandomExplorationTask = RandomExplorationTask(this.autautMF,autAutTestingStrategy,delay,useCoordinateClicks,true,1)

    var isFillingText: Boolean = false
    protected var currentEdge: Edge<AbstractState, AbstractTransition>?=null
    protected var expectedNextAbState: AbstractState?=null
    var currentPath: TransitionPath? = null
    val possiblePaths = ArrayList<TransitionPath>()

    var destWindow: Window? = null
    var useInputTargetWindow: Boolean = false
    var retryTimes: Int = 0
    var isTarget: Boolean = false

    val fillingDataActionList = Stack<ExplorationAction>()

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
        destWindow = currentPath!!.getFinalDestination().window
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root.data
        val destination = currentPath!!.getFinalDestination()
        log.debug("Try reaching ${destination.window}")
        log.debug(currentPath.toString())
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
        if (isFillingText)
            return false
        //if app reached the final destination
        val currentAppState = autautMF.getAbstractState(currentState)!!
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
                addIncorrectPath(currentAppState)
                /*if (!expectedNextAbState!!.isOutOfApplication && !expectedNextAbState!!.isRequestRuntimePermissionDialogBox
                ) {

                    if (!currentAppState.isOutOfApplication)
                        addIncorrectPath(currentAppState)
                }
                if (expectedNextAbState!!.isRequestRuntimePermissionDialogBox || expectedNextAbState!!.isOutOfApplication) {
                    //check current abstract is in the path
                    if (currentPath!!.getVertices().map { it.data }.contains(currentAppState)) {
                        expectedNextAbState == currentAppState
                        currentEdge = currentPath!!.edges().filter { it.destination?.data == expectedNextAbState!! }.firstOrNull()
                        return false
                    } else {
                        addIncorrectPath(currentAppState)
                    }
                }*/
                if (retryTimes <= 3*autautStrategy.budgetScale) {
                    retryTimes += 1
                    initPossiblePaths(currentState)
                    if (possiblePaths.isNotEmpty()) {
                        initialize(currentState)
                        return false
                    }
                }
                return true
            }
            else
            {
                if (expectedNextAbState == currentPath!!.getFinalDestination()) {
                    return true
                }
                if (destWindow == null && autautMF.getUnexploredWidget(currentState).isNotEmpty()) {
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
         var reached = false
         val currentAbState = autautMF.getAbstractState(currentState)

         var expectedAbstractState: AbstractState? = expectedNextAbState
         var lastTraverseEdge: Edge<AbstractState,AbstractTransition>? = currentEdge
         var isFirst = true
         while (expectedAbstractState != null) {

             if (isFirst) {
                 isFirst = false
             } else {
                 val traverseEdge = currentPath!!.edges(expectedAbstractState!!).firstOrNull()
                 //currentEdge = traverseEdge

                 if (traverseEdge == null) {
                     return false
                 } else {
                     expectedAbstractState = traverseEdge.destination!!.data
                     lastTraverseEdge = traverseEdge
                     //expectedNextAbState = expectedAbstractState
                 }
             }

             if (expectedAbstractState!!.window != currentAbState!!.window)
                 continue
             // The expected abstract state is the same window with current abstract state
             if (expectedAbstractState == currentAbState) {
                 reached = true
                 break
             }
             if (lastTraverseEdge!=null && lastTraverseEdge.label.isExplicit()) {
                 continue
             }
             if(expectedAbstractState!!.rotation != currentAbState.rotation)
                 continue
             if(expectedAbstractState!!.isOpeningKeyboard != currentAbState.isOpeningKeyboard)
                 continue
             /*  if (expectedNextAbState is VirtualAbstractState && expectedNextAbState!!.window == currentAbState.window)
         return true*/
             //if next action is feasible
             val nextEdge = currentPath!!.edges(expectedAbstractState!!).firstOrNull()
             if (nextEdge == null) {
                 if (destWindow!=null && autautStrategy.phaseStrategy.isTargetWindow(destWindow!!)) {
                     if (autautStrategy.phaseStrategy.isTargetState(currentState)) {
                         reached = true
                     } else {
                         reached = false
                     }
                 } else {
                     reached = true
                 }
                 break
             }
             val nextEvent = nextEdge.label
             if (nextEvent.abstractAction.attributeValuationSet == null) {
                 if (expectedAbstractState!!.isOpeningKeyboard == currentAbState.isOpeningKeyboard
                         && expectedAbstractState!!.rotation == currentAbState.rotation
                 ) {
                     reached = true
                     break
                 }
                 if (expectedAbstractState is VirtualAbstractState && expectedAbstractState.window == currentAbState.window)
                 {
                     reached = true
                     break
                 }
                 continue
             }
             val widget = nextEvent.abstractAction.attributeValuationSet
             if (autautMF.getRuntimeWidgets(widget, currentAbState, currentState).isNotEmpty()) {
                 reached = true
                 break
             }
             val staticWidget = expectedAbstractState.EWTGWidgetMapping[widget]
             if (staticWidget != null) {
                 val availableWidgets = currentAbState.EWTGWidgetMapping.filter { it.value.intersect(staticWidget).isNotEmpty() }
                 if (availableWidgets.isNotEmpty()) {
                     reached = true
                     break
                 }
             }
         }
         if (reached) {
             expectedNextAbState = expectedAbstractState
             currentEdge = lastTraverseEdge
         }
         return reached
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root.data == autautMF.getAbstractState(currentState)!!
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
        usingPressback = true
        destWindow = null
        currentPath = null
        useInputTargetWindow = false
        includeReset = false
        isExploration = false
    }

    override fun isAvailable(currentState: State<*>): Boolean {
        reset()
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }
        return false
    }

    open fun isAvailable(currentState: State<*>, destWindow: Window, usingPressback: Boolean, includeResetApp: Boolean, isExploration: Boolean): Boolean {
        log.info("Checking if there is any path to $destWindow")
        reset()
        this.usingPressback = usingPressback
        this.destWindow = destWindow
        this.useInputTargetWindow = true
        this.includeReset = includeResetApp
        this.isExploration = isExploration
        initPossiblePaths(currentState)
        if (possiblePaths.size > 0) {
            return true
        }

/*       if (this.destWindow is WTGDialogNode || this.destWindow is WTGOptionsMenuNode) {
            val newTarget = WTGActivityNode.allNodes.find { it.classType == this.destWindow!!.classType || it.activityClass == this.destWindow!!.activityClass }
            if (newTarget == null || abstractState.window == newTarget)
                return false
            if (AbstractStateManager.instance.ABSTRACT_STATES.filter { it.window == newTarget }.map { it.getUnExercisedActions(null).size }.any { it > 0 } )
            {
                this.destWindow = newTarget
                initPossiblePaths(currentState)
                if (possiblePaths.size > 0) {
                    return true
                }
            }
        }*/
        return false
    }

    var usingPressback = true
    var includeReset = true
    var isExploration = true
    open protected fun initPossiblePaths(currentState: State<*>) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
            PathFindingHelper.PathType.INCLUDE_INFERED
        else
                computeNextPathType(currentPath!!.pathType,includeReset)
        if (useInputTargetWindow && destWindow!=null) {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToWindowToExplore(currentState,destWindow!!,nextPathType,isExploration))
                if (nextPathType == PathFindingHelper.PathType.WTG ||
                        (!includeReset && nextPathType == PathFindingHelper.PathType.FOLLOW_TRACE)) {
                    break
                } else {
                    nextPathType = computeNextPathType(nextPathType,includeReset)
                }
            }

        } else {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToVisitedStates(currentState,nextPathType))
                if (nextPathType == PathFindingHelper.PathType.WTG ||
                        (!includeReset && nextPathType == PathFindingHelper.PathType.FOLLOW_TRACE)) {
                    break
                }else {
                    nextPathType = computeNextPathType(nextPathType,includeReset)
                }
            }

           /* if (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathToUnexploredState(currentState))
            }*/

        }
    }

     fun computeNextPathType(pathType: PathFindingHelper.PathType, includeResetApp: Boolean): PathFindingHelper.PathType {
        return when (pathType) {
            PathFindingHelper.PathType.INCLUDE_INFERED -> PathFindingHelper.PathType.FOLLOW_TRACE
            PathFindingHelper.PathType.FOLLOW_TRACE ->
                if (includeResetApp) PathFindingHelper.PathType.RESET else PathFindingHelper.PathType.INCLUDE_INFERED
            PathFindingHelper.PathType.RESET -> PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.WTG -> PathFindingHelper.PathType.INCLUDE_INFERED
            else -> PathFindingHelper.PathType.ANY
        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val widgetGroup = currentEdge!!.label.abstractAction.attributeValuationSet
        if (widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            val currentAbstractState = autautMF.getAbstractState(currentState)!!
            val widgets = autautMF.getRuntimeWidgets(widgetGroup,currentAbstractState ,currentState)
            if (widgets.isEmpty()) {
                val staticWidget = prevAbState!!.EWTGWidgetMapping[widgetGroup]
                if (staticWidget == null)
                    return emptyList()
                val correspondentWidgetGroups = currentAbstractState.EWTGWidgetMapping.filter { it.value.intersect(staticWidget).isNotEmpty() }
                return correspondentWidgetGroups.map { autautMF.getRuntimeWidgets(it.key,currentAbstractState,currentState) }.flatten()
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
        val currentAbstractState = autautMF.getAbstractState(currentState)!!
        if (currentAbstractState.isOpeningKeyboard && !expectedNextAbState!!.isOpeningKeyboard) {
            return  GlobalAction(actionType = ActionType.CloseKeyboard)
        }
        prevState = currentState
        if (isFillingText) {
            if (fillingDataActionList.isEmpty()) {
                isFillingText = false
                return executeCurrentEdgeAction(currentState, prevEdge, currentAbstractState)
            } else {
                return fillingDataActionList.pop()
            }
        }
        prevAbState = expectedNextAbState
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == autautMF.getAbstractState(currentState)!!.window) {
            currentEdge = currentPath!!.edges(expectedNextAbState!!).firstOrNull()
            if (currentEdge != null) {
                autautMF.setTargetNode(currentEdge!!.destination!!.data)
                nextNode = currentEdge!!.destination!!.data
                expectedNextAbState = nextNode
                log.info("Next expected node: ${nextNode}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                if (currentPath!!.edgeConditions.get(currentEdge!!)!!.isNotEmpty())
                {
                    val inputData = currentPath!!.edgeConditions[currentEdge!!]!!.random()
                    inputData.forEach {
                        val inputWidget = currentState.visibleTargets.find { w -> it.key.uid.equals(w.uid)
                                && it.key.xpath == w.xpath }
                        if (inputWidget != null) {
                            if (inputWidget.isInputField) {
                                if (inputWidget.text != it.value) {
                                    fillingDataActionList.add(inputWidget.setText(it.value,sendEnter = false))
                                }
                            } else if (inputWidget.checked.isEnabled()) {
                                if (inputWidget.checked.toString()!= it.value) {
                                    fillingDataActionList.add(inputWidget.click())
                                }
                            }
                        }
                        /*if (regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).isNotEmpty())
                        {
                            // val inputWidget = regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).random()

                        }*/
                    }
                    if (fillingDataActionList.isNotEmpty())
                    {
                        isFillingText = true
                    }
                }
                if (isFillingText) {
                    return fillingDataActionList.pop()
                }
                return executeCurrentEdgeAction(currentState, prevEdge, currentAbstractState)
            }
        }
        if (prevEdge!=null) {
            addIncorrectPath(prevEdge,currentAbstractState)
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    private fun executeCurrentEdgeAction(currentState: State<*>, prevEdge: Edge<AbstractState, AbstractTransition>?, currentAbstractState: AbstractState): ExplorationAction {
        if (currentEdge!!.label.abstractAction.actionType == AbstractActionType.PRESS_MENU) {
            return pressMenuOrClickMoreOption(currentState)
        }
        if (currentEdge!!.label.abstractAction.attributeValuationSet != null) {
            val widgets = chooseWidgets(currentState)
            if (widgets.isNotEmpty()) {
                tryOpenNavigationBar = false
                tryScroll = false
                val candidates = runBlocking { getCandidates(widgets) }
                val chosenWidget = candidates[random.nextInt(candidates.size)]
                val actionName = currentEdge!!.label.abstractAction.actionType
                val actionData = if (currentEdge!!.label.data != null && currentEdge!!.label.data != "") {
                    currentEdge!!.label.data
                } else {
                    currentEdge!!.label.abstractAction.extra
                }
                log.info("Widget: $chosenWidget")

                return chooseActionWithName(actionName, actionData, chosenWidget, currentState, currentEdge!!.label.abstractAction)
                        ?: ExplorationAction.pressBack()
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
                if (prevEdge != null) {
                    addIncorrectPath(prevEdge, currentAbstractState)
                }
                mainTaskFinished = true
                log.debug("Can not get target widget, finish task.")
                return randomExplorationTask!!.chooseAction(currentState)
            }
        } else {
            tryOpenNavigationBar = false
            val action = currentEdge!!.label.abstractAction.actionType
            //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
            if (currentEdge!!.label.data != null && currentEdge!!.label.data != "") {
                return chooseActionWithName(action, currentEdge!!.label.data, null, currentState, currentEdge!!.label.abstractAction)
                        ?: ExplorationAction.pressBack()
            } else {
                return chooseActionWithName(action, currentEdge!!.label.abstractAction.extra
                        ?: "", null, currentState, currentEdge!!.label.abstractAction) ?: ExplorationAction.pressBack()
            }
        }
    }

    var scrollAttempt = 0

    protected fun addIncorrectPath(currentAbstractState: AbstractState) {
        if (currentEdge!=null)
            PathFindingHelper.addDisablePathFromState(currentPath!!,currentEdge!!,currentAbstractState)

    }

    protected fun addIncorrectPath(edge: Edge<AbstractState,AbstractTransition>, currentAbstractState: AbstractState) {
        PathFindingHelper.addDisablePathFromState(currentPath!!, edge,currentAbstractState)
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherWindow? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: AutAutMF,
                        autAutTestingStrategy: AutAutTestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherWindow {
            if (instance == null) {
                instance = GoToAnotherWindow(regressionWatcher, autAutTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}