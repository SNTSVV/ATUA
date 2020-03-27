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
import org.droidmate.exploration.modelFeatures.autaut.RegressionTestingMF
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.strategy.autaut.task.*
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.interaction.State

open class RegressionTestingStrategy @JvmOverloads constructor(priority: Int,
                                                               dictionary: List<String> = emptyList(),
                                                               useCoordinateClicks: Boolean = true
) : RandomWidget(priority, dictionary,useCoordinateClicks) {
    lateinit var eContext: ExplorationContext<*,*,*>

    protected val regressionWatcher: RegressionTestingMF
        get() = (eContext.findWatcher { it is RegressionTestingMF } as RegressionTestingMF)

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

    override suspend fun ExplorationContext<*, *, *>.computeCandidates(): List<Widget> {
        var candidates = strategyTask!!.chooseWidgets(eContext.getCurrentState())
        var nonCrashingWidgets = candidates.nonCrashingWidgets()
        this.lastTarget?.let { nonCrashingWidgets = nonCrashingWidgets.filterNot { p -> p.uid == it.uid } }
        candidates = candidates.filter { nonCrashingWidgets.contains(it) }
        log.debug("Available target Widgets: ${candidates.size}")
        return candidates
    }

    override suspend fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> chooseRandomWidget(eContext: ExplorationContext<M, S, W>): ExplorationAction {
        return chooseRegression(eContext)
    }
    var prevNode: AbstractState? = null
    var currentPhase: Int = 1
    internal fun<M: AbstractModel<S, W>,S: State<W>,W: Widget> chooseRegression(eContext: ExplorationContext<M,S,W>): ExplorationAction {
        var chosenAction: ExplorationAction = ExplorationAction.closeAndReturn()
        val currentWTGNode = regressionWatcher.getAbstractState(eContext.getCurrentState())
        if (currentWTGNode==null)
        {
            var action:ExplorationAction?=null
            runBlocking {
                action = super.chooseRandomWidget(eContext)
            }
            return action?:ExplorationAction.closeAndReturn()
        }
        if (phaseStrategy is PhaseOneStrategy && regressionWatcher.modifiedMethodCoverageFromLastChangeCount>50)
        {
            phaseStrategy = PhaseTwoStrategy(this,delay,useCoordinateClicks)
        }
        else if (phaseStrategy is PhaseTwoStrategy)
        {
            if ((phaseStrategy as PhaseTwoStrategy).attempt < 0)
                phaseStrategy = PhaseThreeStrategy(this,delay, useCoordinateClicks)
        }
        log.debug("Current activity: ${regressionWatcher.getStateActivity(eContext.getCurrentState())}")
        runBlocking {
            val availableWidgets = eContext.getCurrentState().widgets

            chosenAction = phaseStrategy.nextAction(eContext)
        }


        prevNode = regressionWatcher.getAbstractState(eContext.getCurrentState())
        if (!chosenAction.name.isPressMenu())
        {
            regressionWatcher.isRecentPressMenu = false
        }
        return chosenAction
    }






    override fun <M : AbstractModel<S, W>, S : State<W>, W : Widget> initialize(initialContext: ExplorationContext<M, S, W>) {
        super.initialize(initialContext)
        eContext = initialContext
        phaseStrategy = PhaseOneStrategy(this,delay, useCoordinateClicks)
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