package org.allenai.ari.sentences

object TermFrequencies {

}
object SimilarityMeasures {

  def overlap(text: Set[String], hypothesis: Set[String]) = text.intersect(hypothesis).size

  def hypothesisCoverage(text: Set[String], hypothesis: Set[String]): Double = overlap(text, hypothesis) / hypothesis.size.toDouble

  def tfIdf(text: Set[String], hypothesis: Set[String]) = ???

  def wordnetEntailment(text: String, hypothesis: String) = ???

}
