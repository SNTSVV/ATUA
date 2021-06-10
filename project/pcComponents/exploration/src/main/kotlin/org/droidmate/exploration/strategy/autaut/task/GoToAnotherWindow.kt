package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.rotate
import org.droidmate.exploration.actions.setText
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.modelFeatures.atua.EWTG.*
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.helper.PathFindingHelper
import org.droidmate.exploration.strategy.autaut.ATUATestingStrategy
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
    protected var randomExplorationTask: RandomExplorationTask = RandomExplorationTask(this.atuaMF,atuaTestingStrategy,delay,useCoordinateClicks,true,1)

    var isFillingText: Boolean = false
    //protected var currentEdge: AbstractTransition?=null
    protected var expectedNextAbState: AbstractState?=null
    var currentPath: TransitionPath? = null
    val possiblePaths = ArrayList<TransitionPath>()
    var pathTraverser: PathTraverser? = null

    var destWindow: Window? = null
    var useInputTargetWindow: Boolean = false
    var retryTimes: Int = 0
    var isTarget: Boolean = false

    val fillingDataActionList = Stack<ExplorationAction>()
    init {
        randomExplorationTask.isPureRandom = true
    }
    override fun chooseRandomOption(currentState: State<*>) {
        val notIncludeResetPaths = possiblePaths.filter {
            it.path.values.all { it.abstractAction.actionType != AbstractActionType.RESET_APP }
        }
        if (notIncludeResetPaths.isEmpty())
            currentPath = possiblePaths.random()
        else
            currentPath = notIncludeResetPaths.random()
      //  currentEdge = null
        pathTraverser = PathTraverser(currentPath!!)
        destWindow = currentPath!!.getFinalDestination().window
        possiblePaths.remove(currentPath!!)
        expectedNextAbState=currentPath!!.root
        mainTaskFinished = false
        isFillingText = false
        tryOpenNavigationBar = false
        tryScroll = false
    }

    override fun isTaskEnd(currentState: State<*>): Boolean {
        if (atuaMF.prevAbstractStateRefinement>0)
            return true
        if (pathTraverser==null)
            return true
        if (mainTaskFinished)
            return true
        if (currentPath == null)
            return true
        if (isFillingText)
            return false
        //if app reached the final destination
        val currentAppState = atuaMF.getAbstractState(currentState)!!
        if (pathTraverser!!.finalStateAchieved()) {
            if (currentAppState == currentPath!!.getFinalDestination()) {
                return true
            }
        }
        //if currentNode is expectedNextNode
        if (isExploration) {
            if (currentAppState.window == destWindow) {
                if (currentAppState.getUnExercisedActions(currentState, atuaMF).isNotEmpty()) {
                    return true
                }
            }
        }
        if (expectedNextAbState!=null) {
            if (!isReachExpectedNode(currentState)) {
                // Try another path if current state is not target node
                /*if (currentAppState.window != targetWindow && regressionTestingMF.prevAbstractStateRefinement == 0) {
                    log.debug("Fail to reach expected node")
                    addIncorrectPath()
                }*/

                log.debug("Fail to reach $expectedNextAbState" )
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
                if (retryTimes <5*autautStrategy.scaleFactor) {
//                    //TODO check currentPath is valid
//                    if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedNextAbState!!)) {
//                        initPossiblePaths(currentState,true)
//                        log.debug("The expected state is removed by the refinement. Reidentify paths to the destination.")
//                    } else {
//
//                    }
                    retryTimes += 1
                    val currentEdge = pathTraverser!!.getCurrentTransition()
                    /*if (currentEdge!!.abstractAction.actionType == AbstractActionType.PRESS_BACK) {
                        initPossiblePaths(currentState,true)
                        log.debug("Reidentify paths to the destination.")
                    } else {
                    }*/
                    log.debug("Reidentify paths to the destination.")
                    if (currentPath!!.pathType==PathFindingHelper.PathType.TRACE
                            || currentPath!!.pathType==PathFindingHelper.PathType.RESET) {
                        initPossiblePaths(currentState, true)
                        val currentPathLeft = currentPath!!.path.size - (pathTraverser!!.latestEdgeId!! + 1)
                        if (!possiblePaths.any { it.path.size < currentPathLeft-1 }) {
                            initPossiblePaths(currentState)
                        }
                    } else {
                        initPossiblePaths(currentState)
                    }
                    if (possiblePaths.isNotEmpty()) {
                        initialize(currentState)
                        log.debug(" Paths is not empty")
                        return false
                    }
                }
                return true
            }
            else
            {
                expectedNextAbState = pathTraverser!!.getCurrentTransition()!!.dest
                if (pathTraverser!!.finalStateAchieved()) {
                    return true
                }
//                // Try find another available shorter path
//               if (currentPath!!.pathType==PathFindingHelper.PathType.TRACE
//                       || currentPath!!.pathType==PathFindingHelper.PathType.RESET) {
//                   initPossiblePaths(currentState, true)
//                   val currentPathLeft = currentPath!!.path.size - (pathTraverser!!.latestEdgeId!! + 1)
//                   if (possiblePaths.any { it.path.size < currentPathLeft-1 }) {
//                       possiblePaths.removeIf { it.path.size >= currentPathLeft-1 }
//                       initialize(currentState)
//                   }
//               }
                return false
            }
        } else {
            //something wrong, should end task
            return true
        }
    }

     fun isReachExpectedNode(currentState: State<*>):Boolean {
         var reached = false
         val currentAbState = atuaMF.getAbstractState(currentState)!!
         var expectedAbstractState = pathTraverser!!.getCurrentTransition()!!.dest
         if (expectedAbstractState.isRequestRuntimePermissionDialogBox) {
             pathTraverser!!.next()
             expectedAbstractState = pathTraverser!!.getCurrentTransition()!!.dest
         }
         if (pathTraverser!!.finalStateAchieved() && expectedAbstractState.isRequestRuntimePermissionDialogBox)
             return true
         if (expectedAbstractState == currentAbState)
             return true
         if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedAbstractState)) {
             val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                 it.hashCode == expectedAbstractState!!.hashCode
             }
             if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                 return true
             } else {
                 return false
             }
         }
         if (pathTraverser!!.finalStateAchieved()) {
             if (expectedAbstractState is VirtualAbstractState
                     && expectedAbstractState.window == currentAbState.window) {
                 return true
             }
             return false
         }

         val tmpPathTraverser = PathTraverser(currentPath!!)
         tmpPathTraverser.latestEdgeId = pathTraverser!!.latestEdgeId
         while (!tmpPathTraverser.finalStateAchieved()) {
             val currentTransition = tmpPathTraverser.getCurrentTransition()
             if (currentTransition == null)
                 break
             val expectedAbstractState1 = currentTransition.dest

             if (expectedAbstractState1!!.window != currentAbState!!.window) {
                 tmpPathTraverser.next()
                 continue
             }
             if (expectedAbstractState1 == currentAbState) {
                 reached = true
                 break
             }
             if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedAbstractState1)) {
                 val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                     it.hashCode == expectedAbstractState1!!.hashCode
                 }
                 if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                     reached = true
                     break
                 }
             }

             if (expectedAbstractState1 !is VirtualAbstractState) {
                 if (expectedAbstractState1!!.isMenusOpened != currentAbState.isMenusOpened) {
                     tmpPathTraverser.next()
                     continue
                 }
                 /*if(expectedAbstractState1!!.rotation != currentAbState.rotation) {
                     tmpPathTraverser.next()
                     continue
                 }
                 if(expectedAbstractState1!!.isOpeningKeyboard != currentAbState.isOpeningKeyboard) {
                     tmpPathTraverser.next()
                     continue
                 }*/
             }
             val nextTransition = tmpPathTraverser.transitionPath.path[tmpPathTraverser.latestEdgeId!!+1]
             if (nextTransition!=null) {
                 if (!nextTransition.abstractAction.isWidgetAction()) {
                     reached = true
                     break
                 } else {
                     if (nextTransition.abstractAction.actionType == AbstractActionType.SWIPE
                             && currentPath!!.pathType != PathFindingHelper.PathType.RESET
                             && currentPath!!.pathType != PathFindingHelper.PathType.TRACE) {
                         val tmpPathTraverser2 = PathTraverser(tmpPathTraverser.transitionPath)
                         tmpPathTraverser2.latestEdgeId = tmpPathTraverser.latestEdgeId
                         var notSwipeTransition: AbstractTransition? = null
                         while (notSwipeTransition == null) {
                             val nextTransition2 = tmpPathTraverser2.transitionPath.path[tmpPathTraverser2.latestEdgeId!!+1]
                             if (nextTransition2 == null)
                                 break
                             if (nextTransition2.abstractAction.actionType!=AbstractActionType.SWIPE)
                                 notSwipeTransition = nextTransition2
                             else
                                 tmpPathTraverser2.next()
                         }
                         if (notSwipeTransition!=null) {
                             if (!notSwipeTransition.abstractAction.isWidgetAction()) {
                                 reached = true
                                 tmpPathTraverser.latestEdgeId = tmpPathTraverser2.latestEdgeId
                                 break
                             }
                             val avm = notSwipeTransition.abstractAction.attributeValuationMap!!
                             val guiWidgets = getGUIWidgetsByAVM(avm, currentState)
                             if (guiWidgets.isNotEmpty()) {
                                 reached = true
                                 tmpPathTraverser.latestEdgeId = tmpPathTraverser2.latestEdgeId
                                 break
                             }
                         }
                     }
                     val avm = nextTransition.abstractAction.attributeValuationMap!!
                     val guiWidgets = getGUIWidgetsByAVM(avm, currentState)
                     if (guiWidgets.isNotEmpty()) {
                         reached = true
                         break
                     }
                 }
             } else {
                 if (tmpPathTraverser.finalStateAchieved()) {
                     val destState = tmpPathTraverser.getCurrentTransition()!!.dest
                     if (destState.window != currentAbState.window) {
                         reached = false
                     }
                     else if (destState == currentAbState) {
                         reached = true
                     }
                     else if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(destState)) {
                         val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                             it.hashCode == destState!!.hashCode
                         }
                         if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                             reached = true
                         }
                     } else if (destState is VirtualAbstractState
                             && destState.window == currentAbState.window) {
                         reached = true
                     }
                     if (!reached) {
                         val lastActionType = autautStrategy.eContext.getLastActionType()
                         var abstractActionType = when (lastActionType) {
                             "Tick" -> AbstractActionType.CLICK
                             "ClickEvent" -> AbstractActionType.CLICK
                             "LongClickEvent" -> AbstractActionType.LONGCLICK
                             else -> AbstractActionType.values().find { it.actionName.equals(lastActionType) }
                         }
                         if (abstractActionType == AbstractActionType.WAIT) {
                             reached = true
                         }
                     }
                 }
                 break
             }
             tmpPathTraverser.next()
         }
         if (reached) {
             expectedNextAbState = tmpPathTraverser.getCurrentTransition()!!.dest
             pathTraverser!!.latestEdgeId = tmpPathTraverser.latestEdgeId
         }
         return reached
    }

    override fun hasAnotherOption(currentState: State<*>): Boolean {
        if (currentPath!!.root == atuaMF.getAbstractState(currentState)!!
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
        autautStrategy.phaseStrategy.fullControl = true
    }

    override fun reset() {
        possiblePaths.clear()
        includePressbackAction = true
        destWindow = null
        currentPath = null
        useInputTargetWindow = false
        includeResetAction = true
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

    open fun isAvailable(currentState: State<*>, destWindow: Window, includePressback: Boolean, includeResetApp: Boolean, isExploration: Boolean): Boolean {
        log.info("Checking if there is any path to $destWindow")
        reset()
        this.includePressbackAction = includePressback
        this.destWindow = destWindow
        this.useInputTargetWindow = true
        this.includeResetAction = includeResetApp
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

    var includePressbackAction = true
    var includeResetAction = true
    var isExploration = true

    open protected fun initPossiblePaths(currentState: State<*>, continueMode:Boolean = false) {
        possiblePaths.clear()
        var nextPathType = if (currentPath == null)
                PathFindingHelper.PathType.INCLUDE_INFERED
        else if (continueMode)
                PathFindingHelper.PathType.TRACE
        else
            computeNextPathType(currentPath!!.pathType,includeResetAction)

        if (useInputTargetWindow && destWindow!=null) {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToWindowToExplore(currentState,destWindow!!,nextPathType,isExploration))
                if (nextPathType == PathFindingHelper.PathType.WTG)
                    break
                if (continueMode)
                    break
                if (!includeResetAction && nextPathType == PathFindingHelper.PathType.INCLUDE_INFERED) {
                    break
                } else {
                    nextPathType = computeNextPathType(nextPathType,includeResetAction)
                }
            }
        } else {
            while (possiblePaths.isEmpty()) {
                possiblePaths.addAll(autautStrategy.phaseStrategy.getPathsToExploreStates(currentState,nextPathType))
                if (nextPathType == PathFindingHelper.PathType.WTG)
                    break
                if (continueMode)
                    break
                if (!includeResetAction && nextPathType == PathFindingHelper.PathType.INCLUDE_INFERED) {
                    break
                } else {
                    nextPathType = computeNextPathType(nextPathType,includeResetAction)
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
            PathFindingHelper.PathType.NORMAL -> PathFindingHelper.PathType.TRACE
            PathFindingHelper.PathType.TRACE ->
                if (includeResetApp) PathFindingHelper.PathType.RESET else PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.RESET -> PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.WTG -> PathFindingHelper.PathType.INCLUDE_INFERED
            else -> PathFindingHelper.PathType.ANY
        }
    }

    override fun chooseWidgets(currentState: State<*>): List<Widget> {
        val widgetGroup = pathTraverser!!.getCurrentTransition()!!.abstractAction.attributeValuationMap
        if (widgetGroup==null)
        {
            return emptyList()
        }
        else
        {
            val guiWidgets: List<Widget> = getGUIWidgetsByAVM(widgetGroup,currentState)
            return guiWidgets
        }
    }

    private fun getGUIWidgetsByAVM(avm: AttributeValuationMap, currentState: State<*>): List<Widget> {
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        val widgets = ArrayList<Widget>()
        widgets.addAll(atuaMF.getRuntimeWidgets(avm,currentAbstractState ,currentState))
//        if (widgets.isEmpty()) {
//            val staticWidget = currentAbstractState.EWTGWidgetMapping[avm]
//            if (staticWidget == null)
//                return emptyList()
//            val correspondentWidgetGroups = currentAbstractState.EWTGWidgetMapping.filter { it.value == staticWidget }
//            widgets.addAll(correspondentWidgetGroups.map { atuaMF.getRuntimeWidgets(it.key,currentAbstractState,currentState) }.flatten())
//        }
        return widgets
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
        var nextAbstractState = expectedNextAbState
        val currentAbstractState = atuaMF.getAbstractState(currentState)!!
        if (currentAbstractState.isOpeningKeyboard && !expectedNextAbState!!.isOpeningKeyboard) {
            return  GlobalAction(actionType = ActionType.CloseKeyboard)
        }
        prevState = currentState
        if (isFillingText) {
            if (fillingDataActionList.isEmpty()) {
                isFillingText = false
                return executeCurrentEdgeAction(currentState, pathTraverser!!.getCurrentTransition()!!, currentAbstractState)
            } else {
                return fillingDataActionList.pop()
            }
        }
        prevAbState = expectedNextAbState
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == currentAbstractState.window) {
            if (expectedNextAbState!!.rotation != currentAbstractState.rotation) {
                if (currentAbstractState.rotation == Rotation.LANDSCAPE) {
                    return ExplorationAction.rotate(-90)
                } else {
                    return ExplorationAction.rotate(90)
                }
            }
            if (!expectedNextAbState!!.isOpeningKeyboard && currentAbstractState.isOpeningKeyboard) {
                return GlobalAction(ActionType.CloseKeyboard)
            }
            val nextTransition = pathTraverser!!.next()
            if (nextTransition != null) {
                nextAbstractState = nextTransition!!.dest
                expectedNextAbState = nextAbstractState
                log.info("Next action: ${nextTransition.abstractAction}")
                log.info("Expected state: ${nextAbstractState}")
                //log.info("Event: ${currentEdge!!.label.abstractAction.actionName} on ${currentEdge!!.label.abstractAction.widgetGroup}")
                //Fill text input (if required)
                //TODO Need save swipe action data
                if (pathTraverser!!.transitionPath.pathType==PathFindingHelper.PathType.RESET && nextTransition!!.userInputs.isNotEmpty()?:false)
                {
                    val inputData = nextTransition!!.userInputs.random()
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
                return executeCurrentEdgeAction(currentState, nextTransition!!, currentAbstractState)
            }
            else {
                log.debug("Cannot get next transition.")
            }
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    private fun executeCurrentEdgeAction(currentState: State<*>, nextTransition: AbstractTransition, currentAbstractState: AbstractState): ExplorationAction {
        val currentEdge = nextTransition
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
                log.debug("Can not get target widget. Random exploration.")
                mainTaskFinished=true
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
        val currentEdge = if (currentAbstractState.window != expectedNextAbState!!.window)
                pathTraverser!!.getCurrentTransition()
            else
                pathTraverser!!.transitionPath.path[pathTraverser!!.latestEdgeId!!+1]
        PathFindingHelper.addDisablePathFromState(currentPath!!, currentEdge)
        /*if (currentEdge!=null) {

        }*/

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