package org.droidmate.exploration.strategy.regression.task

import org.droidmate.exploration.modelFeatures.regression.textInput.DataField
import org.droidmate.exploration.modelFeatures.regression.textInput.InputConfiguration
import org.droidmate.explorationModel.interaction.State
import org.droidmate.explorationModel.interaction.Widget
import kotlin.random.Random
import kotlin.streams.asSequence

class TextInput () {
    companion object{
        var inputConfiguration: InputConfiguration? = null
        var generalDictionary: ArrayList<String>? = null
        val historyTextInput: ArrayList<String> = ArrayList()
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
                return historyTextInput.random()
            }
            if (generalDictionary !=null && generalDictionary!!.isNotEmpty())
            {
                return generalDictionary!![random.nextInt(generalDictionary!!.size)]
            }
            @Suppress("SpellCheckingInspection") val source = "abcdefghijklmnopqrstuvwxyz"
            return random.ints( random.nextInt(20).toLong()+3, 0, source.length)
                    .asSequence()
                    .map(source::get)
                    .joinToString("")
        }

        protected open fun randomInt(): String{
            return random.nextInt().toString()
        }

    }
}