package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticEvent
import org.droidmate.exploration.modelFeatures.autaut.staticModel.StaticWidget


data class Widget_MethodInvocations (
        val widget: StaticWidget,
        var methodInvocations: ArrayList<Pair<StaticEvent, ArrayList<String>>>
)