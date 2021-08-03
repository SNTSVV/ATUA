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

package org.atua.modelFeatures.ewtg

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.swipeDown
import org.droidmate.exploration.actions.swipeLeft
import org.droidmate.exploration.actions.swipeRight
import org.droidmate.exploration.actions.swipeUp
import org.atua.modelFeatures.dstg.AbstractStateManager
import org.atua.modelFeatures.ewtg.window.Window
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.legacy.findSingle
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.abs



class Helper {
    companion object {
        fun matchingGUIWidgetWithEWTGWidgets(guiwidget_Ewtgwidgets: HashMap<Widget, EWTGWidget>, guiState: State<*>, bestMatchedNode: Window, isMenuOpen: Boolean, appPackage: String) {
            val guiWidgetId_ewtgWidgets = HashMap<ConcreteId, EWTGWidget>()
            WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.putIfAbsent(bestMatchedNode, HashMap())
            val processed = ArrayList<Widget>()
            val workingList: LinkedList<Widget> = LinkedList<Widget>()
            val addedToWorkingList = HashSet<ConcreteId>()
            // -----DEBUG----
            val trivialWebViewItem = guiState.widgets.filterNot {
                it.isKeyboard
            }.filter { isVisibleWidget(it) }.filter{
                isTrivialWebViewContent(it, guiState)
            }
            // -----END_DEBUG----


            val unmappedWidgets = guiState.widgets.filterNot {
                it.isKeyboard
            }.filter { isVisibleWidget(it) }.filterNot {
                hasParentWithType(it,guiState,"WebView") && it.resourceId.isBlank()
            }
            unmappedWidgets.filter { it.childHashes.isEmpty() }.forEach {
                workingList.add(it)
                addedToWorkingList.add(it.id)
            }

            while (workingList.isNotEmpty()) {
                val item = workingList.first
                if (item.parentId != null && !addedToWorkingList.contains(item.parentId!!)) {
                    workingList.addFirst(guiState.widgets.findSingle { it.id == item.parentId })
                    addedToWorkingList.add(item.parentId!!)
                    continue
                }
                workingList.removeFirst()
                //avmId_ewtgWidgets.putIfAbsent(avm.avsId, ArrayList())
                if (!processed.contains(item)) {
                    /*  val staticWidgets =  if (avm_widgets_map.containsKey(avm)) {
                          Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                      } else {
                          Helper.getStaticWidgets(avm, wtgWindow, false, avmId_ewtgWidgets)
                      }*/
                    processed.add(item)
                    //val staticWidgets = getStaticWidgets(avm, wtgWindow, false)
                    //if a widgetGroup has more
                    val matchingWidget = matchEWTGWidget(item, guiState, bestMatchedNode,  appPackage,isMenuOpen,true,guiWidgetId_ewtgWidgets)
                    if (matchingWidget != null) {
                        guiwidget_Ewtgwidgets.put(item,matchingWidget)
                        guiWidgetId_ewtgWidgets.put(item.id,matchingWidget)
                    }
                }
            }
            val unprocessedWidgets = unmappedWidgets.filterNot{processed.contains(it)}
            if (unprocessedWidgets.isNotEmpty()) {
                val leaflikeWidgets = unprocessedWidgets.filter { it.childHashes.isEmpty()
                        || unprocessedWidgets.map { it.idHash }.intersect(it.childHashes).isEmpty()}
                leaflikeWidgets.forEach {
                    workingList.add(it)
                    addedToWorkingList.add(it.id)
                }
                while (workingList.isNotEmpty()) {
                    val item = workingList.first
                    if (item.parentId != null && !addedToWorkingList.contains(item.parentId!!)) {
                        workingList.addFirst(guiState.widgets.findSingle { it.id == item.parentId })
                        addedToWorkingList.add(item.parentId!!)
                        continue
                    }
                    workingList.removeFirst()
                    //avmId_ewtgWidgets.putIfAbsent(avm.avsId, ArrayList())
                    if (!processed.contains(item)) {
                        /*  val staticWidgets =  if (avm_widgets_map.containsKey(avm)) {
                              Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                          } else {
                              Helper.getStaticWidgets(avm, wtgWindow, false, avmId_ewtgWidgets)
                          }*/
                        processed.add(item)
                        //val staticWidgets = getStaticWidgets(avm, wtgWindow, false)
                        //if a widgetGroup has more
                        val matchingWidget = matchEWTGWidget(item, guiState, bestMatchedNode,appPackage ,isMenuOpen,true)
                        if (matchingWidget != null) {
                            guiwidget_Ewtgwidgets.put(item,matchingWidget)
                            guiWidgetId_ewtgWidgets.put(item.id,matchingWidget)
                        }
                    }
                }
            }
        }

        private fun isTrivialWebViewContent(it: Widget, guiState: State<*>) =
                !it.className.contains("WebView") && hasParentWithType(it, guiState, "WebView") && it.resourceId.isBlank()

        fun calculateMatchScoreForEachNode2(guiState: State<*>, allPossibleNodes: List<Window>, appPackage: String, isMenuOpen: Boolean): HashMap<Window, Double> {
            val matchWidgetsPerWindow = HashMap<Window, HashMap<Widget, EWTGWidget>>()
            val missWidgetsPerWindow = HashMap<Window, HashSet<Widget>>()

            allPossibleNodes.forEach {
                matchWidgetsPerWindow[it] = HashMap()
                missWidgetsPerWindow[it] = HashSet()
            }
          /*  val actionableWidgets = ArrayList<Widget>()
            actionableWidgets.addAll(getVisibleWidgetsForAbstraction(guiState))*/
            val unmappedWidgets = guiState.widgets.filterNot { it.isKeyboard }. filter { it.isVisible || it.visibleAreas.isNotEmpty() }

            allPossibleNodes.forEach {wtgWindow->
                val processed = ArrayList<UUID>()
                val workingList: LinkedList<Widget> = LinkedList<Widget>()
                val addedToWorkingList = HashSet<ConcreteId>()
                unmappedWidgets.filter { it.childHashes.isEmpty() }. forEach {
                    workingList.add(it)
                    addedToWorkingList.add(it.id)
                }

                while (workingList.isNotEmpty()) {
                    val item = workingList.first
                    if (item.parentId != null && !addedToWorkingList.contains(item.parentId!!)) {
                        workingList.addFirst(guiState.widgets.findSingle { it.id == item.parentId })
                        addedToWorkingList.add(item.parentId!!)
                        continue
                    }
                    workingList.removeFirst()
                    //avmId_ewtgWidgets.putIfAbsent(avm.avsId, ArrayList())
                    if (!processed.contains(item.uid)) {
                        /*  val staticWidgets =  if (avm_widgets_map.containsKey(avm)) {
                              Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                          } else {
                              Helper.getStaticWidgets(avm, wtgWindow, false, avmId_ewtgWidgets)
                          }*/
                        processed.add(item.uid)
                        //val staticWidgets = getStaticWidgets(avm, wtgWindow, false)
                        //if a widgetGroup has more
                        val matchingWidget = matchEWTGWidget(item, guiState, wtgWindow, appPackage, isMenuOpen,false)
                        if (matchingWidget != null) {
                            if (matchWidgetsPerWindow.containsKey(wtgWindow)) {
                                matchWidgetsPerWindow[wtgWindow]!!.put(item, matchingWidget)
                            }
                        } else {
                            if (missWidgetsPerWindow.contains(wtgWindow) && item.resourceId.isNotBlank()) {
                                missWidgetsPerWindow[wtgWindow]!!.add(item)
                            }
                        }
                    }
                }
            }
            val scores = HashMap<Window, Double>()
            allPossibleNodes.forEach { window ->
                val missStaticWidgets = window.widgets
                        .filterNot{ staticWidget ->
                            matchWidgetsPerWindow[window]!!.values.any { it == staticWidget } }
                val totalWidgets = unmappedWidgets.size
                if (totalWidgets == 0) {
                    scores.put(window, Double.NEGATIVE_INFINITY)
                } else if ((window.isRuntimeCreated || window.widgets.any { it.createdAtRuntime }) && matchWidgetsPerWindow[window]!!.values.size / totalWidgets.toDouble() < 0.5) {
                    // for windows created at runtime
                    // need match perfectly at least all widgets with resource id.
                    // Give a bound to be 70%
                    scores.put(window, Double.NEGATIVE_INFINITY)
                } else {
                    val score = (matchWidgetsPerWindow[window]!!.size * 1.0) / totalWidgets
                    /*val score = if (matchWidgetsPerWindow[window]!!.size > 0 || window.widgets.size == 0) {
                        *//*(matchWidgetsPerWindow[window]!!.size * 1.0 - missWidgetsPerWindow.size * 1.0) / totalWidgets*//*
                    } else {
                        Double.NEGATIVE_INFINITY
                    }*/
                    scores.put(window, score)
                }
            }
            return scores
        }

        val guiState_actionableWidgets = HashMap<State<Widget>,List<Widget>>()
        fun getActionableWidgetsWithoutKeyboard(guiState: State<*>): List<Widget> {
            if (guiState_actionableWidgets.containsKey(guiState))
                return guiState_actionableWidgets[guiState]!!
            val excludeWidgets = ArrayList<Widget>()
            val visibleWidgets = getVisibleWidgets(guiState).filter { !it.isKeyboard }
            /*val onTopWidgets = visibleWidgets.filter { it.parentId == null }
            val toCheckWidgets = LinkedList<Widget>()
            toCheckWidgets.addAll(onTopWidgets)
            while (toCheckWidgets.isNotEmpty()) {
                val widget = toCheckWidgets.last
                toCheckWidgets.removeLast()
                val childWidgets = visibleWidgets.filter { it.parentHash == widget.idHash }
                if (childWidgets.size > 1) {

                }
            }*/
            val drawerLayout = visibleWidgets.find { it.className.contains("DrawerLayout") }
            if (drawerLayout!=null) {
                val childWidgets = visibleWidgets.filter { it.parentHash == drawerLayout.idHash }
                if (childWidgets.size > 1) {
                    val widgetDrawOrders = childWidgets.map { Pair(it, it.metaInfo.find { it.contains("drawingOrder") }!!.split(" = ")[1].toInt()) }
                    val maxDrawerOrder = widgetDrawOrders.maxBy { it.second}!!.second
                    excludeWidgets.addAll(widgetDrawOrders.filter { it.second<maxDrawerOrder }.map { it.first }.map { getAllChild(visibleWidgets,it) }.flatten())
                }
            }
            var result = visibleWidgets.filter{!excludeWidgets.contains(it)}.filter {
                isInteractiveWidgetButNotKeyboard(it)
            }
            if (result.isEmpty()) {
                if (guiState.widgets.any { it.isKeyboard }) {
                    val abstractState = AbstractStateManager.INSTANCE.getAbstractState(guiState)
                    if (abstractState!=null) {
                        val nonKeyboardAbstractStates = AbstractStateManager.INSTANCE.ABSTRACT_STATES
                                .filter { it.window == abstractState.window && !it.isOpeningKeyboard }
                        val nonKeyboardGUIStates = nonKeyboardAbstractStates.map { it.guiStates }.flatten()
                        val nonKeyboardRelatedWidgets = nonKeyboardGUIStates.map { it.widgets }.flatten().map { it.uid }.distinct()
                        result = guiState.widgets
                                .filter { isInteractiveWidgetButNotKeyboard(it) && !nonKeyboardRelatedWidgets.contains(it.uid)}
                    }
                }
            }
            guiState_actionableWidgets.put(guiState,result)
            return result
        }

        fun getVisibleWidgets(state: State<*>) =
                getVisibleWidgets2(state)

        val visibileWidgetsByState = HashMap<State<*>,List<Widget>>()
        fun getVisibleWidgets2(state: State<*>): List<Widget> {
            if (visibileWidgetsByState.containsKey(state))
                return visibileWidgetsByState.get(state)!!
            val result = ArrayList<Widget>()
            val originalVisibleWidgets = state.widgets.filter { isVisibleWidget(it) }
            //TODO try recompute VisibleAreas
/*            val visibleAreasByWidget = HashMap<Widget,List<Rectangle>> ()
            val topNodes = state.widgets.filter { !it.hasParent}
            val workingList = Stack<Widget>()
            topNodes.forEach {
                workingList.push(it)
                val visibleAreas = ArrayList<Rectangle>()
                visibleAreas.add(it.boundaries)
                visibleAreasByWidget.put(it,visibleAreas)
            }
            while (workingList.isNotEmpty()) {
                val node = workingList.pop()
                val currentVisibleAreas = ArrayList(visibleAreasByWidget.get(node)!!)
                val childNodes = originalVisibleWidgets.filter { it.parentId == node.id }
                val stillVisibleNodes = ArrayList(childNodes)
                val groupByDrawOrder = childNodes.groupBy { it.getDrawOrder() }
                val drawOrders = groupByDrawOrder.map { it.key }.sorted()
                for (i in drawOrders.size-1 downTo 0) {
                    val drawOrder= drawOrders[i]!!
                    val widgetGroup = groupByDrawOrder[drawOrder]!!
                    for (widget1 in widgetGroup) {
                        if (widget1.metaInfo.contains("markedAsOccupied = false")) {
                            val tmpVisibleArea = ArrayList(currentVisibleAreas)
                            val widgetVisibleAreas = widget1.boundaries.visibleAxis(tmpVisibleArea)
                            if (widgetVisibleAreas.isNotEmpty()) {
                                val originSurface = widget1.boundaries.surface()
                                val visibleSurface = widgetVisibleAreas.map { it.surface() }.sum()
                                if (visibleSurface > originSurface/3) {
                                    visibleAreasByWidget.put(widget1,widgetVisibleAreas)
                                    workingList.push(widget1)
                                }
                            }
                        } else {
                            val widgetVisibleAreas = widget1.boundaries.visibleAxis(currentVisibleAreas)
                            if (widgetVisibleAreas.isNotEmpty()) {
                                val originSurface = widget1.boundaries.surface()
                                val visibleSurface = widgetVisibleAreas.map { it.surface() }.sum()
                                if (visibleSurface > originSurface / 3) {
                                    visibleAreasByWidget.put(widget1, widgetVisibleAreas)
                                    workingList.push(widget1)
                                }
                            }
                        }
                    }
                }
            }
            result.addAll(visibleAreasByWidget.keys)*/
            result.addAll(originalVisibleWidgets)
            visibileWidgetsByState.put(state,result)
            return result
        }

        fun Rectangle.visibleAxis(uncovered: MutableCollection<Rectangle>): List<Rectangle>{
            if(uncovered.isEmpty() || this.isEmpty()) return emptyList()
            val newR = LinkedList<Rectangle>()
            var changed = false
            val del = LinkedList<Rectangle>()
            return uncovered.mapNotNull {
                val r = this.setIntersect(it)
                if(!it.isEmpty() && !r.isEmpty()) {
                    changed = true
                    if( r!= it){  // try detect elements which are for some reason rendered 'behind' an transparent layout element

                    }
                    del.add(it)
                    // this probably is done by the apps to determine their definedAsVisible app areas
                    newR.apply{ // add surrounding ones areas
                        add(Rectangle.create(it.leftX,it.topY,it.rightX,r.topY-1))// above intersection
                        add(Rectangle.create(it.leftX,r.topY,r.leftX-1,r.bottomY))  // left from intersection
                        add(Rectangle.create(r.rightX+1,r.topY,it.rightX,r.bottomY)) // right from intersection
                        add(Rectangle.create(it.leftX,r.bottomY+1,it.rightX,it.bottomY))  // below from intersection
                    }
                    r
                }else null }.also { res ->
                if(changed) {
                    uncovered.addAll(newR)
                    uncovered.removeAll { it.isEmpty() || del.contains(it) }
                }
            }
        }

        fun Rectangle.setIntersect( b: Rectangle): Rectangle {
            if (leftX < b.rightX && b.leftX < rightX && topY < b.bottomY && b.topY < bottomY) {
                val leftX = Math.max(leftX, b.leftX)
                val topY = Math.max(topY, b.topY)
                val rightX = Math.min(rightX, b.rightX)
                val bottomY = Math.min(bottomY, b.bottomY)
                return Rectangle.create(
                        left=leftX,
                        right=rightX,
                        top=topY,
                        bottom=bottomY)
            }
            return Rectangle.empty()
        }
        private fun isVisibleWidget(it: Widget) =
                it.enabled &&  isWellVisualized(it) && (it.isVisible || it.metaInfo.contains("visibleToUser = true"))

        fun getVisibleWidgetsForAbstraction(state: State<*>): List<Widget> {
            val result = ArrayList(getActionableWidgetsWithoutKeyboard(state).filterNot { isTrivialWebViewContent(it,state) })
            val potentialLayoutWidgets = state.widgets.filterNot{result.contains(it)}.filter {
                it.isVisible && (it.childHashes.isNotEmpty()
                        && (it.className.contains("ListView") || it.className.contains("RecyclerView"))) || it.className.contains("android.webkit.WebView")
            }
            result.addAll(potentialLayoutWidgets)
            return result
        }

        fun getUserInputFields(state: State<*>) =
                getVisibleWidgets(state).filter {!it.isKeyboard && isUserLikeInput(it) }

        fun matchEWTGWidget(guiWidget: Widget, guiState: State<*>, window: Window, appPackage: String,isMenuOpen: Boolean ,updateModel: Boolean,
                            guiWidgetId_ewtgWidgets: HashMap<ConcreteId, EWTGWidget> = HashMap() ): EWTGWidget? {
            var matchedEWTGWidgets: ArrayList<EWTGWidget> = ArrayList()
            if (WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.containsKey(window)) {
                val existingMapping = WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(window)!!
                if (existingMapping.containsKey(guiWidget)) {
                    matchedEWTGWidgets.add(existingMapping.get(guiWidget)!!)
                }
            }
            if (matchedEWTGWidgets.isEmpty() && guiWidget.resourceId.isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(guiWidget.resourceId,appPackage)
                val candidates = window.widgets.filter {
                    if (guiWidget.resourceId == "android:id/title" && isMenuOpen && it.className=="android.view.MenuItem") {
                        // in case of an menuItem
                        it.possibleTexts.contains(guiWidget.text)
                    } else {
                        if (it.resourceIdName == unqualifiedResourceId) {
                            if (it.structure.isBlank())
                                true
                            else
                                it.structure == guiWidget.deriveStructure()
                        } else
                            false
                    }
                }
                if (candidates.any{it.structure.isNotBlank()}) {
                    matchedEWTGWidgets.addAll(candidates.filter { it.structure.isNotBlank()})
                } else
                    matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()
                    && guiWidget.resourceId.isBlank()
                    && guiWidget.contentDesc.isNotBlank()) {
                val candidates = window.widgets.filter {it.structure.isNotBlank() }.filter { w ->
                    w.resourceIdName.isBlank() &&
                    w.structure == guiWidget.deriveStructure() &&
                    w.possibleContentDescriptions.contains(guiWidget.contentDesc)
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && guiWidget.resourceId.isBlank()&& !guiWidget.isInputField && guiWidget.text.isNotBlank()) {
                val candidates = window.widgets.filter {it.structure.isNotBlank()}.filter { w ->
                    w.resourceIdName.isBlank()
                            && w.structure == guiWidget.deriveStructure()
                            && w.possibleTexts.contains(guiWidget.text)
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && guiWidget.resourceId.isBlank()) {
                val candidates = window.widgets.filter { it.structure.isNotBlank() }.filter { w ->
                    w.resourceIdName.isBlank()
                            && guiWidget.deriveStructure() == w.structure
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && guiWidget.resourceId.isBlank()) {
                val candidates = window.widgets.filter { it.structure.isBlank() }.filter { w ->
                    w.resourceIdName.isBlank()
                            && w.className == guiWidget.className
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.size>1) {
                val matchingScores = HashMap<EWTGWidget,Double>()

                matchedEWTGWidgets.forEach {
                    val hierarchyMatchingScore: Double = verifyMatchingHierchyWindowLayout2(guiWidget, it, window, guiState,guiWidgetId_ewtgWidgets )
                    if (hierarchyMatchingScore != Double.POSITIVE_INFINITY){
                        matchingScores.put(it,hierarchyMatchingScore)
                    }
                }
                if (matchingScores.isEmpty()){
                    matchedEWTGWidgets.clear()
                } else {
                    val maxScore = matchingScores.minBy { it.value }!!.value
                    matchedEWTGWidgets.removeIf { matchingScores[it] != maxScore }
                }

            }
            if (matchedEWTGWidgets.isNotEmpty()) {
                guiWidgetId_ewtgWidgets[guiWidget.id]=matchedEWTGWidgets.first()
            }
            if (updateModel) {
                matchedEWTGWidgets.forEach {
                    if (guiWidget.isInputField && guiWidget.text.isNotBlank()) {
                        it.textInputHistory.add(guiWidget.text)
                    }
                }
                if (matchedEWTGWidgets.isEmpty()) {
                    val widgetId = guiWidget.id
                    val newWidget = EWTGWidget.getOrCreateStaticWidget(
                            widgetId = widgetId.toString(),
                            resourceIdName = getUnqualifiedResourceId(guiWidget.resourceId,appPackage),
                            className = guiWidget.className,
                            wtgNode = window,
                            resourceId = "",
                            structure = guiWidget.deriveStructure()
                    )
                    newWidget.createdAtRuntime = true
                    updateWindowHierarchy(guiWidget, guiState, guiWidgetId_ewtgWidgets, newWidget, window)
                    if (guiWidget.text.isNotBlank())
                        newWidget.possibleTexts.add(guiWidget.text)
                    if (guiWidget.contentDesc.isNotBlank())
                        newWidget.possibleContentDescriptions.add(guiWidget.contentDesc)
                    window.addWidget(newWidget)
                    matchedEWTGWidgets.add(newWidget)
                    guiWidgetId_ewtgWidgets[guiWidget.id]=newWidget
                } else {
                    val matchedWidget = matchedEWTGWidgets.first()
                    if (matchedWidget.structure.isBlank()) {
                        matchedWidget.structure = guiWidget.deriveStructure()
                        updateWindowHierarchy(guiWidget, guiState, guiWidgetId_ewtgWidgets, matchedWidget, window)
                    }
                    if (guiWidget.text.isNotBlank())
                        matchedWidget.possibleTexts.add(guiWidget.text)
                    if (guiWidget.contentDesc.isNotBlank())
                        matchedWidget.possibleContentDescriptions.add(guiWidget.contentDesc)
                }
                WindowManager.instance.guiWidgetEWTGWidgetMappingByWindow.get(window)!!.put(guiWidget,matchedEWTGWidgets.first())
            }
            return matchedEWTGWidgets.firstOrNull()
        }

        private fun updateWindowHierarchy(guiWidget: Widget, guiState: State<*>, guiWidgetId_ewtgWidgets: HashMap<ConcreteId, EWTGWidget>, newWidget: EWTGWidget, window: Window) {
            val ancestorWidgetMatchedEWTGWidget: Widget? = findAncestorWidgetHavingMatchedEWTGWidget(guiWidget.parentId, guiState, guiWidgetId_ewtgWidgets)
            if (ancestorWidgetMatchedEWTGWidget != null) {
                val matchedAncestorEWTGWidget = guiWidgetId_ewtgWidgets.get(ancestorWidgetMatchedEWTGWidget.id)
                newWidget.parent = matchedAncestorEWTGWidget
            } else {
                val layoutRoots = window.widgets.filter { it.parent == null && it != newWidget }
                layoutRoots.forEach {
                    it.parent = newWidget
                }
            }
        }

        fun Widget.deriveStructure() = xpath.replace("\\[\\d*]".toRegex(),"")

        private fun findAncestorWidgetHavingMatchedEWTGWidget(parentWidgetId: ConcreteId?, guiState: State<*>, guiWidget_Ewtgwidgets: HashMap<ConcreteId,EWTGWidget>): Widget? {
            if (parentWidgetId == null)
                return null
            val parentWidget = guiState.widgets.find { it.id == parentWidgetId }!!
            if (guiWidget_Ewtgwidgets.containsKey(parentWidgetId)) {
                return parentWidget
            }
            return findAncestorWidgetHavingMatchedEWTGWidget(parentWidget.parentId,guiState, guiWidget_Ewtgwidgets)
        }

/*        private fun findAncestorAVMHavingMatchedEWTGWidget(parentAvmId: String, avmEwtgwidgets: HashMap<String,EWTGWidget>,activity: String): AttributeValuationMap? {
            if (parentAvmId == "")
                return null
            val parentAVM = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAvmId)!!
            if (avmEwtgwidgets.containsKey(parentAvmId)) {
                if (avmEwtgwidgets.containsKey(parentAvmId)) {
                    return parentAVM
                }
            }
            return findAncestorAVMHavingMatchedEWTGWidget(parentAVM.parentAttributeValuationMapId,avmEwtgwidgets,activity)
        }*/

        private fun verifyMatchingHierchyWindowLayout2(guiWidget: Widget, ewtgWidget: EWTGWidget, wtgNode: Window, guiState: State<*>, guiWidgetId_EWTGWidgets: HashMap<ConcreteId,EWTGWidget>): Double {
            if (guiWidget.parentId == null && ewtgWidget.parent == null)
                return 1.0
            var traversingWidget: Widget = guiWidget
            while (traversingWidget.parentId!= null && ewtgWidget.parent != null) {
                if (!guiWidgetId_EWTGWidgets.containsKey(traversingWidget.parentId!!)) {
                    traversingWidget = guiState.widgets.findSingle { it.id == traversingWidget.parentId }
                    continue
                }
                val matchingParentWidgets = arrayListOf(guiWidgetId_EWTGWidgets.get(traversingWidget.parentId!!)!!)
                var totalCorrectnessDistance = 0.0
                matchingParentWidgets.forEach {
                    val correctnessDistance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent!!,it, ArrayList(), ArrayList())
                    totalCorrectnessDistance += correctnessDistance
                }
                val averageCorrectness = totalCorrectnessDistance/matchingParentWidgets.size
                return averageCorrectness
            }
            return Double.POSITIVE_INFINITY
        }
/*        private fun verifyMatchingHierchyWindowLayout(avm: AttributeValuationMap, ewtgWidget: EWTGWidget, wtgNode: Window, avmEwtgwidgets: HashMap<String, EWTGWidget>): Double {
            if (avm.parentAttributeValuationMapId == "" && ewtgWidget.parent == null)
                return 1.0
            var traversingAVM: AttributeValuationMap = avm
            while (traversingAVM.parentAttributeValuationMapId!= "" && ewtgWidget.parent != null) {
                if (!avmEwtgwidgets.containsKey(traversingAVM.parentAttributeValuationMapId)) {
                    traversingAVM = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[wtgNode.classType]!!.get(traversingAVM.parentAttributeValuationMapId)!!
                    continue
                }
                val matchingParentWidgets = arrayListOf(avmEwtgwidgets.get(traversingAVM.parentAttributeValuationMapId)!!)
                var totalCorrectnessDistance = 0.0
                matchingParentWidgets.forEach {
                    val correctnessDistance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent!!,it, ArrayList(), ArrayList())
                    totalCorrectnessDistance += correctnessDistance
                }
                val averageCorrectness = totalCorrectnessDistance/matchingParentWidgets.size
                return averageCorrectness
            }
            return Double.POSITIVE_INFINITY
        }*/

        private fun calculateEWTGWidgetAncestorCorrectness(ewtgWidget1: EWTGWidget, ewtgWidget2: EWTGWidget,
                                                           traversedWidgets1: ArrayList<EWTGWidget>,traversedWidgets2: ArrayList<EWTGWidget>): Double {
            if (ewtgWidget1 == ewtgWidget2)
                return 1.0
            if (ewtgWidget1.parent == ewtgWidget1) // avoid loop
                return Double.POSITIVE_INFINITY
            var distance: Double = Double.POSITIVE_INFINITY
            if (ewtgWidget1.parent!=null && !traversedWidgets1.contains(ewtgWidget1.parent!!)) {
                traversedWidgets1.add(ewtgWidget1.parent!!)
                distance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget1.parent!!, ewtgWidget2,traversedWidgets1,traversedWidgets2)
                traversedWidgets1.remove(ewtgWidget1.parent!!)
            }
            if (distance == Double.POSITIVE_INFINITY && ewtgWidget2.parent!=null) {
                if (ewtgWidget2.parent == ewtgWidget2)
                    return distance
                if (!traversedWidgets2.contains(ewtgWidget2.parent!!)) {
                    traversedWidgets2.add(ewtgWidget2.parent!!)
                    distance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget2.parent!!, ewtgWidget1, traversedWidgets2,traversedWidgets1)
                    traversedWidgets2.remove(ewtgWidget2.parent!!)
                }
            }
            return 1.0+distance
        }

        internal var changeRatioCriteria: Double = 0.05

        fun isInteractiveWidgetButNotKeyboard(widget: Widget): Boolean =
              isVisibleWidget(widget) &&  !widget.isKeyboard && (widget.isInputField || widget.clickable || widget.checked != null || widget.longClickable || isScrollableWidget(widget)
                        || widget.className == "android.webkit.WebView"/*|| (!widget.hasClickableDescendant && widget.selected.isEnabled())*/)

        fun isWellVisualized(widget: Widget): Boolean {
            if (!widget.isVisible && widget.visibleAreas.isEmpty())
                return false
            if (widget.visibleBounds.width > 20 && widget.visibleBounds.height > 20)
                return true
            else
                return false
        }
        fun isScrollableWidget(widget: Widget): Boolean {
            if (widget.visibleBounds.width > 200 && widget.visibleBounds.height > 200) {
                return widget.scrollable
            } else
                return false
        }

        fun getViewsChildrenLayout(widget: Widget, state: State<*>): DescendantLayoutDirection {
            val childWidgets = state.widgets.filter { it.isVisible && widget.childHashes.contains(it.idHash) }
            if (childWidgets.size < 2) {
                return DescendantLayoutDirection.UNKNOWN
            }
            val arrayLeft = childWidgets.map { it.visibleBounds.leftX }
            val arrayTop = childWidgets.map { it.visibleBounds.topY }
            val avgLeft = arrayLeft.average()
            val avgTop = arrayTop.average()
            val avgDistantX = childWidgets.map { abs(it.visibleBounds.leftX - avgLeft) }.average()
            val avgDistantY = childWidgets.map { abs(it.visibleBounds.topY - avgTop) }.average()
            if (avgDistantX < 200 && avgDistantY < 200) {
                return DescendantLayoutDirection.UNKNOWN
            }
            if (avgDistantX >= avgDistantY * 0.9 && avgDistantX <= avgDistantY * 1.1) {
                return DescendantLayoutDirection.UNKNOWN
            }
            if (avgDistantX > avgDistantY * 0.9) {
                return DescendantLayoutDirection.HORIZONTAL
            }
            return DescendantLayoutDirection.VERTICAL
        }

        fun hasParentWithType(it: Widget, state: State<*>, parentType: String): Boolean {
            var widget: Widget = it
            while (widget.hasParent) {
                val parent = state.widgets.find { w -> w.id == widget.parentId }
                if (parent != null) {
                    if (parent.className.contains(parentType)) {
                        return true
                    }
                    widget = parent
                } else {
                    return false
                }
            }
            return false
        }

        fun hasParentWithResourceId(widget: Widget, state: State<*>, parentIdPatterns: List<String>): Boolean {
            var w: Widget = widget
            while (w.hasParent) {
                val parent = state.widgets.find { it -> it.id == w.parentId }
                if (parent != null) {
                    if (parentIdPatterns.find { w.resourceId.contains(it) } != null) {
                        return true
                    }
                    w = parent
                } else {
                    return false
                }
            }
            return false
        }

        fun tryGetParentHavingResourceId(widget: Widget, currentState: State<*>): Widget {
            var parentWidget: Widget? = widget
            while (parentWidget != null) {
                if (parentWidget.resourceId.isNotBlank())
                    return parentWidget
                parentWidget = currentState.widgets.find {
                    it.idHash == parentWidget!!.parentHash
                }

            }
            return widget
        }

        fun tryGetParentHavingClassName(widget: Widget, currentState: State<*>, className: String): Widget? {
            if (!widget.hasParent)
                return null
            var parentWidget: Widget? = widget
            while (parentWidget!=null && parentWidget.hasParent) {
                parentWidget = currentState.widgets.find { it.id == parentWidget!!.parentId }
                if (parentWidget!=null && parentWidget.className.contains(className))
                    return parentWidget
            }
            return null
        }

        fun getUnqualifiedResourceId(widget: Widget): String {
            val unqualifiedResourceId = widget.resourceId.substring(widget.resourceId.indexOf("/") + 1)
            return unqualifiedResourceId
        }

        fun getUnqualifiedResourceId(qualifiedResourceId: String, appPackage: String): String {
            if (qualifiedResourceId.indexOf("/")<0) {
                return qualifiedResourceId
            }
            val resourceIdPackage = qualifiedResourceId.substring(0, qualifiedResourceId.indexOf("/"))
            if (!resourceIdPackage.startsWith(appPackage)) {
                return qualifiedResourceId
            } else {
                val unqualifiedResourceId = qualifiedResourceId.substring(qualifiedResourceId.indexOf("/") + 1)
                return unqualifiedResourceId
            }
        }

        fun haveClickableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.clickable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets, it)) {
                    return true
                }
            }
            return false
        }

        fun haveLongClickableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.longClickable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets, it)) {
                    return true
                }
            }
            return false
        }

        fun haveScrollableChild(allWidgets: List<Widget>, parent: Widget): Boolean {
            val allChildren = ArrayList<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    allChildren.add(childWidget)
                    if (childWidget.scrollable) {
                        return true
                    }
                }
            }
            allChildren.forEach {
                if (haveClickableChild(allWidgets, it)) {
                    return true
                }
            }
            return false
        }

        fun getAllInteractiveChild(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    if (isInteractiveWidgetButNotKeyboard(childWidget) && isVisibleWidget(childWidget)) {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild(allWidgets, childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllInteractiveChild2(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val interactiveWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    if (childWidget.canInteractWith) {
                        interactiveWidgets.add(childWidget)
                    }
                    interactiveWidgets.addAll(getAllInteractiveChild2(allWidgets, childWidget))
                }

            }
            return interactiveWidgets
        }

        fun getAllChild(allWidgets: List<Widget>, parent: Widget): List<Widget> {
            val visibleWidgets = arrayListOf<Widget>()
            parent.childHashes.forEach {
                val childWidget = allWidgets.firstOrNull { w -> w.idHash == it && w.isVisible }
                if (childWidget != null) {
                    if (childWidget.isVisible) {
                        visibleWidgets.add(childWidget)
                    }
                    visibleWidgets.addAll(getAllChild(allWidgets, childWidget))
                }

            }
            return visibleWidgets
        }

        fun computeGuiTreeDimension(guiState: State<*>): Rectangle {
            val outboundViews = guiState.widgets.filter { !it.hasParent && !it.isKeyboard }
            if (outboundViews.isNotEmpty()) {
                val outBound = outboundViews.maxBy { it.boundaries.height + it.boundaries.width }!!.boundaries
                return outBound
            }
            val bound = guiState.widgets.sortedBy { it.boundaries.width + it.boundaries.height }.last().boundaries
            return bound
        }

        fun computeGuiTreeVisibleDimension(guiState: State<*>): Rectangle {
            val outboundViews = guiState.widgets.filter { !it.hasParent && !it.isKeyboard }
            if (outboundViews.isNotEmpty()) {
                val outBound = outboundViews.maxBy { it.visibleBounds.height + it.visibleBounds.width }!!.visibleBounds
                return outBound
            }
            val bound = guiState.widgets.sortedBy { it.visibleBounds.width + it.visibleBounds.height }.last().visibleBounds
            return bound
        }

        fun isSameFullScreenDimension(rotation: org.atua.modelFeatures.Rotation, guiTreeDimension: Rectangle, autautMF: org.atua.modelFeatures.ATUAMF): Boolean {
            if (rotation == org.atua.modelFeatures.Rotation.PORTRAIT) {
                if (guiTreeDimension.leftX == 0 && guiTreeDimension.width >= autautMF.portraitScreenSurface.width) {
                    if (guiTreeDimension.height / autautMF.portraitScreenSurface.height.toDouble() > 0.9) {
                        return true
                    }
                }
                return false
            }
            if (guiTreeDimension.leftX == 0 && guiTreeDimension.width > 0.9 * autautMF.portraitScreenSurface.height) {
                if (guiTreeDimension.height / autautMF.portraitScreenSurface.width.toDouble() > 0.9) {
                    return true
                }
            }
            return false
        }

        fun isDialog(rotation: org.atua.modelFeatures.Rotation, guiTreeDimension: Rectangle, guiState: State<*>, autautMF: org.atua.modelFeatures.ATUAMF):Boolean {
            if (isSameFullScreenDimension(rotation, guiTreeDimension, autautMF))
                return false
            if (guiState.widgets.any { it.resourceId == "android:id/content" }) {
                return true
            }
            return false
        }

        fun parseSwipeData(data: String): List<Pair<Int, Int>> {
            val splitData = data.split(" TO ")
            if (splitData.size != 2) {
                return emptyList()
            }
            val first = splitData[0].split(",").let { Pair(first = it[0].toInt(), second = it[1].toInt()) }
            val second = splitData[1].split(",").let { Pair(first = it[0].toInt(), second = it[1].toInt()) }
            return arrayListOf(first, second)
        }

        fun computeStep(swipeInfo: List<Pair<Int, Int>>): Int {
            val dx = abs(swipeInfo[0].first - swipeInfo[1].first)
            val dy = abs(swipeInfo[0].second - swipeInfo[1].second)
            return (dx + dy) / 2
        }

        fun parseCoordinationData(data: String): Pair<Int, Int> {
            val splitData = data.split(",")
            if (splitData.size == 2) {
                return Pair(splitData[0].toInt(), splitData[1].toInt())
            }
            return Pair(0, 0)
        }

        fun extractInputFieldAndCheckableWidget(prevState: State<*>): Map<UUID, String> {
            val condition = HashMap<UUID, String>()
            prevState.visibleTargets.filter { it.isInputField }.forEach { widget ->
                condition.put(widget.uid, widget.text)
            }
            prevState.visibleTargets.filter { Helper.isUserLikeInput(it) && !it.isInputField }.forEach { widget ->
                condition.put(widget.uid, widget.checked.toString())
            }
            return condition
        }

        fun parseRectangle(s: String): Rectangle {
            val data = s.split(":")
            val rectangle = Rectangle.create(data[0].toInt(), data[1].toInt(), data[2].toInt(), data[3].toInt())
            return rectangle
        }

        fun isOptionsMenuLayout(currentState: State<*>): Boolean {
            val root = currentState.widgets.filter { it.isVisible }.find { it.parentId == null }
            if (root == null)
            //cannot detect
                return false
            var hasMultipleChilds = root
            while (hasMultipleChilds != null) {
                if (hasMultipleChilds.childHashes.size > 1)
                    break
                if (hasMultipleChilds.childHashes.isEmpty()) {
                    hasMultipleChilds = null
                    break
                }
                hasMultipleChilds = currentState.widgets.find { it.idHash == hasMultipleChilds!!.childHashes.single() }
            }
            if (hasMultipleChilds == null)
                return false
            if (hasMultipleChilds.className == "android.widget.ListView") {
                // if (currentState.widgets.any { it.resourceId == "android:id/title" } )
                    return true
            }
            return false
        }

        fun isUserLikeInput(guiWidget: Widget): Boolean {
            return when (guiWidget.className) {
                "android.widget.RadioButton", "android.widget.CheckBox", "android.widget.Switch", "android.widget.ToggleButton" -> true
                else -> guiWidget.isInputField
            }
        }

         fun getAvailableActionsForWidget(chosenWidget: Widget, currentState: State<*>,delay: Long, useCoordinateClicks:Boolean): ArrayList<ExplorationAction> {
             val availableActions = ArrayList(chosenWidget.availableActions(delay, useCoordinateClicks))
             availableActions.removeIf {!chosenWidget.clickable &&
                             (it.name =="Click" || it.name == "ClickEvent") }
             availableActions.removeIf { it is Swipe }
              if (Helper.isScrollableWidget(chosenWidget)) {
                when (Helper.getViewsChildrenLayout(chosenWidget, currentState)) {
                    DescendantLayoutDirection.HORIZONTAL -> {
                        availableActions.add(chosenWidget.swipeLeft())
                        availableActions.add(chosenWidget.swipeRight())
                    }
                    DescendantLayoutDirection.VERTICAL -> {
                        availableActions.add(chosenWidget.swipeUp())
                        availableActions.add(chosenWidget.swipeDown())
                    }
                    else -> {
                        availableActions.add(chosenWidget.swipeUp())
                        availableActions.add(chosenWidget.swipeDown())
                        availableActions.add(chosenWidget.swipeLeft())
                        availableActions.add(chosenWidget.swipeRight())
                    }
                }
            }
            ExplorationTrace.widgetTargets.clear()
            if (availableActions.isNotEmpty())
                ExplorationTrace.widgetTargets.add(chosenWidget)
            return availableActions
        }

        fun getScoredAvailableActionsForWidget(chosenWidget: Widget, currentState: State<*>,delay: Long, useCoordinateClicks:Boolean): ArrayList<Pair<String,String>> {
            val result = ArrayList<Pair<String,String>>()
            if (chosenWidget.clickable) {
                val action_data = Pair("Click","")
                result.add(action_data)
            }
            if (chosenWidget.longClickable) {
                val action_data = Pair("LongClick","")
                result.add(action_data)
            }
            if (Helper.isScrollableWidget(chosenWidget)) {
                when (Helper.getViewsChildrenLayout(chosenWidget, currentState)) {
                    DescendantLayoutDirection.HORIZONTAL -> {
                        val action_data1 = Pair("Swipe","SwipeLeft")
                        result.add(action_data1)
                        val action_data2 = Pair("Swipe","SwipeRight")
                        result.add(action_data2)
                    }
                    DescendantLayoutDirection.VERTICAL -> {
                        val action_data1 = Pair("Swipe","SwipeUp")
                        result.add(action_data1)
                        val action_data2 = Pair("Swipe","SwipeDown")
                        result.add(action_data2)
                    }
                    else -> {
                        val action_data1 = Pair("Swipe","SwipeLeft")
                        result.add(action_data1)
                        val action_data2 = Pair("Swipe","SwipeRight")
                        result.add(action_data2)
                        val action_data3 = Pair("Swipe","SwipeUp")
                        result.add(action_data3)
                        val action_data4 = Pair("Swipe","SwipeDown")
                        result.add(action_data4)
                    }
                }
            }
            return result
        }

        fun getSwipeDirection(begin: Pair<Int, Int>, end: Pair<Int, Int>): String {
            return if (begin.first == end.first) {
                if (begin.second < end.second) {
                    //swipe down
                    "SwipeDown"
                } else {
                    //swipe up
                    "SwipeUp"
                }
            } else if (begin.first < end.first) {
                //siwpe right
                "SwipeRight"
            } else {
                "SwipeLeft"
            }
        }
    }
}

private fun Rectangle.surface(): Int {
    return width*height
}

private fun Rectangle.fullyContains(r: Rectangle): Boolean {
    return ( r.leftX in leftX..rightX
            && r.rightX in leftX..rightX) && // left or right x is contained in this
            ( r.topY in topY..bottomY  // top or bottom y is contained in this
                    && r.bottomY in topY..bottomY)
}

private fun Widget.getDrawOrder(): Int {
    return metaInfo.find { it.contains("drawingOrder") }!!.split(" = ")[1].toInt()
}

enum class DescendantLayoutDirection {
    HORIZONTAL,
    VERTICAL,
    UNKNOWN
}
