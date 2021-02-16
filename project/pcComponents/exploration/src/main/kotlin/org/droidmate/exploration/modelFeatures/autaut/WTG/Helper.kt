package org.droidmate.exploration.modelFeatures.autaut.WTG

import org.droidmate.deviceInterface.exploration.ExplorationAction
import org.droidmate.deviceInterface.exploration.Rectangle
import org.droidmate.deviceInterface.exploration.Swipe
import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.actions.availableActions
import org.droidmate.exploration.actions.swipeDown
import org.droidmate.exploration.actions.swipeLeft
import org.droidmate.exploration.actions.swipeRight
import org.droidmate.exploration.actions.swipeUp
import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.explorationModel.ExplorationTrace
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.abs

class Helper {
    companion object {

        fun mergeOptionsMenuWithActivity(newState: State<*>, widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>, optionsMenuNode: Window, activityNode: Window, wtg: WindowTransitionGraph, autAutMF: AutAutMF): Boolean {

            var shouldMerge = false
            var containsOptionMenuWidgets = false
            var containsActivityWidgets = false
            var optionsMenuWidgets = ArrayList<EWTGWidget>()
            var activityWidgets = ArrayList<EWTGWidget>()

            val avms = widget_AttributeValuationSetHashMap.values.distinct()
            avms.forEach {
                val attributeValuationSet = it
                optionsMenuWidgets.addAll(getStaticWidgets(attributeValuationSet, optionsMenuNode, false))

            }
            if (optionsMenuWidgets.isEmpty()) {
                containsOptionMenuWidgets = false
            } else {
                containsOptionMenuWidgets = true
            }

            avms.forEach {
                val attributeValuationSet = it
                activityWidgets.addAll(getStaticWidgets(attributeValuationSet, activityNode, false))
                if (activityWidgets.isEmpty()) {
                    containsActivityWidgets = false
                } else {
                    containsActivityWidgets = true
                }
            }
            if (containsActivityWidgets && containsOptionMenuWidgets) {
                shouldMerge = true
            }
            if (shouldMerge) {
                AutAutMF.log.info("Merge $optionsMenuNode to $activityNode")
                wtg.mergeNode(optionsMenuNode, activityNode)
                /*transitionGraph.removeVertex(optionsMenuNode)
                regressionTestingMF.staticEventWindowCorrelation.filter { it.value.containsKey(optionsMenuNode) }.forEach { event, correlation ->
                    correlation.remove(optionsMenuNode)
                }*/


                return true
            }
            return false
        }

        fun calculateMatchScoreForEachNode(guiState: State<*>, allPossibleNodes: List<Window>, appName: String, activity: String, widget_AttributeValuationSetHashMap: HashMap<Widget, AttributeValuationSet>,
                                           autAutMF: AutAutMF): HashMap<Window, Double> {
            val matchWidgets = HashMap<Window, HashMap<AttributeValuationSet, HashSet<EWTGWidget>>>()
            val missWidgets = HashMap<Window, HashSet<AttributeValuationSet>>()
            //val propertyChangedWidgets = HashMap<Window, HashSet<Widget>>()
            val visibleWidgets = ArrayList<Widget>()
            visibleWidgets.addAll(getVisibleWidgetsForAbstraction(guiState))
            if (visibleWidgets.isEmpty()) {
                visibleWidgets.addAll(guiState.widgets.filterNot { it.isVisible && it.isKeyboard })
            }
            allPossibleNodes.forEach {
                matchWidgets[it] = HashMap()
                missWidgets[it] = HashSet()
                //propertyChangedWidgets[it] = HashSet()
            }
            val avms = widget_AttributeValuationSetHashMap.values.distinct()
            avms.forEach {
                val attributeValuationSet = it
                allPossibleNodes.forEach {
                    val matchingWidget = getStaticWidgets(attributeValuationSet, it, false)
                    if (matchingWidget.isNotEmpty()) {
                        if (matchWidgets.containsKey(it)) {
                            matchWidgets[it]!!.put(attributeValuationSet, HashSet(matchingWidget))
                        }
                    } else {
                        if (missWidgets.contains(it) && attributeValuationSet.getResourceId().isNotBlank()) {
                            missWidgets[it]!!.add(attributeValuationSet)
                        }
                    }
                }
            }
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
                val missStaticWidgets = window.widgets.filterNot { staticWidget -> matchWidgets[window]!!.values.any { it.contains(staticWidget) } }
                val totalWidgets = matchWidgets[window]!!.size + missStaticWidgets.size
                if (!window.fromModel && matchWidgets[window]!!.values.flatten().size / window.widgets.size.toDouble() < 0.7) {
                    //need match perfectly at least all widgets with resource id.
                    //Give a bound to be 70%
                    scores.put(window, Double.NEGATIVE_INFINITY)
                } else {
                    val score = if (matchWidgets[window]!!.size > 0 || window.widgets.size == 0) {
                        (matchWidgets[window]!!.size * 1.0 - missWidgets.size * 1.0) / totalWidgets
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
            val visibleWidgets = getVisibleWidgets(guiState)
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
            val result = visibleWidgets.filter{!excludeWidgets.contains(it)}.filter {
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
                        val result2 = guiState.widgets
                                .filter { isInteractiveWidgetButNotKeyboard(it) && !nonKeyboardRelatedWidgets.contains(it.uid)}
                        guiState_actionableWidgets.put(guiState,result2)
                        return result2
                    } else {
                        //this is new state
                        return guiState.widgets
                                .filter { isInteractiveWidgetButNotKeyboard(it)}
                    }

                }
            }
            guiState_actionableWidgets.put(guiState,result)
            return result
        }

        fun getVisibleWidgets(state: State<*>) =
                state.widgets.filter { isVisibleWidget(it) }

        private fun isVisibleWidget(it: Widget) =
                it.enabled && (it.isVisible || it.visibleAreas.isNotEmpty()) && !it.isKeyboard

        fun getVisibleWidgetsForAbstraction(state: State<*>): List<Widget> {
            val result = getActionableWidgetsWithoutKeyboard(state)
            return result
        }

        fun getInputFields(state: State<*>) =
                getVisibleWidgets(state).filter { isUserLikeInput(it) }

        fun getStaticWidgets( attributeValuationSet: AttributeValuationSet, wtgNode: Window, updateModel: Boolean,
                             avm_ewtgWidgets: HashMap<String, ArrayList<EWTGWidget>> = HashMap()): List<EWTGWidget> {

            var matchedEWTGWidgets: ArrayList<EWTGWidget> = ArrayList()
            if (attributeValuationSet.getResourceId().isNotBlank()) {
                val unqualifiedResourceId = getUnqualifiedResourceId(attributeValuationSet.getResourceId())

                matchedEWTGWidgets.addAll(wtgNode.widgets.filter {
                    if (attributeValuationSet.getResourceId() == "android:id/title" || attributeValuationSet.getResourceId() == "android:id/alertTitle") {
                        it.resourceIdName == unqualifiedResourceId && it.text == attributeValuationSet.getText()
                    } else {
                        it.resourceIdName == unqualifiedResourceId
                    }
                })
            }
            if (matchedEWTGWidgets.isEmpty() && attributeValuationSet.getContentDesc().isNotBlank()) {
                matchedEWTGWidgets.addAll(wtgNode.widgets.filter { w ->
                    attributeValuationSet.getContentDesc() == w.contentDesc
                })
            }
            if (matchedEWTGWidgets.isEmpty() && !attributeValuationSet.isInputField() && attributeValuationSet.getText().isNotBlank()) {
                val candidates = wtgNode.widgets.filter { w ->
                    w.possibleTexts.contains(attributeValuationSet.getText())
                }
                matchedEWTGWidgets.addAll(candidates)
            }
            if (matchedEWTGWidgets.isEmpty()) {
                matchedEWTGWidgets.addAll(wtgNode.widgets.filter { it.attributeValuationSetId != "" }.filter { w ->
                    val attributeValuationSet_w = AttributeValuationSet.allAttributeValuationSet[wtgNode.activityClass]!!.get(w.attributeValuationSetId)
                    if (attributeValuationSet_w != null) {
                        attributeValuationSet.isDerivedFrom(attributeValuationSet_w)
                    } else
                        false
                })
            }
            if (matchedEWTGWidgets.isEmpty() && attributeValuationSet.getResourceId().isBlank()) {
                matchedEWTGWidgets.addAll(wtgNode.widgets.filter { it.resourceIdName.isBlank() && it.className == attributeValuationSet.getClassName() })
            }
            if (matchedEWTGWidgets.isNotEmpty()) {
                val matchingScores = HashMap<EWTGWidget,Double>()
                matchedEWTGWidgets.forEach {
                    val hierarchyMatchingScore: Double = verifyMatchingHierchyWindowLayout(attributeValuationSet, it, wtgNode, avm_ewtgWidgets)
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
            if (avm_ewtgWidgets.isNotEmpty()) {
                avm_ewtgWidgets[attributeValuationSet.avsId]!!.addAll(matchedEWTGWidgets)
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
                    if (attributeValuationSet.isInputField() && attributeValuationSet.getText().isNotBlank()) {
                        it.textInputHistory.add(attributeValuationSet.getText())
                    }
                }
                if (matchedEWTGWidgets.isEmpty()) {
                    val attributeValuationSetId = if (getUnqualifiedResourceId(attributeValuationSet.getResourceId()).isBlank())
                        ""
                    else
                        attributeValuationSet.avsId
                    val newWidget = EWTGWidget.getOrCreateStaticWidget(
                            widgetId = attributeValuationSet.avsId.toString(),
                            resourceIdName = getUnqualifiedResourceId(attributeValuationSet.getResourceId()),
                            className = attributeValuationSet.getClassName(),
                            wtgNode = wtgNode,
                            resourceId = "",
                            activity = wtgNode.activityClass,
                            attributeValuationSetId = attributeValuationSetId
                    )
                    val ancestorAVMWithMatchedEWTGWidget: AttributeValuationSet? = findAncestorAVMHavingMatchedEWTGWidget(attributeValuationSet.parentAttributeValuationSetId,
                            avm_ewtgWidgets,wtgNode.activityClass)
                    if (ancestorAVMWithMatchedEWTGWidget!=null) {
                        val matchedAncestorEWTGWidgets = avm_ewtgWidgets.get(ancestorAVMWithMatchedEWTGWidget.avsId)!!
                        matchedAncestorEWTGWidgets.forEach {
                            newWidget.parent = it
                        }
                    } else {
                        val layoutRoots = wtgNode.widgets.filter { it.parent == null && it != newWidget}
                        layoutRoots.forEach {
                            it.parent = newWidget
                        }
                    }
                    //newWidget.contentDesc = originalWidget.contentDesc

                    if (attributeValuationSet.getResourceId() == "android:id/title") {
                        newWidget.text = attributeValuationSet.getText()
                    }
                    wtgNode.addWidget(newWidget)
                    matchedEWTGWidgets.add(newWidget)
                    avm_ewtgWidgets[attributeValuationSet.avsId]!!.add(newWidget)
                }
            }
            return matchedEWTGWidgets
        }

        private fun findAncestorAVMHavingMatchedEWTGWidget(parentAvmId: String, avmEwtgwidgets: HashMap<String,ArrayList<EWTGWidget>>,activity: String): AttributeValuationSet? {
            if (parentAvmId == "")
                return null
            val parentAVM = AttributeValuationSet.allAttributeValuationSet[activity]!!.get(parentAvmId)!!
            if (avmEwtgwidgets.containsKey(parentAvmId)) {
                if (avmEwtgwidgets.get(parentAvmId)!!.isNotEmpty()) {
                    return parentAVM
                }
            }
            return findAncestorAVMHavingMatchedEWTGWidget(parentAVM.parentAttributeValuationSetId,avmEwtgwidgets,activity)
        }

        private fun verifyMatchingHierchyWindowLayout(avm: AttributeValuationSet, ewtgWidget: EWTGWidget, wtgNode: Window, avmEwtgwidgets: HashMap<String, ArrayList<EWTGWidget>>): Double {
            if (avm.parentAttributeValuationSetId == "" && ewtgWidget.parent == null)
                return 1.0
            var traversingAVM: AttributeValuationSet = avm
            while (traversingAVM.parentAttributeValuationSetId!= "" && ewtgWidget.parent != null) {
                if (!avmEwtgwidgets.containsKey(traversingAVM.parentAttributeValuationSetId)) {
                    traversingAVM = AttributeValuationSet.allAttributeValuationSet[wtgNode.activityClass]!!.get(traversingAVM.parentAttributeValuationSetId)!!
                    continue
                }
                val matchingParentWidgets = avmEwtgwidgets.get(traversingAVM.parentAttributeValuationSetId)!!
                var totalCorrectnessDistance = 0.0
                matchingParentWidgets.forEach {
                    val correctnessDistance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent!!,it)
                    totalCorrectnessDistance += correctnessDistance
                }
                val averageCorrectness = totalCorrectnessDistance/matchingParentWidgets.size
                return averageCorrectness
            }
            return Double.POSITIVE_INFINITY
        }

        private fun calculateEWTGWidgetAncestorCorrectness(ewtgWidget: EWTGWidget?, ancestor: EWTGWidget): Double {
            if (ewtgWidget == null)
                return Double.POSITIVE_INFINITY
            if (ewtgWidget == ancestor)
                return 1.0
            var distance = calculateEWTGWidgetAncestorCorrectness(ewtgWidget.parent, ancestor)
            if (distance == Double.POSITIVE_INFINITY && ancestor.parent!=null) {
                distance = calculateEWTGWidgetAncestorCorrectness(ancestor.parent,ewtgWidget)
            }
            return 1.0+distance
        }

        internal var changeRatioCriteria: Double = 0.05

        fun isInteractiveWidgetButNotKeyboard(widget: Widget): Boolean =
                !widget.isKeyboard && (widget.isInputField || widget.clickable || widget.checked != null || widget.longClickable || isScrollableWidget(widget) || (!widget.hasClickableDescendant && widget.selected.isEnabled()))

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

        fun isSameFullScreenDimension(rotation: Rotation, guiTreeDimension: Rectangle, autautMF: AutAutMF): Boolean {
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

        fun extractInputFieldAndCheckableWidget(prevState: State<*>): Map<Widget, String> {
            val condition = HashMap<Widget, String>()
            prevState.visibleTargets.filter { it.isInputField }.forEach { widget ->
                condition.put(widget, widget.text)
            }
            prevState.visibleTargets.filter { Helper.isUserLikeInput(it) && !it.isInputField }.forEach { widget ->
                condition.put(widget, widget.checked.toString())
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
