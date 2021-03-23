package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.EWTG.Input
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget


data class Widget_MethodInvocations (
        val widget: EWTGWidget,
        var methodInvocations: ArrayList<Pair<Input, ArrayList<String>>>
)