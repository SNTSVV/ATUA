package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AbstractStateManager
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, window: Window, packageName: String, rotation: Rotation, atuaMF: ATUAMF): HashMap<Widget, AttributeValuationMap>{
            AttributeValuationMap.allWidgetAVMHashMap.putIfAbsent(window, HashMap())
            AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.putIfAbsent(window,HashMap())
            AttributeValuationMap.attributePath_AttributeValuationMap.putIfAbsent(window, HashMap())
            val derivedWidgets = AttributeValuationMap.allWidgetAVMHashMap[window]!!
            val widgetAVMHashMap = HashMap<Widget, AttributeValuationMap>()
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val derivedAttributePaths = HashSet<AttributePath>()
            val capturedAttributePaths = ArrayList<AttributePath>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            val capturedWidgets = if (window.classType.startsWith("com.oath.mobile.platform.phoenix.core.")) {
                Helper.getVisibleWidgets(guiState).filter { !it.isKeyboard }
            } else {
                Helper.getVisibleWidgetsForAbstraction(guiState)
            }
         /*   capturedWidgets.forEach { widget ->
                if (derivedWidgets.containsKey(widget)) {
                    widgetAVMHashMap.put(widget,derivedWidgets.get(widget)!!)
                }
            }*/
            val toReduceWidgets = ArrayList(Helper.getVisibleWidgets(guiState).filterNot { it.isKeyboard })
            if (toReduceWidgets.isEmpty()) {
                toReduceWidgets.addAll(guiState.widgets.filter { !it.isKeyboard })
            }
            toReduceWidgets.forEach {
                val existingAVM = derivedWidgets.get(it)
                if (existingAVM!=null) {
                    if (capturedWidgets.contains(it)) {
                       widgetAVMHashMap.put(it,existingAVM)
                    }
                }
            }
            toReduceWidgets.removeIf { derivedWidgets.containsKey(it) }
            //toReduceWidgets.removeIf { derivedWidgets.contains(it) }
            //val toReduceWidgets = Helper.getVisibleWidgetsForAbstraction(guiState)
            val guiTreeRectangle = Helper.computeGuiTreeDimension(guiState)
            var isOptionsMenu = if (!Helper.isDialog(rotation,guiTreeRectangle, guiState, atuaMF))
                Helper.isOptionsMenuLayout(guiState)
            else
                false
            toReduceWidgets .forEach {
                val widgetAttributePath = if (tempFullAttrPaths.containsKey(it))
                {
                    tempFullAttrPaths[it]!!
                }
                else {
                    WidgetReducer.reduce(it, guiState,isOptionsMenu,guiTreeRectangle,window , rotation, atuaMF, tempFullAttrPaths, tempRelativeAttrPaths)
                }
                widgetReduceMap.put(it, widgetAttributePath)
                if (capturedWidgets.contains(it)) {
                    if (!capturedAttributePaths.contains(widgetAttributePath)) {
                        capturedAttributePaths.add(widgetAttributePath)
                    }
                }
                derivedAttributePaths.add(widgetAttributePath)
            }
            val workingList = Stack<AttributePath>()
            val processedList = Stack<AttributePath>()
            derivedAttributePaths.filterNot { it.attributePathId == emptyUUID }.also {
                workingList.addAll(it)
            }
            while (workingList.isNotEmpty()) {
                val element = workingList.pop()
                processedList.add(element)
                var attributeValuationSet =  AttributeValuationMap.getExistingObject(element,window)
                if (attributeValuationSet == null)
                    attributeValuationSet =  AttributeValuationMap(element,window)
                /*val similarAVMs = AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.get(window)!!.values.filter { it!=attributeValuationSet && it.haveTheSameAttributePath(attributeValuationSet) }
                if (similarAVMs.isNotEmpty()) {
                    throw Exception()
                }*/
                widgetReduceMap.filter { it.value.equals(element) }.forEach { w, _ ->
                    if (capturedAttributePaths.contains(element)) {
                        widgetAVMHashMap.put(w, attributeValuationSet!!)
                    }
                    derivedWidgets.put(w, attributeValuationSet!!)
                }
                val nonProcessedChildren =  derivedAttributePaths.filter { it.parentAttributePathId == element.attributePathId }.filterNot { processedList.contains(it) }
                if (nonProcessedChildren.isNotEmpty()) {
                    nonProcessedChildren.forEach {
                        workingList.push(it)
                    }
                }
            }
            return widgetAVMHashMap
        }
    }

}