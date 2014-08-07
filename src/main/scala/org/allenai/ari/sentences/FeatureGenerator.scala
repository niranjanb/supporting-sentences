package org.allenai.ari.sentences

import java.io.PrintWriter

import scala.io.Source

/**
 * Created by niranjan on 8/7/14.
 */
case class QuestionSentence(qid: String, question: String, focus: String, url: String, sentence: String, annotation: Int)

case class Instance(questionSentence: QuestionSentence, features: Map[String, Double]) {
  override  def toString(): String = {
    questionSentence.toString + "\t" + features.values.mkString("\t")
  }
}


object FeatureGenerator extends App {


  //private val config = ConfigFactory.load()
  val inputFile = args(0)
  val outputFile = args(1)

  val questionSentences = Source.fromFile(inputFile).getLines().map {
    line =>
      //Assume line is of the following form:
      //Annotator	Q ID	T/F question	Focus	URL	Sentence	Supporting (0-2)?	Necessary rewrite
      //Example
      //Ashish	44	Is it true that sleet, rain, snow, and hail are forms of precipitation? 	precipitation	http://ww2010.atmos.uiuc.edu/%28Gh%29/guides/mtr/cld/prcp/home.rxml	Precipitation occurs in a variety of forms; hail, rain, freezing rain, sleet or snow.	2
      val splits = line.split("\t")
      QuestionSentence(splits(0), splits(1), splits(2), splits(3), splits(4), splits(5).toInt)
  }

  def overlap(src: String, tgt: String) = {
    import org.allenai.ari.solvers.utils.Tokenizer._
    val srcKeywords = toKeywords(src)
    val tgtKeywords = toKeywords(tgt)
    srcKeywords.intersect(tgtKeywords) / tgtKeywords.size
  }

  def features(questionSentence: QuestionSentence) = {
    var features = Map[String, Double]()
    features += ("sentence-length" -> questionSentence.sentence.length.toDouble)
    //add more features.
    features += ("word-overlap" -> overlap(questionSentence.question, questionSentence.sentence))
    features
  }

  val instances: Iterator[Instance] = questionSentences.map {
    questionSentence => Instance(questionSentence, features(questionSentence))
  }

  val writer = new PrintWriter(outputFile)

  instances.foreach{
    instance =>
      writer.println(instance.toString)
  }
  writer.close()

}
