package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractAction
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractActionType
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractState
import org.droidmate.exploration.modelFeatures.atua.dstg.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.ewtg.Helper
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.interaction.Interaction
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ActionCount  {
    private val wCnt = HashMap<UUID, MutableMap<String, Int>>()
    val widgetCount = HashMap<ConcreteId,MutableMap<String,Int>>()
    fun widgetnNumExplored(s: State<*>, selection: Collection<Widget>): Map<Widget, Int> {
        val abstractState = AbstractStateManager.INSTANCE.getAbstractState(s)!!
        val activity = abstractState.window.classType
        return selection.map {
            it to (wCnt[it.uid]?.get(activity) ?: 0)
        }.toMap()
    }

    fun widgetNumExplored2(s: State<*>, selection: Collection<Widget>): Map<Widget,Int> {
        val abstractState = AbstractStateManager.INSTANCE.getAbstractState(s)!!
        val activity = abstractState.window.classType
        return selection.map {
            it to (widgetCount[it.id]?.get(activity) ?: 0)
        }.toMap()
    }

    fun getUnexploredWidget(guiState: State<Widget>): List<Widget> {
        val unexploredWidget = ArrayList<Widget>()
        val abstractState = AbstractStateManager.INSTANCE.getAbstractState(guiState)!!
        val activity = abstractState.window.classType
        Helper.getActionableWidgetsWithoutKeyboard(guiState).forEach {
            val widgetUid = it.uid
            if (wCnt.containsKey(widgetUid)) {
                if (wCnt.get(widgetUid)!!.containsKey(activity)) {
                    if (wCnt.get(widgetUid)!!.get(activity) == 0) {
                        unexploredWidget.add(it)
                    }
                }
            }
        }
        return unexploredWidget
    }

     fun initWidgetActionCounterForNewState(newState: State<*>) {
        val newAbstractState: AbstractState = AbstractStateManager.INSTANCE.getAbstractState(newState)!!
        Helper.getActionableWidgetsWithoutKeyboard(newState).filter { it.clickable }. forEach {
            val widgetUid = it.uid
            if (!wCnt.containsKey(widgetUid)) {
                wCnt.put(widgetUid, HashMap())
            }
            if (!wCnt.get(widgetUid)!!.containsKey(newAbstractState.window.classType)) {
                wCnt.get(widgetUid)!!.put(newAbstractState.window.classType, 0)
            }
            if (!widgetCount.containsKey(it.id)) {
                widgetCount.put(it.id, HashMap())
            }
            if (!widgetCount.get(it.id)!!.containsKey(newAbstractState.window.classType)) {
                widgetCount.get(it.id)!!.put(newAbstractState.window.classType, 0)
            }

        }
    }

    fun updateWidgetActionCounter(prevAbstractState: AbstractState,  prevState: State<*>, interaction: Interaction<Widget>) {
        //update widget count
        val actionType = AbstractAction.normalizeActionType(interaction,prevState)
        if (actionType != AbstractActionType.CLICK) {
            return
        }
        val prevActivity = prevAbstractState.window.classType
        val widgetUid = interaction.targetWidget!!.uid
        if (!wCnt.containsKey(widgetUid)) {
            wCnt.put(widgetUid, HashMap())
        }
        if (!wCnt.get(widgetUid)!!.containsKey(prevActivity)) {
            wCnt.get(widgetUid)!!.put(prevActivity, 0)
        }
        val currentCnt = wCnt.get(widgetUid)!!.get(prevActivity)!!
        wCnt.get(widgetUid)!!.put(prevActivity, currentCnt + 1)

        val widgetConcreteId = interaction.targetWidget!!.id
        if (!widgetCount.containsKey(widgetConcreteId)) {
            widgetCount.put(widgetConcreteId,HashMap())
        }
        if (!widgetCount.get(widgetConcreteId)!!.containsKey(prevActivity)) {
            widgetCount.get(widgetConcreteId)!!.put(prevActivity, 0)
        }
        val currentCnt2 = widgetCount.get(widgetConcreteId)!!.get(prevActivity)!!
        widgetCount.get(widgetConcreteId)!!.put(prevActivity, currentCnt2 + 1)
    }
}