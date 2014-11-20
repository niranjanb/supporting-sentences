package org.allenai.ari.sentences

import java.io.PrintWriter

import edu.knowitall.tool.sentence.OpenNlpSentencer
import org.allenai.common.Logging
import org.jsoup.Jsoup

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

case class QueryUrl(query: String, urlIndex: String, url: String)

object UrlsToSentences extends App with Logging {

  val inputFile = args(0)
  val outputDirectory = args(1)
  Source.fromFile(inputFile, "UTF-8").getLines() foreach {
    line =>
      try {
        logger.debug(s"Processing $line")
        line.split("\t") match {
          case Array(query: String, urlIndex: String, url: String) =>
            val queryUrl = QueryUrl(query, urlIndex, url)
            val content = crawl(queryUrl.url)
            saveHtmlToFile(outputDirectory, queryUrl, content)
            saveSentencesToFile(outputDirectory, queryUrl, toSentences(toText(content)))
          case _ =>
            logger.warn(s"Skipping $line")
        }
      } catch {
        case e:Exception =>
          logger.error(s"Failed to process $line")
          logger.error(e.getStackTraceString)
      }
  }
  def crawl(url: String) = Try {
    Source.fromURL(url).getLines().mkString
  } getOrElse {
    logger.error(s"Failed to download url $url")
    ""
  }
  def saveHtmlToFile(outputDirectory: String, queryUrl: QueryUrl, content: String) = {
    val htmlFile = s"${outputDirectory}/${queryUrl.query}-${queryUrl.urlIndex}.html"
    val writer = new PrintWriter(htmlFile, "UTF-8")
    writer.write(content)
    writer.close()
  }

  def saveSentencesToFile(outputDirectory: String, queryUrl: QueryUrl, sentences: mutable.WrappedArray[String]) {
    val sentencesFile = s"$outputDirectory/${queryUrl.query}-${queryUrl.urlIndex}.txt"
    val writer = new PrintWriter(sentencesFile, "UTF-8")
    sentences.foreach {
      sentence =>
        writer.println(s"${queryUrl.query}\t$sentence\t${queryUrl.url}")
    }
    writer.close()
  }

  def toText(html: String) = Jsoup.parse(html).text()

  val sentencer = new OpenNlpSentencer()

  def toSentences(text: String) = sentencer.segmentTexts(text)

}
