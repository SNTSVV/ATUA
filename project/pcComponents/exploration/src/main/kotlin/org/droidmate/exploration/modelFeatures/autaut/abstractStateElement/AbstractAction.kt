package org.droidmate.exploration.modelFeatures.regression.abstractStateElement

data class AbstractAction (
    val actionName: String,
    val widgetGroup: WidgetGroup?=null,
    val extra: Any?=null
    )