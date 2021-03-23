package org.droidmate.exploration.modelFeatures.atua

import org.droidmate.exploration.modelFeatures.atua.EWTG.EventType
import org.droidmate.exploration.modelFeatures.atua.EWTG.EWTGWidget

open class WindowTransition (val source: String,
                             val target: String,
                             val targetView: EWTGWidget,
                             val eventType: EventType) {
}