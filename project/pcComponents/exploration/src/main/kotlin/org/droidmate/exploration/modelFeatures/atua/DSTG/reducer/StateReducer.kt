package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.emptyUUID
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, classType: String, packageName: String, rotation: Rotation, atuaMF: ATUAMF): HashMap<Widget, AttributeValuationMap>{
            AttributeValuationMap.allWidgetAVMHashMap.putIfAbsent(classType, HashMap())
            AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.putIfAbsent(classType,HashMap())
            val derivedWidgets = AttributeValuationMap.allWidgetAVMHashMap[classType]!!
            val widgetAVMHashMap = HashMap<Widget, AttributeValuationMap>()
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val attributePath_Cardinalitys = HashMap<AttributePath,Cardinality>()
            val capturedAttributePaths = ArrayList<AttributePath>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            val capturedWidgets = if (classType.startsWith("com.oath.mobile.platform.phoenix.core.")) {
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
                    WidgetReducer.reduce(it, guiState,isOptionsMenu,guiTreeRectangle, classType, rotation, atuaMF, tempFullAttrPaths, tempRelativeAttrPaths)
                }

                if (capturedWidgets.contains(it)) {
                    widgetReduceMap.put(it, widgetAttributePath)
                    if (!capturedAttributePaths.contains(widgetAttributePath)) {
                        capturedAttributePaths.add(widgetAttributePath)
                    }
                }
                if (attributePath_Cardinalitys.contains(widgetAttributePath)){
                    attributePath_Cardinalitys[widgetAttributePath] = Cardinality.MANY
                }
                else
                {
                    attributePath_Cardinalitys[widgetAttributePath] = Cardinality.ONE
                }
            }
            val workingList = Stack<AttributePath>()
            val processedList = Stack<AttributePath>()
            attributePath_Cardinalitys.keys.filterNot { it.attributePathId == emptyUUID }.also {
                workingList.addAll(it)
            }
            while (workingList.isNotEmpty()) {
                val element = workingList.pop()
                processedList.add(element)
                var attributeValuationSet =  AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[classType]!!.map { it.value }. find { it.haveTheSameAttributePath(element) }
                if (attributeValuationSet == null) {
                    attributeValuationSet =  AttributeValuationMap(element,attributePath_Cardinalitys[element]!!,classType,attributePath_Cardinalitys)
                }
                widgetReduceMap.filter { it.value.equals(element) }.forEach { w, _ ->
                    if (capturedAttributePaths.contains(element)) {
                        widgetAVMHashMap.put(w, attributeValuationSet!!)
                    }
                    derivedWidgets.put(w, attributeValuationSet!!)
                }
                val nonProcessedChildren =  attributePath_Cardinalitys.keys.filter { it.parentAttributePathId == element.attributePathId }.filterNot { processedList.contains(it) }
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