package org.droidmate.exploration.modelFeatures.atua.DSTG

import org.droidmate.exploration.modelFeatures.graph.*
import org.droidmate.exploration.modelFeatures.reporter.StatementCoverageMF
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import kotlin.collections.ArrayList

class AbstractTransitionGraph(private val graph: IGraph<AbstractState, AbstractTransition> =
                              Graph(AbstractStateManager.instance.appResetState,
                                      stateComparison = { a, b -> a == b },
                                      labelComparison = { a, b ->
                                        a==b
                                      })): IGraph<AbstractState, AbstractTransition> by graph {


    override fun add(source: AbstractState, destination: AbstractState?, label: AbstractTransition, updateIfExists: Boolean, weight: Double): Edge<AbstractState, AbstractTransition> {
        val edge = graph.add(source, destination, label, updateIfExists, weight)
        return edge
    }

    override fun update(source: AbstractState, prevDestination: AbstractState?, newDestination: AbstractState, prevLabel: AbstractTransition, newLabel: AbstractTransition): Edge<AbstractState, AbstractTransition>? {
        val edge = graph.update(source, prevDestination,newDestination, prevLabel, newLabel)
        return edge
    }

    override fun remove(edge: Edge<AbstractState, AbstractTransition>): Boolean {
        edge.source.data.abstractTransitions.remove(edge.label)
        return graph.remove(edge)
    }
    fun dump(statementCoverageMF: StatementCoverageMF, bufferedWriter: BufferedWriter) {
        bufferedWriter.write(header())
        //val fromResetState = AbstractStateManager.instance.launchAbstractStates.get(AbstractStateManager.LAUNCH_STATE.RESET_LAUNCH)!!
        //val fromResetAbstractState = AbstractStateManager.instance.getAbstractState(fromResetState)!!
        val dumpedSourceStates = ArrayList<AbstractState>()
        this.getVertices().forEach {
            recursiveDump(it.data,statementCoverageMF,dumpedSourceStates, bufferedWriter)
        }


                
    }

    private fun header(): String {
        return "SourceState;ResultingState;ActionType;InteractedAVS;Data;PrevWindow;PrevAbstractStateId;PrevHandlers;CoveredUpdatedMethods;CoveredUpdatedStatements;GUITransitionID"
    }

    fun recursiveDump(sourceAbstractState: AbstractState, statementCoverageMF: StatementCoverageMF, dumpedSourceStates: ArrayList<AbstractState> , bufferedWriter: BufferedWriter) {
        dumpedSourceStates.add(sourceAbstractState)
        val explicitEdges = this.edges(sourceAbstractState).filter { it.label.isExplicit() && it.destination!=null }
        val nextSources = ArrayList<AbstractState>()
        explicitEdges.forEach { edge ->
            if (!nextSources.contains(edge.destination!!.data) && !dumpedSourceStates.contains(edge.destination!!.data)) {

                nextSources.add(edge.destination!!.data)
            }
            bufferedWriter.newLine()
            val abstractTransitionInfo = "${sourceAbstractState.abstractStateId};${edge.destination!!.data.abstractStateId};" +
                    "${edge.label.abstractAction.actionType};${edge.label.abstractAction.attributeValuationMap?.avmId};${edge.label.data};" +
                    "${edge.label.prevWindow?.windowId};${edge.label.preWindowAbstractState?.abstractStateId};\"${getInteractionHandlers(edge,statementCoverageMF)}\";\"${getCoveredModifiedMethods(edge,statementCoverageMF)}\";\"${getCoveredUpdatedStatements(edge,statementCoverageMF)}\";" +
                    "\"${edge.label.interactions.map { it.actionId }.joinToString(separator = ";")}\""
            bufferedWriter.write(abstractTransitionInfo)

        }
        nextSources.forEach {
            recursiveDump(it,statementCoverageMF,dumpedSourceStates, bufferedWriter)
        }
    }

    private fun getCoveredModifiedMethods(edge: Edge<AbstractState, AbstractTransition>, statementCoverageMF: StatementCoverageMF): String {
        return edge.label.modifiedMethods.filterValues { it == true }.map { statementCoverageMF.getMethodName(it.key) }.joinToString(separator = ";")
    }

    private fun getCoveredUpdatedStatements(edge: Edge<AbstractState, AbstractTransition>, statementCoverageMF: StatementCoverageMF): String {
        return edge.label.modifiedMethodStatement.filterValues { it == true }.map { it.key }.joinToString(separator = ";")
    }

    private fun getInteractionHandlers(edge: Edge<AbstractState, AbstractTransition>, statementCoverageMF: StatementCoverageMF) =
            edge.label.handlers.filter { it.value == true }.map { it.key }.map { statementCoverageMF.getMethodName(it) }.joinToString(separator = ";")

    companion object {

        @JvmStatic
        private val log: Logger by lazy { LoggerFactory.getLogger(AbstractTransitionGraph::class.java) }


    }
}