package org.allenai.ari.sentences

import java.io.PrintWriter

import edu.knowitall.tool.sentence.OpenNlpSentencer
import org.allenai.common.Logging
import org.jsoup.Jsoup

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

object UrlsToSentences extends App with Logging {

  case class QueryUrl(query: String, urlIndex: String, url: String) {}

  def crawl(url: String) = Try {
    Source.fromURL(url).getLines().mkString
  } getOrElse {
    logger.error(s"Failed to download url $url")
    ""
  }

  def writeContent(outputDirectory: String, queryUrl: QueryUrl, content: String) = {
    val htmlFile = s"${outputDirectory}/${queryUrl.query}-${queryUrl.urlIndex}.html"
    val htmlWriter = new PrintWriter(htmlFile, "UTF-8")
    logger.info(s"Url content size ${content.size} starting with ${content.substring(0, math.min(100, content.length))}")
    htmlWriter.write(content)
    htmlWriter.close()
  }
  
  def writeSentences(outputDirectory: String, queryUrl: QueryUrl, sentences: mutable.WrappedArray[String]) {
    val sentencesFile = s"$outputDirectory/${queryUrl.query}-${queryUrl.urlIndex}.txt"
    val sentenceWriter = new PrintWriter(sentencesFile, "UTF-8")
    sentences.foreach {
      sentence =>
        sentenceWriter.println(queryUrl.query + "\t" + sentence + "\t" + queryUrl.url)
    }
    sentenceWriter.close()
  }

  def toText(html: String) = Jsoup.parse(html).text()

  val sentencer = new OpenNlpSentencer()
  def toSentences(text: String) = sentencer.segmentTexts(text)

  val inputFile = args(0)
  val outputDirectory = args(1)

  Source.fromFile(inputFile, "UTF-8").getLines().foreach {
    line =>
      try {
        logger.info(s"Processing line $line")
        val splits = line.split("\t")
        if (splits.size > 2) {
          logger.info("Url: " + splits(2))
          val queryUrl = QueryUrl(splits(0), splits(1), splits(2))
          val content = crawl(queryUrl.url)
          writeContent(outputDirectory, queryUrl, content)
          val sentences = toSentences(toText(content))
          logger.info(s"# sentences ${sentences.size}")
          writeSentences(outputDirectory, queryUrl, sentences)
        }
      } catch {
        case e:Exception =>
          logger.error(s"Failed to process $line")
          logger.error(e.getStackTraceString)
      }
  }
}
