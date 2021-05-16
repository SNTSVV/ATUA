package org.droidmate.exploration.strategy.autaut.task

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.click
import org.droidmate.exploration.actions.pressBack
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

    override fun chooseRandomOption(currentState: State<*>) {
        currentPath = possiblePaths.random()
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
                if (retryTimes <= 3*autautStrategy.budgetScale) {
                    //TODO check currentPath is valid
                    if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedNextAbState!!)) {
                        initPossiblePaths(currentState,true)
                        log.debug("The expected state is removed by the refinement. Reidentify paths to the destination.")
                    } else {
                        retryTimes += 1
                        val currentEdge = pathTraverser!!.getCurrentTransition()
                        if (currentEdge!!.abstractAction.actionType == AbstractActionType.PRESS_BACK) {
                            initPossiblePaths(currentState,true)
                            log.debug("Reidentify paths to the destination.")
                        } else {
                            initPossiblePaths(currentState)
                            log.debug("Reidentify paths to the destination.")
                        }
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
                // Try find another available shorter path
               /* if (currentPath!!.pathType!=PathFindingHelper.PathType.TRACE) {
                    initPossiblePaths(currentState, true)
                    val currentPathLeft = currentPath!!.path.size - (pathTraverser!!.latestEdgeId!! + 1)
                    if (possiblePaths.any { it.path.size < currentPathLeft }) {
                        currentPath = possiblePaths.find { it.path.size < currentPathLeft }!!
                        expectedNextAbState = currentPath!!.root
                    }
                }*/
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
         val expectedAbstractState = pathTraverser!!.getCurrentTransition()!!.dest
         if (expectedAbstractState == currentAbState)
             return true
         if (!AbstractStateManager.instance.ABSTRACT_STATES.contains(expectedAbstractState)) {
             val equivalentAbstractState = AbstractStateManager.instance.ABSTRACT_STATES.find {
                 it.hashCode == expectedAbstractState!!.hashCode
             }
             if (equivalentAbstractState!= null && equivalentAbstractState == currentAbState) {
                 return true
             }
         }

         if (pathTraverser!!.finalStateAchieved()) {
             if (pathTraverser!!.getCurrentTransition()!!.dest is VirtualAbstractState) {
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
             val expectedAbstractState = currentTransition.dest

             if (expectedAbstractState!!.window != currentAbState!!.window) {
                 tmpPathTraverser.getNextTransition()
                 continue
             }

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
                     tmpPathTraverser.getNextTransition()
                     continue
                 }
                 if(expectedAbstractState!!.isOpeningKeyboard != currentAbState.isOpeningKeyboard) {
                     tmpPathTraverser.getNextTransition()
                     continue
                 }
             }
             val nextTransition = tmpPathTraverser.transitionPath.path[tmpPathTraverser.latestEdgeId!!+1]
             if (nextTransition!=null) {
                 if (!nextTransition.abstractAction.isWidgetAction()) {
                     reached = true
                     break
                 } else {
                     val avm = nextTransition.abstractAction.attributeValuationMap!!
                     val guiWidgets = getGUIWidgetsByAVM(avm, currentState)
                     if (guiWidgets.isNotEmpty()) {
                         reached = true
                         break
                     }
                 }
             }
             tmpPathTraverser.getNextTransition()
         }
         if (reached) {
             expectedNextAbState = expectedAbstractState
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
            if (!needReset(currentState,autautStrategy.eContext))
                PathFindingHelper.PathType.INCLUDE_INFERED
            else
                PathFindingHelper.PathType.RESET
        else if (continueMode)
                PathFindingHelper.PathType.INCLUDE_INFERED
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

    protected fun needReset(currentState: State<*>, eContext: ExplorationContext<*, *, *>): Boolean {
        val interval = 50
        val lastReset = runBlocking {
            eContext.explorationTrace.P_getActions()
                    .indexOfLast { it.actionType == "ResetApp" }
        }
        val currAction = eContext.explorationTrace.size
        val diff = currAction - lastReset
        return diff > interval
    }

    fun computeNextPathType(pathType: PathFindingHelper.PathType, includeResetApp: Boolean): PathFindingHelper.PathType {
        return when (pathType) {
            PathFindingHelper.PathType.INCLUDE_INFERED ->
                if (includeResetApp) PathFindingHelper.PathType.RESET else PathFindingHelper.PathType.WTG
            PathFindingHelper.PathType.RESET -> PathFindingHelper.PathType.NORMAL
            PathFindingHelper.PathType.NORMAL -> PathFindingHelper.PathType.TRACE
            PathFindingHelper.PathType.TRACE -> PathFindingHelper.PathType.WTG
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
        if (widgets.isEmpty()) {
            val staticWidget = currentAbstractState.EWTGWidgetMapping[avm]
            if (staticWidget == null)
                return emptyList()
            val correspondentWidgetGroups = currentAbstractState.EWTGWidgetMapping.filter { it.value == staticWidget }
            widgets.addAll(correspondentWidgetGroups.map { atuaMF.getRuntimeWidgets(it.key,currentAbstractState,currentState) }.flatten())
        }
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
                return executeCurrentEdgeAction(currentState, pathTraverser!!.getCurrentTransition(), currentAbstractState)
            } else {
                return fillingDataActionList.pop()
            }
        }
        prevAbState = expectedNextAbState
        if (currentPath == null || expectedNextAbState == null)
            return randomExplorationTask.chooseAction(currentState)
        log.info("Destination: ${currentPath!!.getFinalDestination()}")
        if (expectedNextAbState!!.window == atuaMF.getAbstractState(currentState)!!.window) {
            val nextTransition = pathTraverser!!.getNextTransition()
            if (nextTransition != null) {
                nextAbstractState = nextTransition!!.dest
                expectedNextAbState = nextAbstractState
                log.info("Next expected node: ${nextAbstractState}")
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
        }
        mainTaskFinished = true
        log.debug("Cannot get target action, finish task.")
        return randomExplorationTask!!.chooseAction(currentState)
    }

    private fun executeCurrentEdgeAction(currentState: State<*>, prevEdge: AbstractTransition?, currentAbstractState: AbstractState): ExplorationAction {
        val currentEdge = pathTraverser!!.getCurrentTransition()
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
        if (currentAbstractState.window == expectedNextAbState!!.window && expectedNextAbState is VirtualAbstractState)
            return
        val currentEdge = pathTraverser!!.getCurrentTransition()
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