package org.droidmate.exploration.modelFeatures.atua.EWTG

import org.droidmate.exploration.modelFeatures.atua.EWTG.window.Window

data class WindowTransition (val source: Window,
                        val destination: Window,
                        val input: Input,
                        val prevWindow: Window?) {

}