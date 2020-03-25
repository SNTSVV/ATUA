package org.droidmate.exploration.modelFeatures.regression.helper

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
        population.forEach { _, u ->
            sum = sum + u
        }
        var indice = 0.0
        population.forEach { t, u ->
            val length = u/sum
            distribution.add(Triple(t,indice, length+indice))
            indice += length
        }
    }

    fun getRandomVariable(): T
    {
        val randomNumber = Random.nextDouble(0.0,1.0)
        val variable = distribution.find { randomNumber >= it.second && randomNumber <= it.third }!!.first
        return variable
    }


}