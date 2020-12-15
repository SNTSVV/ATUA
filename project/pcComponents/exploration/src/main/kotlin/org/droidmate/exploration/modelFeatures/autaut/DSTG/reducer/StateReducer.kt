package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.AutAutMF
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.Rotation
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, activity: String, packageName: String,rotation: Rotation,autAutMF: AutAutMF): HashMap<Widget, AttributeValuationSet>{
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val attributePath_Cardinalitys = HashMap<UUID,Cardinality>()
            val capturedAttributePaths = ArrayList<AttributePath>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            val capturedWidgets = if (activity.startsWith("com.oath.mobile.platform.phoenix.core.")) {
                Helper.getVisibleWidgets(guiState)
            } else {
                Helper.getVisibleWidgetsForAbstraction(guiState)
            }
            val toReduceWidgets = ArrayList(Helper.getVisibleWidgets(guiState))
            if (toReduceWidgets.isEmpty()) {
                toReduceWidgets.addAll(guiState.widgets.filter { !it.isKeyboard })
            }
            //val toReduceWidgets = Helper.getVisibleWidgetsForAbstraction(guiState)
            toReduceWidgets .forEach {
                val widgetAttributePath = if (tempFullAttrPaths.containsKey(it))
                {
                    tempFullAttrPaths[it]!!
                }
                else
                {
                    WidgetReducer.reduce(it,guiState,activity,rotation,autAutMF, tempFullAttrPaths,tempRelativeAttrPaths)
                }
                if (capturedWidgets.contains(it)) {
                    widgetReduceMap.put(it, widgetAttributePath)
                    if (!capturedAttributePaths.contains(widgetAttributePath)) {
                        capturedAttributePaths.add(widgetAttributePath)
                    }
                }
                if (attributePath_Cardinalitys.contains(widgetAttributePath.attributePathId)){
                    attributePath_Cardinalitys[widgetAttributePath.attributePathId] = Cardinality.MANY
                }
                else
                {
                    attributePath_Cardinalitys[widgetAttributePath.attributePathId] = Cardinality.ONE
                }
            }
            val widgetList = HashMap<Widget, AttributeValuationSet>()
            if (!AttributeValuationSet.allAttributeValuationSet.containsKey(activity)) {
                AttributeValuationSet.allAttributeValuationSet.put(activity,HashMap())
            }
            capturedAttributePaths.forEach { a ->
                var attributeValuationSet =  AttributeValuationSet.allAttributeValuationSet[activity]!!.map { it.value }. find { it.haveTheSameAttributePath(a) }
                if (attributeValuationSet == null) {
                    attributeValuationSet =  AttributeValuationSet(a,attributePath_Cardinalitys[a.attributePathId]!!,activity,attributePath_Cardinalitys)
                }
                widgetReduceMap.filter { it.value.equals(a)}.forEach { w,_ ->
                    widgetList.put(w,attributeValuationSet)
                }

            }
            return widgetList
        }
    }

}