package org.droidmate.exploration.modelFeatures.regression

import org.droidmate.exploration.modelFeatures.regression.staticModel.EventType
import org.droidmate.exploration.modelFeatures.regression.staticModel.StaticWidget

open class WindowTransition (val source: String,
                             val target: String,
                             val targetView: StaticWidget,
                             val eventType: EventType) {
}