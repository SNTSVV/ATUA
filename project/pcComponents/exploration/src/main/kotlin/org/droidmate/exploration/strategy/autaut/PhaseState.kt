package org.droidmate.exploration.strategy.autaut

enum class PhaseState {
    NULL,
    P1_INITIAL,
    //phase 2
    P2_INITIAL,
    P2_RANDOM_EXPLORATION,
    P2_GO_TO_TARGET_NODE,
    P2_EXERCISE_TARGET_NODE,
    P2_GO_TO_ANOTHER_NODE,
    //phase 3
    P3_INITIAL,
    P3_EXERCISE_TARGET_NODE,
    P3_GO_TO_ANOTHER_NODE,
    P3_GO_TO_TARGET_NODE,
    P3_RANDOM_EXPLORATION

}
