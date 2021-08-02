package org.droidmate.exploration.modelFeatures.atua.ewtg

import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window
import org.droidmate.exploration.modelFeatures.calm.modelReuse.ModelVersion
import java.lang.StringBuilder
import kotlin.collections.ArrayList
import kotlin.random.Random

open class EWTGWidget constructor(val widgetId: String,//sootandroid id
                                  val resourceIdName: String,
                                  val className: String,
                                  text: String, //usefull for mapping menu item
                                  contentDesc: String,
                                  var window: Window,
                                  var createdAtRuntime: Boolean = false,
                                  var structure: String = ""
                                    ){
    var isUserLikeInput: Boolean = false
    val possibleTexts= ArrayList<String>()
    val possibleContentDescriptions = ArrayList<String>()
    var exercised: Boolean = false
    var exerciseCount: Int = 0
    var textInputHistory: ArrayList<String> = ArrayList()
    var parent: EWTGWidget? = null
        set(value) {
            if (value==null)
                return
            field = value
            if (!value.children.contains(this)) {
                value.children.add(this)
            }
        }
    val children: ArrayList<EWTGWidget> = ArrayList()
    var modelVersion: ModelVersion = ModelVersion.RUNNING
    init {
        window.widgets.add(this)
        allStaticWidgets.add(this)
        if (text.isNotBlank()) {
                    possibleTexts.add(text)
                }
        if (contentDesc.isNotBlank()) {
            possibleContentDescriptions.add(text)
        }
    }
    override fun toString(): String {
        return "$widgetId-resourceId=$resourceIdName-className=$className"
    }


    fun clone(newNode: Window): EWTGWidget {
        val newStaticWidget = getOrCreateStaticWidget(
                widgetId = this.widgetId,
                resourceIdName = this.resourceIdName,
                className = this.className,
                wtgNode = newNode,
                structure = structure
        )
        newStaticWidget.possibleTexts.addAll( this.possibleTexts)
        newStaticWidget.possibleContentDescriptions.addAll(this.possibleContentDescriptions)
        newStaticWidget.exercised = this.exercised
        newStaticWidget.exerciseCount = this.exerciseCount
        return newStaticWidget
    }

    fun generateSignature(): String {
        if (this.children.isEmpty())
            return "[className]${this.className}[resourceId]${this.resourceIdName}"
        val sigSB = StringBuilder()
        sigSB.append("[className]${this.className}[resourceId]${this.resourceIdName}")
        sigSB.append("/")
        this.children.sortedBy { it.className+it.resourceIdName }.forEach {
            sigSB.append(it.generateSignature())
        }
        return sigSB.toString()
    }


    companion object{
        val allStaticWidgets= ArrayList<EWTGWidget>()
        fun getOrCreateStaticWidget(widgetId: String,
                                    resourceIdName: String = "",
                                    resourceId: String = "",
                                    className: String,
                                    wtgNode: Window,
                                    structure: String = ""): EWTGWidget {
            val returnWidget = allStaticWidgets.find{ it.widgetId==widgetId && it.window == wtgNode}
            if ( returnWidget == null) {
                var staticWidget = EWTGWidget(widgetId = widgetId
                        , resourceIdName = resourceIdName
                        , window = wtgNode
                        , className = className
                        , text = ""
                        , contentDesc = ""
                        , structure = structure)
                return staticWidget
            }
            else
            {

                return returnWidget
            }
        }

        fun getWidgetId(): String{
            var widgetId = Random.nextLong().toString()
            while(allStaticWidgets.find { it.widgetId == widgetId }!=null)
            {
                widgetId = Random.nextLong().toString()
            }
            return widgetId
        }
    }
}