package org.allenai.ari.sentences

import java.io.InputStream

import org.allenai.ari.solvers.inference.matching.{EntailmentService, EntailmentWrapper}
import org.allenai.common.Resource

import scala.io.Source

object SimilarityMeasures {

  private def getResourceAsStream(name: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(name)

  val wordFrequency = loadWordFrequencies("word-frequencies.txt")
  val minFreq = wordFrequency.values.min

  def overlap(text: Set[String], hypothesis: Set[String]) =
    text.intersect(hypothesis).size

  import org.allenai.ari.solvers.utils.Tokenizer._
  def hypothesisCoverage(text: String, hypothesis: String): Double = {
    hypothesisCoverage(toKeywords(text).toSet, toKeywords(hypothesis).toSet)
  }

  def hypothesisCoverage(text: Set[String], hypothesis: Set[String]): Double =
    overlap(text, hypothesis) / hypothesis.size.toDouble

  private def frequencyWeight(token: String): Double = {
    // constants were hand-tuned by Peter
    val wordWeightK: Double = 10.0
    val normalizationConstant: Double = 2.3978953
    (1 / math.log(wordFrequency.getOrElse(token, minFreq) + wordWeightK)) * normalizationConstant
  }

  /** Load word frequency information from a file of frequency<space>term pairs.
    */
  private def loadWordFrequencies(path: String): Map[String, Int] = {
    val wordFrequenciesStream = getResourceAsStream(path)
    val counts = scala.collection.mutable.HashMap[String, Int]()
    Resource.using(Source.fromInputStream(wordFrequenciesStream)) {
      input =>
        for (line <- input.getLines()) {
          line.split("\\s+") match {
            case Array(count, term) => {
              counts.update(term, count.toInt + counts.getOrElse(term, 0))
            }
            case _ => throw new MatchError("Couldn't parse line " + line + " from " + path)
          }
        }
    }
    counts.toMap
  }

  def tfIdf(text: Set[String], hypothesis: Set[String]) = {
    val hypWeights = hypothesis.map { frequencyWeight(_) }
    val overlapWeights = text.intersect(hypothesis) map { frequencyWeight(_) }
    overlapWeights.sum / hypWeights.sum
  }

  val wordnetEntailmentService: EntailmentService = {
    val wordnetEntailmentUrl = "http://entailment.dev.allenai.org:8191/api/entails"
    val wrapper = new EntailmentWrapper(wordnetEntailmentUrl)
    wrapper.CachedEntails
  }

  def wordnetEntailment(text: String, hypothesis: String) =
    wordnetEntailmentService(text, hypothesis) map { _.confidence } getOrElse 0d

  val word2vecEntailmentService: EntailmentService = {
    val word2vecEntailmentUrl = "???"
    val wrapper = new EntailmentWrapper(word2vecEntailmentUrl)
    wrapper.CachedEntails
  }

  //GregJ
  def word2vecEntailment(text: String, hypothesis: String) = ???

  //Ellie
  def ppdbEntailment(text: String, hypothesis: String) = ???

}
