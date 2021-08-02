package org.droidmate.explorationModel.retention.loading

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.debugOut
import org.droidmate.explorationModel.factory.AbstractModel
import org.droidmate.explorationModel.factory.ModelProvider
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.droidmate.explorationModel.retention.loading.WidgetParserI.Companion.computeWidgetIndices
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.coroutines.CoroutineContext

internal abstract class StateParserI<T,DeferredWidget, M: AbstractModel<S,W>, S: State<W>, W: Widget>: ParserI<T, S,M> {
	var headerRenaming: Map<String,String> = emptyMap()
	abstract val widgetParser: WidgetParserI<DeferredWidget,M>
	abstract val reader: ContentReader

  override val logger: Logger = LoggerFactory.getLogger(javaClass)

	/** temporary map of all processed widgets for state parsing */
	abstract val queue: MutableMap<ConcreteId, T>
	/**
	 * when compatibility mode is enabled this list will contain the mapping oldId->newlyComputedId
	 * to transform the model to the current (newer) id computation.
	 * This mapping is supposed to be used to adapt the action targets in the trace parser (Interaction entries)
	 */
	internal val idMapping: ConcurrentHashMap<ConcreteId, ConcreteId> = ConcurrentHashMap()
//	override fun logcat(msg: String) {	}

	/** parse the state either asynchronous (Deferred) or sequential (blocking) */
	@Suppress("FunctionName")
	abstract fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): T

	internal val parseIfAbsent: (CoroutineContext) -> (ConcreteId)->T =	{ context ->{ id ->
		log("parse absent state $id")
		P_S_process(id,context)
	}}

	protected suspend fun computeState(stateId: ConcreteId): S {
		log("\ncompute state $stateId")
		val(contentPath,isHomeScreen) = reader.getStateFile(stateId)
		if(!widgetParser.indicesComputed.get()) {
			widgetParser.setCustomWidgetIndices( computeWidgetIndices(reader.getHeader(contentPath), headerRenaming) )
			widgetParser.indicesComputed.set(true)
		}
		val uiProperties = reader.processLines(path = contentPath, lineProcessor = widgetParser.processor)
				.map { (id,e) -> id to widgetParser.getElem(id to e)	}
		uiProperties.groupBy { it.second.idHash }.forEach {
			if(it.value.size>1){
				//FIXME that may happen for old models and will destroy the parent/child mapping, so for 'old' models we would have to parse the parentId instead
				logger.error("ambiguous idHash elements found, this will result in model inconsistencies (${it.value})")
			}
		}
		debugOut("${uiProperties.map { it.first.toString()+": HashId = ${it.second.idHash}" }}",false)
		val widgets = model.generateWidgets(uiProperties.associate { (_,e) ->  e.idHash to e })
		if(enableChecks) widgets.forEach { w ->
			uiProperties.find { (_,properties) -> properties.idHash == w.idHash }!!.let{ (id,_) ->
				verify("ERROR on widget parsing inconsistent ID created ${w.id} instead of $id",{id == w.id}) {
					val curVal = widgetParser.idMapping[id]
					if(curVal != null && curVal != w.id) logger.error("Widget-id collision for source id $id")
					widgetParser.idMapping[id] = w.id
				}
			}
		}
		model.incWidgetCounter(widgets.size)

		return if (widgets.isNotEmpty()) {
			model.createState(widgets, isHomeScreen).also { newState:S->

				verify("ERROR different set of widgets used for UID computation used", {
					stateId == newState.stateId
				}) {
					idMapping[stateId] = newState.stateId
				}
				model.addState(newState)
			}
		} else model.emptyState.also { model.addState(it) } // the model should contain the empty state if it was in the trace, to prevent any strange behavior when trying to lookup this state later
	}

	fun fixedStateId(idString: String) = ConcreteId.fromString(idString).let{	idMapping[it] ?: it }

}

internal class StateParserS<M: AbstractModel<S,W>, S: State<W>, W: Widget>(override val widgetParser: WidgetParserS<M>,
                                                                override val reader: ContentReader,
                                                                override val modelProvider: ModelProvider<M>,
                                                                override val compatibilityMode: Boolean,
                                                                override val enableChecks: Boolean) : StateParserI<S, UiElementPropertiesI, M,S,W>() {
	override val queue: MutableMap<ConcreteId, S> = HashMap()

	override fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): S = runBlocking { computeState(id) }

	override suspend fun getElem(e: S): S = e
}

internal class StateParserP<M: AbstractModel<S,W>, S: State<W>, W: Widget>(override val widgetParser: WidgetParserP<M>,
                                                    override val reader: ContentReader,
                                                    override val modelProvider: ModelProvider<M>,
                                                    override val compatibilityMode: Boolean,
                                                    override val enableChecks: Boolean)
	: StateParserI<Deferred<S>, Deferred<UiElementPropertiesI>, M,S,W>() {
	override val queue: MutableMap<ConcreteId, Deferred<S>> = ConcurrentHashMap()

	@Suppress("DeferredIsResult")
	override fun P_S_process(id: ConcreteId, coroutineContext: CoroutineContext): Deferred<S> =	CoroutineScope(coroutineContext+Job()).async(CoroutineName("parseWidget $id")){
		log("parallel compute state $id")
		computeState(id)
	}

	override suspend fun getElem(e: Deferred<S>): S =
			e.await()
}