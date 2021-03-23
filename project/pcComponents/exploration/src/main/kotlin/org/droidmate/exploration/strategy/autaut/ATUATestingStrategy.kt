package org.droidmate.exploration.strategy.autaut


import kotlinx.coroutines.sync.Mutex
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.VirtualAbstractState
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State

open class ATUATestingStrategy @JvmOverloads constructor(priority: Int,
                                                         val budgetScale: Double = 1.0,
                                                         dictionary: List<String> = emptyList(),
                                                         useCoordinateClicks: Boolean = true
) : RandomWidget(priority, dictionary,useCoordinateClicks) {
    lateinit var eContext: ExplorationContext<*,*,*>

    protected val regressionWatcher: ATUAMF
        get() = (eContext.findWatcher { it is ATUAMF } as ATUAMF)

    protected val statementWatcher: StatementCoverageMF
    get() = (eContext.findWatcher { it is StatementCoverageMF } as StatementCoverageMF)

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
    var currentPhase: Int = 1

    internal suspend fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> chooseRegression(eContext: ExplorationContext<M,S,W>): ExplorationAction {
        var chosenAction: ExplorationAction
        ExplorationTrace.widgetTargets.clear()
        val currentAbstractState = AbstractStateManager.instance.getAbstractState(eContext.getCurrentState())
        if (currentAbstractState == null) {
            if (eContext.isEmpty()) {
                return GlobalAction(ActionType.FetchGUI)
            }
            log.info("Cannot retrieve current abstract state.")
            return eContext.resetApp()
        }

        if ((AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH]==currentAbstractState
                || AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH]==currentAbstractState)
                && currentAbstractState.rotation == Rotation.LANDSCAPE) {
            return ExplorationAction.rotate(-90)
        }

        if(currentAbstractState.isOpeningKeyboard && !AbstractStateManager.instance.ABSTRACT_STATES.any { it !is VirtualAbstractState && it.window == currentAbstractState.window && !it.isOpeningKeyboard }) {
            return GlobalAction(actionType = ActionType.CloseKeyboard)
        }

        if (!phaseStrategy.hasNextAction(eContext.getCurrentState())) {
            if (phaseStrategy is PhaseOneStrategy) {
                val unreachableWindow = (phaseStrategy as PhaseOneStrategy).unreachableWindows
                if (regressionWatcher.allTargetWindow_ModifiedMethods.keys.filterNot { unreachableWindow.contains(it) }.isNotEmpty()) {
                    phaseStrategy = PhaseTwoStrategy(this, budgetScale, delay, useCoordinateClicks, unreachableWindow)
                    regressionWatcher.updateStage1Info(eContext)
                    return eContext.resetApp()
                }
                    /*phaseStrategy = PhaseThreeStrategy(this,budgetScale, delay, useCoordinateClicks)
                    regressionWatcher.updateStage2Info(eContext)*/
            } else if (phaseStrategy is PhaseTwoStrategy) {
                phaseStrategy = PhaseThreeStrategy(this,budgetScale, delay, useCoordinateClicks)
                regressionWatcher.updateStage2Info(eContext)
            } else if (phaseStrategy is PhaseThreeStrategy) {
                return ExplorationAction.terminateApp()
            }
        }

        log.info("Current abstract state: ${currentAbstractState}")
        log.info("Abstract State counts: ${AbstractStateManager.instance.ABSTRACT_STATES.filter{it !is VirtualAbstractState}.size}")
        val availableWidgets = eContext.getCurrentState().widgets
        chosenAction = phaseStrategy.nextAction(eContext)
        prevNode = regressionWatcher.getAbstractState(eContext.getCurrentState())
        return chosenAction
    }

    override fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> initialize(initialContext: ExplorationContext<M, S, W>) {
        super.initialize(initialContext)
        eContext = initialContext
        phaseStrategy = PhaseOneStrategy(this,budgetScale,delay, useCoordinateClicks)
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