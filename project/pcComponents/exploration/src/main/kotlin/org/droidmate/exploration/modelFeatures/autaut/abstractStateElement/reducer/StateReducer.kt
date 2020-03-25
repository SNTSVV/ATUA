package org.droidmate.exploration.modelFeatures.regression.abstractStateElement.reducer

import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.AttributePath
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.Cardinality
import org.droidmate.exploration.modelFeatures.regression.abstractStateElement.WidgetGroup
import org.droidmate.exploration.modelFeatures.regression.staticModel.Helper
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget

class StateReducer
{
    companion object{
        fun reduce(guiState: State<*>, activity: String): HashMap<Widget, WidgetGroup>{
            val widgetReduceMap = HashMap<Widget,AttributePath>()
            val attributePaths = HashMap<AttributePath,Int>()
            val tempFullAttrPaths = HashMap<Widget,AttributePath>()
            val tempRelativeAttrPaths = HashMap<Widget,AttributePath>()
            //TODO: Save all computed attributePath to prevent from recomputing
            Helper.getVisibleWidgets(guiState).forEach {

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
            val widgetList = HashMap<Widget, WidgetGroup>()
            attributePaths.forEach { a, c ->
                val cardinality: Cardinality = when (c)
                {
                    0 -> Cardinality.ZERO
                    1 -> Cardinality.ONE
                    else -> Cardinality.MANY
                }
                val widgetGroup =  WidgetGroup(a,cardinality)
                widgetReduceMap.filter { it.value == a }.forEach { w,_ ->
                    widgetList.put(w,widgetGroup)
                }
            }
            return widgetList
        }
    }

}