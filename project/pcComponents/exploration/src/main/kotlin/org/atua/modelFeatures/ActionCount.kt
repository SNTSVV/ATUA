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
package org.atua.modelFeatures

import org.atua.modelFeatures.dstg.AbstractState
import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.ewtg.Helper
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
        val widgetCount = selection.map {
            it to (wCnt[it.uid]?.get(activity) ?: 0)
        }.toMap()
        return widgetCount
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