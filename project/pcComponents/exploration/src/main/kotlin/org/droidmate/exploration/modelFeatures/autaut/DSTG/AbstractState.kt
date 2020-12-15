package org.droidmate.exploration.modelFeatures.autaut.DSTG

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.WTG.EWTGWidget
import org.droidmate.exploration.modelFeatures.autaut.WTG.*
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Activity
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Dialog
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Launcher
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OptionsMenu
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.OutOfApp
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.toUUID
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

open class AbstractState(
        val activity: String,
        val attributeValuationSets: List<AttributeValuationSet> = emptyList(),
        val guiStates: ArrayList<State<*>> = ArrayList(),
        var window: Window,
        val EWTGWidgetMapping: HashMap<AttributeValuationSet, ArrayList<EWTGWidget>> = HashMap(),
        val abstractTransitions: ArrayList<AbstractTransition> = ArrayList(),
        val inputMappings: HashMap<AbstractAction, ArrayList<Input>> = HashMap(),
        val isHomeScreen: Boolean = false,
        val isOpeningKeyboard: Boolean = false,
        val isRequestRuntimePermissionDialogBox: Boolean = false,
        val isAppHasStoppedDialogBox: Boolean = false,
        val isOutOfApplication: Boolean = false,
        var hasOptionsMenu: Boolean = true,
        var rotation: Rotation,
        var internet: InternetStatus,
        var loaded: Boolean = false
) {
    val actionCount = HashMap<AbstractAction, Int>()
    val targetActions = HashSet<AbstractAction>()

    val abstractStateId: UUID by lazy { lazyIds.value }


    init {

        window.mappedStates.add(this)
        attributeValuationSets.forEach {
            it.captured = true
        }

        if (!AbstractStateManager.instance.attrValSetsFrequency.containsKey(window)) {
            AbstractStateManager.instance.attrValSetsFrequency.put(window, HashMap())
        }
        val widgetGroupFrequency = AbstractStateManager.instance.attrValSetsFrequency[window]!!
        attributeValuationSets.forEach {
            if (!widgetGroupFrequency.containsKey(it)) {
                widgetGroupFrequency.put(it, 1)
            } else {
                widgetGroupFrequency[it] = widgetGroupFrequency[it]!! + 1
            }
        }
    }

    protected open val lazyIds: Lazy<UUID> =
            lazy {
                computeAbstractStateId(attributeValuationSets,activity, rotation, internet)
            }



    fun initAction(){
        val pressBackAction = AbstractAction(
                actionType = AbstractActionType.PRESS_BACK
        )
        actionCount.put(pressBackAction, 0)
        if (window !is OptionsMenu && window !is Dialog && !this.isOpeningKeyboard) {
            val pressMenuAction = AbstractAction(
                    actionType = AbstractActionType.PRESS_MENU
            )
            actionCount.put(pressMenuAction, 0)
            val minmaxAction = AbstractAction(
                    actionType = AbstractActionType.MINIMIZE_MAXIMIZE
            )
            actionCount.put(minmaxAction, 0)

        }
        if (window is Activity || rotation == Rotation.LANDSCAPE) {
            val rotationAction = AbstractAction(
                    actionType = AbstractActionType.ROTATE_UI
            )
            actionCount.put(rotationAction, 0)
        }

        if (window is Dialog) {
            val clickOutDialog = AbstractAction(
                    actionType = AbstractActionType.CLICK_OUTBOUND
            )
            actionCount.put(clickOutDialog, 0)
        }
        if (isOpeningKeyboard) {
            val closeKeyboardAction = AbstractAction(
                    actionType = AbstractActionType.CLOSE_KEYBOARD
            )
            actionCount.put(closeKeyboardAction, 0)
        }
        attributeValuationSets.forEach {
            it.initActions()
        }
    }

    @Suppress
    fun addAttributeValuationSet(attributeValuationSet: AttributeValuationSet) {
        if (attributeValuationSets.contains(attributeValuationSet)) {
            return
        }
        //attributeValuationSets.add(attributeValuationSet)
        attributeValuationSet.captured = true
        val attrValSetsFrequency = AbstractStateManager.instance.attrValSetsFrequency[window]!!
        attrValSetsFrequency[attributeValuationSet] = 1
    }

    fun addAction(action: AbstractAction) {
        if (action.actionType == AbstractActionType.PRESS_HOME) {
            return
        }
        if (action.attributeValuationSet == null) {
            if (!actionCount.containsKey(action)) {
                actionCount[action] = 0
            }
            return
        }
        if (!action.attributeValuationSet.actionCount.containsKey(action)) {
            action.attributeValuationSet.actionCount[action] = 0
        }
    }

    fun getAttributeValuationSet(widget: Widget, guiState: State<*>): AttributeValuationSet? {
        if (!guiStates.contains(guiState))
            return null
        val mappedWidget_AttributeValuationSet = AbstractStateManager.instance.activity_widget_AttributeValuationSetHashMap[activity]
        if (mappedWidget_AttributeValuationSet == null)
            return null
        val mappedAttributeValuationSet = mappedWidget_AttributeValuationSet.get(widget)
        if (mappedAttributeValuationSet == null)
            return null
        val attributeValuationSet = attributeValuationSets.find {
            it.haveTheSameAttributePath(mappedAttributeValuationSet)
        }
        return attributeValuationSet
    }

    fun getAvailableActions(): List<AbstractAction> {
        val allActions = ArrayList<AbstractAction>()
        allActions.addAll(actionCount.keys)
        allActions.addAll(attributeValuationSets.map { it.actionCount.keys }.flatten())
        //
        return allActions
    }

    fun getUnExercisedActions(currentState: State<*>?): List<AbstractAction> {
        val unexcerisedActions = HashSet<AbstractAction>()
        //use hashmap to optimize the performance of finding widget
        val widget_WidgetGroupMap = HashMap<Widget, AttributeValuationSet>()
        if (currentState != null) {
            Helper.getVisibleWidgetsForAbstraction(currentState).forEach { w ->
                val wg = this.getAttributeValuationSet(w, currentState)
                if (wg != null)
                    widget_WidgetGroupMap.put(w, wg)
            }
        }
        unexcerisedActions.addAll(actionCount.filter {
            it.value == 0
                    && it.key.actionType != AbstractActionType.FAKE_ACTION
                    && it.key.actionType != AbstractActionType.LAUNCH_APP
                    && it.key.actionType != AbstractActionType.RESET_APP
                    && it.key.actionType != AbstractActionType.ENABLE_DATA
                    && it.key.actionType != AbstractActionType.DISABLE_DATA
        }
                .map { it.key })
        val widgetActionCounts = if (currentState != null) {
            widget_WidgetGroupMap.values.filter { !it.isInputField() }
                    .map { w -> w.actionCount }
        } else {
            attributeValuationSets
                    .filter { !it.isInputField() }.map { w -> w.actionCount }
        }
        widgetActionCounts.forEach {
            val actions = it.filter { it.value == 0 }.map { it.key }
            unexcerisedActions.addAll(actions)
            /* val exercised = it.filter { it.value > 0}
             exercised.forEach { action, count ->
                 if (action.widgetGroup!!.cardinality == Cardinality.MANY && action.actionName!="Swipe") {
                     val guiWidgetCounts = widget_WidgetGroupMap.filter { p -> p.value == action.widgetGroup }.size
                     if (count < guiWidgetCounts / 2 + 1) {
                         unexcerisedActions.add(action)
                     }
                 } *//*else {
                        val widgetFrequency = AbstractStateManager.instance.widgetGroupFrequency[this.window]!!
                        val widgetGroupCount = widgetFrequency[action.widgetGroup!!]
                        if (widgetGroupCount != null) {
                            if (count <= widgetGroupCount) {
                                unexcerisedActions.add(action)
                            }
                        }
                    }*//*
                    *//*if (currentState!=null && action.widgetGroup!!.attributePath.hasParentWithClassName("DrawerLayout")) {
                        val guiWidgetCounts = widget_WidgetGroupMap.filter {p-> p.value == action.widgetGroup } .size
                        if (count < guiWidgetCounts) {
                            unexcerisedActions.add(action)
                        }
                    } else {

                    }*//*
                }*/
            /*val exercised = it.filter { it.value > 0 && it.key.widgetGroup!!.cardinality== Cardinality.MANY }
            exercised.forEach {
                if (currentState!=null) {
                    val guiWidgetCounts = widget_WidgetGroupMap.filter {p-> p.value == it.key.widgetGroup } .size
                    if (it.value < guiWidgetCounts/2+1) {
                        unexcerisedActions.add(it.key)
                    }
                } else {
                    if (it.value < 4) {
                        unexcerisedActions.add(it.key)
                    }
                    *//*if (guiStates.isNotEmpty()) {
                            val guiState = guiStates.maxBy { it.actionableWidgets.size }!!
                            val guiWidgetCount = widget_WidgetGroupMap.filter {p-> p.value == it.key.widgetGroup }.size
                            if (it.value < guiWidgetCount/2+1) {
                                unexcerisedActions.add(it.key)
                            }
                        }
                        else *//*
                    }
                }*/

        }

        val itemActions = attributeValuationSets.map { w -> w.actionCount.filter { it.key.isItemAction() } }.map { it.keys }.flatten()
        /*  if (currentState!=null || guiStates.isNotEmpty()) {
              val state = if(currentState == null) {
                  currentState?:guiStates.maxBy { it.actionableWidgets.size }!!
              } else
              {
                  currentState
              }
              itemActions.forEach {
                  val widgetGroup = it.widgetGroup!!
                  val runtimeWidgets = widget_WidgetGroupMap.filter { it.value == widgetGroup }.keys
                  var childSize = 0
                  val numberOfTry: Int
                  if (widgetGroup.attributePath.getClassName().contains("WebView")) {
                      numberOfTry = 5
                  } else {
                      runtimeWidgets.forEach { w ->
                          val childWidgets = Helper.getAllInteractiveChild(state.widgets, w)
                          childSize += childWidgets.size
                      }
                      numberOfTry = childSize
                  }

                  if (widgetGroup.actionCount[it]!! < numberOfTry) {
                      unexcerisedActions.add(it)
                  }
              }
          }*/
        /*          if (this.window.activityClass == "org.wikipedia.main.MainActivity") {
                      unexcerisedActions.addAll(widgets.map {
                          w -> w.actionCount.filter {
                          it.value < 4 && it.key.actionName == "Swipe" && it.key.extra == "SwipeUp"}}.map { it.keys }.flatten())
                      }*/
        return unexcerisedActions.toList()
    }


    override fun toString(): String {
        return "AbstractState[${this.abstractStateId}]-${window}-Rotation:$rotation"
    }

    fun setActionCount(action: AbstractAction, count: Int) {
        if (action.attributeValuationSet == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = count
            }
            return
        }
        if (action.attributeValuationSet.actionCount.containsKey(action)) {
            action.attributeValuationSet.actionCount[action] = count
        }
    }

    fun increaseActionCount(action: AbstractAction, updateSimilarAbstractState: Boolean = false) {
        if (action.attributeValuationSet == null) {
            if (actionCount.containsKey(action)) {
                actionCount[action] = actionCount[action]!! + 1
            } else {
                actionCount[action] = 1
            }
            val nonDataAction = AbstractAction(
                    actionType = action.actionType
            )
            if (actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                actionCount[nonDataAction] = actionCount[nonDataAction]!! + 1
            }
        } else if (attributeValuationSets.contains(action.attributeValuationSet)) {
            val widgetGroup = attributeValuationSets.find { it.equals(action.attributeValuationSet) }!!
            if (widgetGroup.actionCount.containsKey(action)) {
                widgetGroup.actionCount[action] = widgetGroup.actionCount[action]!! + 1
                widgetGroup.exerciseCount++
            } /*else {
                widgetGroup.actionCount[action] = 1
            }*/
            val nonDataAction = AbstractAction(
                    actionType = action.actionType,
                    attributeValuationSet = widgetGroup
            )
            if (widgetGroup.actionCount.containsKey(nonDataAction) && nonDataAction != action) {
                widgetGroup.actionCount[nonDataAction] = widgetGroup.actionCount[nonDataAction]!! + 1
            }
        }
        if (updateSimilarAbstractState) {
            increaseSimilarActionCount(action)
        }
    }

    fun increaseSimilarActionCount(action: AbstractAction) {
        AbstractStateManager.instance.ABSTRACT_STATES
                .filterNot { it is VirtualAbstractState }
                .filter { it.window == this.window }
                .forEach {

                    if (it.getAvailableActions().contains(action)) {
                        it.increaseActionCount(action)
                    }
                }
    }

    fun getActionCount(action: AbstractAction): Int {
        if (action.attributeValuationSet == null) {
            return actionCount[action] ?: 0
        }
        return action.attributeValuationSet.actionCount[action] ?: 0
    }

    fun getActionCountMap(): Map<AbstractAction, Int> {
        val result = HashMap<AbstractAction, Int>()
        result.putAll(actionCount)
        attributeValuationSets.map { it.actionCount }.forEach {
            result.putAll(it)
        }
        return result
    }

    fun computeScore(autautMF: AutAutMF): Double {
        var localScore = 0.0
        val unexploredActions = getUnExercisedActions(null).filterNot { it.attributeValuationSet == null }
        val windowWidgetFrequency = AbstractStateManager.instance.attrValSetsFrequency[this.window]!!
        unexploredActions.forEach {
            val actionScore = it.getScore()
            if (windowWidgetFrequency.containsKey(it.attributeValuationSet!!))
                localScore += (actionScore / windowWidgetFrequency[it.attributeValuationSet!!]!!.toDouble())
            else
                localScore += actionScore
        }
        localScore += this.getActionCountMap().map { it.value }.sum()
        this.guiStates.forEach {
            localScore += autautMF.getUnexploredWidget(it).size
        }

        val actions = getAvailableActions()
        var potentialActionCount = 0.0
        /* actions.forEach {action ->
             autautMF.abstractTransitionGraph.edges(this).filter { edge->
                     edge.label.abstractAction == action
                             && edge.source != edge.destination
                             && edge.destination?.data !is VirtualAbstractState
                 }.forEach { edge ->
                     val dest = edge.destination?.data
                     if (dest != null) {
                         val widgetGroupFrequences = AbstractStateManager.instance.widgetGroupFrequency[dest.window]!!
                         val potentialActions = dest.getUnExercisedActions(null).filterNot { it.widgetGroup == null }
                         potentialActions.forEach {
                             if (widgetGroupFrequences.containsKey(it.widgetGroup))
                                 potentialActionCount += (1/widgetGroupFrequences[it.widgetGroup]!!.toDouble())
                             else
                                 potentialActionCount += 1
                         }
                         potentialActionCount += potentialActions.size
                     }
                 }
         }*/
        return potentialActionCount + localScore
    }

    /**
     * write csv
     * uuid -> AbstractState_[uuid]
     */
    open fun dump(parentDirectory: Path) {
        val dumpedAttributeValuationSet = ArrayList<UUID>()
        File(parentDirectory.resolve("AbstractState_" + abstractStateId.toString() + ".csv").toUri()).bufferedWriter().use { all ->
            all.write(header())
            attributeValuationSets.forEach {
                if (!dumpedAttributeValuationSet.contains(it.avsId)) {
                    all.newLine()
                    it.dump(all, dumpedAttributeValuationSet,this)
                }
            }
        }
    }

    fun header(): String {
        return "AttributeValuationSetID;className;resourceId;contentDesc;text;enabled;selected;checkable;isInputField;clickable;longClickable;scrollable;checked;isLeaf;parentId;childId;cardinality;captured;wtgWidgetMapping"
    }

    fun belongToAUT(): Boolean {
        return !this.isHomeScreen && !this.isOutOfApplication && !this.isRequestRuntimePermissionDialogBox && !this.isAppHasStoppedDialogBox
    }

    companion object {
        fun computeAbstractStateId(attributeValuationSets: List<AttributeValuationSet>, activity: String, rotation: Rotation, internet: InternetStatus): UUID {
            return attributeValuationSets.sortedBy { it.avsId }.fold(emptyUUID) { id, avs ->
                /*// e.g. keyboard elements are ignored for uid computation within [addRelevantId]
                // however different selectable auto-completion proposes are only 'rendered'
                // such that we have to include the img id (part of configId) to ensure different state configuration id's if these are different*/

                //ConcreteId(addRelevantId(id, widget), configId + widget.uid + widget.id.configId)
                id + avs.avsId
            }.plus(listOf<String>(rotation.toString(),internet.toString(),activity).joinToString("<;>").toUUID())
        }

        internal operator fun UUID.plus(uuid: UUID?): UUID {
            return if (uuid == null) this
            else UUID(this.mostSignificantBits + uuid.mostSignificantBits, this.leastSignificantBits + uuid.mostSignificantBits)
        }

    }
}

enum class InternetStatus {
    Enable,
    Disable,
    Undefined
}

class VirtualAbstractState(activity: String,
                           staticNode: Window,
                           isHomeScreen: Boolean = false) : AbstractState(
        activity = activity,
        window = staticNode,
        rotation = Rotation.PORTRAIT,
        internet = InternetStatus.Undefined,
        isHomeScreen = isHomeScreen,
        isOutOfApplication = (staticNode is OutOfApp)
)

class AppResetAbstractState() : AbstractState(activity = "", window = Launcher.getOrCreateNode(), rotation = Rotation.PORTRAIT, internet = InternetStatus.Undefined)