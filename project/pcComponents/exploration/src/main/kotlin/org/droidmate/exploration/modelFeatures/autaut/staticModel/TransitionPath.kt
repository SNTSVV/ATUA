package org.droidmate.exploration.modelFeatures.autaut.staticModel

import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.graph.Graph
import org.droidmate.exploration.modelFeatures.graph.IGraph
import org.droidmate.exploration.modelFeatures.graph.Vertex
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractInteraction
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.abstractStateElement.WidgetGroup
import org.droidmate.explorationModel.interaction.Widget

class TransitionPath(root: AbstractState, private val graph: IGraph<AbstractState, AbstractInteraction> =
                              Graph(root,
                                      stateComparison = { a, b -> a == b },
                                      labelComparison = { a, b ->
                                          a==b
                                      })): IGraph<AbstractState, AbstractInteraction> by graph {

    // val edgeConditions: HashMap<Edge<*,*>,HashMap<WidgetGroup,String>> = HashMap()
    val edgeConditions: HashMap<Edge<*,*>,HashMap<Widget,String>> = HashMap()
    override fun toString(): String {
        return graph.toString()
    }
    fun getFinalDestination(): AbstractState{
        var traverseNode:Vertex<AbstractState>? = root
//        var prevNode: Vertex<WTGNode> = root
        while (traverseNode!=null)
        {
            val edge = edges(traverseNode).firstOrNull()
            if (edge == null)
                break
            else
                traverseNode = edge.destination
        }
        if (traverseNode!=null)
            return traverseNode.data
        return root.data
    }

    //ensure there's only one transition from a node to an another one
    override fun add(source: AbstractState, destination: AbstractState?, label: AbstractInteraction, updateIfExists: Boolean, weight: Double): Edge<AbstractState, AbstractInteraction> {
        if (edges(source, destination).isNotEmpty())
            return edges(source, destination).first()
        return graph.add(source, destination, label)
    }

    fun clone(): TransitionPath{
        val newGraph = TransitionPath(
                root = this.root.data
        )
        this.getVertices().forEach { source ->
            this.edges(source).forEach { edge ->
                newGraph.add(source.data,edge.destination?.data, edge.label)
            }
        }
        return newGraph
    }

}

