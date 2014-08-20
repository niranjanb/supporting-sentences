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
        fromString(line)
    }.toList
  }

  def fromString(line: String): QuestionSentence = {
    val splits = line.split("\t")
    val annotationOpt = if (splits.size > 5) Some(splits(5).toInt) else None
    QuestionSentence(splits(0), splits(1), splits(2), None, splits(3), splits(4), annotationOpt)
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

  def fromFileWithSidsB(file: String, headerLinesToDrop: Int) = {
    Source.fromFile(file).getLines().drop(headerLinesToDrop).map {
      line =>
        fromStringWithSidsB(line)
    }.toList
  }

  def fromStringWithSidsB(line: String): QuestionSentence = {
    val splits = line.split("\t")
    val annotationOpt = if (!splits(3).isEmpty()) Some(splits(3).toInt) else None
    QuestionSentence(splits(0), splits(1), splits(2), Some(splits(8)), splits(9), splits(4), annotationOpt)
  }

  def fromStringWithSidsC(line: String): QuestionSentence = {
    val splits = line.split("\t")
    //176
    // Is it true that winter season is a season of the year during which a rabbits fur would be thickest?
    // winter
    // Some(Some(4))
    // http://www.linkstolearning.com/quizzes/nytest/index8087.htm
    // During which season of the year would a rabbit’s fur be thickest?   a.
    // 0
    val annotationOpt = if (!splits(6).isEmpty() && splits(6).charAt(0) != '?') Some(splits(6).toInt) else None
    val sid = splits(3).replaceAll("""\(""", "").replaceAll("""\)""", "").replaceAll("Some", "")
    QuestionSentence(qid = splits(0),
      question = splits(1), focus = splits(2), sid = Some(sid), url = splits(4), sentence = splits(5), annotationOpt)
  }
}
