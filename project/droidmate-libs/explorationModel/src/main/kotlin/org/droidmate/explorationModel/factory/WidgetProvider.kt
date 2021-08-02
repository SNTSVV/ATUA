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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.droidmate.explorationModel.factory

import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.ConcreteId
import org.droidmate.explorationModel.DummyProperties
import org.droidmate.explorationModel.interaction.Widget

interface WidgetFactory<T>: Factory<T,Pair<UiElementPropertiesI, ConcreteId?>>{
	fun create(properties: UiElementPropertiesI, parentId: ConcreteId?) = create(Pair(properties,parentId))
}

abstract class WidgetProvider<T: Widget>: WidgetFactory<T> {
	override var mocked: T? = null
	protected var emptyWidget: T? = null

	protected abstract fun init(properties: UiElementPropertiesI,parentId: ConcreteId?):T
	protected fun init(args: Pair<UiElementPropertiesI,ConcreteId?>) = init(args.first,args.second)

	// if memory becomes an issue we could use a 'common' set of widgets based on ConcreteId and take the element out of this set if it already exists and only call create otherwise
	override fun create(arg: Pair<UiElementPropertiesI,ConcreteId?>): T = mocked ?: init(arg)
	override fun empty(): T = emptyWidget ?: init(DummyProperties,null).also { emptyWidget = it }
}

open class DefaultWidgetProvider: WidgetProvider<Widget>(){
	override fun init(properties: UiElementPropertiesI, parentId: ConcreteId?): Widget = Widget(properties,parentId)
}