package org.droidmate.exploration.modelFeatures.autaut.DSTG.reducer

import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributePath
import org.droidmate.exploration.modelFeatures.autaut.DSTG.Cardinality
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AttributeValuationSet
import org.droidmate.exploration.modelFeatures.autaut.WTG.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, activity: String, packageName: String): HashMap<Widget, AttributeValuationSet>{
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val attributePaths = HashMap<AttributePath,Int>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            val toReduceWidgets = if (activity.startsWith("com.oath.mobile.platform.phoenix.core.")) {
                Helper.getVisibleWidgets(guiState)
            } else {
                Helper.getVisibleWidgetsForAbstraction(guiState,packageName = packageName)
            }
            //val toReduceWidgets = Helper.getVisibleWidgetsForAbstraction(guiState)
            toReduceWidgets.forEach {
                val widgetAttributePath = if (tempFullAttrPaths.containsKey(it))
                {
                    tempFullAttrPaths[it]!!
                }
                else
                {
                    WidgetReducer.reduce(it,guiState,activity,tempFullAttrPaths,tempRelativeAttrPaths)
                }
                widgetReduceMap.put(it,widgetAttributePath)
                if (attributePaths.containsKey(widgetAttributePath)){
                    val count = attributePaths[widgetAttributePath]!! + 1
                    attributePaths.put(widgetAttributePath,count)
                }
                else
                {
                    attributePaths.put(widgetAttributePath,1)
                }

            }
            val widgetList = HashMap<Widget, AttributeValuationSet>()
            val attributeValuationSets = ArrayList<AttributeValuationSet>()
            attributePaths.forEach { a, c ->
                val cardinality: Cardinality = when (c)
                {
                    0 -> Cardinality.ZERO
                    1 -> Cardinality.ONE
                    else -> Cardinality.MANY
                }
                var attributeValuationSet = attributeValuationSets.find { it.haveTheSameAttributePath(a) }
                if (attributeValuationSet == null) {
                    attributeValuationSet =  AttributeValuationSet(a,cardinality,activity,attributeValuationSets)

                } else {
                    attributeValuationSet.cardinality = cardinality
                }
                widgetReduceMap.filter { it.value == a }.forEach { w,_ ->
                    widgetList.put(w,attributeValuationSet)
                }

            }
            return widgetList
        }
    }

}