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

import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.lang.RuntimeException

abstract class ModelProvider<out T: AbstractModel<*,*>> {
	private var model: T? = null
	lateinit var config: ModelConfig

	/** the config has to be initialized before the [get] method can be called successfully, calling this method will also reset the local 'model' variable */
	fun init(cfg: ModelConfig){
		config = cfg
		model = null
	}
	protected abstract fun create(config: ModelConfig): T

	fun get() = model ?:
	if(!::config.isInitialized)
		throw RuntimeException("the ModelConfig has to be initialized by calling 'init' before the model can be initialized in get()")
	else create(config).also { model = it }

}

typealias Model = AbstractModel<State<Widget>,Widget>

class DefaultModel<S,W>(override val config: ModelConfig,
                             override val stateProvider: StateFactory<S, W>,
                             override val widgetProvider: WidgetFactory<W> )
	: AbstractModel<S,W>()
		where S: State<W>, W: Widget

class DefaultModelProvider: ModelProvider<DefaultModel<State<Widget>,Widget>>(){
	override fun create(config: ModelConfig): DefaultModel<State<Widget>, Widget>
			= DefaultModel(config,DefaultStateProvider(),DefaultWidgetProvider())
}