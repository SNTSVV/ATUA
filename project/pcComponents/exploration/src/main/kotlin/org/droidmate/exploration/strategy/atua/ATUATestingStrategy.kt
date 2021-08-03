package org.droidmate.exploration.strategy.atua


import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.explorationModel.interaction.Widget
import org.atua.modelFeatures.dstg.AbstractAction
import org.atua.modelFeatures.dstg.AbstractState
import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.dstg.VirtualAbstractState
import org.atua.modelFeatures.ewtg.EventType
import org.atua.modelFeatures.ewtg.window.Launcher
import org.droidmate.exploration.strategy.AExplorationStrategy
import org.droidmate.exploration.strategy.atua.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State

open class ATUATestingStrategy @JvmOverloads constructor(priority: Int,
                                                         val scaleFactor: Double = 1.0,
                                                         dictionary: List<String> = emptyList(),
                                                         useCoordinateClicks: Boolean = true
) : RandomWidget(priority, dictionary,useCoordinateClicks) {
    lateinit var eContext: ExplorationContext<*,*,*>

    protected val atuaMF: org.atua.modelFeatures.ATUAMF
        get() = (eContext.findWatcher { it is org.atua.modelFeatures.ATUAMF } as org.atua.modelFeatures.ATUAMF)

    protected val statementWatcher: StatementCoverageMF
    get() = (eContext.findWatcher { it is StatementCoverageMF } as StatementCoverageMF)

    val handleTargetAbsent = org.droidmate.exploration.strategy.atua.HandleTargetAbsent()
    protected val stateGraph: StateGraphMF by lazy { eContext.getOrCreateWatcher<StateGraphMF>() }

    private val maximumActionCount = 10

    protected var strategyTask: AbstractStrategyTask? = null
    protected var recentFailedState: State<*>? = null
    var isRecentlyFillText: Boolean = false
    fun getContext() = eContext

    fun getActionCounter() = counter
    fun getBlacklist() = blackList
    var isFullyRandomExploration: Boolean = false
    var latestAbstractAction: AbstractAction? = null
    lateinit var phaseStrategy: AbstractPhaseStrategy
    //lateinit var phaseTwoStrategy: AbstractPhaseStrategy
    /**
     * Mutex for synchronization
     */
    protected val mutex = Mutex()

    override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> chooseRandomWidget(eContext: ExplorationContext<M, S, W>): ExplorationAction {
        return chooseRegression(eContext)
    }

    var prevNode: AbstractState? = null



    internal suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> chooseRegression(eContext: ExplorationContext<M,S,W>): ExplorationAction {
/*        if (!phaseStrategy.fullControl && handleTargetAbsent.hasNext(eContext)) {
            return handleTargetAbsent.nextAction(eContext)
        }*/
        var chosenAction: ExplorationAction
        ExplorationTrace.widgetTargets.clear()
        val currentState = eContext.getCurrentState()
        val currentAbstractState = AbstractStateManager.INSTANCE.getAbstractState(currentState)
        if (currentAbstractState == null) {
            if (eContext.isEmpty() || currentState == eContext.model.emptyState) {
                return eContext.launchApp()
            }
            log.info("Cannot retrieve current abstract state.")
            return eContext.resetApp()
        }

        if ((AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH]==currentAbstractState
                || AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH]==currentAbstractState)
                && currentAbstractState.rotation == org.atua.modelFeatures.Rotation.LANDSCAPE) {
            return ExplorationAction.rotate(-90)
        }

//        if(currentAbstractState.isOpeningKeyboard && !AbstractStateManager.instance.ABSTRACT_STATES.any { it !is VirtualAbstractState && it.window == currentAbstractState.window && !it.isOpeningKeyboard }) {
//            return GlobalAction(actionType = ActionType.CloseKeyboard)
//        }

        if (!phaseStrategy.hasNextAction(eContext.getCurrentState())) {
            if (phaseStrategy is PhaseOneStrategy) {
                val unreachableWindow = (phaseStrategy as PhaseOneStrategy).unreachableWindows
                if (atuaMF.modifiedMethodsByWindow.keys.filterNot { unreachableWindow.contains(it) }.isNotEmpty()) {
                    phaseStrategy = PhaseTwoStrategy(this, scaleFactor, delay, useCoordinateClicks, unreachableWindow)
                    atuaMF.updateStage1Info(eContext)
                }
                    /*phaseStrategy = PhaseThreeStrategy(this,budgetScale, delay, useCoordinateClicks)
                    regressionWatcher.updateStage2Info(eContext)*/
            } else if (phaseStrategy is PhaseTwoStrategy) {
                if (targetInputAvailable()) {
                    phaseStrategy = PhaseThreeStrategy(this, scaleFactor, delay, useCoordinateClicks)
                    atuaMF.updateStage2Info(eContext)
                }
            } else if (phaseStrategy is PhaseThreeStrategy) {
                return ExplorationAction.terminateApp()
            }
        }

        log.info("Current abstract state: ${currentAbstractState}")
        log.info("Abstract State counts: ${AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter{it !is VirtualAbstractState}.size}")
        val availableWidgets = eContext.getCurrentState().widgets
        chosenAction = phaseStrategy.nextAction(eContext)
        prevNode = atuaMF.getAbstractState(eContext.getCurrentState())
        return chosenAction
    }

    private fun targetInputAvailable(): Boolean {
        atuaMF.modifiedMethodsByWindow.keys.filter { it !is Launcher }.forEach { window ->
            val abstractStates = AbstractStateManager.INSTANCE.getPotentialAbstractStates().filter { it.window == window }
            if (abstractStates.isNotEmpty()) {
                val targetInputs = atuaMF.notFullyExercisedTargetInputs.filter {it.sourceWindow == window && it.eventType!=EventType.implicit_launch_event
                        && it.eventType != EventType.resetApp}
                val realisticInputs = abstractStates.map { it.inputMappings.values }.flatten().flatten().distinct()
                val realisticTargetInputs = targetInputs.intersect(realisticInputs)
                if (realisticTargetInputs.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    override fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> initialize(initialContext: ExplorationContext<M, S, W>) {
        super.initialize(initialContext)
        eContext = initialContext
        phaseStrategy = PhaseOneStrategy(this,scaleFactor,delay, useCoordinateClicks)
    }


    override fun hashCode(): Int {
        return this.javaClass.hashCode()
    }

//    override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
//        if (eContext.getCurrentState().isAppHasStoppedDialogBox)
//            return false
//        return true
//    }

}

class HandleTargetAbsent():  AExplorationStrategy() {
    private var cnt = 0
    private var pressbackCnt = 0
    private var clickScreen = false
    private var pressEnter = false
    // may be used to terminate if there are no targets after waiting for maxWaitTime
    private var terminate = false
    private val maxWaitTime: Long = 5000
    override fun getPriority(): Int = 1

    override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> hasNext(eContext: ExplorationContext<M, S, W>): Boolean {
        val hasNext = !eContext.explorationCanMoveOn().also {
            if(it) {
                cnt = 0  // reset the counter if we can proceed
                pressbackCnt = 0
                clickScreen = false
                pressEnter = false
                terminate = false
            }
        }
        return hasNext
    }

    suspend fun waitForLaunch(eContext: ExplorationContext<*,*,*>): ExplorationAction{
        return when{
            cnt++ < 2 ->{
                delay(maxWaitTime)
                GlobalAction(ActionType.FetchGUI) // try to refetch after waiting for some time
            }
            terminate -> {
                log.debug("Cannot explore. Last action was reset. Previous action was to press back. Returning 'Terminate'")
                eContext.resetApp()
            }
            else -> eContext.resetApp()
        }
    }

    override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> nextAction(eContext: ExplorationContext<M, S, W>): ExplorationAction {
        //DEBUG
        val currentState = eContext.getCurrentState()
        //END DEBUG
        val lastActionType = eContext.getLastActionType()
        val (lastLaunchDistance,secondLast) = with(
                eContext.explorationTrace.getActions().filterNot {
                    it.actionType.isQueueStart()|| it.actionType.isQueueEnd() }
        ){
            lastIndexOf(findLast{ it.actionType == "ResetApp" || it.actionType.isLaunchApp() }).let{ launchIdx ->
                val beforeLaunch = this.getOrNull(launchIdx - 1)
                Pair( size-launchIdx, beforeLaunch)
            }
        }
        val s = eContext.getCurrentState()
        val s_res = eContext.getState(eContext.getLastAction().resState)
        val s_prev = eContext.getState(eContext.getLastAction().prevState)
        return when {
            lastActionType.isPressBack() -> {
                // if previous action was back, terminate
                if (s.isAppHasStoppedDialogBox) {
                    log.debug("Cannot explore. Last action was back. Currently on an 'App has stopped' dialog. Returning 'Wait'")
                    waitForLaunch(eContext)
                } else {
                    //some screens require pressback 2 times to exit activity
                    if (s.isHomeScreen) {
                        eContext.launchApp()
                    } else {
                        if (pressbackCnt < 2) {
                            log.debug("Cannot explore. Try pressback again")
                            pressbackCnt++
                            ExplorationAction.pressBack()
                        } else if (pressbackCnt < 3) {
                            // Try double pressback
                            pressbackCnt++
                            log.debug("Cannot explore. Try double pressback")
                            ActionQueue(arrayListOf(ExplorationAction.pressBack(), ExplorationAction.pressBack()), delay = 25)
                        } else {
                            log.debug("Cannot explore. Last action was back. Returning 'Launch'")
                            eContext.launchApp()
                        }
                    }
                }
            }
            lastLaunchDistance <=3 || eContext.getLastActionType().isFetch() -> { // since app reset is an ActionQueue of (Launch+EnableWifi), or we had a WaitForLaunch action
                when {  // last action was reset
                    s.isAppHasStoppedDialogBox -> {
                        log.debug("Cannot explore. Last action was reset. Currently on an 'App has stopped' dialog. Returning 'Terminate'")
                        ExplorationAction.terminateApp()
                    }
                    eContext.explorationTrace.getActions().takeLast(3).filterNot {it.actionType.isFetch()}.isEmpty() ->{
                        //Last three actions are FetchUI, try to pressBack
                        eContext.resetApp()
                    }

                    secondLast?.actionType?.isPressBack() ?: false -> {
                        terminate = true  // try to wait for launch but terminate if we still have nothing to explore afterwards
                        waitForLaunch(eContext)
                    }
                    else -> { // the app may simply need more time to start (synchronization for app-launch not yet perfectly working) -> do delayed re-fetch for now
                        log.debug("Cannot explore. Returning 'Wait'")
                        waitForLaunch(eContext)
                    }
                }
            }
            // by default, if it cannot explore, presses back
            else -> {
                if (!s.actionableWidgets.any { it.clickable }  ) {
                    // for example: vlc video player
                    log.debug("Cannot explore because of no actionable widgets. Randomly choose PressBack or Click")
                    if (pressEnter || clickScreen) {
                        pressbackCnt +=1
                        log.debug("PressBack.")
                        ExplorationAction.pressBack()
                    } else
                    {
                        log.debug("Click on Screen")
                        val largestWidget = s.widgets.maxBy { it.boundaries.width+it.boundaries.height }
                        if (largestWidget !=null) {
                            clickScreen = true
                            largestWidget.click()
                        } else {
                            pressEnter = true
                            ExplorationAction.pressEnter()
                        }
                    }
                } else {
                    pressbackCnt +=1
                    ExplorationAction.pressBack()
                }
            }
        }
    }

}