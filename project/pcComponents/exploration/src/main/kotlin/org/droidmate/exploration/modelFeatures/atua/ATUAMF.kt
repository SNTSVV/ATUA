/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.ewtg.EWTG
import com.natpryce.konfig.PropertyGroup
import com.natpryce.konfig.booleanType
import com.natpryce.konfig.doubleType
import com.natpryce.konfig.getValue
import com.natpryce.konfig.stringType

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.droidmate.deviceInterface.exploration.*
import org.droidmate.exploration.ExplorationContext
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.pressBack
import org.droidmate.exploration.actions.swipeDown
import org.droidmate.exploration.actions.swipeLeft
import org.droidmate.exploration.actions.swipeRight
import org.droidmate.exploration.actions.swipeUp
import org.droidmate.exploration.modelFeatures.ModelFeature
import org.droidmate.exploration.modelFeatures.explorationWatchers.CrashListMF
import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.graph.StateGraphMF
import org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput.InputConfiguration
import org.droidmate.exploration.modelFeatures.atua.dstg.*
import org.droidmate.exploration.modelFeatures.atua.inputRepo.deviceEnvironment.DeviceEnvironmentConfiguration
import org.droidmate.exploration.modelFeatures.atua.inputRepo.intent.IntentFilter
import org.droidmate.exploration.modelFeatures.atua.ewtg.*
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Activity
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Dialog
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.FakeWindow
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Launcher
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.OutOfApp
import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.calm.ewtgdiff.EWTGDiff
import org.droidmate.exploration.modelFeatures.atua.helper.ProbabilityDistribution
import org.droidmate.exploration.modelFeatures.calm.AppModelLoader
import org.droidmate.exploration.modelFeatures.calm.modelReuse.ModelVersion
import org.droidmate.exploration.modelFeatures.calm.ModelBackwardAdapter
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class ATUAMF(
    private val appName: String,
    private val resourceDir: Path,
    private val manualInput: Boolean,
    private val manualIntent: Boolean,
    private val reuseBaseModel: Boolean,
    private val baseModelDir: Path,
    private val getCurrentActivity: suspend () -> String,
    private val getDeviceRotation: suspend () -> Int
) : ModelFeature() {
    val packageName = appName
    var portraitScreenSurface = Rectangle.empty()
    private var portraitVisibleScreenSurface = Rectangle.empty()
    private var landscapeScreenSurface = Rectangle.empty()
    private var landscapeVisibleScreenSurface = Rectangle.empty()
    private val targetWidgetFileName = "autaut-report.txt"
    override val coroutineContext: CoroutineContext = CoroutineName("RegressionTestingModelFeature") + Job()
    var statementMF: StatementCoverageMF? = null
    private var crashlist: CrashListMF? = null
    var wtg: EWTG = EWTG()
    lateinit var dstg: DSTG
    private var stateGraph: StateGraphMF? = null

    var isRecentItemAction: Boolean = false
    private var isRecentPressMenu: Boolean = false

    private var currentRotation: Rotation = Rotation.PORTRAIT

    private val widgetProbability = mutableMapOf<UUID, Double>() // probability of each widget invoking modified methods

    val allTargetStaticWidgets = hashSetOf<EWTGWidget>() //widgetId -> idWidget
    val notFullyExercisedTargetInputs = hashSetOf<Input>()
    val modifiedMethodsByWindow = hashMapOf<Window, HashSet<String>>()
    val allTargetHandlers = hashSetOf<String>()
    val allEventHandlers = hashSetOf<String>()
    val allModifiedMethod = hashMapOf<String, Boolean>()

    val allDialogOwners = hashMapOf<String, ArrayList<String>>() // window -> listof (Dialog)

    private val targetInputsByWindowClass = mutableMapOf<String, ArrayList<Input>>()

    val targetItemEvents = HashMap<Input, HashMap<String, Int>>()
    var isAlreadyRegisteringEvent = false
    private val stateActivityMapping = mutableMapOf<State<*>, String>()

    private val dateFormater = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private var lastExecutedTransition: AbstractTransition? = null
    private var necessaryCheckModel: Boolean = false

    private var isModelUpdated: Boolean = false
        private set


    val openNavigationCheck = ArrayList<AbstractState>()

    var appRotationSupport = true
    var internetStatus = true
    val abstractStateVisitCount = HashMap<AbstractState, Int>()
    private val windowVisitCount = HashMap<Window, Int>()
    private val stateVisitCount = HashMap<State<*>, Int>()
    var appPrevState: State<*>? = null
    var windowStack: Stack<Window> = Stack()
    private var abstractStateStack: Stack<Pair<AbstractState,State<*>?>> = Stack()
    val stateList: ArrayList<State<*>> = ArrayList()


    private val actionScore = HashMap<Triple<UUID?, String, String>, MutableMap<Window, Double>>()
    private val actionScore2 = HashMap<Input, Double>()
    val actionCount = ActionCount()
    var traceId = 0
    private var transitionId = 0

    val interactionsTracingMap = HashMap<List<Interaction<*>>, Pair<Int, Int>>()
    private val tracingInteractionsMap = HashMap<Pair<Int, Int>,List<Interaction<*>>>()

    private val prevWindowStateMapping = HashMap<State<*>,State<*>>()
    val interactionPrevWindowStateMapping = HashMap<Interaction<Widget>,State<*>>()


    private var lastOpeningAnotherAppInteraction: Interaction<Widget>? = null

    var updateMethodCovFromLastChangeCount: Int = 0
    private var updateStmtCovFromLastChangeCount: Int = 0
    private var methodCovFromLastChangeCount: Int = 0
    var stmtCovFromLastChangeCount: Int = 0
    private var lastUpdatedMethodCoverage: Double = 0.0
    private var lastMethodCoverage: Double = 0.0
    var lastUpdatedStatementCoverage: Double = 0.0


    val unreachableModifiedMethods = ArrayList<String>()

    val intentFilters = HashMap<String, ArrayList<IntentFilter>>()
    val targetIntFilters = HashMap<IntentFilter, Int>()
    var inputConfiguration: InputConfiguration? = null
    var deviceEnvironmentConfiguration: DeviceEnvironmentConfiguration? = null

    val inputWindowCorrelation = HashMap<Input, HashMap<Window, Double>>()
    val untriggeredTargetHiddenHandlers = hashSetOf<String>()


    private var phase1MethodCoverage: Double = 0.0
    private var phase2MethodCoverage: Double = 0.0
    private var phase1ModifiedMethodCoverage: Double = 0.0
    private var phase2ModifiedCoverage: Double = 0.0
    private var phase1StatementCoverage: Double = 0.0
    private var phase2StatementCoverage: Double = 0.0
    private var phase1ModifiedStatementCoverage: Double = 0.0
    private var phase2ModifiedStatementCoverage: Double = 0.0
    private var phase1Actions: Int = 0
    private var phase2Actions: Int = 0
    var phase3Actions: Int = 0
    private var phase2StartTime: String = ""
    private var phase3StartTime: String = ""

    private fun setPhase2StartTime() {
        phase2StartTime = dateFormater.format(System.currentTimeMillis())
    }

    private fun setPhase3StartTime() {
        phase3StartTime = dateFormater.format(System.currentTimeMillis())
    }


    fun getTargetIntentFilters(): List<IntentFilter> {
        return targetIntFilters.filter { it.value < 1 }.map { it.key }
    }

    private var mainActivity = ""


    /**
     * Mutex for synchronization
     *
     *
     */
    private val mutex = Mutex()
    private var trace: ExplorationTrace<*, *>? = null
    private var eContext: ExplorationContext<*, *, *>? = null
    private var fromLaunch = true
    private var firstRun = true

    //region Model feature override
    override suspend fun onAppExplorationFinished(context: ExplorationContext<*, *, *>) {
        this.join()
        produceTargetWidgetReport(context)
        ATUAModelOutput.dumpModel(context.model.config, this)
    }

    override fun onAppExplorationStarted(context: ExplorationContext<*, *, *>) {
        this.eContext = context
        this.trace = context.explorationTrace
        this.stateGraph = context.getOrCreateWatcher()
        this.statementMF = context.getOrCreateWatcher()
        this.crashlist = context.getOrCreateWatcher()

        StaticAnalysisJSONParser.readAppModel(getAppModelFile()!!, this, manualIntent, manualInput)
        removeDuplicatedWidgets()
        processOptionsMenusWindow()
        AbstractStateManager.INSTANCE.init(this, appName)
        AbstractStateManager.INSTANCE.initVirtualAbstractStates()
        if (reuseBaseModel) {
            log.info("Loading base model...")
            loadBaseModel()
            AbstractStateManager.INSTANCE.initVirtualAbstractStates()
        }

        postProcessingTargets()

        AbstractStateManager.INSTANCE.initAbstractInteractionsForVirtualAbstractStates()
        dstg.edges().forEach {
            if (it.label.source !is VirtualAbstractState && it.label.dest !is VirtualAbstractState) {
                AbstractStateManager.INSTANCE.addImplicitAbstractInteraction(
                        currentState = null,
                        abstractTransition = it.label,
                        transitionId = null)
            }
        }
        appPrevState = null

    }

    private fun postProcessingTargets() {
        untriggeredTargetHiddenHandlers.clear()
        allModifiedMethod.entries.removeIf { !statementMF!!.modMethodInstrumentationMap.containsKey(it.key) }
        allEventHandlers.addAll(windowHandlersHashMap.map { it.value }.flatten())
        WindowManager.instance.updatedModelWindows.forEach { window ->
            window.inputs.forEach { input ->
                val toremove = input.modifiedMethods.filter { !statementMF!!.modMethodInstrumentationMap.containsKey(it.key) }.keys
                toremove.forEach { method ->
                    input.modifiedMethods.remove(method)
                }
            }
            if (windowHandlersHashMap.containsKey(window)) {
                val hiddenHandlers = windowHandlersHashMap[window]!!.subtract(window.inputs.map { it.eventHandlers }.flatten())
                val targetHiddenHandlers = modifiedMethodTopCallersMap.values.flatten().distinct().intersect(hiddenHandlers)
                untriggeredTargetHiddenHandlers.addAll(targetHiddenHandlers)
            }
        }
        modifiedMethodTopCallersMap.entries.removeIf { !statementMF!!.modMethodInstrumentationMap.containsKey(it.key) }
        val targetHandlers = modifiedMethodTopCallersMap.values.flatten().distinct()

        /*untriggeredTargetHiddenHandlers.addAll(targetHandlers)*/
        modifiedMethodsByWindow.entries.removeIf { it.key is Launcher || it.key is OutOfApp }
        modifiedMethodsByWindow.entries.removeIf { entry ->
            entry.key.inputs.all { it.modifiedMethods.isEmpty() }
                    && (windowHandlersHashMap[entry.key] == null
                    || (
                    windowHandlersHashMap[entry.key] != null
                            && windowHandlersHashMap[entry.key]!!.all { !targetHandlers.contains(it) }
                    ))
        }
        WindowManager.instance.updatedModelWindows.filter { it.inputs.any { it.modifiedMethods.isNotEmpty() } }.forEach {
            modifiedMethodsByWindow.putIfAbsent(it, HashSet())
            val allUpdatedMethods = modifiedMethodsByWindow.get(it)!!
            it.inputs.forEach {
                if (it.modifiedMethods.isNotEmpty()) {
                    allUpdatedMethods.addAll(it.modifiedMethods.keys)
                    notFullyExercisedTargetInputs.add(it)
                }
            }
        }
        notFullyExercisedTargetInputs.removeIf {
            it.modifiedMethods.isEmpty()
        }
    }

    private fun removeDuplicatedWidgets() {
        WindowManager.instance.updatedModelWindows.forEach { window ->
            val workingList = Stack<EWTGWidget>()
            window.widgets.filter { it.children.isEmpty() }.forEach {
                workingList.add(it)
            }
            val roots = HashSet<EWTGWidget>()
            while (workingList.isNotEmpty()) {
                val widget = workingList.pop()
                if (widget.parent != null) {
                    workingList.push(widget.parent)
                } else {
                    roots.add(widget)
                }
                if (widget.children.isNotEmpty()) {
                    val childrenSignatures = widget.children.map { Pair(it,it.generateSignature()) }
                    childrenSignatures.groupBy { it.second }.filter{it.value.size>1 }.forEach { _, pairs ->
                        val keep = pairs.first().first
                        val removes = pairs.filter { it.first!=keep }
                        removes.map{it.first}. forEach {removewidget->
                            val relatedInputs = window.inputs.filter { it.widget == removewidget  }
                            relatedInputs.forEach {
                                it.widget = keep
                            }
                            window.widgets.remove(removewidget)
                        }
                    }
                }
            }
            val rootSignatures = roots.map {Pair(it,it.generateSignature())  }
            rootSignatures.groupBy { it.second }.filter{it.value.size>1 }.forEach { _, pairs ->
                val keep = pairs.first().first
                val removes = pairs.filter { it.first!=keep }
                removes.map{it.first}. forEach {removewidget->
                    val relatedInputs = window.inputs.filter { it.widget == removewidget  }
                    relatedInputs.forEach {
                        it.widget = keep
                    }
                    window.widgets.remove(removewidget)
                }
            }
        }
    }

    private fun loadBaseModel() {
        AppModelLoader.loadModel(baseModelDir.resolve(appName), this)
        ModelBackwardAdapter.instance.initialBaseAbstractStates.addAll(
                AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                    it.modelVersion == ModelVersion.BASE
                }
        )
        ModelBackwardAdapter.instance.initialBaseAbstractStates.forEach {
            ModelBackwardAdapter.instance.initialBaseAbstractTransitions.addAll(it.abstractTransitions.filter { it.isExplicit() })
        }
        val ewtgDiff = EWTGDiff.instance
        val ewtgDiffFile = getEWTGDiffFile(appName, resourceDir)
        if (ewtgDiffFile!=null)
            ewtgDiff.loadFromFile(ewtgDiffFile, this)
        AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.forEach { window, u ->
            u.values.forEach {
                it.computeHashCode()
            }
        }
        ModelBackwardAdapter.instance.keptBaseAbstractStates.addAll(
                AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter {
                    it.modelVersion == ModelVersion.BASE
                }
        )
        ModelBackwardAdapter.instance.keptBaseAbstractStates.forEach {
            ModelBackwardAdapter.instance.keptBaseAbstractTransitions.addAll(
                    it.abstractTransitions.filter { it.isExplicit() })
        }

    }

    private fun processOptionsMenusWindow() {
        WindowManager.instance.updatedModelWindows.filter {
            it is OptionsMenu
        }.forEach { menus ->
            val activity = WindowManager.instance.updatedModelWindows.find { it is Activity && it.classType == menus.classType }
            if (activity != null) {
                wtg.mergeNode(menus, activity)
            }
        }
        WindowManager.instance.updatedModelWindows.removeIf { w ->
            if (w is OptionsMenu) {
                wtg.removeVertex(w)
                true
            } else false
        }

        modifiedMethodsByWindow.entries.removeIf { !WindowManager.instance.updatedModelWindows.contains(it.key) }
    }



    override suspend fun onContextUpdate(context: ExplorationContext<*, *, *>) {
        //this.join()
        mutex.lock()
        try {
            log.info("RegressionTestingMF: Start OnContextUpdate")
            val interactions = ArrayList<Interaction<Widget>>()
            val lastAction = context.getLastAction()
            if (lastAction.actionType.isQueueEnd()) {
                val lastQueueStart = context.explorationTrace.getActions().last { it.actionType.isQueueStart() }
                val lastQueueStartIndex = context.explorationTrace.getActions().lastIndexOf(lastQueueStart)
                val lastLaunchAction = context.explorationTrace.getActions().last { it.actionType.isLaunchApp() || it.actionType == "ResetApp" }
                val lastLauchActionIndex = context.explorationTrace.getActions().lastIndexOf(lastLaunchAction)
                if (lastLauchActionIndex > lastQueueStartIndex) {
                    interactions.add(lastLaunchAction)
                } else {
                    context.explorationTrace.getActions()
                            .takeLast(context.explorationTrace.getActions().lastIndex - lastQueueStartIndex + 1)
                            .filterNot { it.actionType.isQueueStart() || it.actionType.isQueueEnd() || it.actionType.isFetch() }.let {
                                interactions.addAll(it)
                            }
                }
            } else {
                interactions.add(context.getLastAction())
            }
            if (interactions.any { it.actionType.isLaunchApp() || it.actionType == "ResetApp" }) {
                fromLaunch = true
                windowStack.clear()
                windowStack.push(Launcher.getOrCreateNode())
//                abstractStateStack.push(Pair(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window is Launcher}!!,stateList.findLast { it.isHomeScreen } ))
            } else {
                fromLaunch = false
            }
            isModelUpdated = false
            val prevState = context.getState(context.getLastAction().prevState) ?: context.model.emptyState
            val newState = context.getCurrentState()
            if (prevState == context.model.emptyState) {
                if (windowStack.isEmpty()) {
                    windowStack.push(Launcher.getOrCreateNode())
//                    abstractStateStack.push(Pair(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window is Launcher}!!,stateList.findLast { it.isHomeScreen } ))
                }
            } else {
                appPrevState = prevState
                if (prevState.isHomeScreen) {
                    if (retrieveScreenDimension(prevState)) {
                        AbstractStateManager.INSTANCE.ABSTRACT_STATES.removeIf {
                            it !is VirtualAbstractState && !it.loadedFromModel
                        }
                    }
                }
                if (!prevState.isHomeScreen && prevState.widgets.find { it.packageName == appName } != null) {


                    //getCurrentEventCoverage()
                    //val currentCov = statementMF!!.getCurrentCoverage()
                    val currentCov = statementMF!!.getCurrentMethodCoverage()
                    if (currentCov > lastMethodCoverage) {
                        methodCovFromLastChangeCount = 0
                        lastMethodCoverage = currentCov
                    } else {
                        methodCovFromLastChangeCount += 1
                    }
                    //val currentModifiedMethodStmtCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    val currentUpdatedMethodCov = statementMF!!.getCurrentModifiedMethodCoverage()
                    if (currentUpdatedMethodCov > lastUpdatedMethodCoverage) {
                        updateMethodCovFromLastChangeCount = 0
                        lastUpdatedMethodCoverage = currentUpdatedMethodCov
                    } else {
                        updateMethodCovFromLastChangeCount += 1
                    }
                    val currentUpdatedStmtCov = statementMF!!.getCurrentModifiedMethodStatementCoverage()
                    if (currentUpdatedStmtCov > lastUpdatedStatementCoverage) {
                        updateStmtCovFromLastChangeCount = 0
                        lastUpdatedStatementCoverage = currentUpdatedStmtCov
                    } else {
                        updateStmtCovFromLastChangeCount += 1
                    }
                }
            }
            if (windowStack.isEmpty()) {
                windowStack.push(Launcher.getOrCreateNode())
//                abstractStateStack.push(Pair(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window is Launcher}!!,stateList.findLast { it.isHomeScreen } ))
            }
            if (newState != context.model.emptyState) {
                if (newState.isAppHasStoppedDialogBox) {
                    log.debug("Encountering Crash state.")
                }
                if (newState.isHomeScreen) {
                    if (retrieveScreenDimension(newState)) {
                        AbstractStateManager.INSTANCE.ABSTRACT_STATES.removeIf {
                            it !is VirtualAbstractState
                                    && !it.loadedFromModel
                        }
                    }
                }
//                val notMarkedAsOccupied = newState.widgets.filter {
//                    it.metaInfo.contains("markedAsOccupied = false")
//                }
                currentRotation = computeRotation()
                lastExecutedTransition = null
                updateAppModel(prevState, newState, interactions, context)
                //validateModel(newState)
            }

        } finally {
            mutex.unlock()
        }
    }



    private fun retrieveScreenDimension(state: State<*>): Boolean {
        //get fullscreen app resolution
        val rotation = computeRotation()
        if (rotation == Rotation.PORTRAIT && portraitScreenSurface == Rectangle.empty()) {
            val fullDimension = Helper.computeGuiTreeDimension(state)
            val fullVisbleDimension = Helper.computeGuiTreeVisibleDimension(state)
            portraitScreenSurface = fullDimension
            portraitVisibleScreenSurface = fullVisbleDimension
            landscapeScreenSurface = Rectangle.create(fullDimension.topY, fullDimension.leftX, fullDimension.bottomY, fullDimension.rightX)
            landscapeVisibleScreenSurface = Rectangle.create(fullVisbleDimension.topY, fullVisbleDimension.leftX, fullVisbleDimension.bottomY, fullVisbleDimension.rightX)
            log.debug("Screen resolution: $portraitScreenSurface")
            return true
        } else if (rotation == Rotation.LANDSCAPE && landscapeScreenSurface == Rectangle.empty()) {
            val fullDimension = Helper.computeGuiTreeDimension(state)
            val fullVisbleDimension = Helper.computeGuiTreeVisibleDimension(state)
            landscapeScreenSurface = fullDimension
            landscapeVisibleScreenSurface = fullVisbleDimension
            portraitScreenSurface = Rectangle.create(fullDimension.topY, fullDimension.leftX, fullDimension.bottomY, fullDimension.rightX)
            portraitVisibleScreenSurface = Rectangle.create(fullVisbleDimension.topY, fullVisbleDimension.leftX, fullVisbleDimension.bottomY, fullVisbleDimension.rightX)
            log.debug("Screen resolution: $portraitScreenSurface")
            return true
        }
        return false
    }

    var isActivityResult = false
    private fun updateWindowStack(prevAbstractState: AbstractState?, prevState: State<*>, currentAbstractState: AbstractState, currentState: State<*>, isLaunch: Boolean) {
        if (isLaunch) {
            windowStack.clear()
//            abstractStateStack.clear()
            windowStack.push(Launcher.getOrCreateNode())
//            abstractStateStack.push(Pair(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window is Launcher }!!,stateList.findLast { it.isHomeScreen }))
            val homeScreenState = stateList.findLast { it.isHomeScreen }
            if (homeScreenState != null) {
                stateList.add(homeScreenState)
                prevWindowStateMapping.put(currentState,homeScreenState)
            }
        } else if (prevAbstractState != null) {
            if (windowStack.contains(currentAbstractState.window) && windowStack.size > 1) {
                // Return to the prev window
                // Pop the window
//                abstractStateStack.pop()
                while (windowStack.pop() != currentAbstractState.window) {
//                    abstractStateStack.pop()
                }

            } else {
                if (currentAbstractState.window is Launcher) {
                    windowStack.clear()
//                    abstractStateStack.clear()
                } else if (currentAbstractState.window != prevAbstractState.window) {
                    if (prevAbstractState.window is Activity || prevAbstractState.window is OutOfApp) {
                        windowStack.push(prevAbstractState.window)
//                        abstractStateStack.push(Pair(prevAbstractState,prevState))
                        prevWindowStateMapping.put(currentState,prevState)
                    }
                }/* else if (currentAbstractState.isOpeningKeyboard || currentAbstractState.isOpeningMenus) {
                    windowStack.push(currentAbstractState.window)

                    *//*abstractStateStack.push(Pair(currentAbstractState,currentState))*//*
                }*/
            }
        }
        /*if (currentAbstractState.window !is OutOfApp) {

        } */
/*        if (windowStack.isEmpty()) {
            windowStack.push(Launcher.getOrCreateNode())
            abstractStateStack.push(Pair(AbstractStateManager.instance.ABSTRACT_STATES.find { it.window is Launcher}!!,stateList.findLast { it.isHomeScreen } ))
            return
        }*/
    }

    private fun registerPrevWindowState(prevState: State<*>, currentState: State<*>, lastInteraction: Interaction<Widget>) {
        if (windowStack.isEmpty())
            return
        val prevWindow = windowStack.peek()
        if (prevWindow is Launcher) {
            val prevLaucherState = stateList.findLast { it.isHomeScreen }
            if (prevLaucherState!=null) {
                prevWindowStateMapping.put(currentState,prevLaucherState)
                interactionPrevWindowStateMapping.put(lastInteraction,prevLaucherState)
            }
            return
        }
        var tempCandidate: State<*>? = null
        for (i in transitionId downTo 1) {
            val traveredInteraction = tracingInteractionsMap.get(Pair(traceId,i))
            if (traveredInteraction == null)
                throw Exception()
            val prevWindowState = stateList.find { it.stateId == traveredInteraction.last().prevState }!!
            val prevWindowAbstractState = getAbstractState(prevWindowState)!!
            if (prevWindowAbstractState.window == prevWindow
                    && !prevWindowAbstractState.isOpeningKeyboard
                    && !prevWindowAbstractState.isOpeningMenus) {
                prevWindowStateMapping[currentState] = prevWindowState
                interactionPrevWindowStateMapping[lastInteraction] = prevWindowState
                break
            }
            if (tempCandidate!=null && prevWindowAbstractState.window != prevWindow) {
                prevWindowStateMapping[currentState] = tempCandidate
                interactionPrevWindowStateMapping[lastInteraction] = tempCandidate
                break
            }
            if (prevWindowAbstractState.window == prevWindow
                    && !prevWindowAbstractState.isOpeningMenus
                    && prevWindowAbstractState.isOpeningKeyboard) {
                tempCandidate = prevWindowState
            }
            if (i==1)
            {
                prevWindowStateMapping[currentState] = stateList.findLast { it.isHomeScreen }!!
                interactionPrevWindowStateMapping[lastInteraction] = stateList.findLast { it.isHomeScreen }!!
            }
        }
    }

    private fun computeRotation(): Rotation {
        /*val roots = newState.widgets.filter { !it.hasParent || it.resourceId=="android.id/content"}
        if (roots.isEmpty())
            return Rotation.PORTRAIT
        val root = roots.sortedBy { it.boundaries.height+it.boundaries.width }.last()
        val height = root.boundaries.height
        val width = root.boundaries.width
        if (height > width) {
            return Rotation.PORTRAIT
        }
        else
            return Rotation.LANDSCAPE*/
        var rotation = 0
        runBlocking {
            rotation = getDeviceRotation()
        }
        if (rotation == 0 || rotation == 2)
            return Rotation.PORTRAIT
        return Rotation.LANDSCAPE
    }

    val guiInteractionList = ArrayList<Interaction<Widget>>()

    private fun deriveAbstractInteraction(interactions: ArrayList<Interaction<Widget>>, prevState: State<*>, currentState: State<*>, statementCovered: Boolean) {
        log.info("Computing Abstract Interaction.")
        if (interactions.isEmpty())
            return
        val prevAbstractState = AbstractStateManager.INSTANCE.getAbstractState(prevState)
        val currentAbstractState = AbstractStateManager.INSTANCE.getAbstractState(currentState)!!
        if (prevAbstractState == null)
            return
        if (interactions.size == 1) {
            val interaction = interactions.first()
            if (!prevAbstractState.guiStates.any { it.stateId==interaction.prevState })
                throw Exception("Missing GUI States for interaction")
            if (!currentAbstractState.guiStates.any { it.stateId==interaction.resState })
                throw Exception("Missing GUI States for interaction")
            deriveSingleInteraction(prevAbstractState, interaction, currentAbstractState, prevState, currentState)

        } else {
            val actionType = AbstractActionType.ACTION_QUEUE
            val data = interactions
            val abstractAction = AbstractAction(
                    actionType = actionType,
                    attributeValuationMap = null,
                    extra = interactions
            )
            val abstractTransition = AbstractTransition(
                    abstractAction = abstractAction,
                    interactions = HashSet(),
                    isImplicit = false,
                    /*prevWindow = windowStack.peek(),*/
                    data = data,
                    source = prevAbstractState,
                    dest = currentAbstractState)
            dstg.add(prevAbstractState, currentAbstractState, abstractTransition)
            lastExecutedTransition = abstractTransition
        }

        if (lastExecutedTransition == null) {
            log.info("Not processed interaction: ${interactions.toString()}")
            return
        }
        if (lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.RESET_APP) {
            if (statementCovered || currentState != prevState) {
                transitionId++
                interactionsTracingMap[interactions] = Pair(traceId, transitionId)
                tracingInteractionsMap[Pair(traceId,transitionId)] = interactions
                lastExecutedTransition!!.tracing.add(Pair(traceId, transitionId))
            }
            // remove all obsolete abstract transitions that are not derived from interactions
            AbstractStateManager.INSTANCE.removeObsoleteAbsstractTransitions(lastExecutedTransition!!)
        }

        log.info("Computing Abstract Interaction. - DONE")

    }

    private fun deriveSingleInteraction(prevAbstractState: AbstractState, interaction: Interaction<Widget>, currentAbstractState: AbstractState, prevState: State<*>, currentState: State<*>) {
        if (!prevAbstractState.guiStates.any { it.stateId == interaction.prevState }) {
            log.debug("Prev Abstract State does not contain interaction's prev state.")
            log.debug("Abstract state: " + prevAbstractState)
            log.debug("Gui state: " + interaction.prevState)
        }
        if (!currentAbstractState.guiStates.any { it.stateId == interaction.resState }) {
            log.debug("Current Abstract State does not contain interaction' res state.")
            log.debug("Abstract state: " + currentAbstractState)
            log.debug("Gui state: " + interaction.resState)
        }
        /*val prevWindowAbstractState = if (!fromLaunch) {
            if (prevWindowStateMapping.containsKey(prevState))
                getAbstractState(prevWindowStateMapping.get(prevState)!!)
            else
                null
        } else
            null*/
        val prevWindowAbstractState: AbstractState? = getPrevWindowAbstractState(traceId,transitionId)

        if (isRecentPressMenu) {
            if (prevAbstractState != currentAbstractState) {
                if (prevAbstractState.hasOptionsMenu)
                    currentAbstractState.hasOptionsMenu = false
                else
                    currentAbstractState.hasOptionsMenu = true
            } else {
                prevAbstractState.hasOptionsMenu = false
            }
            isRecentPressMenu = false
        }
        val actionType: AbstractActionType = AbstractAction.normalizeActionType(interaction, prevState)
        val actionData = AbstractAction.computeAbstractActionExtraData(actionType, interaction, prevState, prevAbstractState, this)
        val interactionData = AbstractTransition.computeAbstractTransitionData(actionType,interaction,prevState,prevAbstractState,this)
        when (actionType) {
            AbstractActionType.LAUNCH_APP -> {
                AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = currentState
                if (AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] == null) {
                    AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] = currentState
                    currentAbstractState.isInitalState = true
                }
            }
            AbstractActionType.RESET_APP -> {
                AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] = currentState
                if (AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] == null) {
                    AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = currentState
                }
                currentAbstractState.isInitalState = true
            }
            else -> {
                currentAbstractState.isInitalState = false
            }
        }
        if (interaction.targetWidget == null) {
            val allAbstractTransitions = dstg.edges(prevAbstractState)
            if (actionType == AbstractActionType.RESET_APP || actionType == AbstractActionType.LAUNCH_APP) {
                if (actionType == AbstractActionType.RESET_APP)
                    setNewTrace()
                deriveLaunchOrResetInteraction( prevState, allAbstractTransitions, actionType, null, currentAbstractState, interaction, prevAbstractState)
            } else {
                deriveNonLaunchAndResetNullTargetInteraction(prevState, allAbstractTransitions, actionType ,actionData , interactionData, currentAbstractState, interaction, prevAbstractState,prevWindowAbstractState)
            }
        } else {
            actionCount.updateWidgetActionCounter(prevAbstractState, prevState, interaction)
            val widgetGroup = prevAbstractState.getAttributeValuationSet(interaction.targetWidget!!, prevState, this)
            if (widgetGroup != null) {
                deriveWidgetInteraction(prevAbstractState, actionType, widgetGroup, actionData, currentAbstractState, interaction, interactionData, prevWindowAbstractState, prevState)
            } else {
                if (Helper.hasParentWithType(interaction.targetWidget!!, prevState, "WebView")) {
                    deriveWebViewInteraction(interaction, prevState, prevAbstractState, actionType, actionData, currentAbstractState, interactionData, prevWindowAbstractState)
                }
                else if (actionType == AbstractActionType.RANDOM_KEYBOARD) {
                    deriveKeyboardInteraction(prevAbstractState, actionType, actionData, interactionData, currentAbstractState, interaction, prevState,prevWindowAbstractState)
                } else {
                    val underviceAction = AbstractActionType.UNKNOWN
                    createNewAbstractTransition(underviceAction, interaction, prevState, prevAbstractState, null, interaction, currentAbstractState,prevWindowAbstractState)
                    log.debug("Cannot find the target widget's AVM")
                    prevAbstractState.getAttributeValuationSet(interaction.targetWidget!!, prevState, this)
                }
            }
        }
        if (lastExecutedTransition!=null) {
            if (!lastExecutedTransition!!.source.guiStates.any { it.stateId == interaction.prevState })
                throw Exception("Missing GUI States for interaction")
            if (!lastExecutedTransition!!.dest.guiStates.any { it.stateId == interaction.resState })
                throw Exception("Missing GUI States for interaction")
            updateActionScore(currentState, prevState, interaction)
        } else {
            log.warn("No abstract transition derived")
        }
    }

    fun getPrevWindowAbstractState(traceId: Int, transitionId: Int): AbstractState? {
        val prevWindowAbstractState: AbstractState?
        if (transitionId < 1) {
            prevWindowAbstractState = null
        } else {
            val traveredInteraction =  tracingInteractionsMap.get(Pair(traceId,transitionId))
            if (traveredInteraction == null)
                throw Exception()
            if (!interactionPrevWindowStateMapping.containsKey(traveredInteraction.last())) {
                prevWindowAbstractState = null
            } else {
                val prevWindowState = interactionPrevWindowStateMapping.get(traveredInteraction.last())!!
                prevWindowAbstractState = getAbstractState(prevWindowState)
            }
        }
        return prevWindowAbstractState
    }

    private fun deriveKeyboardInteraction(prevAbstractState: AbstractState, actionType: AbstractActionType, actionData: Any?, interactionData: Any?, currentAbstractState: AbstractState, interaction: Interaction<Widget>, prevState: State<*>, prevWindowAbstractState: AbstractState?) {
        val explicitInteractions = prevAbstractState.abstractTransitions.filter { it.isImplicit == false }
        val existingTransition = AbstractTransition.findExistingAbstractTransitions(
                explicitInteractions,
                AbstractAction(actionType,null,actionData),
                prevAbstractState,
                currentAbstractState,
                false
        )
        if (existingTransition != null) {
            lastExecutedTransition = existingTransition
            updateExistingAbstractTransition(existingTransition, interaction, interactionData, prevWindowAbstractState)
        } else {
            //No recored abstract interaction before
            //Or the abstractInteraction is implicit
            //Record new AbstractInteraction
            createNewAbstractTransition(actionType, interaction, prevState, prevAbstractState, null, interactionData, currentAbstractState,prevWindowAbstractState)
        }
    }

    private fun deriveWebViewInteraction(interaction: Interaction<Widget>, prevState: State<*>, prevAbstractState: AbstractState, actionType: AbstractActionType, actionData: Any?, currentAbstractState: AbstractState, interactionData: Any?, prevWindowAbstractState: AbstractState?) {
        val webViewWidget = Helper.tryGetParentHavingClassName(interaction.targetWidget!!, prevState, "WebView")
        if (webViewWidget != null) {
            val avm = prevAbstractState.getAttributeValuationSet(webViewWidget, prevState, this)
            if (avm == null) {
                log.debug("Cannot find WebView's AVM")
            }
            else {
                val explicitInteractions = prevAbstractState.abstractTransitions.filter { it.isImplicit == false }
                val existingTransition = AbstractTransition.findExistingAbstractTransitions(
                        abstractTransitionSet = explicitInteractions,
                        abstractAction = AbstractAction(actionType, avm, actionData),
                        source = prevAbstractState,
                        dest = currentAbstractState,
                        isImplicit = false
                )
                if (existingTransition != null) {
                    lastExecutedTransition = existingTransition
                    updateExistingAbstractTransition(existingTransition, interaction, interactionData, prevWindowAbstractState)
                } else {
                    //No recored abstract interaction before
                    //Or the abstractInteraction is implicit
                    //Record new AbstractInteraction
                    createNewAbstractTransition(actionType, interaction, prevState, prevAbstractState, avm, interactionData, currentAbstractState, prevWindowAbstractState)
                }
            }
        }
    }

    private fun deriveWidgetInteraction(prevAbstractState: AbstractState, actionType: AbstractActionType, widgetGroup: AttributeValuationMap?, actionData: Any?, currentAbstractState: AbstractState, interaction: Interaction<Widget>, interactionData: Any?, prevWindowAbstractState: AbstractState?, prevState: State<*>) {
        val explicitInteractions = prevAbstractState.abstractTransitions.filter { it.isImplicit == false }

        val existingTransition = AbstractTransition.findExistingAbstractTransitions(
                explicitInteractions,
                AbstractAction(actionType,widgetGroup,actionData),
                prevAbstractState,
                currentAbstractState,
                false
        )
        if (existingTransition != null) {
            lastExecutedTransition = existingTransition
            updateExistingAbstractTransition(
                    existingTransition, interaction, interactionData, prevWindowAbstractState
            )
        } else {
            //No recored abstract interaction before
            //Or the abstractInteraction is implicit
            //Record new AbstractInteraction
            createNewAbstractTransition(actionType, interaction, prevState, prevAbstractState, widgetGroup, interactionData, currentAbstractState,prevWindowAbstractState)
        }
    }

    private fun updateExistingAbstractTransition(existingTransition: AbstractTransition, interaction: Interaction<Widget>, interactionData: Any?, prevWindowAbstractState: AbstractState?) {
        existingTransition.interactions.add(interaction)
        if (interactionData != null) {
            existingTransition.data = interactionData
            // TODO update data to array
        }
        if (prevWindowAbstractState != null && !existingTransition.dependentAbstractStates.contains(prevWindowAbstractState)) {
            existingTransition.dependentAbstractStates.add(prevWindowAbstractState)
        }
    }

    private fun setNewTrace() {
        traceId++
        transitionId = 0
    }

    var newWidgetScore = 1000.00
    var newActivityScore = 5000.00
    var coverageIncreaseScore = 1000.00
    private fun updateActionScore(currentState: State<*>, prevState: State<*>, interaction: Interaction<Widget>) {
        //newWidgetScore+=10
        val currentAbstractState = getAbstractState(currentState)
        if (currentAbstractState == null) {
            return
        }
        var reward = 0.0
        if (!windowVisitCount.containsKey(currentAbstractState.window)) {
            return
        }
        if (windowVisitCount[currentAbstractState.window] == 1 && currentAbstractState.window is Activity) {
            reward += newActivityScore
            newActivityScore *= 1.1
        }

        /*if (coverageIncreased > 0) {
            reward+=coverageIncreaseScore
            coverageIncreaseScore*=1.1
        }*/
        //init score for new state
        var newWidgetCount = 0
        val actionableWidgets = Helper.getActionableWidgetsWithoutKeyboard(currentState).filter { !Helper.isUserLikeInput(it) }
        if (eContext!!.explorationCanMoveOn()) {
            currentAbstractState.inputMappings.forEach { abstractAction, inputs ->
                inputs.filter { it.eventType!=EventType.resetApp }.forEach {
                    if(!actionScore2.containsKey(it)) {
                        actionScore2[it] = newWidgetScore
                    }
                }
            }
            actionableWidgets.groupBy { it.uid }.forEach { uid, w ->
                val actions = Helper.getScoredAvailableActionsForWidget(w.first(), currentState, 0, false)
                actions.forEach { action ->
                    val widget_action = Triple(uid, action.first,action.second)
                    actionScore.putIfAbsent(widget_action, HashMap())
                    /*if (unexercisedWidgetCnt.contains(w)) {
                    reward += 10
                }*/
                    if (actionScore[widget_action]!!.values.isNotEmpty()) {
                        val avgScore = actionScore[widget_action]!!.values.average()
                        actionScore[widget_action]!!.putIfAbsent(currentAbstractState.window, avgScore)
                    } else {
                        val score = if (normalizeActionType(action.first) == AbstractActionType.LONGCLICK) {
                            0.0
                        } else {
                            newWidgetScore
                        }
                        actionScore[widget_action]!!.putIfAbsent(currentAbstractState.window, score)
                        if (!currentAbstractState.isOutOfApplication) {
                            newWidgetCount++
                        }
                    }
                }
            }
            val pressBackAction = Triple<UUID?, String,String>(null,"PressBack","")
            val pressMenuAction = Triple<UUID?, String,String>(null, "PressMenu","")
            val minimizeMaximizeAction = Triple<UUID?, String,String>(null, "MinimizeMaximize","")

            actionScore.putIfAbsent(pressBackAction, HashMap())
            //actionScore.putIfAbsent(pressMenuAction, HashMap())
            actionScore[pressBackAction]!!.putIfAbsent(currentAbstractState.window, newWidgetScore)

            //actionScore[pressMenuAction]!!.putIfAbsent(currentAbstractState.window, newWidgetScore)

        }

        if (newWidgetCount == 0)
            reward -= 1000
        else
            reward += 1000
/*        if (stateVisitCount[currentState]!! == 1 && !currentState.widgets.any { it.isKeyboard }) {
            // this is a new state
            reward += newWidgetScore
        }*/
        /*if (!isScoreAction(interaction, prevState)) {
            return
        }*/
        val widget = interaction.targetWidget

        val prevAbstractState = getAbstractState(prevState)
        if (prevAbstractState == null) {
            return
        }
        if (currentAbstractState.isRequestRuntimePermissionDialogBox)
            return
        val normalizeActionType = AbstractAction.normalizeActionType(interaction, prevState)
        if (normalizeActionType == AbstractActionType.TEXT_INSERT)
            return
        val data = if (normalizeActionType == AbstractActionType.SWIPE) {
            val swipeData= Helper.parseSwipeData(interaction.data)
            Helper.getSwipeDirection(swipeData[0],swipeData[1])
        } else
            interaction.data
        val widget_action = Triple(widget?.uid, interaction.actionType,data)
        actionScore.putIfAbsent(widget_action, HashMap())
        actionScore[widget_action]!!.putIfAbsent(prevAbstractState.window, newWidgetScore)
        val currentScore = actionScore[widget_action]!![prevAbstractState.window]!!
        /*if (coverageIncreased ==0) {
            reward -= coverageIncreaseScore
        }*/
        if (prevState == currentState) {
            val newScore = currentScore - 0.9 * (currentScore)
            actionScore[widget_action]!![prevAbstractState.window] = newScore
        } else {
            val maxCurrentStateScore: Double
            val currentStateWidgetScores = actionScore.filter { (actionableWidgets.any { w -> w.uid == it.key.first } || it.key.first == null) && it.value.containsKey(currentAbstractState.window) }
            if (currentStateWidgetScores.isNotEmpty())
                maxCurrentStateScore = currentStateWidgetScores.map { it.value.get(currentAbstractState.window)!! }.max()!!
            else
                maxCurrentStateScore = 0.0
            val newScore = currentScore + 0.5 * (reward + 0.9 * maxCurrentStateScore - currentScore)
            actionScore[widget_action]!![prevAbstractState.window] = newScore
        }
        if (lastExecutedTransition!=null) {
            val executedInputs = prevAbstractState.inputMappings.get(lastExecutedTransition!!.abstractAction)
            executedInputs?.filter { it.eventType != EventType.resetApp }?.forEach {
                val currentScore1 = actionScore2.get(it)
                if (currentScore1!=null) {
                    if (prevState == currentState) {
                        actionScore2[it] = currentScore1 - 0.9* (currentScore1)
                    } else {
                        val maxCurrentStateScore = actionScore2.filter { currentAbstractState.inputMappings.values.flatten().contains(it.key) }.entries.maxBy { it.value }
                        val newScore = currentScore1 + 0.5 * (reward + 0.9 * (maxCurrentStateScore?.value?:0.0) - currentScore1)
                        actionScore2[it] = newScore
                    }
                }
            }
        }
    }

    private fun isScoreAction(interaction: Interaction<Widget>, prevState: State<*>): Boolean {
        if (interaction.targetWidget != null)
            return true
        val actionType = AbstractAction.normalizeActionType(interaction, prevState)
        return when (actionType) {
            AbstractActionType.PRESS_MENU, AbstractActionType.PRESS_BACK-> true
            else -> false
        }

    }



    private fun deriveNonLaunchAndResetNullTargetInteraction(prevState: State<*>,
                                                             allAbstractTransitions: List<Edge<AbstractState, AbstractTransition>>,
                                                             actionType: AbstractActionType,
                                                             actionData: Any?,
                                                             interactionData: Any?,
                                                             currentAbstractState: AbstractState,
                                                             interaction: Interaction<Widget>,
                                                             prevAbstractState: AbstractState,
                                                             prevWindowAbstractState: AbstractState?) {
        val abstractTransition = AbstractTransition.findExistingAbstractTransitions(
                abstractTransitionSet = allAbstractTransitions.map { it.label },
                abstractAction = AbstractAction(actionType,null,actionData),
                source = prevAbstractState,
                dest = currentAbstractState,
                isImplicit = false)
        if (abstractTransition != null) {
            lastExecutedTransition = abstractTransition
            updateExistingAbstractTransition(
                    abstractTransition,interaction, interactionData, prevWindowAbstractState
            )
        } else {
            createNewAbstractTransition( actionType, interaction, prevState, prevAbstractState, null, interactionData, currentAbstractState,prevWindowAbstractState)
        }

    }

    private fun deriveLaunchOrResetInteraction(prevGuiState: State<Widget>, allAbstractTransitions: List<Edge<AbstractState, AbstractTransition>>, actionType: AbstractActionType, actionData: Any?, currentAbstractState: AbstractState, interaction: Interaction<Widget>, prevAbstractState: AbstractState) {
        if (actionType == AbstractActionType.LAUNCH_APP) {
            val abstractTransition = AbstractTransition.findExistingAbstractTransitions(
                    abstractTransitionSet =  allAbstractTransitions.map { it.label },
                    abstractAction =  AbstractAction(actionType,null,null),
                    source = prevAbstractState,
                    dest = currentAbstractState,
                    isImplicit = false
            )
            if (abstractTransition != null) {
                lastExecutedTransition = abstractTransition
                lastExecutedTransition!!.interactions.add(interaction)
            } else {
                val attributeValuationMap: AttributeValuationMap? = null
                createNewAbstractTransition( actionType, interaction, prevGuiState, prevAbstractState, attributeValuationMap, null,currentAbstractState,null)
            }
        }
        AbstractStateManager.INSTANCE.updateLaunchAndResetTransition()
    }

    private fun createNewAbstractTransition(actionType: AbstractActionType,
                                            interaction: Interaction<Widget>,
                                            prevGuiState: State<Widget>,
                                            sourceAbstractState: AbstractState,
                                            attributeValuationMap: AttributeValuationMap?,
                                            interactionData: Any?,
                                            destAbstractState: AbstractState,
                                            prevWindowAbstractState: AbstractState?) {
        val lastExecutedAction = AbstractAction.getOrCreateAbstractAction(actionType, interaction, prevGuiState, sourceAbstractState, attributeValuationMap,this)
        val prevWindow = windowStack.peek()
        val newAbstractInteraction = AbstractTransition(
                abstractAction = lastExecutedAction,
                interactions = HashSet(),
                isImplicit = false,
                /*prevWindow = prevWindow,*/
                data = interactionData,
                source = sourceAbstractState,
                dest = destAbstractState)
        newAbstractInteraction.interactions.add(interaction)
        if (prevWindowAbstractState!=null)
            newAbstractInteraction.dependentAbstractStates.add(prevWindowAbstractState)
        dstg.add(sourceAbstractState, destAbstractState, newAbstractInteraction)
        if (!sourceAbstractState.inputMappings.containsKey(newAbstractInteraction.abstractAction)
                && !newAbstractInteraction.abstractAction.isActionQueue()
                && !newAbstractInteraction.abstractAction.isLaunchOrReset()) {
            Input.createInputFromAbstractInteraction(sourceAbstractState, destAbstractState, newAbstractInteraction, interaction,wtg)
        }
        newAbstractInteraction.updateGuardEnableStatus()
        lastExecutedTransition = newAbstractInteraction
    }


    var prevAbstractStateRefinement: Int = 0

    private fun normalizeActionType(actionName: String): AbstractActionType {
        val actionType = actionName
        var abstractActionType = when (actionType) {
            "Tick" -> AbstractActionType.CLICK
            "ClickEvent" -> AbstractActionType.CLICK
            "LongClickEvent" -> AbstractActionType.LONGCLICK
            else -> AbstractActionType.values().find { it.actionName.equals(actionType) }
        }
        if (abstractActionType == null) {
            throw Exception("No abstractActionType for $actionType")
        }
        return abstractActionType
    }

    private fun compareDataOrNot(abstractTransition: AbstractTransition, actionType: AbstractActionType): Boolean {
        if (actionType == AbstractActionType.SEND_INTENT || actionType == AbstractActionType.SWIPE) {
            return abstractTransition.data == actionType
        }
        return true
    }


    //endregion


    private val stateFailedDialogs = arrayListOf<Pair<State<*>, String>>()


    private val unreachableTargetComponentState = arrayListOf<State<*>>()
    fun addUnreachableTargetComponentState(state: State<*>) {
        log.debug("Add unreachable target component activity: ${stateActivityMapping[state]}")
        if (unreachableTargetComponentState.find { it.equals(state) } == null)
            unreachableTargetComponentState.add(state)
    }


    private fun computeAbstractState(newState: State<*>, explorationContext: ExplorationContext<*, *, *>): AbstractState {

        var currentActivity: String = ""
        runBlocking {
            currentActivity = getCurrentActivity()
        }
        if (activityAlias.containsKey(currentActivity))
            currentActivity = activityAlias[currentActivity]!!
        if (mainActivity == "") {
            mainActivity = explorationContext.apk.launchableMainActivityName
        }
        stateActivityMapping[newState] = currentActivity
        log.info("Computing Abstract State.")
        val newAbstractState = AbstractStateManager.INSTANCE.getOrCreateNewAbstractState(
                newState, currentActivity, currentRotation, null)
        if(getAbstractState(newState)==null)
            throw Exception("State has not been derived")
        AbstractStateManager.INSTANCE.updateLaunchAndResetAbstractTransitions(newAbstractState)
        increaseNodeVisit(abstractState = newAbstractState)
        log.info("Computing Abstract State. - DONE")
        return newAbstractState
    }

    private fun increaseNodeVisit(abstractState: AbstractState) {
        if (!windowVisitCount.containsKey(abstractState.window)) {
            windowVisitCount[abstractState.window] = 1

        } else {
            windowVisitCount[abstractState.window] = windowVisitCount[abstractState.window]!! + 1
        }
        if (!abstractStateVisitCount.contains(abstractState)) {
            abstractStateVisitCount[abstractState] = 1
        } else {
            abstractStateVisitCount[abstractState] = abstractStateVisitCount[abstractState]!! + 1
        }
        increaseVirtualAbstractStateVisitCount(abstractState)

    }

    private fun increaseVirtualAbstractStateVisitCount(abstractState: AbstractState) {
        val virtualAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.find {
            it.window == abstractState.window
                    && it is VirtualAbstractState
        }
        if (virtualAbstractState != null) {
            if (!abstractStateVisitCount.contains(virtualAbstractState)) {
                abstractStateVisitCount[virtualAbstractState] = 1
                increaseVirtualAbstractStateVisitCount(virtualAbstractState)
            } else {
                abstractStateVisitCount[virtualAbstractState] = abstractStateVisitCount[virtualAbstractState]!! + 1
            }
        }
    }
    /**
     * Return: True if model is updated, otherwise False
     */
    private fun updateAppModel(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>, context: ExplorationContext<*, *, *>): Boolean {
        //update lastChildExecutedEvent
        log.info("Updating App Model")
        measureTimeMillis {
            runBlocking {
                while (!statementMF!!.statementRead) {
                    delay(1)
                }
            }
        }.let {
            log.debug("Wait for reading coverage took $it millis")
        }
        var updated: Boolean = false
        val statementCovered: Boolean
        if (statementMF!!.recentExecutedStatements.isEmpty())
            statementCovered = false
        else
            statementCovered = true
        measureTimeMillis {
            statementMF!!.statementRead = false
            val currentAbstractState = computeAbstractState(newState, context)
            stateList.add(newState)


            stateVisitCount.putIfAbsent(newState, 0)
            stateVisitCount[newState] = stateVisitCount[newState]!! + 1
            actionCount.initWidgetActionCounterForNewState(newState)
            var prevAbstractState = getAbstractState(prevState)
            if (prevAbstractState == null && prevState != context.model.emptyState) {
                prevAbstractState = computeAbstractState(prevState, context)
                stateList.add(stateList.size - 1, prevState)
            }
            necessaryCheckModel = true
            if (!newState.isHomeScreen && firstRun) {
                AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH] = newState
                AbstractStateManager.INSTANCE.launchStates[AbstractStateManager.LAUNCH_STATE.NORMAL_LAUNCH] = newState
                firstRun = false
                necessaryCheckModel = false
            }
            if (newState.isAppHasStoppedDialogBox) {
                necessaryCheckModel = false
            }
            if (newState.isRequestRuntimePermissionDialogBox) {
                necessaryCheckModel = false
            }
            if (lastInteractions.isNotEmpty()) {
                lastExecutedTransition = null
                isActivityResult = false
                if (windowStack.contains(currentAbstractState.window) && windowStack.size > 1
                        && !(prevAbstractState?.isOpeningKeyboard?:false)
                        && !currentAbstractState.isOpeningMenus) {
                    // Return to the prev window
                    isActivityResult = true
                }
                deriveAbstractInteraction(ArrayList(lastInteractions), prevState, newState, statementCovered)
                updateWindowStack(prevAbstractState,prevState, currentAbstractState,newState, fromLaunch)
                registerPrevWindowState(prevState,newState,lastInteractions.last())
                //update lastExecutedEvent
                if (lastExecutedTransition == null) {
                    log.debug("lastExecutedEvent is null")
                    updated = false
                } else {
                    if (reuseBaseModel) {
                        val prevWindowAbstractState: AbstractState? = getPrevWindowAbstractState(traceId,transitionId-1)
                        ModelBackwardAdapter.instance.checkingEquivalence(newState,currentAbstractState, lastExecutedTransition!!,prevWindowAbstractState ,dstg)
                    }
                    if (prevAbstractState!!.belongToAUT() && currentAbstractState.isOutOfApplication && lastInteractions.size > 1) {
                        lastOpeningAnotherAppInteraction = lastInteractions.single()
                    }
                    if (!prevAbstractState.belongToAUT() && currentAbstractState.belongToAUT()) {
                        lastOpeningAnotherAppInteraction = null
                    }
                    updated = updateAppModelWithLastExecutedEvent(prevState, newState, lastInteractions)

                }

                // derive dialog type
                if (prevAbstractState != null && lastExecutedTransition != null) {
                    DialogBehaviorMonitor.instance.detectDialogType(lastExecutedTransition!!, prevState, newState)
                }
                if (lastExecutedTransition == null) {
                    log.debug("Last executed Interaction is null")
                } else if (necessaryCheckModel
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.UNKNOWN
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.WAIT
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.ACTION_QUEUE
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.RESET_APP
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.LAUNCH_APP
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.SEND_INTENT
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.RANDOM_CLICK
                        && lastExecutedTransition!!.abstractAction.actionType != AbstractActionType.RANDOM_KEYBOARD
                        && lastInteractions.size==1) {
                    log.info("Refining Abstract Interaction.")
                    prevAbstractStateRefinement = AbstractStateManager.INSTANCE.refineModel(lastInteractions.single(), prevState, lastExecutedTransition!!)

                    log.info("Refining Abstract Interaction. - DONE")
                } else {
                    log.debug("Return to a previous state. Do not need refine model.")
                }

            } else {
                updated = false
            }

        }.let {
            log.debug("Update model took $it  millis")
        }
        return updated
    }

    var checkingDialog: Dialog? = null

    private fun updateAppModelWithLastExecutedEvent(prevState: State<*>, newState: State<*>, lastInteractions: List<Interaction<*>>): Boolean {
        assert(statementMF != null, { "StatementCoverageMF is null" })
        val prevAbstractState = getAbstractState(prevState)
        if (prevAbstractState == null) {
            return false
        }
        val newAbstractState = getAbstractState(newState)!!
        if (lastExecutedTransition != null) {
            prevAbstractState.increaseActionCount2(lastExecutedTransition!!.abstractAction,true)
            AbstractStateManager.INSTANCE.addImplicitAbstractInteraction(
                    newState, lastExecutedTransition!!, Pair(traceId,transitionId))
        }
        val abstractInteraction = lastExecutedTransition!!


        //Extract text input widget data
        val condition = HashMap(Helper.extractInputFieldAndCheckableWidget(prevState))
        val edge = dstg.edge(prevAbstractState, newAbstractState, abstractInteraction)
        if (edge == null)
            return false
        if (condition.isNotEmpty()) {
            if (!edge.label.userInputs.contains(condition)) {
                edge.label.userInputs.add(condition)
            }
        }
        //if (!abstractInteraction.abstractAction.isActionQueue())
        updateCoverage(prevAbstractState, newAbstractState, abstractInteraction, lastInteractions.first())
        //create StaticEvent if it dose not exist in case this abstract Interaction triggered modified methods

        if (!prevAbstractState.isRequestRuntimePermissionDialogBox) {
            val coverageIncreased = statementMF!!.executedModifiedMethodStatementsMap.size - statementMF!!.prevUpdateCoverage
            if (prevAbstractState.isOutOfApplication && newAbstractState.belongToAUT() && !abstractInteraction.abstractAction.isLaunchOrReset() && lastOpeningAnotherAppInteraction != null) {
                val lastAppState = stateList.find { it.stateId == lastOpeningAnotherAppInteraction!!.prevState }!!
                val lastAppAbstractState = getAbstractState(lastAppState)!!
                val lastOpenningAnotherAppAbstractInteraction = findAbstractInteraction(lastOpeningAnotherAppInteraction)
                updateWindowTransitionCoverage(lastAppAbstractState, lastOpenningAnotherAppAbstractInteraction!!, coverageIncreased)
            } else
                updateWindowTransitionCoverage(prevAbstractState, abstractInteraction, coverageIncreased)

        }

        /* if (allTargetWindow_ModifiedMethods.containsKey(prevAbstractState.window) &&
                 newAbstractState.window.activityClass == prevAbstractState.window.activityClass
                 ) {
             if (!allTargetWindow_ModifiedMethods.containsKey(newAbstractState.window)) {
                 allTargetWindow_ModifiedMethods.put(newAbstractState.window, allTargetWindow_ModifiedMethods[prevAbstractState.window]!!)
                 if (windowHandlersHashMap.containsKey(prevAbstractState.window)) {
                     windowHandlersHashMap.put(newAbstractState.window, windowHandlersHashMap[prevAbstractState.window]!!)
                 }
             }
         }*/


        return true
    }

    private fun updateWindowTransitionCoverage(prevAbstractState: AbstractState, abstractTransition: AbstractTransition, coverageIncreased: Int) {
        val event = prevAbstractState.inputMappings[abstractTransition.abstractAction]
        if (event != null) {
            event.forEach {
                if (it.eventType != EventType.resetApp) {
                    updateStaticEventHandlersAndModifiedMethods(it, abstractTransition, coverageIncreased)
                    if (it.modifiedMethods.all {statementMF!!.fullyCoveredMethods.contains(it.key)  }) {
                        if (notFullyExercisedTargetInputs.contains(it))
                            notFullyExercisedTargetInputs.remove(it)
                    } else {
                        if (it.modifiedMethods.isNotEmpty() && !notFullyExercisedTargetInputs.contains(it))
                            notFullyExercisedTargetInputs.add(it)
                    }
                    modifiedMethodsByWindow.entries.removeIf {
                        it.value.all { statementMF!!.fullyCoveredMethods.contains(it) }
                    }

                }
            }
        }

        if (abstractTransition.modifiedMethods.isNotEmpty()) {
            var updateTargetWindow = true
            /*if (prevAbstractState.window is Dialog) {
                val dialog = prevAbstractState.window as Dialog
                val activity = dialog.activityClass
                val activityNode = WindowManager.instance.updatedModelWindows.find { it is OutOfApp && it.activityClass == activity }
                if (activityNode != null) {
                    updateTargetWindow = true
                }
            }*/
            if (updateTargetWindow) {
                updateTargetWindow(prevAbstractState.window, abstractTransition, prevAbstractState)
                if (!prevAbstractState.window.isTargetWindowCandidate()) {
                    updateTargetWindow(abstractTransition.dest.window, abstractTransition, prevAbstractState)
                }
            }
        }

    }

    private fun updateTargetWindow(toUpdateWindow: Window, abstractTransition: AbstractTransition, prevAbstractState: AbstractState) {
        if (!modifiedMethodsByWindow.contains(toUpdateWindow)) {
            modifiedMethodsByWindow[toUpdateWindow] = hashSetOf()
        }
        val windowModifiedMethods = modifiedMethodsByWindow.get(toUpdateWindow)!!
        abstractTransition.modifiedMethods.forEach { m, _ ->
            windowModifiedMethods.add(m)
        }
        val virtualAbstractState = AbstractStateManager.INSTANCE.ABSTRACT_STATES.filter { it is VirtualAbstractState && it.window == toUpdateWindow }.firstOrNull()
        if (virtualAbstractState != null && virtualAbstractState.targetActions.contains(abstractTransition.abstractAction)) {
            virtualAbstractState.targetActions.add(abstractTransition.abstractAction)
        }
    }

    val guiInteractionCoverage = HashMap<Interaction<*>, HashSet<String>>()


    private fun updateCoverage(sourceAbsState: AbstractState, currentAbsState: AbstractState, abstractTransition: AbstractTransition, interaction: Interaction<Widget>) {
        val edge = dstg.edge(sourceAbsState, currentAbsState, abstractTransition)
        if (edge == null)
            return
        val edgeStatementCoverage = edge.label.statementCoverage
        val edgeMethodCoverage = edge.label.methodCoverage
        if (!guiInteractionCoverage.containsKey(interaction)) {
            guiInteractionCoverage.put(interaction, HashSet())
        }
        val interactionCoverage = guiInteractionCoverage.get(interaction)!!

        val lastOpeningAnotherAppAbstractInteraction = findAbstractInteraction(lastOpeningAnotherAppInteraction)
        val recentExecutedStatements = ArrayList<String>()
        val recentExecutedMethods = ArrayList<String>()
        runBlocking {
            statementMF!!.mutex.withLock {
                recentExecutedStatements.addAll(statementMF!!.recentExecutedStatements)
                recentExecutedMethods.addAll(statementMF!!.recentExecutedMethods)
            }
        }
        recentExecutedStatements.forEach { statementId ->
            if (!interactionCoverage.contains(statementId)) {
                interactionCoverage.add(statementId)
            }
            if (!edgeStatementCoverage.contains(statementId)) {
                edgeStatementCoverage.add(statementId)
            }
            if (sourceAbsState.isOutOfApplication && currentAbsState.belongToAUT() && lastOpeningAnotherAppAbstractInteraction != null) {
                lastOpeningAnotherAppAbstractInteraction.updateUpdateStatementCoverage(statementId, this@ATUAMF)
            } else {
                abstractTransition.updateUpdateStatementCoverage(statementId, this@ATUAMF)
            }

        }

        recentExecutedMethods.forEach { methodId ->
            val methodName = statementMF!!.getMethodName(methodId)
            if (!edgeMethodCoverage.contains(methodId)) {
                edgeMethodCoverage.add(methodId)
            }
            if (unreachableModifiedMethods.contains(methodName)) {
                unreachableModifiedMethods.remove(methodName)
            }
            val isATopCallerOfModifiedMethods = modifiedMethodTopCallersMap.filter { it.value.contains(methodId) }.isNotEmpty()
            if (allEventHandlers.contains(methodId) || isATopCallerOfModifiedMethods) {
                if (sourceAbsState.isOutOfApplication && currentAbsState.belongToAUT() && lastOpeningAnotherAppAbstractInteraction != null) {
                    if (lastOpeningAnotherAppAbstractInteraction.handlers.containsKey(methodId)) {
                        lastOpeningAnotherAppAbstractInteraction.handlers[methodId] = true
                    } else {
                        lastOpeningAnotherAppAbstractInteraction.handlers.put(methodId, true)
                    }
                    if (isATopCallerOfModifiedMethods) {
                        val coverableModifiedMethods = modifiedMethodTopCallersMap.filter { it.value.contains(methodId) }
                        coverableModifiedMethods.forEach { mmethod, _ ->
                            lastOpeningAnotherAppAbstractInteraction.modifiedMethods.putIfAbsent(mmethod, false)
                        }
                    }
                } else {
                    if (abstractTransition.handlers.containsKey(methodId)) {
                        abstractTransition.handlers[methodId] = true
                    } else {
                        abstractTransition.handlers.put(methodId, true)
                    }
                }
            }

            if (untriggeredTargetHiddenHandlers.contains(methodId)) {
                untriggeredTargetHiddenHandlers.remove(methodId)
            }
        }

        allTargetHandlers.removeIf { handler ->
            val invokableModifiedMethos = modifiedMethodTopCallersMap.filter { it.value.contains(handler) }.keys
            invokableModifiedMethos.all { statementMF!!.fullyCoveredMethods.contains(it) }
        }
    }

    private fun findAbstractInteraction(interaction: Interaction<Widget>?): AbstractTransition? {
        return if (interaction != null) {
            val lastAppState = stateList.find { it.stateId == interaction.prevState }
            if (lastAppState == null)
                throw Exception()
            val lastAppAbstractState = getAbstractState(lastAppState)!!
            lastAppAbstractState.abstractTransitions.find { it.interactions.contains(interaction) }
        } else {
            null
        }
    }

    private fun updateStaticEventHandlersAndModifiedMethods(input: Input, abstractTransition: AbstractTransition, coverageIncreased: Int) {
        val prevWindows = abstractTransition.dependentAbstractStates.map { it.window }
        if (prevWindows.isNotEmpty()) {
            prevWindows.forEach { prevWindow->
                val existingTransition = wtg.edge(abstractTransition.source.window, abstractTransition.dest.window, WindowTransition(
                        abstractTransition.source.window,
                        abstractTransition.dest.window,
                        input,
                        prevWindow
                ))
                if (existingTransition == null) {
                    wtg.add(abstractTransition.source.window, abstractTransition.dest.window, WindowTransition(
                            abstractTransition.source.window,
                            abstractTransition.dest.window,
                            input,
                            prevWindow
                    ))
                }
            }
        }
        //update ewtg transition

        // process event handlers
        abstractTransition.handlers.filter { it.value == true }.forEach {
            input.verifiedEventHandlers.add(it.key)
        }
        if (input.verifiedEventHandlers.isEmpty() && input.eventHandlers.isNotEmpty()) {
            input.eventHandlers.clear()
            input.modifiedMethods.entries.removeIf { it.value==false }
            input.modifiedMethodStatement.entries.removeIf {
                val methodId = statementMF!!.statementMethodInstrumentationMap.get(it.key)
                !input.modifiedMethods.containsKey(methodId)
            }
        }
        if (input.verifiedEventHandlers.isNotEmpty() && input.eventHandlers.isNotEmpty()) {
            if (input.verifiedEventHandlers.intersect(input.eventHandlers).isEmpty()) {
                input.eventHandlers.clear()
                input.modifiedMethods.entries.removeIf { it.value==false }
                input.modifiedMethodStatement.entries.removeIf {
                    val methodId = statementMF!!.statementMethodInstrumentationMap.get(it.key)
                    !input.modifiedMethods.containsKey(methodId)
                }
            }
                input.eventHandlers.addAll(input.verifiedEventHandlers)

        }

        abstractTransition.modifiedMethods.forEach {
            val methodId = it.key
            if (input.modifiedMethods.containsKey(methodId)) {
                if (it.value == true) {
                    input.modifiedMethods[it.key] = it.value
                }
            }
             else {
                input.modifiedMethods[it.key] = it.value
                val methodId = it.key
                val modifiedStatements = statementMF!!.statementMethodInstrumentationMap.filter {it2-> it2.value == methodId }
                modifiedStatements.forEach { s, _ ->
                    input.modifiedMethodStatement.put(s,false)
                }
            }
        }
        val newCoveredStatements = ArrayList<String>()
        abstractTransition.modifiedMethodStatement.filter { it.value == true }.forEach {
            if (!input.modifiedMethodStatement.containsKey(it.key) || input.modifiedMethodStatement.get(it.key) == false) {
                input.modifiedMethodStatement[it.key] = it.value
                newCoveredStatements.add(it.key)
            }
        }
        if (coverageIncreased > 0) {
            log.debug("New $coverageIncreased updated statements covered by event: $input.")
            //log.debug(newCoveredStatements.toString())
        }
        input.coverage[dateFormater.format(System.currentTimeMillis())] = input.modifiedMethodStatement.filterValues { it == true }.size
        removeUnreachableModifiedMethods(input)

    }

    private fun removeUnreachableModifiedMethods(input: Input) {
        val toRemovedModifiedMethods = ArrayList<String>()
        input.modifiedMethods.forEach {
            val methodName = it.key
            if (modifiedMethodTopCallersMap.containsKey(methodName)) {
                val handlers = modifiedMethodTopCallersMap[methodName]!!
                if (input.eventHandlers.intersect(handlers).isEmpty()) {
                    toRemovedModifiedMethods.add(methodName)
                }
            }
        }
//        toRemovedModifiedMethods.forEach {
//            input.modifiedMethods.remove(it)
//        }
    }


    //endregion



    //region compute
    fun getProbabilities(state: State<*>): Map<Widget, Double> {
        try {
            runBlocking { mutex.lock() }
            val data = state.actionableWidgets
                    .map { it to (widgetProbability[it.uid] ?: 0.0) }
                    .toMap()

            assert(data.isNotEmpty()) { "No actionable widgets to be interacted with" }

            return data
        } finally {
            mutex.unlock()
        }
    }

    val unableFindTargetNodes = HashMap<Window, Int>()


    fun getCandidateActivity_P1(currentActivity: String): List<String> {
        val candidates = ArrayList<String>()
        //get possible target widgets
        targetInputsByWindowClass.filter { it.key != currentActivity }.forEach {
            if (it.value.size > 0)
                candidates.add(it.key)

        }
        return candidates
    }



    //endregion

    //region phase2

    fun validateEvent(e: Input, currentState: State<*>): List<AbstractAction> {
        if (e.eventType == EventType.implicit_rotate_event && !appRotationSupport) {
            if (notFullyExercisedTargetInputs.contains(e)) {
                notFullyExercisedTargetInputs.remove(e)
            }
            return emptyList()
        }
        val currentAbstractState = getAbstractState(currentState)!!
        val availableAbstractActions = currentAbstractState.inputMappings.filter { it.value.contains(e) }.map { it.key }
        val validatedAbstractActions = ArrayList<AbstractAction>()
        availableAbstractActions.forEach {
            if (!it.isWidgetAction()) {
                validatedAbstractActions.add(it)
            } else {
                if (it.attributeValuationMap!!.getGUIWidgets(currentState).isNotEmpty()) {
                    validatedAbstractActions.add(it)
                }
            }
        }
        return validatedAbstractActions
    }



    //endregion


    fun getRuntimeWidgets(attributeValuationMap: AttributeValuationMap, widgetAbstractState: AbstractState, currentState: State<*>): List<Widget> {
        val allGUIWidgets = attributeValuationMap.getGUIWidgets(currentState)
//        if (allGUIWidgets.isEmpty()) {
//            //try get the same static widget
//            val abstractState = getAbstractState(currentState)
//            if (abstractState == widgetAbstractState)
//                return allGUIWidgets
//            if (widgetAbstractState.EWTGWidgetMapping.containsKey(attributeValuationMap) && abstractState != null) {
//                val staticWidgets = widgetAbstractState.EWTGWidgetMapping[attributeValuationMap]!!
//                val similarWidgetGroups = abstractState.EWTGWidgetMapping.filter { it.value == staticWidgets }.map { it.key }
//                return similarWidgetGroups.map { it.getGUIWidgets(currentState) }.flatten()
//            }
//        }
        return allGUIWidgets
    }

    //endregion
    init {

    }

    //region statical analysis helper
        //endregion

    fun getAppName() = appName



    fun getAbstractState(state: State<*>): AbstractState? {
        return AbstractStateManager.INSTANCE.getAbstractState(state)
    }

    //region readJSONFile

    val methodTermsHashMap = HashMap<String, HashMap<String, Long>>()
    val windowTermsHashMap = HashMap<Window, HashMap<String, Long>>()
    val windowHandlersHashMap = HashMap<Window, Set<String>>()
    val activityAlias = HashMap<String, String>()
    val modifiedMethodTopCallersMap = HashMap<String, Set<String>>()


    fun getAppModelFile(): Path? {
        if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val appModelFile = getAppModelFile(appName, resourceDir)
            if (appModelFile != null)
                return appModelFile
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding instrumentation file.")
                return null
            }
        }
    }

    private fun getAppModelFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-AppModel.json")
                }
                .findFirst()
                .orElse(null)
    }

    private fun getEWTGDiffFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-ewtgdiff.json")
                }
                .findFirst()
                .orElse(null)
    }

    fun getTextInputFile(): Path? {
        if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val textInputFile = getTextInputFile(appName, resourceDir)
            if (textInputFile != null)
                return textInputFile
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding text input file.")
                return null
            }
        }
    }

    private fun getTextInputFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-input.json")
                }
                .findFirst()
                .orElse(null)
    }

    fun getDeviceConfigurationFile(): Path? {
        if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val configurationFile = getDeviceConfigurationFile(appName, resourceDir)
            if (configurationFile != null)
                return configurationFile
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding configuration file.")
                return null
            }
        }
    }

    private fun getDeviceConfigurationFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-configuration.json")
                }
                .findFirst()
                .orElse(null)
    }

    fun getIntentModelFile(): Path? {
        if (!Files.exists(resourceDir)) {
            log.warn("Provided Dir does not exist: $resourceDir.")
            return null

        } else {
            val intentModelFile = getIntentModelFile(appName, resourceDir)
            if (intentModelFile != null)
                return intentModelFile
            else {
                log.warn("Provided directory ($resourceDir) does not contain " +
                        "the corresponding intent model file.")
                return null
            }
        }
    }

    private fun getIntentModelFile(apkName: String, targetDir: Path): Path? {
        return Files.list(targetDir)
                .filter {
                    it.fileName.toString().contains(apkName)
                            && it.fileName.toString().endsWith("-intent.json")
                }
                .findFirst()
                .orElse(null)
    }


    //endregion

    fun produceTargetWidgetReport(context: ExplorationContext<*, *, *>) {
        val sb = StringBuilder()
        sb.appendln("Statements;${statementMF!!.statementInstrumentationMap.size}")
        sb.appendln("Methods;${statementMF!!.methodInstrumentationMap.size}")
        sb.appendln("ModifiedMethods;${statementMF!!.modMethodInstrumentationMap.size}")
        sb.appendln("ModifiedMethodsStatements;${
        statementMF!!.statementMethodInstrumentationMap.filter { statementMF!!.modMethodInstrumentationMap.contains(it.value) }.size
        } ")
        sb.appendln("CoveredStatements;${statementMF!!.executedStatementsMap.size}")
        sb.appendln("CoveredMethods;${statementMF!!.executedMethodsMap.size}")
        sb.appendln("CoveredModifiedMethods;${statementMF!!.executedModifiedMethodsMap.size}")
        sb.appendln("CoveredModifiedMethodsStatements;${statementMF!!.executedModifiedMethodStatementsMap.size}")
        sb.appendln("ListCoveredModifiedMethods;")
        if (statementMF!!.executedModifiedMethodsMap.isNotEmpty()) {
            val sortedMethods = statementMF!!.executedModifiedMethodsMap.entries
                    .sortedBy { it.value }
            val initialDate = sortedMethods.first().value
            sortedMethods
                    .forEach {
                        sb.appendln("${it.key};${statementMF!!.modMethodInstrumentationMap[it.key]};${Duration.between(initialDate.toInstant(), it.value.toInstant()).toMillis() / 1000}")
                    }

        }
        sb.appendln("EndOfList")
        sb.appendln("ListUnCoveredModifiedMethods;")
        statementMF!!.modMethodInstrumentationMap.filterNot { statementMF!!.executedModifiedMethodsMap.containsKey(it.key) }.forEach {
            sb.appendln("${it.key};${it.value}")
        }
        sb.appendln("EndOfList")
        sb.appendln("Phase1StatementMethodCoverage;$phase1StatementCoverage")
        sb.appendln("Phase1MethodCoverage;$phase1MethodCoverage")
        sb.appendln("Phase1ModifiedStatementCoverage;$phase1ModifiedStatementCoverage")
        sb.appendln("Phase1ModifiedMethodCoverage;$phase1ModifiedMethodCoverage")
        sb.appendln("Phase1ActionCount;$phase1Actions")
        sb.appendln("Phase2StatementMethodCoverage;$phase2StatementCoverage")
        sb.appendln("Phase2MethodCoverage;$phase2MethodCoverage")
        sb.appendln("Phase2ModifiedMethodCoverage;$phase2ModifiedCoverage")
        sb.appendln("Phase2ModifiedStatementCoverage;$phase2ModifiedStatementCoverage")
        sb.appendln("Phase2ActionCount;$phase2Actions")
        sb.appendln("Phase2StartTime:$phase2StartTime")
        sb.appendln("Phase3StartTime:$phase3StartTime")
        sb.appendln("Unreached windows;")
        WindowManager.instance.updatedModelWindows.filterNot {
            it is Launcher
                    || it is OutOfApp || it is FakeWindow
        }.filter { node -> AbstractStateManager.INSTANCE.ABSTRACT_STATES.find { it.window == node && it !is VirtualAbstractState } == null }.forEach {
            sb.appendln(it.toString())
        }
        /*  sb.appendln("Unmatched widget: ${allTargetStaticWidgets.filter { it.mappedRuntimeWidgets.isEmpty() }.size}")
          allTargetStaticWidgets.forEach {
              if (it.mappedRuntimeWidgets.isEmpty())
              {
                  sb.appendln("${it.resourceIdName}-${it.className}-${it.widgetId} in ${it.activity}")
              }
          }*/

        val numberOfAppStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES.size
        sb.appendln("NumberOfAppStates;$numberOfAppStates")

        val outputFile = context.model.config.baseDir.resolve(targetWidgetFileName)
        log.info("Prepare writing triggered widgets report file: " +
                "\n- File name: ${outputFile.fileName}" +
                "\n- Absolute path: ${outputFile.toAbsolutePath().fileName}")

        Files.write(outputFile, sb.lines())
        log.info("Finished writing report in ${outputFile.fileName}")

        if (reuseBaseModel) {
           ModelBackwardAdapter.instance.produceReport(context)
        }
    }


    fun accumulateTargetEventsDependency(): HashMap<Input, HashMap<String, Long>> {
        val result = HashMap<Input, HashMap<String, Long>>()
        notFullyExercisedTargetInputs.forEach { event ->
            val eventDependency = HashMap<String, Long>()
            event.eventHandlers.forEach {
                if (methodTermsHashMap.containsKey(it)) {
                    if (methodTermsHashMap[it]!!.isNotEmpty()) {
                        methodTermsHashMap[it]!!.forEach { term, count ->
                            if (!eventDependency.containsKey(term))
                                eventDependency.put(term, count)
                            else
                                eventDependency[term] = eventDependency[term]!! + count
                        }
                    }
                }

            }
            if (eventDependency.isNotEmpty())
                result.put(event, eventDependency)
        }
        return result
    }

    fun updateStage1Info(eContext: ExplorationContext<*, *, *>) {
        phase1ModifiedMethodCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        phase1StatementCoverage = statementMF!!.getCurrentCoverage()
        phase1MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        phase1ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        phase1Actions = eContext.explorationTrace.getActions().size
        setPhase2StartTime()
    }

    fun updateStage2Info(eContext: ExplorationContext<*, *, *>) {
        phase2ModifiedCoverage = statementMF!!.getCurrentModifiedMethodCoverage()
        phase2StatementCoverage = statementMF!!.getCurrentCoverage()
        phase2MethodCoverage = statementMF!!.getCurrentMethodCoverage()
        phase2ModifiedStatementCoverage = statementMF!!.getCurrentModifiedMethodStatementCoverage()
        phase2Actions = eContext.explorationTrace.getActions().size
        setPhase3StartTime()
    }

    fun havingInternetConfiguration(window: Window): Boolean {
        if (deviceEnvironmentConfiguration == null)
            return false
        if (!deviceEnvironmentConfiguration!!.configurations.containsKey("Internet"))
            return false
        if (deviceEnvironmentConfiguration!!.configurations["Internet"]!!.contains(window))
            return true
        return false
    }

    fun getCandidateAction(currentState: State<*>, delay: Long, useCoordinator: Boolean): Map<Widget?, List<ExplorationAction>> {
        val result = HashMap<Widget?,List<ExplorationAction>>()
        val currentAbstractState = getAbstractState(currentState)!!
        val actionableWidget = Helper.getActionableWidgetsWithoutKeyboard(currentState)
        val currentStateActionScores = actionScore
                .filter {
                    (it.key.first != null
                            && actionableWidget.any { w -> w.uid == it.key.first })
                            && it.value.containsKey(currentAbstractState.window) }
                .map { Pair(it.key, it.value.get(currentAbstractState.window)!!) }.toMap()
        if (currentStateActionScores.isEmpty()) {
            result.put(null, listOf(ExplorationAction.pressBack()) )
            return result
        }
        val excludedActions = ArrayList<Triple<UUID?, String,String>>()
        while (result.isEmpty()) {
            val availableCurrentStateScores = currentStateActionScores.filter { !excludedActions.contains(it.key)  }
            if (availableCurrentStateScores.isEmpty()) {
                break
            }
            val maxCurrentStateScore = if (Random.nextDouble() < 0.5) {
                availableCurrentStateScores.maxBy { it.value }!!.key
            } else {
                val pb = ProbabilityDistribution<Triple<UUID?, String,String>>(availableCurrentStateScores)
                pb.getRandomVariable()
            }
            if (maxCurrentStateScore.first != null) {
                val candidateWidgets = actionableWidget.filter { it.uid == maxCurrentStateScore.first }
                if (candidateWidgets.isEmpty()) {
                    result.put(null, listOf(ExplorationAction.pressBack()) )
                    return result
                }
                val widgetActions = candidateWidgets.map { w ->
                    val actionList = if (maxCurrentStateScore.second != "Swipe") {
                        Helper.getAvailableActionsForWidget(w, currentState, delay, useCoordinator)
                                .filter { it.name == maxCurrentStateScore.second}
                    } else {
                        when (maxCurrentStateScore.third) {
                            "SwipeLeft" -> listOf<ExplorationAction>(w.swipeLeft())
                            "SwipeRight" -> listOf<ExplorationAction>(w.swipeRight())
                            "SwipeUp" ->listOf<ExplorationAction>(w.swipeUp())
                            "SwipeDown" -> listOf<ExplorationAction>(w.swipeDown())
                            else -> ArrayList<ExplorationAction>(w.availableActions(delay,useCoordinator).filter { it is Swipe})
                        }
                    }
                    Pair<Widget, List<ExplorationAction>>(w, actionList)
                }
                val candidateActions = widgetActions.filter { it.second.isNotEmpty() }
                if (candidateActions.isNotEmpty()) {
                    candidateActions.forEach {
                        result.put(it.first,it.second)
                    }
                    ExplorationTrace.widgetTargets.clear()
                } else
                    excludedActions.add(maxCurrentStateScore)
            } else {
                val action: ExplorationAction = when (maxCurrentStateScore.second) {
                    "PressBack" -> ExplorationAction.pressBack()
                    "PressMenu" -> GlobalAction(ActionType.PressMenu)
                    else -> ExplorationAction.pressBack()
                }
                result.put(null, listOf(action) )
                return result
            }
        }
        if (result.isEmpty()) {
            result.put(null, listOf(ExplorationAction.pressBack()) )
            return result
        }
        //check candidate action
        return result
    }

    fun computeAppStatesScore() {
        //Initiate reachable modified methods list
        val abstractStatesScores = HashMap<AbstractState, Double>()
        val abstractStateProbabilityByWindow = HashMap<Window, ArrayList<Pair<AbstractState, Double>>>()

        val modifiedMethodWeights = HashMap<String, Double>()
        val modifiedMethodMissingStatements = HashMap<String, HashSet<String>>()
        val appStateModifiedMethodMap = HashMap<AbstractState, HashSet<String>>()
        val modifiedMethodTriggerCount = HashMap<String, Int>()
        val windowScores = HashMap<Window, Double>()
        val windowsProbability = HashMap<Window, Double>()
        modifiedMethodMissingStatements.clear()
        modifiedMethodTriggerCount.clear()
        appStateModifiedMethodMap.clear()
        modifiedMethodWeights.clear()
        val allTargetInputs = ArrayList(notFullyExercisedTargetInputs)

        val triggeredStatements = statementMF!!.getAllExecutedStatements()
        statementMF!!.getAllModifiedMethodsId().forEach {
            val methodName = statementMF!!.getMethodName(it)
            if (!unreachableModifiedMethods.contains(methodName)) {
                modifiedMethodTriggerCount.put(it, 0)
                val statements = statementMF!!.getMethodStatements(it)
                val missingStatements = statements.filter { !triggeredStatements.contains(it) }
                modifiedMethodMissingStatements.put(it, HashSet(missingStatements))
            }
        }
        allTargetInputs.removeIf {
            it.modifiedMethods.map { it.key }.all {
                modifiedMethodMissingStatements.containsKey(it) && modifiedMethodMissingStatements[it]!!.size == 0
            }
        }
        //get all AppState
        val appStateList = ArrayList<AbstractState>()
        AbstractStateManager.INSTANCE.getPotentialAbstractStates().forEach { appStateList.add(it) }

        //get all AppState's edges and appState's modified method
        val edges = ArrayList<Edge<AbstractState, AbstractTransition>>()
        appStateList.forEach { appState ->
            edges.addAll(dstg.edges(appState).filter { it.label.isExplicit() || it.label.fromWTG })
            appStateModifiedMethodMap.put(appState, HashSet())
            appState.abstractTransitions.map { it.modifiedMethods }.forEach { hmap ->
                hmap.forEach { m, _ ->
                    if (!appStateModifiedMethodMap[appState]!!.contains(m)) {
                        appStateModifiedMethodMap[appState]!!.add(m)
                    }
                }
            }
        }

        //for each edge, count modified method appearing
        edges.forEach { edge ->
            val coveredMethods = edge.label.methodCoverage
            coveredMethods.forEach {
                if (statementMF!!.isModifiedMethod(it)) {
                    if (modifiedMethodTriggerCount.containsKey(it)) {
                        modifiedMethodTriggerCount[it] = modifiedMethodTriggerCount[it]!! + edge.label.interactions.size
                    }
                }
            }
        }
        //calculate modified method score
        val totalAbstractInteractionCount = edges.size
        modifiedMethodTriggerCount.forEach { m, c ->
            val score = 1 - c / totalAbstractInteractionCount.toDouble()
            modifiedMethodWeights.put(m, score)
        }

        //calculate appState score
        appStateList.forEach {
            var appStateScore = 0.0
            if (appStateModifiedMethodMap.containsKey(it)) {
                appStateModifiedMethodMap[it]!!.forEach {
                    if (!modifiedMethodWeights.containsKey(it))
                        modifiedMethodWeights.put(it, 1.0)
                    val methodWeight = modifiedMethodWeights[it]!!
                    if (modifiedMethodMissingStatements.containsKey(it)) {
                        val missingStatementNumber = modifiedMethodMissingStatements[it]!!.size
                        appStateScore += (methodWeight * missingStatementNumber)
                    }
                }
                //appStateScore += 1
                abstractStatesScores.put(it, appStateScore)
            }
        }

        //calculate appState probability
        appStateList.groupBy { it.window }.forEach { window, abstractStateList ->
            var totalScore = 0.0
            abstractStateList.forEach {
                totalScore += abstractStatesScores[it]!!
            }

            val appStatesProbab = ArrayList<Pair<AbstractState, Double>>()
            abstractStateProbabilityByWindow.put(window, appStatesProbab)
            abstractStateList.forEach {
                val pb = abstractStatesScores[it]!! / totalScore
                appStatesProbab.add(Pair(it, pb))
            }
        }

        //calculate staticNode score
        var staticNodeTotalScore = 0.0
        windowScores.clear()
        modifiedMethodsByWindow.filter { abstractStateProbabilityByWindow.containsKey(it.key) }.forEach { n, _ ->
            var weight: Double = 0.0
            val modifiedMethods = HashSet<String>()
/*            appStateModifiedMethodMap.filter { it.key.staticNode == n}.map { it.value }.forEach {
                it.forEach {
                    if (!modifiedMethods.contains(it))
                    {
                        modifiedMethods.add(it)
                    }
                }
            }*/
            allTargetInputs.filter { it.sourceWindow == n }.forEach {
                modifiedMethods.addAll(it.modifiedMethods.map { it.key })

            }

            if (windowHandlersHashMap.containsKey(n)) {
                windowHandlersHashMap[n]!!.forEach { handler ->
                    val methods = modifiedMethodTopCallersMap.filter { it.value.contains(handler) }.map { it.key }
                    modifiedMethods.addAll(methods)
                }
            }

            modifiedMethods.filter { modifiedMethodWeights.containsKey(it) }.forEach {
                val methodWeight = modifiedMethodWeights[it]!!
                val missingStatementsNumber = modifiedMethodMissingStatements[it]?.size ?: 0
                weight += (methodWeight * missingStatementsNumber)
            }
            if (weight > 0.0) {
                windowScores.put(n, weight)
                staticNodeTotalScore += weight
            }
        }
        windowsProbability.clear()
        //calculate staticNode probability
        windowScores.forEach { n, s ->
            val pb = s / staticNodeTotalScore
            windowsProbability.put(n, pb)
        }
    }

    fun getAbstractStateStack(): List<AbstractState> {
        return abstractStateStack.toList().map { it.first }.reversed()
    }

    fun getKeyboardClosedAbstractState(keyboardopenState: State<*>,tracing: Pair<Int, Int>): AbstractState? {
        var result: AbstractState? = null
        val keyboardOpenAbstractState = getAbstractState(keyboardopenState)!!
        for (i in tracing.second-1 downTo 1) {
            val interaction = tracingInteractionsMap.get(Pair(tracing.first,i))?.lastOrNull()
            if (interaction == null)
                throw Exception()
            val resState = stateList.find { it.stateId==interaction.resState}!!
            val abstractState = getAbstractState(resState)!!
            if (abstractState.window == keyboardOpenAbstractState.window
                    && !abstractState.isOpeningKeyboard) {
                result = abstractState
                break
            }
            if (abstractState.window != keyboardOpenAbstractState.window)
                break
        }
        return result
    }

    companion object {

        @JvmStatic
        val log: Logger by lazy { LoggerFactory.getLogger(ATUAMF::class.java) }

        object RegressionStrategy : PropertyGroup() {
            val baseModelDir by stringType
            val use by booleanType
            val budgetScale by doubleType
            val manualInput by booleanType
            val manualIntent by booleanType
            val reuseBaseModel by booleanType
        }


    }
}


enum class Rotation {
    LANDSCAPE,
    PORTRAIT
}