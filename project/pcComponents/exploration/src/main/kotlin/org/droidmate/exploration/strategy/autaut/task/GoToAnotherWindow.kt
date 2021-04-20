package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.collections.ArrayList

open class GoToAnotherWindow constructor(
        autautMF: ATUAMF,
        atuaTestingStrategy: ATUATestingStrategy,
        delay: Long, useCoordinateClicks: Boolean) : AbstractStrategyTask(atuaTestingStrategy, autautMF, delay, useCoordinateClicks) {

    private var tryOpenNavigationBar: Boolean = false
    private var tryScroll: Boolean = false
    protected var mainTaskFinished:Boolean = false
    protected var prevState: State<*>?=null
    protected var prevAbState: AbstractState?=null
    protected var randomExplorationTask: RandomExplorationTask = RandomExplorationTask(this.autautMF,atuaTestingStrategy,delay,useCoordinateClicks,true,1)

    var isFillingText: Boolean = false
    protected var currentEdge: AbstractTransition?=null
    protected var expectedNextAbState: AbstractState?=null
    var currentPath: TransitionPath? = null
    val possiblePaths = ArrayList<TransitionPath>()
    var pathTraverser: PathTraverser? = null

    var destWindow: Window? = null
    var useInputTargetWindow: Boolean = false
    var retryTimes: Int = 0
    var isTarget: Boolean = false

    val fillingDataActionList = Stack<ExplorationAction>()

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
        pathTraverser = PathTraverser(currentPath!!)
        destWindow = currentPath!!.getFinalDestination().window
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root
        val destination = currentPath!!.getFinalDestination()
        mainTaskFinished = false
        isFillingText = false
        tryOpenNavigationBar = false
        tryScroll = false
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (autautMF.prevAbstractStateRefinement>0)
            return true
        if (pathTraverser==null)
            return true
        if (currentEdge == null)
            return true
        if (mainTaskFinished)
            return true
        if (currentPath == null)
            return true
        if (isFillingText)
            return false
        //if app reached the final destination
        val currentAppState = autautMF.getAbstractState(currentState)!!
        if (pathTraverser!!.finalStateAchieved()) {
            if (currentAppState == currentPath!!.getFinalDestination()) {
                return true
            }
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
                if (pathTraverser!!.finalStateAchieved() && currentPath!!.destination.window == currentAppState.window)
                    return true
                if (retryTimes <= 3*autautStrategy.budgetScale) {
                    //TODO check currentPath is valid
                    if (!PathFindingHelper.allAvailableTransitionPaths.values.any {it.contains(pathTraverser!!.transitionPath)}) {
                        initPossiblePaths(currentState,true)
                    } else {
                        retryTimes += 1
                        initPossiblePaths(currentState)
                    }
                    if (possiblePaths.isNotEmpty()) {
                        initialize(currentState)
                        return false
                    }
                }
                return true
            }
            else
            {
                expectedNextAbState = pathTraverser!!.getPrevTransition()!!.dest
                if (pathTraverser!!.finalStateAchieved()) {
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
         val currentAbState = autautMF.getAbstractState(currentState)!!
         val expectedAbstractState = pathTraverser!!.getPrevTransition()!!.dest
         if (expectedAbstractState == currentAbState)
             return true
         if (!PathFindingHelper.allAvailableTransitionPaths.values.any {it.contains(pathTraverser!!.transitionPath)}) {
             return false
         }
         if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedAbstractState)) {
             val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                 it.hashCode == expectedAbstractState!!.hashCode
             }
             if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                 return true
             }
         }

         if (pathTraverser!!.finalStateAchieved()) {
             if (pathTraverser!!.getPrevTransition()!!.dest is VirtualAbstractState) {
                 return true
             }
             return false
         }
         while (!pathTraverser!!.finalStateAchieved()) {
             val currentTransition = pathTraverser!!.getPrevTransition()
             if (currentTransition == null)
                 break
             val expectedAbstractState = currentTransition.dest

             if (expectedAbstractState!!.window != currentAbState!!.window) {
                 pathTraverser!!.getNextTransition()
                 continue
             }

             // The expected abstract state is the same window with current abstract state
             if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedAbstractState)) {
                 val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                     it.hashCode == expectedAbstractState!!.hashCode
                 }
                 if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                     reached = true
                     break
                 }
             }
             if (expectedAbstractState !is VirtualAbstractState) {
                 if(expectedAbstractState!!.rotation != currentAbState.rotation) {
                     pathTraverser!!.getNextTransition()
                     continue
                 }
                 if(expectedAbstractState!!.isOpeningKeyboard != currentAbState.isOpeningKeyboard) {
                     pathTraverser!!.getNextTransition()
                     continue
                 }
             }
             val nextTransition = pathTraverser!!.transitionPath.path[pathTraverser!!.latestEdgeId!!+1]
             if (nextTransition!=null) {
                 if (!nextTransition.abstractAction.isWidgetAction()) {
                     reached = true
                     break
                 } else {
                     val avm = nextTransition.abstractAction.attributeValuationMap!!
                     if (autautMF.getRuntimeWidgets(avm, currentAbState, currentState).isNotEmpty()) {
                         reached = true
                         break
                     }
                     val staticWidget = expectedAbstractState.EWTGWidgetMapping[avm]
                     if (staticWidget != null) {
                         val availableWidgets = currentAbState.EWTGWidgetMapping.filter { it.value == staticWidget }
                         if (availableWidgets.isNotEmpty()) {
                             reached = true
                             break
                         }
                     }
                 }
             }
             pathTraverser!!.getNextTransition()
         }
         if (reached) {
             expectedNextAbState = expectedAbstractState
             currentEdge = pathTraverser!!.getPrevTransition()
         }
         return reached
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root == autautMF.getAbstractState(currentState)!!
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
        isExploration = true
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

    open protected fun initPossiblePaths(currentState: State<*>, continueMode:Boolean = false) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
            PathFindingHelper.PathType.INCLUDE_INFERED
        else if (continueMode)
                PathFindingHelper.PathType.NORMAL
        else
            computeNextPathType(currentPath!!.pathType,includeReset)

        if (useInputTargetWindow && destWindow!=null) {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToWindowToExplore(currentState,destWindow!!,nextPathType,isExploration))
                if (nextPathType == PathFindingHelper.PathType.WTG ||
                        (!includeReset && nextPathType == PathFindingHelper.PathType.NORMAL)) {
                    break
                } else {
                    nextPathType = computeNextPathType(nextPathType,includeReset)
                }
            }

        } else {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToExploreStates(currentState,nextPathType))
                if (nextPathType == PathFindingHelper.PathType.WTG ||
                        (!includeReset && nextPathType == PathFindingHelper.PathType.NORMAL)) {
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
            PathFindingHelper.PathType.INCLUDE_INFERED -> PathFindingHelper.PathType.NORMAL
            PathFindingHelper.PathType.NORMAL ->
                if (includeResetApp) PathFindingHelper.PathType.RESET else PathFindingHelper.PathType.INCLUDE_INFERED
            PathFindingHelper.PathType.RESET -> PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.WTG -> PathFindingHelper.PathType.INCLUDE_INFERED
            else -> PathFindingHelper.PathType.ANY
        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val widgetGroup = currentEdge!!.abstractAction.attributeValuationMap
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
                val correspondentWidgetGroups = currentAbstractState.EWTGWidgetMapping.filter { it.value == staticWidget }
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
                return executeCurrentEdgeAction(currentState, currentEdge!!, currentAbstractState)
            } else {
                return fillingDataActionList.pop()
            }
        }
        prevAbState = expectedNextAbState
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == autautMF.getAbstractState(currentState)!!.window) {
            currentEdge = pathTraverser!!.getNextTransition()
            if (currentEdge != null) {
                autautMF.setTargetNode(currentEdge!!.dest
                )
                nextNode = currentEdge!!.dest
                expectedNextAbState = nextNode
                log.info("Next expected node: ${nextNode}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                /*if (currentEdge!!.abstractAction.actionType!= AbstractActionType.TEXT_INSERT && currentEdge!!.userInputs.isNotEmpty()?:false)
                {
                    val inputData = currentEdge!!.userInputs.random()
                    inputData.forEach {
                        val inputWidget = currentState.visibleTargets.find { w -> it.key.equals(w.uid)}
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
                        *//*if (regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).isNotEmpty())
                        {
                            // val inputWidget = regressionTestingMF.getRuntimeWidgets(it.key,currentEdge!!.source.data,currentState).random()

                        }*//*
                    }
                    if (fillingDataActionList.isNotEmpty())
                    {
                        isFillingText = true
                    }
                }*/
                if (isFillingText) {
                    return fillingDataActionList.pop()
                }
                return executeCurrentEdgeAction(currentState, currentEdge!!, currentAbstractState)
            }
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    private fun executeCurrentEdgeAction(currentState: State<*>, prevEdge: AbstractTransition?, currentAbstractState: AbstractState): ExplorationAction {
        if (currentEdge!!.abstractAction.actionType == AbstractActionType.PRESS_MENU) {
            return pressMenuOrClickMoreOption(currentState)
        }
        if (currentEdge!!.abstractAction.attributeValuationMap != null) {
            val widgets = chooseWidgets(currentState)
            if (widgets.isNotEmpty()) {
                tryOpenNavigationBar = false
                tryScroll = false
                val candidates = runBlocking { getCandidates(widgets) }
                val chosenWidget = candidates[random.nextInt(candidates.size)]
                val actionName = currentEdge!!.abstractAction.actionType
                val actionData = if (currentEdge!!.data != null && currentEdge!!.data != "") {
                    currentEdge!!.data
                } else {
                    currentEdge!!.abstractAction.extra
                }
                log.info("Widget: $chosenWidget")

                return chooseActionWithName(actionName, actionData, chosenWidget, currentState, currentEdge!!.abstractAction)
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
            val action = currentEdge!!.abstractAction.actionType
            //val actionCondition = currentPath!!.edgeConditions[currentEdge!!]
            if (currentEdge!!.data != null && currentEdge!!.data != "") {
                return chooseActionWithName(action, currentEdge!!.data, null, currentState, currentEdge!!.abstractAction)
                        ?: ExplorationAction.pressBack()
            } else {
                return chooseActionWithName(action, currentEdge!!.abstractAction.extra
                        ?: "", null, currentState, currentEdge!!.abstractAction) ?: ExplorationAction.pressBack()
            }
        }
    }

    var scrollAttempt = 0

    protected fun addIncorrectPath(currentAbstractState: AbstractState) {
        if (currentEdge!=null)
            PathFindingHelper.addDisablePathFromState(currentPath!!,currentEdge!!)

    }

    protected fun addIncorrectPath(edge: AbstractTransition, currentAbstractState: AbstractState) {
        PathFindingHelper.addDisablePathFromState(currentPath!!, edge)
    }

    companion object {
        private val log: Logger by lazy { LoggerFactory.getLogger(this.javaClass.name) }

        var instance: GoToAnotherWindow? = null
        var executedCount:Int = 0
        fun getInstance(regressionWatcher: ATUAMF,
                        atuaTestingStrategy: ATUATestingStrategy,
                        delay: Long, useCoordinateClicks: Boolean): GoToAnotherWindow {
            if (instance == null) {
                instance = GoToAnotherWindow(regressionWatcher, atuaTestingStrategy, delay, useCoordinateClicks)
            }
            return instance!!
        }
    }
}