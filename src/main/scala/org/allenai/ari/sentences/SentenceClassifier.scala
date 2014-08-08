package org.allenai.ari.sentences

import org.allenai.ari.solvers.utils.Tokenizer
import org.allenai.common.Logging

import java.io.PrintWriter
import java.util.Random

import scala.io.Source

import weka.core.converters.ConverterUtils.DataSource
import weka.core.Instances
import weka.classifiers.Classifier
import weka.classifiers.Evaluation
import weka.classifiers.bayes._
import weka.classifiers.functions._
import weka.classifiers.meta._
import weka.classifiers.misc._
import weka.classifiers.rules._
import weka.classifiers.trees._
import weka.classifiers.functions.supportVector._

case class QuestionSentence(qid: String, question: String, focus: String, url: String, sentence: String, annotation: Int)

object SentenceClassifier extends App with Logging {

  //private val config = ConfigFactory.load()
  logger.info("Parsing commandline arguments")
  assert(args.size >= 2 && args.size <= 3)
  val configClassifierName = args(0)
  val configTrainingFile = args(1)
  val configValidationFileOpt = args.size match {
    case 3 => Option(args(2))
    case _ => Option(null)
  }

  val configArffTrain = "train.arff"
  val configArffValidationOpt = configValidationFileOpt map {
    configValidationFile => "validation.arff"
  }

  logger.info(s"Extracting question+sentences from $configTrainingFile")
  val questionSentences = Source.fromFile(configTrainingFile).getLines().drop(1).map {
    line =>
      //Assume line is of the following form:
      //Q ID	T/F question	Focus	URL	Sentence	Supporting (0-2)?	Necessary rewrite
      //Example
      //44	Is it true that sleet, rain, snow, and hail are forms of precipitation? 	precipitation	http://ww2010.atmos.uiuc.edu/%28Gh%29/guides/mtr/cld/prcp/home.rxml	Precipitation occurs in a variety of forms; hail, rain, freezing rain, sleet or snow.	2
      val splits = line.split("\t")
      QuestionSentence(splits(0), splits(1), splits(2), splits(3), splits(4), splits(5).toInt)
  }.toList

  logger.info("Computing sentence features")
  val featureMap = questionSentences.map {
    questionSentence => (questionSentence -> features(questionSentence))
  }.toMap

  logger.info(s"Writing output ARFF to file $configArffTrain")
  toARFF(configArffTrain)

  logger.info("Invoking the classifier")
  invokeClassifier(configClassifierName, configArffTrain, configArffValidationOpt)

  logger.info("Done!")

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
    questionSentences.foreach {
      questionSentence =>
        writer.println(featureMap(questionSentence).values.mkString(",") + "," + (questionSentence.annotation > 0))
    }
    writer.close()
  }

  def invokeClassifier(classifierName: String, arffTrain: String, arffValidationOpt: Option[String]) = {
    logger.info(s"WEKA: reading training data from $arffTrain")
    val sourceTrain: DataSource = new DataSource(arffTrain)
    val dataTrain: Instances = sourceTrain.getDataSet
    if (dataTrain.classIndex == -1)
      dataTrain.setClassIndex(dataTrain.numAttributes - 1)

    val dataValidationOpt = arffValidationOpt map {
      arffValidation =>
        logger.info(s"WEKA: reading validation data from $arffValidation")
        val sourceValidation: DataSource = new DataSource(arffValidation)
        val dataValidation: Instances = sourceValidation.getDataSet
        if (dataValidation.classIndex == -1)
          dataValidation.setClassIndex(dataValidation.numAttributes - 1)
        dataValidation
    }

    logger.info(s"WEKA: creating classifier $classifierName")
    val classifier: Classifier = classifierName match {
      case "J48" => new J48()
      case "RandomForest" => new RandomForest()
      case "DecisionTable" => new DecisionTable()
      case "REPTree" => new REPTree()
      case "Logistic" => new Logistic()
      case "SMO" => new SMO()
      case "NaiveBayes" => new NaiveBayes()
      case "JRip" => new JRip()
      //case "IBk" => new IBk()
      case "RBFNetwork" => new RBFNetwork()
      case "RotationForest" => new RotationForest()
      case "ConjunctiveRule" => new ConjunctiveRule()
      case "RandomCommittee" => new RandomCommittee()
      case "LibSVM" => new LibSVM()
    }

    val eval: Evaluation = new Evaluation(dataTrain)

    dataValidationOpt match {
      case Some(dataValidation: Instances) =>
        logger.info(s"WEKA: training the classifier on $arffTrain")
        classifier.buildClassifier(dataTrain)
        logger.info(s"WEKA: validating the classifier on $arffValidationOpt")
        eval.evaluateModel(classifier, dataValidation)
      case _ =>
        val nfolds: Int = 10
        logger.info(s"WEKA: training AND ${nfolds}-fold cross validating the classifier on $arffTrain")
        val random: Random = new Random(42)
        eval.crossValidateModel(classifier, dataTrain, 10, random)
    }
    logger.info(eval.toSummaryString("\n======== RESULTS ========\n", false))
    //logger.info(s"\nf-measure = ${eval.fMeasure(0).toString}")
  }
}
