package org.droidmate.exploration.modelFeatures.atua.inputRepo.textInput

import org.droidmate.deviceInterface.exploration.isEnabled
import org.droidmate.exploration.strategy.atua.task.InputCoverage
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.random.Random
import kotlin.streams.asSequence

class TextInput () {
    companion object{
        var inputConfiguration: InputConfiguration? = null
        var generalDictionary: HashSet<String> = HashSet()
        val historyTextInput: HashSet<String> = HashSet()
        val specificTextInput: HashMap<UUID, ArrayList<String>> = HashMap()

        protected var random = java.util.Random(Random.nextLong())
            private set

        fun getInputWidgetAssociatedDataField(widget: Widget, state: State<*>): DataField?{
            if(inputConfiguration !=null)
            {
                val dataField = inputConfiguration!!.getDataField(widget,state)
                return dataField
            }
            return null
        }

        fun getSetTextInputValue(widget: Widget, state: State<*>, randomInput: Boolean, inputCoverageType: InputCoverage): String {
            val inputValue = when (widget.inputType) {
                2 -> randomInt()
                1 -> inputString(widget, state,randomInput,inputCoverageType)
                else -> inputString(widget, state,randomInput,inputCoverageType)
            }
            return inputValue
        }

        fun resetInputData()
        {
            if (inputConfiguration !=null)
            {
                inputConfiguration!!.resetCurrentDataInputs()
            }
        }

        protected open fun inputString(widget: Widget, state: State<*>, randomInput: Boolean, inputCoverageType: InputCoverage): String{
            var inputCandidates = ""
            if(inputConfiguration !=null )
            {
                if ((randomInput && random.nextBoolean()) || !randomInput) {
                    inputCandidates = inputConfiguration!!.getInputDataField(widget, state)
                    if (inputCandidates.isNotBlank()) {
                        return inputCandidates
                    }
                }
            }
            if (widget.checked.isEnabled()) {
                when (inputCoverageType) {
                    InputCoverage.FILL_ALL -> return "true"
                    InputCoverage.FILL_EMPTY -> return "false"
                    InputCoverage.FILL_NONE -> return widget.checked.toString()
                    InputCoverage.FILL_RANDOM -> return if (random.nextBoolean())
                        "true"
                    else
                        "false"
                }
            }
            //widget is TextInput
            when (inputCoverageType) {
                InputCoverage.FILL_EMPTY -> return ""
                InputCoverage.FILL_NONE -> return widget.text
                InputCoverage.FILL_ALL, InputCoverage.FILL_RANDOM -> return generateInput(widget)
            }
            return ""
        }

        private fun generateInput(widget: Widget): String {
            val reuseString = random.nextBoolean()
            if (reuseString && historyTextInput.isNotEmpty())
            {
                if (random.nextBoolean() && specificTextInput.containsKey(widget.uid))
                    return specificTextInput[widget.uid]!!.random()
                return historyTextInput.random()
            }
            val textValue: String
            val choice = random.nextInt(3)
            when (choice) {
                0 -> textValue = generalDictionary.random()
                1 -> textValue = ""
                2 -> {
                    val source = "1234567890abcdefghijklmnopqrstuvwxyz@#$%^&*()-_=+[]"
                    textValue = random.ints( random.nextInt(20).toLong()+3, 0, source.length)
                        .asSequence()
                        .map(source::get)
                        .joinToString("")
                }
                else -> {
                    val source = "1234567890abcdefghijklmnopqrstuvwxyz@#$%^&*()-_=+[]"
                    textValue = random.ints( random.nextInt(20).toLong()+3, 0, source.length)
                        .asSequence()
                        .map(source::get)
                        .joinToString("")
                }
            }
            historyTextInput.add(textValue)
            return textValue
        }

        protected open fun randomInt(): String{
            return random.nextInt().toString()
        }

        fun saveSpecificTextInputData(guiState: State<*>) {
            guiState.widgets.forEach {
                if (it.text.isNotBlank() && !arrayListOf<String>("ALLOW", "DENY", "DON'T ALLOW").contains(it.text)) {
                    generalDictionary.add(it.text)
                }
                if (it.isInputField && !it.text.isBlank() && !arrayListOf<String>("ALLOW", "DENY", "DON'T ALLOW").contains(it.text)) {
                    if (!specificTextInput.containsKey(it.uid)) {
                        specificTextInput.put(it.uid, ArrayList())
                    }
                    if (!specificTextInput[it.uid]!!.contains(it.text)) {
                        specificTextInput[it.uid]!!.add(it.text)
                    }
                }
            }
        }

    }
}