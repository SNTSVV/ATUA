package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.StaticWidget


data class Widget_MethodInvocations (
        val widget: StaticWidget,
        var methodInvocations: ArrayList<Pair<Input, ArrayList<String>>>
)