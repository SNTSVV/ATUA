package org.droidmate.exploration.strategy.autaut


import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.*
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.exploration.strategy.widget.RandomWidget
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractStateManager
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State

open class RegressionTestingStrategy @JvmOverloads constructor(priority: Int,
                                                               val budgetScale: Double = 1.0,
                                                               dictionary: List<String> = emptyList(),
                                                               useCoordinateClicks: Boolean = true
) : RandomWidget(priority, dictionary,useCoordinateClicks) {
    lateinit var eContext: ExplorationContext<*,*,*>

    protected val regressionWatcher: AutAutMF
        get() = (eContext.findWatcher { it is AutAutMF } as AutAutMF)

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
            return eContext.resetApp()
        }
        if ((AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH]!!.contains(currentAbstractState)
                || AbstractStateManager.instance.launchAbstractStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH]!!.contains(currentAbstractState))
                && currentAbstractState.rotation == Rotation.LANDSCAPE) {
            return ExplorationAction.rotate(-90)
        }

        if (phaseStrategy.isEnd()) {
            if (phaseStrategy is PhaseOneStrategy) {
                val unreachableWindow = (phaseStrategy as PhaseOneStrategy).unreachableWindows
                if (regressionWatcher.allTargetWindows.filterNot { unreachableWindow.contains(it) }.isNotEmpty()) {
                    phaseStrategy = PhaseTwoStrategy(this, budgetScale, delay, useCoordinateClicks, unreachableWindow)
                    regressionWatcher.updateStage1Info(eContext)
                    return eContext.resetApp()
                }
            } else if (phaseStrategy is PhaseTwoStrategy) {
                phaseStrategy = PhaseThreeStrategy(this,budgetScale, delay, useCoordinateClicks)
                regressionWatcher.updateStage2Info(eContext)
                return eContext.resetApp()
            } else if (phaseStrategy is PhaseThreeStrategy) {
                return ExplorationAction.terminateApp()
            }
        }
        log.info("Current abstract state: ${currentAbstractState.toString()}")
        log.info("Abstract State counts: ${AbstractStateManager.instance.ABSTRACT_STATES.size}")
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