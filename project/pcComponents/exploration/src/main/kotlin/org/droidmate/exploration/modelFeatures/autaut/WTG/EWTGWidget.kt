package org.droidmate.exploration.modelFeatures.autaut.WTG

import org.droidmate.exploration.modelFeatures.autaut.WTG.window.Window
import org.droidmate.explorationModel.emptyUUID
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random

open class EWTGWidget constructor(val widgetId: String,//sootandroid id
                                  val resourceIdName: String,
                                  @Suppress val resourceId: String,
                                  val className: String,
                                  @Suppress var contentDesc: String,
                                  var text: String, //usefull for mapping menu item
                                  var activity: String,
                                  var wtgNode: Window,
                                  val createdAtRuntime: Boolean = false,
                                  val attributeValuationSetId: UUID = emptyUUID
                                    ){
    var possibleTexts= ArrayList<String>()
    var exercised: Boolean = false
    var exerciseCount: Int = 0
    var textInputHistory: ArrayList<String> = ArrayList()

    init {
        wtgNode.widgets.add(this)
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
                                    attributeValuationSetId: UUID = emptyUUID): EWTGWidget {
            val returnWidget = allStaticWidgets.find{ it.widgetId==widgetId && it.activity == activity}
            if ( returnWidget == null) {
                var staticWidget = EWTGWidget(widgetId = widgetId, resourceIdName = resourceIdName,
                        resourceId = resourceId, wtgNode = wtgNode, className = className, text = "", contentDesc = "",
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