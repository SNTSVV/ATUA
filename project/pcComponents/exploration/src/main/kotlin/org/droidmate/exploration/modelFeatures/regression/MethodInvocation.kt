package org.droidmate.exploration.modelFeatures.regression

import org.droidmate.exploration.modelFeatures.regression.staticModel.StaticEvent
import org.droidmate.exploration.modelFeatures.regression.staticModel.StaticWidget


data class Widget_MethodInvocations (
        val widget: StaticWidget,
        var methodInvocations: ArrayList<Pair<StaticEvent, ArrayList<String>>>
)