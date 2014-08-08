package org.allenai.ari.sentences

import scala.io.Source

object SentenceScorer extends App {

  val questionSentences = QuestionSentence.fromFileWithSids(args(0))
  import SimilarityMeasures._
  questionSentences.foreach {
    questionSentence =>
      val wnScore = wordnetEntailment(questionSentence.sentence, questionSentence.question)
      val overlapScore = hypothesisCoverage(questionSentence.sentence, questionSentence.question)
      println(s"${questionSentence.toString}\t$wnScore\t$overlapScore")
  }
}
