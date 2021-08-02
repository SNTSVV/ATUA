package org.droidmate.exploration.modelFeatures.atua.ewtg

import org.droidmate.exploration.modelFeatures.atua.ewtg.window.Window

data class WindowTransition (val source: Window,
                        val destination: Window,
                        val input: Input,
                        val prevWindow: Window?) {

}