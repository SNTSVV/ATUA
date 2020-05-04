package org.droidmate.exploration.strategy.autaut.task

import org.droidmate.exploration.modelFeatures.autaut.textInput.DataField
import org.droidmate.exploration.modelFeatures.autaut.textInput.InputConfiguration
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
        val historyTextInput: ArrayList<String> = ArrayList()
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

        fun getSetTextInputValue(widget: Widget, state: State<*>): String {
            val inputValue = when (widget.inputType) {
                2 -> randomInt()
                1 -> inputString(widget, state)
                else -> inputString(widget, state)
            }
            return inputValue
        }

        fun resetInputData()
        {
            if (inputConfiguration!=null)
            {
                inputConfiguration!!.resetCurrentDataInputs()
            }
        }

        protected open fun inputString(widget: Widget, state: State<*>): String{
            var inputCandidates = ""
            if(inputConfiguration !=null)
            {
                inputCandidates = inputConfiguration!!.getInputDataField(widget,state)
                if (inputCandidates.isNotBlank())
                {
                    return inputCandidates
                }

            }
            val reuseString = random.nextBoolean()
            if (reuseString && historyTextInput.isNotEmpty())
            {
                if (random.nextBoolean() && specificTextInput.containsKey(widget.uid))
                    return specificTextInput[widget.uid]!!.random()
                return historyTextInput.random()
            }
            if ( generalDictionary.isNotEmpty())
            {
                return generalDictionary.random()
            }
            @Suppress("SpellCheckingInspection") val source = "abcdefghijklmnopqrstuvwxyz@#$%^&*()-_=+[]"
            return random.ints( random.nextInt(20).toLong()+3, 0, source.length)
                    .asSequence()
                    .map(source::get)
                    .joinToString("")
        }

        protected open fun randomInt(): String{
            return random.nextInt().toString()
        }

        fun saveSpecificTextInputData(guiState: State<*>) {
            guiState.widgets.forEach {
                if (it.text.isNotBlank()) {
                    generalDictionary.add(it.text)
                }
                if (it.isInputField && !it.text.isBlank()) {
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