/*
 * Copyright (c) 2019.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.explorationModel.factory

import kotlinx.coroutines.*
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.config.ConfigProperties
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.ActionResult
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractModel<S,W>: CoroutineScope where S: State<W>, W: Widget {
	abstract val config: ModelConfig

	protected abstract val stateProvider: StateFactory<S,W>
	protected abstract val widgetProvider: WidgetFactory<W>
	protected val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }
	protected val paths = ArrayList<ExplorationTrace<S,W>>()

	/** dummy element if a state has to be given but no widget data is available */
	val emptyState get() = stateProvider.empty()
	val emptyWidget get() = widgetProvider.empty()

	/**---------------------------------- public interface --------------------------------------------------------------**/
	/** non-mutable view of all traces contained within this model */
	fun getPaths(): List<ExplorationTrace<S,W>> = paths

	/** @return a view to the data (suspending function) */
	suspend fun getStates() = stateProvider.getStates()

	/** adding a value to the actor is non blocking and should not take much time */
	/** should be used only by model loader/parser */
	internal suspend fun addState(s: S) = stateProvider.addState(s)

	suspend fun getState(id: ConcreteId) = stateProvider.getState(id)

	suspend fun getWidgets(): Collection<W> = getStates().flatMap { it.widgets }

	/** create a new empty trace with given [id] (or random id if none is provided), and add it to this model */
	open fun initNewTrace(watcher: MutableList<ModelFeatureI> = mutableListOf(), id: UUID = UUID.randomUUID()): ExplorationTrace<S,W> {
		return ExplorationTrace(watcher, config, id, stateProvider.empty()).also { actionTrace ->
			paths.add(actionTrace)
		}
	}

	// we use supervisorScope for the dumping, such that cancellation and exceptions are only propagated downwards
	// meaning if a dump process fails the overall model process is not affected
	open fun dumpModel(config: ModelConfig): Job = this.launch(CoroutineName("Model-dump") +backgroundJob){
		getStates().let { states ->
			logger.trace("dump Model with ${states.size}")
			states.forEach { s: State<*> -> launch(CoroutineName("state-dump ${s.uid}")) { s.dump(config) } }
		}
		paths.forEach { t -> launch(CoroutineName("trace-dump")) { t.dump(config) } }
	}

	private var uTime: Long = 0
	/** update the model with any [action] executed as part of an execution [trace] **/
	fun updateModel(action: ActionResult, trace: ExplorationTrace<S,W>) {
		measureTimeMillis {
			storeScreenShot(action)
			val widgets = generateWidgets(action, trace).also{ incWidgetCounter(it.size) }
			val newState = createState(widgets, action.guiSnapshot.isHomeScreen).also{ launch{ addState(it) } }
			trace.update(action, newState)

			if (config[ConfigProperties.ModelProperties.dump.onEachAction]) {
				this.launch(CoroutineName("state-dump")) { newState.dump(config) }
				this.launch(CoroutineName("trace-dump")) { trace.dump(config) }
			}
		}.let {
			debugOut("model update took $it millis")
			uTime += it
			debugOut("---------- average model update time ${uTime / trace.size} ms overall ${uTime / 1000.0} seconds --------------")
		}
	}

	/**--------------------------------- concurrency utils ------------------------------------------------------------**/
	//we need + Job()  to be able to CancelAndJoin this context, otherwise we can ONLY cancel this scope or its children
	override val coroutineContext: CoroutineContext = CoroutineName("ModelScope") + Job() //we do not define a dispatcher, this means Dispatchers.Default is automatically used (a pool of worker threads)

	/** this job can be used for any coroutine context which is not essential for the main model process.
	 * In particular we use it to invoke background processes for model or img dump
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	protected val backgroundJob = SupervisorJob()
	/** This will notify all children that this scope is to be canceled (which is an cooperative mechanism, mechanism all non-terminating spawned children have to check this flag).
	 * Moreover, this will propagate the cancellation to our model-actors and join all structural child coroutines of this scope.
	 */
	suspend fun cancelAndJoin() = coroutineContext[Job]!!.cancelAndJoin()

	/** -------------------------------------- protected generator methods --------------------------------------------**/

	/** used on model update to instantiate a new state for the current UI screen */
	@Deprecated("This function no longer needs to be overwritten and will be removed. " +
			"Provide a custom StateProvider instead", ReplaceWith("createState(widgets, isHomeScreen)"))
	protected open fun generateState(action: ActionResult, widgets: Collection<W>): S =
		with(action.guiSnapshot) { stateProvider.create(widgets, isHomeScreen) }

	@Deprecated("This function no longer needs to be overwritten and will be removed. " +
			"Provide a custom StateProvider instead", ReplaceWith("createState(widgets, isHomeScreen)"))
	open fun parseState(widgets: Collection<W>, isHomeScreen: Boolean): S =
		createState(widgets, isHomeScreen)

	/**
	 * used by ModelParser (loading model from persisted data) and in [updateModel] to create [State] object.
	 * However, the resulting state is **not added** to this model.
	 */
	fun createState(widgets: Collection<W>, isHomeScreen: Boolean): S =
		stateProvider.create(widgets, isHomeScreen)

	/** override this function if the Widget class was extended to create the custom object here */
	protected fun createWidget(properties: UiElementPropertiesI, parent: ConcreteId?): W =
		widgetProvider.create(properties, parent)

	private fun generateWidgets(action: ActionResult, @Suppress("UNUSED_PARAMETER") trace: ExplorationTrace<S,W>): Collection<W>{
		val elements: Map<Int, UiElementPropertiesI> = action.guiSnapshot.widgets.associateBy { it.idHash }
		return generateWidgets(elements)
	}

	/** used on model update to compute the list of UI elements contained in the current UI screen ([State]).
	 *  used by ModelParser to create [Widget] object from persisted data
	 */
	open fun generateWidgets(elements: Map<Int, UiElementPropertiesI>): Collection<W>{
		val widgets = HashMap<Int,W>()
		val workQueue = LinkedList<UiElementPropertiesI>().apply {
			addAll(elements.values.filter { it.parentHash == 0 })  // add all roots to the work queue
		}
		check(elements.isEmpty() || workQueue.isNotEmpty()){"ERROR we don't have any roots something went wrong on UiExtraction"}
		while (workQueue.isNotEmpty()){
			with(workQueue.pollFirst()){
				val parent = if(parentHash != 0) widgets[parentHash]!!.id else null
				widgets[idHash] = createWidget(this, parent)
				childHashes.forEach {
					//					check(elements[it]!=null){"ERROR no element with hashId $it in working queue"}
					if(elements[it] == null)
						logger.warn("could not find child with id $it of widget $this ")
					else workQueue.add(elements[it]
						?: error("Missing Widget with idHash '$it'")) } //FIXME if null we can try to find element.parentId = this.idHash !IN workQueue as repair function, but why does it happen at all
			}
		}
		check(widgets.size==elements.size){"ERROR not all UiElements were generated correctly in the model ${elements.filter { !widgets.containsKey(it.key) }.values}"}
		assert(elements.all { e -> widgets.values.any { it.idHash == e.value.idHash } }){ "ERROR not all UiElements were generated correctly in the model ${elements.filter { !widgets.containsKey(it.key) }}" }
		return widgets.values
	}

	/**---------------------------------------- private methods -------------------------------------------------------**/
	private fun storeScreenShot(action: ActionResult) = this.launch(CoroutineName("screenShot-dump") +backgroundJob){
		if(action.screenshot.isNotEmpty())
			withContext(Dispatchers.IO){ Files.write(config.imgDst.resolve("${action.action.id}.jpg"), action.screenshot
				, StandardOpenOption.CREATE, StandardOpenOption.WRITE) }
	}

	/*********** debugging parameters *********************************/
	/** debugging counter do not use it in productive code, instead access the respective element set */
	private var nWidgets = 0
	internal fun incWidgetCounter(num: Int) {
		nWidgets += num
	}

	/** debugging counter do not use it in productive code, instead access the respective element set */
	private var nStates get() =  stateProvider.numStates
	set(value){
		stateProvider.numStates = value
	}

	/**
	 * this only shows how often the addState or addWidget function was called, but if identical id's were added multiple
	 * times the real set will contain less elements then these counter indicate
	 */
	override fun toString(): String {
		return "Model[#addState=$nStates, #addWidget=$nWidgets, paths=${paths.size}]"
	}

}