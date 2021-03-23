package org.droidmate.exploration.modelFeatures.atua.DSTG.reducer

import org.droidmate.exploration.modelFeatures.atua.ATUAMF
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.atua.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.atua.DSTG.AttributeValuationMap
import org.droidmate.exploration.modelFeatures.atua.Rotation
import org.droidmate.exploration.modelFeatures.atua.EWTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, activity: String, packageName: String, rotation: Rotation, atuaMF: ATUAMF): HashMap<Widget, AttributeValuationMap>{
            AttributeValuationMap.allWidgetAVMHashMap.putIfAbsent(activity, HashMap())
            AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP.putIfAbsent(activity,HashMap())
            val derivedWidgets = AttributeValuationMap.allWidgetAVMHashMap[activity]!!
            val widgetAVMHashMap = HashMap<Widget, AttributeValuationMap>()
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val attributePath_Cardinalitys = HashMap<AttributePath,Cardinality>()
            val capturedAttributePaths = ArrayList<AttributePath>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            val capturedWidgets = if (activity.startsWith("com.oath.mobile.platform.phoenix.core.")) {
                Helper.getVisibleWidgets(guiState)
            } else {
                Helper.getVisibleWidgetsForAbstraction(guiState)
            }
         /*   capturedWidgets.forEach { widget ->
                if (derivedWidgets.containsKey(widget)) {
                    widgetAVMHashMap.put(widget,derivedWidgets.get(widget)!!)
                }
            }*/
            val toReduceWidgets = ArrayList(Helper.getVisibleWidgets(guiState))
            if (toReduceWidgets.isEmpty()) {
                toReduceWidgets.addAll(guiState.widgets.filter { !it.isKeyboard })
            }
            //toReduceWidgets.removeIf { derivedWidgets.contains(it) }
            //val toReduceWidgets = Helper.getVisibleWidgetsForAbstraction(guiState)

            toReduceWidgets .forEach {
                val widgetAttributePath = if (tempFullAttrPaths.containsKey(it))
                {
                    tempFullAttrPaths[it]!!
                }
                else {
                    WidgetReducer.reduce(it, guiState, activity, rotation, atuaMF, tempFullAttrPaths, tempRelativeAttrPaths)
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
            attributePath_Cardinalitys.keys.forEach {a->
                var attributeValuationSet =  AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.map { it.value }. find { it.haveTheSameAttributePath(a) }
                if (attributeValuationSet == null) {
                    attributeValuationSet =  AttributeValuationMap(a,attributePath_Cardinalitys[a]!!,activity,attributePath_Cardinalitys)
                    attributeValuationSet!!.initActions()
                }
                widgetReduceMap.filter { it.value.equals(a) }.forEach { w, _ ->
                    if (capturedAttributePaths.contains(a)) {
                        widgetAVMHashMap.put(w, attributeValuationSet!!)
                    }
                    derivedWidgets.put(w, attributeValuationSet!!)
                }
            }
/*            capturedAttributePaths.forEach { a ->
                var attributeValuationSet =  AttributeValuationMap.ALL_ATTRIBUTE_VALUATION_MAP[activity]!!.map { it.value }. find { it.haveTheSameAttributePath(a) }
                if (attributeValuationSet == null) {
                    attributeValuationSet =  AttributeValuationMap(a,attributePath_Cardinalitys[a.attributePathId]!!,activity,attributePath_Cardinalitys)
                    attributeValuationSet!!.initActions()
                } else {
                    val a = 1
                }
                widgetReduceMap.filter { it.value.equals(a)}.forEach { w,_ ->
                    widgetAVMHashMap.put(w,attributeValuationSet)
                    derivedWidgets.put(w,attributeValuationSet)
                }
            }*/
            return widgetAVMHashMap
        }
    }

}