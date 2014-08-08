package org.allenai.ari.sentences

import org.allenai.ari.solvers.utils.Tokenizer
import scala.io.Source

case class Question(text: String, focus: String)

object QueryExpansion {

  def main(args: Array[String]) = {
    val questionSentences =
      Source.fromFile(args(0)).getLines().map {
        line =>

      }
  }

  def expandQuery(question: String, focus: String, topRankedSentences: Seq[(String, Double)]) = {
    import Tokenizer._
    import SimilarityMeasures._
    val questionKeywords = toKeywords(question).toSet
    var frequencies = Map[String, Double]()
    topRankedSentences.foreach {
      case (sentence: String, score: Double) =>
        val keywords = toKeywords(sentence).toSet
        keywords.filter { !questionKeywords.contains(_) } foreach {
          keyword =>
            val count = frequencies.getOrElse(keyword, 0d) + 1d
            frequencies += (keyword -> count)
        }
    }
    frequencies.toSeq.sortBy(-_._2)
  }

}
