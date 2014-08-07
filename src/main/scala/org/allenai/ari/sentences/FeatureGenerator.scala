package org.allenai.ari.sentences

import org.allenai.ari.solvers.utils.Tokenizer

import java.io.PrintWriter

import scala.io.Source


/** Created by niranjan on 8/7/14.
  */
case class QuestionSentence(qid: String, question: String, focus: String, url: String, sentence: String, annotation: Int)

case class Instance(questionSentence: QuestionSentence, features: Map[String, Double]) {
  override def toString(): String = {
    questionSentence.toString + "\t" + features.values.mkString("\t")
  }

  def toARFF(): String = {
    features.values.mkString(",") + "," + (questionSentence.annotation > 0)
  }
}

object FeatureGenerator extends App {

  //private val config = ConfigFactory.load()
  val inputFile = args(0)
  val outputFile = args(1)

  val questionSentences = Source.fromFile(inputFile).getLines().drop(1).map {
    line =>
      //Assume line is of the following form:
      //Annotator	Q ID	T/F question	Focus	URL	Sentence	Supporting (0-2)?	Necessary rewrite
      //Example
      //Ashish	44	Is it true that sleet, rain, snow, and hail are forms of precipitation? 	precipitation	http://ww2010.atmos.uiuc.edu/%28Gh%29/guides/mtr/cld/prcp/home.rxml	Precipitation occurs in a variety of forms; hail, rain, freezing rain, sleet or snow.	2
      val splits = line.split("\t")
      QuestionSentence(splits(0), splits(1), splits(2), splits(3), splits(4), splits(5).toInt)
  }

  val instances: Iterator[Instance] = questionSentences.map {
    questionSentence => Instance(questionSentence, features(questionSentence))
  }

  toARFF(outputFile)

  def overlap(src: String, tgt: String, asFrac: Boolean) = {
    val srcKeywords = Tokenizer.toKeywords(src)
    val tgtKeywords = Tokenizer.toKeywords(tgt)
    if (asFrac)
      Math.round(srcKeywords.intersect(tgtKeywords).size / tgtKeywords.size.toDouble * 1000000.0) / 1000000.0
    else
      srcKeywords.intersect(tgtKeywords).size
  }

  def features(questionSentence: QuestionSentence) = {
    var features = Map[String, Double]()
    // number of words in the sentence
    features += ("sentence-length" -> questionSentence.sentence.split("\\s+").size)
    // number of sentence words that overlap with the question
    features += ("word-overlap-num" -> overlap(questionSentence.question, questionSentence.sentence, false))
    // fraction of sentence words that overlap with the question
    features += ("word-overlap-frac" -> overlap(questionSentence.question, questionSentence.sentence, true))
    features
  }

  def toARFF(arffFile: String) = {
    val writer = new PrintWriter(arffFile)
    // add ARFF header
    writer.println("@relation SENTENCE_SELECTOR")
    writer.println("  @attribute sentence-length       numeric         % length of the sentence")
    writer.println("  @attribute word-overlap-num      numeric         % number of sentence words that overlap the question")
    writer.println("  @attribute word-overlap-frac     numeric         % fractino of sentence words that overlap the question")
    writer.println("  @attribute class                 {true,false}    % BINARY LABEL: whether the sentence supports the question")
    writer.println("")
    // add ARFF data
    writer.println("@data")
    instances.foreach {
      instance => writer.println(instance.toARFF)
    }
    writer.close()
  }

}
