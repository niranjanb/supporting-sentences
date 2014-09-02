package org.allenai.ari.sentences

import java.io.{File, PrintWriter}

import org.allenai.common.{Resource, Logging}

import scala.io.Source

object SentenceScorer extends App with Logging {

  val files = {
    new File(args(0)).listFiles().filter(_.getName.endsWith(".txt"))
  }

  val trueQuestions = Set()
  val trueQuestionIdsFile = "trueQuestionIds.txt"
  val validFiles = Resource.using (Source.fromFile(trueQuestionIdsFile)) {
    input =>
      input.getLines().map {
        line => s"$line.txt"
      }.toSet
  }
  files.filter {file => validFiles.contains(file.getName)}
    .toSeq.sortBy( f => f.getName.replaceAll("""\.txt""", "").toInt )
    .foreach {
      file =>
        try {
          logger.info(s"Processing ${file.getName()}")
          val outputFile = args(1) + File.separator + file.getName
          val writer = new PrintWriter(outputFile, "utf-8")
          writer.println(QuestionSentence.header)
          scoreSentencesInFile(file.getAbsolutePath, writer)
          writer.close()
        } catch {
          case e: Exception => println(s"Caught exception processing input file ${file.getName()}")
        }
    }

  def scoreSentencesInFile(inputFile: String, writer: PrintWriter) = {
    val questionSentences = QuestionSentence.fromFileWithSids(inputFile, 0)
    import org.allenai.ari.sentences.SimilarityMeasures._
    questionSentences.foreach {
      questionSentence =>
        val wnScore = wordnetEntailment(questionSentence.sentence, questionSentence.question)
        val overlapScore = hypothesisCoverage(questionSentence.sentence, questionSentence.question)
        if(overlapScore > 0.0) {
          val outputLine = s"${questionSentence.toString}\t$wnScore\t$overlapScore"
          writer.println(outputLine)
        }
    }
  }
}
