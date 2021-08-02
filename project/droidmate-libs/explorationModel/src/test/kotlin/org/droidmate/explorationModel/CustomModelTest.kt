package org.droidmate.explorationModel

import kotlinx.coroutines.runBlocking
import org.droidmate.deviceInterface.exploration.UiElementPropertiesI
import org.droidmate.explorationModel.config.ModelConfig
import org.droidmate.explorationModel.factory.*
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CustomModelTest: TestI, TestModel by DefaultTestModel() {
	@Test
	fun genericTypeTest() = runBlocking{
		val mp = CustomModelProvider(config)
		val model = mp.get()
		val se: CustomState = model.emptyState
		model.addState(se)
		model.addState(se)
		val we: CustomWidget = model.emptyWidget
		val state by mp.stateProvider.lazyCreate(Pair(listOf(we),false))
		model.addState(state)
		val states: Collection<CustomState> = model.getStates()
		assert(states.size==2) {"Adding twice the same state and one different failed"}  // since states is the set the double add should still result only in one entry
		println("states = \n" +
				"\t${states.joinToString(separator = ",\n\t")}; \nmodel = $model")
	}
}

class CustomWidget(properties: UiElementPropertiesI,
                   parentId: ConcreteId?): Widget(properties,parentId){
	override fun toString(): String = super.toString().replace("Widget","CustomWidget")
}
class CustomState(_widgets: Collection<CustomWidget>, isHomeScreen: Boolean = false): State<CustomWidget>(_widgets,isHomeScreen){
	override fun toString(): String = super.toString().replace("State","CustomState")
}

class CustomModel(
	override val config: ModelConfig,
	override val stateProvider: StateFactory<CustomState, CustomWidget>,
	override val widgetProvider: WidgetFactory<CustomWidget>
) : AbstractModel<CustomState,CustomWidget>(){
	override fun toString(): String = super.toString().replace("Model","CustomModel")
}

class CustomModelProvider(config: ModelConfig): ModelProvider<CustomModel>() {
	init {
		super.init(config)
	}

	val stateProvider = object : StateProvider<CustomState,CustomWidget>(){
		override fun init(widgets: Collection<CustomWidget>, isHomeScreen: Boolean): CustomState = CustomState(widgets, isHomeScreen)
	}
	@Suppress("MemberVisibilityCanBePrivate")
	val widgetProvider = object : WidgetProvider<CustomWidget>(){
		override fun init(properties: UiElementPropertiesI, parentId: ConcreteId?): CustomWidget = CustomWidget(properties,parentId)
	}

	override fun create(config: ModelConfig): CustomModel
	= CustomModel(config = config, stateProvider = stateProvider, widgetProvider = widgetProvider)
}