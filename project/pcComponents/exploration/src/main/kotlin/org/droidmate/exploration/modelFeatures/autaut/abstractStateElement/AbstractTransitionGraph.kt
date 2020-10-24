package org.droidmate.exploration.modelFeatures.autaut.abstractStateElement

import org.droidmate.exploration.modelFeatures.graph.*
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AbstractTransitionGraph(private val graph: IGraph<AbstractState, AbstractInteraction> =
                              Graph(AbstractStateManager.instance.appResetState,
                                      stateComparison = { a, b -> a == b },
                                      labelComparison = { a, b ->
                                        a==b
                                      })): IGraph<AbstractState, AbstractInteraction> by graph{
    // val edgeConditions: HashMap<Edge<*,*>,HashMap<WidgetGroup,String>> = HashMap()
    val edgeConditions: HashMap<Edge<*,*>,ArrayList<HashMap<Widget,String>>> = HashMap()
    val edgeProved: HashMap<Edge<*,*>, Int> = HashMap()
    val statementCoverageInfo: HashMap<Edge<*,*>,ArrayList<String>> = HashMap()
    val methodCoverageInfo: HashMap<Edge<*,*>,ArrayList<String>> = HashMap()

    fun containsCondition(edge: Edge<*,*>, condition: HashMap<Widget,String>): Boolean {
        if (edgeConditions.get(edge)!!.contains(condition)) {
            return true
        }
        return false
    }

    fun addNewCondition(edge: Edge<*,*>, condition: HashMap<Widget,String>) {
        edgeConditions.get(edge)!!.add(condition)
    }

    fun getWindowBackward(abstractState: AbstractState): List<Edge<*, *>>{
        val pressBackEvents = arrayListOf<Edge<*, *>>()
        this.edges(abstractState).filter { it.label.abstractAction.actionType == AbstractActionType.PRESS_BACK }.forEach {
            pressBackEvents.add(it)
        }
        return pressBackEvents
    }

    override fun add(source: AbstractState, destination: AbstractState?, label: AbstractInteraction, updateIfExists: Boolean, weight: Double): Edge<AbstractState, AbstractInteraction> {
        val edge = graph.add(source, destination, label, updateIfExists, weight)
        edgeProved.put(edge,0)
        edgeConditions.put(edge, ArrayList())
        methodCoverageInfo.put(edge, ArrayList())
        statementCoverageInfo.put(edge,ArrayList())
        return edge
    }

    override fun update(source: AbstractState, prevDestination: AbstractState?, newDestination: AbstractState, prevLabel: AbstractInteraction, newLabel: AbstractInteraction): Edge<AbstractState, AbstractInteraction>? {
        val edge = graph.update(source, prevDestination,newDestination, prevLabel, newLabel)
        if (edge!=null) {
            edgeProved.put(edge, 0)
            edgeConditions.put(edge, ArrayList())
            methodCoverageInfo.put(edge, ArrayList())
            statementCoverageInfo.put(edge, ArrayList())
        }
        return edge
    }

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(AbstractTransitionGraph::class.java) }


    }
}