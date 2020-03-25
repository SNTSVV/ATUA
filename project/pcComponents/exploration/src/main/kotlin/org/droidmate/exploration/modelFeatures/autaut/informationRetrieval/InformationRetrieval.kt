/*
 * InformationRetrieval.kt - part of the GATOR project
 *
 * Copyright (c) 2020 The Ohio State University
 *
 * This file is distributed under the terms described in LICENSE in the
 * root directory.
 */

package presto.android.gui.clients.regression.informationRetrieval

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/*
   T: term
   D: document
 */
class InformationRetrieval<D, T> (
        val documentsBagOfWords: HashMap<D, HashMap<T, Long>>
){
    val allDocumentsTermsFrequency = HashMap<D, HashMap<T,Double>>()
    val termsInverseFrequency = HashMap<T, Double>() //idf
    val termsDocumentFrequency = HashMap<T, Long>() //df
    val allDocumentsLength = HashMap<D, Double>()
    val allDocumentsTermsWeight = HashMap<D, HashMap<T, Double>> () //tf-idf
    val allDocumentsNormalizedTermsWeight = HashMap<D, HashMap<T,Double>>()//length-normalization weight

    init {
        computeTermsFrequency()
        countTermsDocumentFrequency()
        computeTermsInverseFrequency()
        computeTermsWeight()
        normalizeTermsWeight()
    }


    fun searchSimilarDocuments(query: HashMap<T,Long>, limit: Int): List<Triple<D, Double, HashMap<T, Double>>>
    {
        if (query.isEmpty())
            return emptyList()
        val queryTermsWeight: Map<T,Double> = computeQueryTermsWeight(query)
        val searchResult: ArrayList<Triple<D, Double,HashMap<T, Double>>> = ArrayList()
        allDocumentsNormalizedTermsWeight.forEach { document, term_weight ->
            val similarityScore = dotProduct(queryTermsWeight, term_weight)
            searchResult.add(Triple(first = document,second = similarityScore.first,third = similarityScore.second))
        }
        if (limit>0)
            return searchResult.sortedByDescending { it.second }.take(limit)
        else
            return searchResult
    }

    private fun computeQueryTermsWeight(query: HashMap<T, Long>): Map<T, Double> {
        val queryTermsWeight = HashMap<T, Double>()
        val queryTermsTF: HashMap<T, Double> = HashMap(computeQueryTermsTF(query))
        val queryLength = computeEuclideanLength(queryTermsTF)
        queryTermsTF.forEach { term, tf ->
            val weight =
            if (termsDocumentFrequency.containsKey(term))
            {
                tf * termsDocumentFrequency[term]!!/queryLength
            }
            else
            {
                0.0
            }
            queryTermsWeight.put(term,weight)
        }

        //val euclideanLength: Double = sqrt(queryTermsWeight.map { it.value }!!.sumByDouble { it.pow(2) })
        //val normalizeQueryTermsWeight = queryTermsWeight.mapValues { it.value/euclideanLength }
        return queryTermsTF
    }

    /**
     * @param query not empty term raw count
     */
    private fun computeQueryTermsTF(query: HashMap<T, Long>): Map<T, Double> {
        val queryTermsTF = HashMap<T,Double>()
        val maxTermRawCount = query.maxBy { it.value }!!.value
        query.forEach { term, rawCount -> 
            //val tf = 0.5 + (0.5 * rawCount/maxTermRawCount)
            val tf = rawCount.toDouble()
            queryTermsTF.put(term,tf)
        }
        return  queryTermsTF
    }

    fun computeCosinScore(document1: D, document2: D): Double{
        var score: Double = 0.0
        val document1TermsWeight = allDocumentsTermsWeight[document1]!!
        val document2TermsWeight = allDocumentsTermsWeight[document2]!!
        //score = dotProduct(document1TermsWeight,document2TermsWeight)

        return score
    }

    private fun simpleProduct(document1TermsWeight: Map<T, Double>, document2TermsWeight: Map<T, Double>): Double {
        var result: Double = 0.0
        document1TermsWeight.forEach { term1, weight1 ->
            if (document2TermsWeight.containsKey(term1))
            {
                val weight2 = document2TermsWeight[term1]!!
                val product = weight1*weight2
                result += product
            }
        }
        return result
    }

    private fun dotProduct(document1TermsWeight: Map<T, Double>, document2TermsWeight: Map<T, Double>): Pair<Double, HashMap<T, Double>> {
        var result: Double = 0.0
        var matchingPair: HashMap<T, Double> = HashMap()
        document1TermsWeight.forEach { term1, weight1 ->
            if (document2TermsWeight.containsKey(term1))
            {
                val weight2 = document2TermsWeight[term1]!!
                val product = weight1*weight2
                result += product
                matchingPair.put(term1,product)
            }
        }
        return Pair(result,matchingPair)
    }

    private fun normalizeTermsWeight() {
        allDocumentsTermsWeight.forEach { document, terms_weight ->
            val documentsEuclideanLength: Double = computeEuclideanLength(terms_weight)
            allDocumentsLength.put(document,documentsEuclideanLength)
            val terms_normalizedWeight = HashMap(terms_weight.mapValues {
                it.value/documentsEuclideanLength
            })
            allDocumentsNormalizedTermsWeight.put(document,terms_normalizedWeight)
        }
    }

    private fun computeEuclideanLength(termsWeight: HashMap<T, Double>): Double {
        var sumOfSquareWeight: Double = 0.0
        termsWeight.forEach { term, weight ->
            val squareOfWeight = weight.pow(2)
            sumOfSquareWeight += squareOfWeight
        }
        val length = sqrt(sumOfSquareWeight)
        return length
    }

    private fun computeTermsWeight() {
        allDocumentsTermsFrequency.forEach { document, term_tf ->
            val documentTermsWeight = HashMap<T, Double>()
            term_tf.forEach { term, tf ->
                val weight = (tf * termsInverseFrequency[term]!!)
                documentTermsWeight.put(term,weight)
            }
            allDocumentsTermsWeight.put(document,documentTermsWeight)
        }
    }

    private fun computeTermsInverseFrequency() {
        val numberOfDocuments = allDocumentsTermsFrequency.size
        termsDocumentFrequency.forEach { term, df ->
            val idf = log10(numberOfDocuments.toDouble()/df)
            termsInverseFrequency.put(term,idf)
        }
    }

    private fun countTermsDocumentFrequency() {
        allDocumentsTermsFrequency.forEach { document, tf ->
            tf.forEach { term, f ->
                if (!termsDocumentFrequency.containsKey(term))
                    termsDocumentFrequency.put(term,1)
                else
                    termsDocumentFrequency[term] = termsDocumentFrequency[term]!! + 1
            }
        }
    }

    /**
     * This function use raw count for document's term frequency
     */
    private fun computeTermsFrequency() {
        documentsBagOfWords.forEach { document, bagOfWords ->
            val termsFrequency = HashMap<T, Double>()
            bagOfWords.forEach { term, count -> 
                termsFrequency.put(term,count.toDouble())
            }
            allDocumentsTermsFrequency.put(document,termsFrequency)
        }
    }

}