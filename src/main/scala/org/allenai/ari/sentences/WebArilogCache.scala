package org.allenai.ari.sentences

import org.allenai.common.{Logging, Resource}
import scala.io.Source
import java.io.PrintWriter

object WebArilogCache extends App with Logging {

  val ermineUrl = "http://ermine.allenai.org:8080/pipeline/ferret-text"
  val client = new ErmineArilogClient(ermineUrl)

  val inputFile = args(0)
  val outputFile = args(1)
  val topk = args(2).toInt
  //val writer = new PrintWriter(args(1), "UTF-8")
  val topkWriter = new PrintWriter(args(1), "UTF-8")
  val headTailRe = """(.*)\t(.*)""".r
  Resource.using(Source.fromFile(inputFile)) {
    input =>
      val scores = input.getLines().drop(1).toSeq.flatMap {
        line =>
          line match {
            case headTailRe (head: String, tail: String) =>
              val score = tail.toDouble
              logger.info(s"head ${head}")
              val questionSentence = QuestionSentence.fromStringWithSidsC(head)
              if(!questionSentence.sentence.contains('?')) {
                Some(questionSentence -> score)
              } else {
                logger.info(s"Ignoring question sentence ${questionSentence.sentence}")
                None
              }
            case _ => None
          }
      }




      var map = 0.0
      var numQuestions = 0d
      var allRelevant = 0d
      scores.groupBy(_._1.question).foreach {
        group =>
          val qsps = group._2.sortBy(-_._2)
          val totalRelevant = qsps.map { qsp => qsp._1.annotationOpt.getOrElse(0) }.sum
          allRelevant = allRelevant + totalRelevant
          var rank = 1d
          var numRelevant = 0d
          var avgPrecision = 0d
          qsps.take(topk).foreach {
            qsp =>
              val questionSentence = qsp._1
              val relevant = questionSentence.annotationOpt.getOrElse(0) > 0
              if( relevant ) {
                numRelevant = numRelevant + 1.0
              }
              if (relevant) { avgPrecision += (numRelevant / rank) } else 0d
              rank = rank + 1
              try {
                logger.info(s"question-sentence ${questionSentence}")
                //val response = client.ermineResponse(questionSentence.sentence)
                topkWriter.println(qsp._2 + "\t" + questionSentence.annotationOpt.getOrElse(0) + "\t" + questionSentence.sentence)
                //writer.println(response)
              }catch{
                case e:Exception =>
                  logger.error(s"Caught exception while processing ${questionSentence}")
                  logger.error(s"Exception: ${e.getStackTraceString}")
              }
          }
          val annotated = qsps.exists(qsp => qsp._1.annotationOpt.isDefined)
          if(annotated) numQuestions = numQuestions + 1d
          if (totalRelevant > 0) {
            map = map + (avgPrecision/totalRelevant)
          }
      }
      logger.info(s"MAP ${map/numQuestions} #questions ${numQuestions} #relevant ${allRelevant}")
  }

  topkWriter.close
  //writer.close()


}
