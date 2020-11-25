package org.droidmate.exploration.modelFeatures.autaut

import org.droidmate.exploration.modelFeatures.autaut.WTG.EventType
import org.droidmate.exploration.modelFeatures.autaut.WTG.StaticWidget

open class WindowTransition (val source: String,
                             val target: String,
                             val targetView: StaticWidget,
                             val eventType: EventType) {
}