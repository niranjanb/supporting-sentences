package org.allenai.ari.sentences

import scala.io.Source

case class QuestionSentence(qid: String, question: String, focus: String, sid: Option[String], url: String, sentence: String, annotationOpt: Option[Int]) {
  override def toString() = s"$qid\t$question\t$focus\t$sid\t$url\t$sentence\t${annotationOpt.getOrElse("?")}"
}

object QuestionSentence {

  def header = "qid\tquestion\tfocus\tsid\turl\tsentence\tannotationOpt"

  def fromTrainingFile(file: String, headerLinesToDrop: Int) = {
    Source.fromFile(file).getLines().drop(headerLinesToDrop).map {
      line =>
        //Assume line is of the following form:
        //Q ID    T/F question    Focus   URL Sentence    Supporting (0-2)?   Necessary rewrite
        //Example
        //44  Is it true that sleet, rain, snow, and hail are forms of precipitation?     precipitation   http://ww2010.atmos.uiuc.edu/%28Gh%29/guides/mtr/cld/prcp/home.rxml Precipitation occurs in a variety of forms; hail, rain, freezing rain, sleet or snow.   2
        val splits = line.split("\t")
        val annotationOpt = if (splits.size > 5) Some(splits(5).toInt) else None
        QuestionSentence(splits(0), splits(1), splits(2), None, splits(3), splits(4), annotationOpt)
    }.toList
  }

  def fromFileWithSids(file: String, headerLinesToDrop: Int) = {
    Source.fromFile(file).getLines().drop(headerLinesToDrop).map {
      line =>
        //Assume line is of the following form:
        //Q ID    T/F question    Focus   URL Sentence    Supporting (0-2)?   Necessary rewrite
        //Example

        val splits = line.split("\t")
        val annotationOpt = if (splits.size > 7) Some(splits(7).toInt) else None
        QuestionSentence(splits(0), splits(2), splits(3), Some(splits(4)), splits(5), splits(6), annotationOpt)
    }.toList
  }

  def fromFileWithSidsLASTMINUTE(file: String, headerLinesToDrop: Int) = {
    Source.fromFile(file).getLines().drop(headerLinesToDrop).take(20).map {
      line =>
        val splits = line.split("\t")
        //println(splits.mkString("\n"))
        QuestionSentence(splits(0), splits(1), splits(2), None, splits(9), splits(4), Some(splits(3).toInt))
    }.toList
  }
}
