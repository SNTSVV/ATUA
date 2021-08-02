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

import org.droidmate.explorationModel.*
import org.droidmate.explorationModel.create
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.HashSet

interface StateFactory<S, W>: Factory<S, Pair<Collection<W>, Boolean>> where S: State<W>, W: Widget{

	var numStates: Int
	fun create(widgets: Collection<W>,isHomeScreen:Boolean = false) = create(Pair(widgets,isHomeScreen))
	/** @return a view to the data (suspending function) */
	suspend fun getStates(): Collection<S>
	suspend fun getState(id: ConcreteId): S?{
		val states = getStates()
		return states.find { it.stateId == id }
	}
	suspend fun addState(e: S)
}

abstract class StateProvider<T,W> : StateFactory<T,W> where T: State<W>, W: Widget {
	override var mocked: T? = null
	protected abstract fun init(widgets:Collection<W>, isHomeScreen: Boolean): T
	private fun init(args:Pair<Collection<W>,Boolean>): T = init(args.first,args.second)
	override fun create(arg: Pair<Collection<W>,Boolean>): T = mocked ?: init(arg)

	override var numStates: Int = 0
	private val states = CollectionActor(HashSet<T>(), "StateActor").create()

	private var emptyState: T? = null
	override fun empty(): T = emptyState ?: create(emptyList())

	/** should be used only by model loader/parser */
	override suspend fun addState(e: T){
		numStates +=1
		states.send(Add(e))
	}
	override suspend fun getStates(): Collection<T> = states.getAll()
}

open class DefaultStateProvider: StateProvider<State<Widget>,Widget>() {
	override fun init(widgets: Collection<Widget>, isHomeScreen: Boolean): State<Widget> = State(widgets,isHomeScreen)
}