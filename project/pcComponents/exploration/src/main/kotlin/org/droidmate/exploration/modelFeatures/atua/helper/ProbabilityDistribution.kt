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
package org.droidmate.exploration.modelFeatures.atua.helper

import kotlin.random.Random

class ProbabilityDistribution<T>(
        val population: Map<T, Double>) {

    val distribution: ArrayList<Triple<T,Double,Double>> = ArrayList()
    init {
        computeDistribution()
    }

    private fun computeDistribution()
    {
        var sum:Double = 0.0

        val newPopulation = HashMap<T, Double>()
        if (population.any{it.value == 0.0}) {
            population.forEach { t, u ->
                newPopulation.put(t,u+1.0)
            }
        }
        population.forEach { _, u ->
            sum = sum + u
        }
        if (sum > 0.0) {
            var indice = 0.0
            population.forEach { t, u ->
                //TODO round the length
                val length = (u) / sum
                distribution.add(Triple(t, indice, length + indice))
                indice += length
            }
        } else {
            sum = population.count().toDouble()
            var indice = 0.0
            population.forEach { t, u ->
                val length = 1.0/sum
                distribution.add(Triple(t,indice,length+indice))
                indice += length
            }
        }
    }

    fun getRandomVariable(): T
    {
        val randomNumber = Random.nextDouble(0.0,1.0)
        val variable = distribution.find { randomNumber >= it.second && randomNumber <= it.third }
        if (variable == null)
            return distribution.random().first
        return variable.first
    }


}