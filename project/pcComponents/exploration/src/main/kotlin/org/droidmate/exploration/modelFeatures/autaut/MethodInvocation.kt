package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.WTG.Input
import org.droidmate.exploration.modelFeatures.autaut.WTG.EWTGWidget


data class Widget_MethodInvocations (
        val widget: EWTGWidget,
        var methodInvocations: ArrayList<Pair<Input, ArrayList<String>>>
)