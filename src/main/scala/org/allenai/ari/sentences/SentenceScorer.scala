package org.allenai.ari.sentences

import java.io.{PrintWriter, File}
import org.allenai.common.Logging

object SentenceScorer extends App with Logging {

  val files = {
    new File(args(0)).listFiles().filter(_.getName.endsWith(".txt"))
  }

  files.take(2).foreach {
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
    val questionSentences = QuestionSentence.fromFileWithSids(inputFile)
    import SimilarityMeasures._
    questionSentences.foreach {
      questionSentence =>
        val wnScore = wordnetEntailment(questionSentence.sentence, questionSentence.question)
        val overlapScore = hypothesisCoverage(questionSentence.sentence, questionSentence.question)
        val outputLine = s"${questionSentence.toString}\t$wnScore\t$overlapScore"
        println(outputLine)
        writer.println(outputLine)
    }
  }
}
