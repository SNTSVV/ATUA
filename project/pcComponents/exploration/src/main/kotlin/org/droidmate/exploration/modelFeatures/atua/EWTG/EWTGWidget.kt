package org.droidmate.exploration.modelFeatures.atua.EWTG

import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window
import org.droidmate.exploration.modelFeatures.atua.modelReuse.ModelVersion
import kotlin.collections.ArrayList
import kotlin.random.Random

open class EWTGWidget constructor(val widgetId: String,//sootandroid id
                                  val resourceIdName: String,
                                  @Suppress val resourceId: String,
                                  val className: String,
                                  @Suppress var contentDesc: String,
                                  var text: String, //usefull for mapping menu item
                                  var activity: String,
                                  var window: Window,
                                  var createdAtRuntime: Boolean = false,
                                  val attributeValuationSetId: String = ""
                                    ){
    var possibleTexts= ArrayList<String>()
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
    }
    override fun toString(): String {
        return "$widgetId-resourceId=$resourceIdName-className=$className"
    }


    fun clone(newNode: Window): EWTGWidget {
        val newStaticWidget = getOrCreateStaticWidget(
                widgetId = this.widgetId,
                resourceId = this.resourceId,
                resourceIdName = this.resourceIdName,
                className = this.className,
                activity = this.activity,
                wtgNode = newNode,
                attributeValuationSetId = attributeValuationSetId
        )
        newStaticWidget.possibleTexts = this.possibleTexts
        newStaticWidget.contentDesc = this.contentDesc
        newStaticWidget.text = this.text
        newStaticWidget.exercised = this.exercised
        newStaticWidget.exerciseCount = this.exerciseCount
        return newStaticWidget
    }



    companion object{
        val allStaticWidgets= ArrayList<EWTGWidget>()
        fun getOrCreateStaticWidget(widgetId: String,
                                    resourceIdName: String = "",
                                    resourceId: String = "",
                                    className: String,
                                    activity: String,
                                    wtgNode: Window,
                                    attributeValuationSetId: String = ""): EWTGWidget {
            val returnWidget = allStaticWidgets.find{ it.widgetId==widgetId && it.activity == activity}
            if ( returnWidget == null) {
                var staticWidget = EWTGWidget(widgetId = widgetId, resourceIdName = resourceIdName,
                        resourceId = resourceId, window = wtgNode, className = className, text = "", contentDesc = "",
                        activity = activity, attributeValuationSetId = attributeValuationSetId)
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