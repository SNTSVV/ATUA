package org.droidmate.exploration.modelFeatures.autaut.WTG

import org.droidmate.exploration.modelFeatures.graph.Edge
import org.droidmate.exploration.modelFeatures.graph.Graph
import org.droidmate.exploration.modelFeatures.graph.IGraph
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractTransition
import org.droidmate.exploration.modelFeatures.autaut.DSTG.AbstractState
import org.droidmate.exploration.modelFeatures.autaut.helper.PathFindingHelper
import org.droidmate.explorationModel.interaction.Widget

class TransitionPath(root: AbstractState, val pathType: PathFindingHelper.PathType, val destination: AbstractState, private val graph: IGraph<AbstractState, AbstractTransition> =
                              Graph(root,
                                      stateComparison = { a, b -> a == b },
                                      labelComparison = { a, b ->
                                          a==b
                                      })): IGraph<AbstractState, AbstractTransition> by graph {

    // val edgeConditions: HashMap<Edge<*,*>,HashMap<WidgetGroup,String>> = HashMap()
    val edgeConditions: HashMap<Edge<*,*>,ArrayList<HashMap<Widget,String>>> = HashMap()
    val transitionIdMap: HashMap<Edge<*,*>,Int> = HashMap()
    var lastId = 0
    override fun toString(): String {
        return graph.toString()
    }
    fun getFinalDestination(): AbstractState{
        return destination
    }

    //ensure there's only one transition from a node to an another one
    override fun add(source: AbstractState, destination: AbstractState?, label: AbstractTransition, updateIfExists: Boolean, weight: Double): Edge<AbstractState, AbstractTransition> {
        if (edges(source, destination).isNotEmpty())
            return edges(source, destination).first()
        val edge = graph.add(source, destination, label)
        val newId = lastId+1
        lastId = newId
        transitionIdMap.put(edge,newId)
        return edge
    }

    fun clone(): TransitionPath{
        val newGraph = TransitionPath(
                root = this.root.data,
                pathType = this.pathType,
                destination = this.destination
        )
        this.getVertices().forEach { source ->
            this.edges(source).forEach { edge ->
                newGraph.add(source.data,edge.destination?.data, edge.label)
            }
        }
        newGraph.transitionIdMap.putAll(this.transitionIdMap)
        return newGraph
    }

}

class PathTraverser (val path: TransitionPath) {
    var latestEdge: Edge<AbstractState,AbstractTransition>? = null
    var destinationReached = false
    fun reset() {
        latestEdge = null
        destinationReached = false
    }
    fun getNextEdge(): Edge<AbstractState,AbstractTransition>? {
        if(destinationReached)
            return null
        val edges = if (latestEdge == null)
                path.edges(path.root.data)
        else
            path.edges(latestEdge!!.destination!!.data)
        if (edges.isEmpty()) {
            if (latestEdge!=null && path.transitionIdMap[latestEdge!!]!! == path.lastId) {
                destinationReached = true
            }
            latestEdge = null
            return null
        }
        val latestEdgeId = if (latestEdge == null)
            0
        else
            path.transitionIdMap[latestEdge!!]!!
        val nextEdge = edges.find { path.transitionIdMap[it]!! == latestEdgeId + 1 }
        if (latestEdge!=null && path.transitionIdMap[latestEdge!!]!! == path.lastId) {
            destinationReached = true
        }
        latestEdge = nextEdge
        return nextEdge
    }

    fun finalStateAchieved(): Boolean {
        return destinationReached
    }
}