/*
 * ATUA is a test automation tool for mobile Apps, which focuses on testing methods updated in each software release.
 * Copyright (C) 2019 - 2021 University of Luxembourg
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.droidmate.exploration.strategy.atua

enum class PhaseState {
    NULL,
    P1_INITIAL,
    P1_GO_TO_TARGET_NODE,
    P1_GO_TO_EXPLORE_STATE,
    P1_EXERCISE_TARGET_NODE,
    P1_RANDOM_IN_EXERCISE_TARGET_NODE,
    P1_RANDOM_EXPLORATION,
    //phase 2
    P2_INITIAL,
    P2_RANDOM_EXPLORATION,
    P2_GO_TO_TARGET_NODE,
    P2_EXERCISE_TARGET_NODE,
    P2_GO_TO_EXPLORE_STATE,
    P2_RANDOM_IN_EXERCISE_TARGET_NODE,
    //phase 3
    P3_INITIAL,
    P3_EXERCISE_TARGET_NODE,
    P3_GO_TO_RELATED_NODE,
    P3_GO_TO_TARGET_NODE,
    P3_EXPLORATION_IN_RELATED_WINDOW

}
