package org.droidmate.exploration.modelFeatures.regression.staticModel

import org.droidmate.explorationModel.interaction.State

open class WTGNode(val classType: String,
                   val nodeId: String)
{
    val widgets = arrayListOf<StaticWidget>()
    val widgetState = HashMap<StaticWidget, Boolean>()
    val mappedStates = arrayListOf<State<*>>()
    var rotation:Int = 0
    val unexercisedWidgetCount: Int
    get() {return widgets.filter { !it.exercised && widgetState[it]?:true && it.interactive && it.mappedRuntimeWidgets.isNotEmpty()}.size}
    var hasOptionsMenu = true
    open fun isStatic() = false
    override fun toString(): String {
        return "$classType-$nodeId"
    }
    companion object{
        val allNodes = arrayListOf<WTGNode>()
        val allMeaningNodes
            get()= allNodes.filter { it !is WTGFakeNode && it !is WTGLauncherNode && it !is WTGOutScopeNode }
        fun getWTGNodeByState(state: State<*>): WTGNode?{
            return allNodes.find { it.mappedStates.find { it.equals(state)}!=null }
        }
    }
}