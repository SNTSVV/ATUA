package org.droidmate.exploration.modelFeatures.atua.EWTG

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.swipeDown
import org.droidmate.exploration.actions.swipeLeft
import org.droidmate.exploration.actions.swipeRight
import org.droidmate.exploration.actions.swipeUp
import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
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
        fun matchingGUIWidgetWithEWTGWidgets(guiwidget_Ewtgwidgets: HashMap<Widget, EWTGWidget>, guiState: State<*>, bestMatchedNode: Window) {
            val guiWidgetId_ewtgWidgets = HashMap<ConcreteId, EWTGWidget>()
            val processed = ArrayList<Widget>()
            val workingList: LinkedList<Widget> = LinkedList<Widget>()
            val addedToWorkingList = HashSet<ConcreteId>()
            val unmappedWidgets = guiState.widgets.filterNot { it.isKeyboard }. filter { it.isVisible || it.visibleAreas.isNotEmpty() }
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
                    val matchingWidget = matchEWTGWidget(item, guiState, bestMatchedNode, true,guiWidgetId_ewtgWidgets)
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
                        val matchingWidget = matchEWTGWidget(item, guiState, bestMatchedNode, true)
                        if (matchingWidget != null) {
                            guiwidget_Ewtgwidgets.put(item,matchingWidget)
                            guiWidgetId_ewtgWidgets.put(item.id,matchingWidget)
                        }
                    }
                }
            }
        }

        fun calculateMatchScoreForEachNode2(guiState: State<*>, allPossibleNodes: List<Window>, appName: String, activity: String,
                                           atuaMF: ATUAMF): HashMap<Window, Double> {
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
                        val matchingWidget = matchEWTGWidget(item, guiState, wtgWindow, false)
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


        fun calculateMatchScoreForEachNode(guiState: State<*>, allPossibleNodes: List<Window>, appName: String, activity: String, widget_AttributeValuationMapHashMap: HashMap<Widget, AttributeValuationMap>,
                                           atuaMF: ATUAMF): HashMap<Window, Double> {
            val matchWidgetsPerWindow = HashMap<Window, HashMap<AttributeValuationMap, EWTGWidget>>()
            val missWidgetsPerWindow = HashMap<Window, HashSet<AttributeValuationMap>>()
            //val propertyChangedWidgets = HashMap<Window, HashSet<Widget>>()
            allPossibleNodes.forEach {
                matchWidgetsPerWindow[it] = HashMap()
                missWidgetsPerWindow[it] = HashSet()
                //propertyChangedWidgets[it] = HashSet()
            }
            val actionableWidgets = ArrayList<Widget>()
            actionableWidgets.addAll(getVisibleWidgetsForAbstraction(guiState))
            val unmappedWidgets = actionableWidgets

            val avm_widgets_map = unmappedWidgets.groupBy { widget_AttributeValuationMapHashMap[it] }
                    .filter { it.key != null }
            allPossibleNodes.forEach {wtgWindow->
                val processed = ArrayList<AttributeValuationMap>()
                val workingList: LinkedList<AttributeValuationMap> = LinkedList<AttributeValuationMap>()
                val addedToWorkingList = HashSet<String>()
                avm_widgets_map.keys. forEach {
                    workingList.add(it!!)
                    addedToWorkingList.add(it.avmId)
                }

                while (workingList.isNotEmpty()) {
                    val avm = workingList.first
                    if (avm.parentAttributeValuationMapId!= "" && !addedToWorkingList.contains(avm.parentAttributeValuationMapId)) {
                        val parentAVM = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(avm.parentAttributeValuationMapId)
                        workingList.addFirst(parentAVM)
                        addedToWorkingList.add(avm.parentAttributeValuationMapId)
                        continue
                    }
                    workingList.removeFirst()
                    //avmId_ewtgWidgets.putIfAbsent(avm.avsId, ArrayList())
                    if (!processed.contains(avm)) {
                        /*  val staticWidgets =  if (avm_widgets_map.containsKey(avm)) {
                              Helper.getStaticWidgets(avm, wtgWindow, true, avmId_ewtgWidgets)
                          } else {
                              Helper.getStaticWidgets(avm, wtgWindow, false, avmId_ewtgWidgets)
                          }*/
                        processed.add(avm)
                        //val staticWidgets = getStaticWidgets(avm, wtgWindow, false)
                        //if a widgetGroup has more
                        val matchingWidget = getStaticWidgets(avm, wtgWindow, false)
                        if (matchingWidget != null) {
                            if (matchWidgetsPerWindow.containsKey(wtgWindow)) {
                                matchWidgetsPerWindow[wtgWindow]!!.put(avm, matchingWidget)
                            }
                        } else {
                            if (missWidgetsPerWindow.contains(wtgWindow) && avm.getResourceId().isNotBlank()) {
                                missWidgetsPerWindow[wtgWindow]!!.add(avm)
                            }
                        }
                    }
                }
            }

/*            val avms = widget_AttributeValuationMapHashMap.values.distinct()
            avms.forEach {
                val attributeValuationSet = it
                allPossibleNodes.forEach {
                    val matchingWidget = getStaticWidgets(attributeValuationSet, it, false)
                    if (matchingWidget != null) {
                        if (matchWidgetsPerWindow.containsKey(it)) {
                            matchWidgetsPerWindow[it]!!.put(attributeValuationSet, matchingWidget)
                        }
                    } else {
                        if (missWidgetsPerWindow.contains(it) && attributeValuationSet.getResourceId().isNotBlank()) {
                            missWidgetsPerWindow[it]!!.add(attributeValuationSet)
                        }
                    }
                }
            }*/
/*            visibleWidgets.iterator().also {
                while (it.hasNext()) {
                    val widget = it.next()
                    *//*  if (autAutMF.getAbstractState(guiState) == null)
                          continue*//*
                    //val attributeValuationSet = autAutMF.getAbstractState(guiState)!!.getAttributeValuationSet(widget, guiState)!!
                    if (!widget_AttributeValuationSetHashMap.containsKey(widget))
                        continue
                    val attributeValuationSet = widget_AttributeValuationSetHashMap.get(widget)!!
                    allPossibleNodes.forEach {
                        val matchingWidget = getStaticWidgets(widget, guiState, attributeValuationSet, it, false, autAutMF)
                        if (matchingWidget.isNotEmpty()) {
                            if (matchWidgets.containsKey(it)) {
                                matchWidgets[it]!!.put(widget, HashSet(matchingWidget))
                            }
                        } else {
                            if (missWidgets.contains(it) && widget.resourceId.isNotBlank()) {
                                missWidgets[it]!!.add(widget)
                            }
                        }
                    }
                }
            }*/
            val scores = HashMap<Window, Double>()
            allPossibleNodes.forEach { window ->
                val missStaticWidgets = window.widgets
                        .filterNot{ staticWidget ->
                            matchWidgetsPerWindow[window]!!.values.any { it == staticWidget } }
                val totalWidgets = matchWidgetsPerWindow[window]!!.size + missStaticWidgets.size
                if (totalWidgets == 0) {
                    scores.put(window, Double.NEGATIVE_INFINITY)
                } else if (window.isRuntimeCreated && matchWidgetsPerWindow[window]!!.values.size / window.widgets.size.toDouble() < 0.5) {
                    // for windows created at runtime
                    // need match perfectly at least all widgets with resource id.
                    // Give a bound to be 70%
                    scores.put(window, Double.NEGATIVE_INFINITY)
                } else {
                    val score = if (matchWidgetsPerWindow[window]!!.size > 0 || window.widgets.size == 0) {
                        /*(matchWidgetsPerWindow[window]!!.size * 1.0 - missWidgetsPerWindow.size * 1.0) / totalWidgets*/
                        (matchWidgetsPerWindow[window]!!.size * 1.0) / totalWidgets
                    } else {
                        Double.NEGATIVE_INFINITY
                    }
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
                    val abstractState = AbstractStateManager.instance.getAbstractState(guiState)
                    if (abstractState!=null) {
                        val nonKeyboardAbstractStates = AbstractStateManager.instance.ABSTRACT_STATES
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
                state.widgets.filter { isVisibleWidget(it) }

        private fun isVisibleWidget(it: Widget) =
                it.enabled &&  isWellVisualized(it) && it.metaInfo.contains("visibleToUser = true")

        fun getVisibleWidgetsForAbstraction(state: State<*>): List<Widget> {
            val result = ArrayList(getActionableWidgetsWithoutKeyboard(state))
            val potentialLayoutWidgets = state.widgets.filterNot{result.contains(it)}.filter {
                it.isVisible && (it.childHashes.isNotEmpty()
                        && (it.className.contains("ListView") || it.className.contains("RecyclerView"))) || it.className.contains("android.webkit.WebView")
            }
            result.addAll(potentialLayoutWidgets)
            return result
        }

        fun getInputFields(state: State<*>) =
                getVisibleWidgets(state).filter {!it.isKeyboard && isUserLikeInput(it) }

        fun matchEWTGWidget(guiWidget: Widget, guiState: State<*>, wtgNode: Window, updateModel: Boolean,
                            guiWidgetId_ewtgWidgets: HashMap<ConcreteId, EWTGWidget> = HashMap() ): EWTGWidget? {
            var matchedEWTGWidgets: ArrayList<EWTGWidget> = ArrayList()
            if (guiWidget.resourceId.isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(guiWidget.resourceId)
                val candidates = wtgNode.widgets.filter {
                    if (guiWidget.resourceId == "android:id/title" || guiWidget.resourceId == "android:id/alertTitle") {
                        it.resourceIdName == unqualifiedResourceId && it.possibleTexts.contains(guiWidget.text)
                    } else {
                        it.resourceIdName == unqualifiedResourceId
                    }
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()  && guiWidget.contentDesc.isNotBlank()) {
                val candidates = wtgNode.widgets.filter { w ->
                    w.possibleContentDescriptions.contains(guiWidget.contentDesc)
                            && w.className == guiWidget.className
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && !guiWidget.isInputField && guiWidget.text.isNotBlank()) {
                val candidates = wtgNode.widgets.filter { w ->
                    w.possibleTexts.contains(guiWidget.text) && w.className == guiWidget.className
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()) {
                val candidates = wtgNode.widgets.filter { it.widgetUUID != "" }.filter { w ->
                    guiWidget.uid.toString() == w.widgetUUID
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && guiWidget.resourceId.isBlank()) {
                val candidates = wtgNode.widgets.filter { it.resourceIdName.isBlank() && it.className == guiWidget.className }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.size>1 && updateModel) {
                val matchingScores = HashMap<EWTGWidget,Double>()

                matchedEWTGWidgets.forEach {
                    val hierarchyMatchingScore: Double = verifyMatchingHierchyWindowLayout2(guiWidget, it, wtgNode, guiState,guiWidgetId_ewtgWidgets )
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
                    val widgetId = guiWidget.id.uid
                    val newWidget = EWTGWidget.getOrCreateStaticWidget(
                            widgetId = widgetId.toString(),
                            resourceIdName = guiWidget.resourceId,
                            className = guiWidget.className,
                            wtgNode = wtgNode,
                            resourceId = "",
                            attributeValuationSetId = widgetId.toString()
                    )
                    newWidget.createdAtRuntime = true
                    val ancestorWidgetMatchedEWTGWidget: Widget? = findAncestorWidgetHavingMatchedEWTGWidget(guiWidget.parentId,guiState,guiWidgetId_ewtgWidgets)
                    if (ancestorWidgetMatchedEWTGWidget != null) {
                        val matchedAncestorEWTGWidget = guiWidgetId_ewtgWidgets.get(ancestorWidgetMatchedEWTGWidget.id)
                        newWidget.parent = matchedAncestorEWTGWidget
                    } else {
                        val layoutRoots = wtgNode.widgets.filter { it.parent == null && it != newWidget}
                        layoutRoots.forEach {
                            it.parent = newWidget
                        }
                    }
                    //newWidget.contentDesc = originalWidget.contentDesc
                    if (guiWidget.text.isNotBlank())
                        newWidget.possibleTexts.add(guiWidget.text)
                    if (guiWidget.contentDesc.isNotBlank())
                        newWidget.possibleContentDescriptions.add(guiWidget.contentDesc)
                    /*if (attributeValuationMap.getResourceId() == "android:id/title") {

                    }*/
                    wtgNode.addWidget(newWidget)
                    matchedEWTGWidgets.add(newWidget)
                    guiWidgetId_ewtgWidgets[guiWidget.id]=newWidget
                }
            }
            return matchedEWTGWidgets.firstOrNull()
        }
        fun getStaticWidgets(attributeValuationMap: AttributeValuationMap, wtgNode: Window, updateModel: Boolean,
                             avm_ewtgWidgets: HashMap<String, EWTGWidget> = HashMap()): EWTGWidget? {

            var matchedEWTGWidgets: ArrayList<EWTGWidget> = ArrayList()
            if (attributeValuationMap.getResourceId().isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(attributeValuationMap.getResourceId())
                val candidates = wtgNode.widgets.filter {
                    if (attributeValuationMap.getResourceId() == "android:id/title" || attributeValuationMap.getResourceId() == "android:id/alertTitle") {
                        it.resourceIdName == unqualifiedResourceId && it.possibleTexts.contains(attributeValuationMap.getText())
                    } else {
                        it.resourceIdName == unqualifiedResourceId
                    }
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()  && attributeValuationMap.getContentDesc().isNotBlank()) {
                val candidates = wtgNode.widgets.filter { w ->
                    w.possibleContentDescriptions.contains(attributeValuationMap.getContentDesc())
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && !attributeValuationMap.isInputField() && attributeValuationMap.getText().isNotBlank()) {
                val candidates = wtgNode.widgets.filter { w ->
                    w.possibleTexts.contains(attributeValuationMap.getText())
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()) {
                val candidates = wtgNode.widgets.filter { it.widgetUUID != "" }.filter { w ->
                    val attributeValuationSet_w = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[wtgNode.classType]!!.get(w.widgetUUID)
                    if (attributeValuationSet_w != null) {
                        attributeValuationMap.isDerivedFrom(attributeValuationSet_w)
                    } else
                        false
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty() && attributeValuationMap.getResourceId().isBlank()) {
                val candidates = wtgNode.widgets.filter { it.resourceIdName.isBlank() && it.className == attributeValuationMap.getClassName() }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.size>1 && updateModel) {
                val matchingScores = HashMap<EWTGWidget,Double>()
                matchedEWTGWidgets.forEach {
                    val hierarchyMatchingScore: Double = verifyMatchingHierchyWindowLayout(attributeValuationMap, it, wtgNode, avm_ewtgWidgets)
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
                avm_ewtgWidgets[attributeValuationMap.avmId]=matchedEWTGWidgets.first()
            }
            /*if (matchedStaticWidgets.isEmpty()
                    && (widget.className == "android.widget.RelativeLayout" || widget.className.contains("ListView") ||  widget.className.contains("RecycleView" ) ||  widget.className == "android.widget.LinearLayout"))
            {
                matchedStaticWidgets.addAll(wtgNode.widgets.filter { w ->
                    w.className.contains(widget.className) && w.resourceId.isBlank() && w.resourceIdName.isBlank()
                })
            }
            if (matchedStaticWidgets.isEmpty() &&
                    (hasParentWithType(widget, state, "ListView") || hasParentWithType(widget, state, "RecycleView"))) {
                //this is an item of ListView or RecycleView

            }*/
            if (updateModel) {
                matchedEWTGWidgets.forEach {
                    if (attributeValuationMap.isInputField() && attributeValuationMap.getText().isNotBlank()) {
                        it.textInputHistory.add(attributeValuationMap.getText())
                    }
                }
                if (matchedEWTGWidgets.isEmpty()) {
                    val attributeValuationSetId = if (getUnqualifiedResourceId(attributeValuationMap.getResourceId()).isBlank())
                        ""
                    else
                        attributeValuationMap.avmId
                    val newWidget = EWTGWidget.getOrCreateStaticWidget(
                            widgetId = attributeValuationMap.avmId.toString(),
                            resourceIdName = getUnqualifiedResourceId(attributeValuationMap.getResourceId()),
                            className = attributeValuationMap.getClassName(),
                            wtgNode = wtgNode,
                            resourceId = "",
                            attributeValuationSetId = attributeValuationSetId
                    )
                    newWidget.createdAtRuntime = true
                    val ancestorAVMWithMatchedEWTGWidget: AttributeValuationMap? = findAncestorAVMHavingMatchedEWTGWidget(attributeValuationMap.parentAttributeValuationMapId,
                            avm_ewtgWidgets,wtgNode.classType)
                    if (ancestorAVMWithMatchedEWTGWidget!=null) {
                        val matchedAncestorEWTGWidget = avm_ewtgWidgets.get(ancestorAVMWithMatchedEWTGWidget.avmId)!!
                        newWidget.parent = matchedAncestorEWTGWidget
                    } else {
                        val layoutRoots = wtgNode.widgets.filter { it.parent == null && it != newWidget}
                        layoutRoots.forEach {
                            it.parent = newWidget
                        }
                    }
                    //newWidget.contentDesc = originalWidget.contentDesc
                    if (attributeValuationMap.getText().isNotBlank())
                        newWidget.possibleTexts.add(attributeValuationMap.getText())
                    if (attributeValuationMap.getContentDesc().isNotBlank())
                        newWidget.possibleContentDescriptions.add(attributeValuationMap.getContentDesc())
                    /*if (attributeValuationMap.getResourceId() == "android:id/title") {

                    }*/
                    wtgNode.addWidget(newWidget)
                    matchedEWTGWidgets.add(newWidget)
                    avm_ewtgWidgets[attributeValuationMap.avmId]=newWidget
                }
            }
            return matchedEWTGWidgets.firstOrNull()
        }

        private fun findAncestorWidgetHavingMatchedEWTGWidget(parentWidgetId: ConcreteId?, guiState: State<*>, guiWidget_Ewtgwidgets: HashMap<ConcreteId,EWTGWidget>): Widget? {
            if (parentWidgetId == null)
                return null
            val parentWidget = guiState.widgets.find { it.id == parentWidgetId }!!
            if (guiWidget_Ewtgwidgets.containsKey(parentWidgetId)) {
                return parentWidget
            }
            return findAncestorWidgetHavingMatchedEWTGWidget(parentWidget.parentId,guiState, guiWidget_Ewtgwidgets)
        }

        private fun findAncestorAVMHavingMatchedEWTGWidget(parentAvmId: String, avmEwtgwidgets: HashMap<String,EWTGWidget>,activity: String): AttributeValuationMap? {
            if (parentAvmId == "")
                return null
            val parentAVM = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.get(parentAvmId)!!
            if (avmEwtgwidgets.containsKey(parentAvmId)) {
                if (avmEwtgwidgets.containsKey(parentAvmId)) {
                    return parentAVM
                }
            }
            return findAncestorAVMHavingMatchedEWTGWidget(parentAVM.parentAttributeValuationMapId,avmEwtgwidgets,activity)
        }

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
                    val correctnessDistance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent!!,it, ArrayList())
                    totalCorrectnessDistance += correctnessDistance
                }
                val averageCorrectness = totalCorrectnessDistance/matchingParentWidgets.size
                return averageCorrectness
            }
            return Double.POSITIVE_INFINITY
        }
        private fun verifyMatchingHierchyWindowLayout(avm: AttributeValuationMap, ewtgWidget: EWTGWidget, wtgNode: Window, avmEwtgwidgets: HashMap<String, EWTGWidget>): Double {
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
                    val correctnessDistance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent!!,it, ArrayList())
                    totalCorrectnessDistance += correctnessDistance
                }
                val averageCorrectness = totalCorrectnessDistance/matchingParentWidgets.size
                return averageCorrectness
            }
            return Double.POSITIVE_INFINITY
        }

        private fun calculateEWTGWidgetAncestorCorrectness(ewtgWidget: EWTGWidget?, ancestor: EWTGWidget,
                                                           traversedWidget: ArrayList<EWTGWidget>): Double {

            if (ewtgWidget == null)
                return Double.POSITIVE_INFINITY
            if (ewtgWidget == ancestor)
                return 1.0
            if (traversedWidget.contains(ewtgWidget) || traversedWidget.contains(ancestor))
                return Double.POSITIVE_INFINITY
            if (ewtgWidget.parent == ewtgWidget)
                return Double.POSITIVE_INFINITY
            traversedWidget.add(ewtgWidget)
            traversedWidget.add(ancestor)
            var distance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent, ancestor,traversedWidget)
            if (distance == Double.POSITIVE_INFINITY && ancestor.parent!=null) {
                if (ancestor.parent == ancestor)
                    return distance
                distance = calculateEWTGWidgetAncestorCorrectness(ancestor.parent,ewtgWidget,traversedWidget)
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

        fun getUnqualifiedResourceId(widget: Widget): String {
            val unqualifiedResourceId = widget.resourceId.substring(widget.resourceId.indexOf("/") + 1)
            return unqualifiedResourceId
        }

        fun getUnqualifiedResourceId(qualifiedResourceId: String): String {
            val unqualifiedResourceId = qualifiedResourceId.substring(qualifiedResourceId.indexOf("/") + 1)
            return unqualifiedResourceId
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

        fun isSameFullScreenDimension(rotation: Rotation, guiTreeDimension: Rectangle, autautMF: ATUAMF): Boolean {
            if (rotation == Rotation.PORTRAIT) {
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

        fun isDialog(rotation: Rotation, guiTreeDimension: Rectangle, guiState: State<*>, autautMF: ATUAMF):Boolean {
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
                if (currentState.widgets.any { it.resourceId == "android:id/title" } )
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
    }
}

enum class DescendantLayoutDirection {
    HORIZONTAL,
    VERTICAL,
    UNKNOWN
}
