package org.allenai.ari.sentences

import java.io.{ File, PrintWriter }
import java.util.Random

import org.allenai.ari.solvers.inference.matching.{ EntailmentService, EntailmentWrapper }
import org.allenai.ari.solvers.utils.Tokenizer
import org.allenai.common.Logging
import weka.classifiers.{ Classifier, Evaluation }
import weka.classifiers.bayes._
import weka.classifiers.functions._
import weka.classifiers.meta._
import weka.classifiers.rules._
import weka.classifiers.trees._
import weka.core.Instances
import weka.core.converters.ConverterUtils.DataSource

import scala.io.Source
import scala.collection.immutable.IndexedSeq

/** *************************************
  * AUTHORS: Ashish, Niranjan
  *
  * EXAMPLE: run-main org.allenai.ari.sentences.SentenceClassifier RotationForest src/main/resources/labeled-data-train.tsv questions/manual-12063 output
  *
  */

object SentenceClassifier extends App with Logging {
  logger.info("Parsing commandline arguments")
  assert(args.size == 4, " classifier trainingfile inputdir outputdir needed")
  val configClassifierName = args(0)
  val configTrainingFile = args(1)
  val inputDirectory = args(2)
  val outputDirectory = args(3)
  val arffDir = "arff"
  val configArffTrain = arffDir + File.separator + "train.arff"

  val configEntailmentUrl = "http://entailment.dev.allenai.org:8191/api/entails"

  logger.info(s"Extracting training question+sentences from $configTrainingFile")
  val questionSentencesTrain = QuestionSentence.fromTrainingFile(configTrainingFile, 1)
  logger.info("Computing training sentence features")
  val featureMapTrain = questionSentencesTrain.map {
    questionSentence => (questionSentence, features(questionSentence))
  }.toMap

  logger.info(s"Writing training ARFF to file $configArffTrain")
  toARFF(questionSentencesTrain, featureMapTrain, configArffTrain)

  val classifier: Classifier = buildClassifier(configClassifierName, configArffTrain)

  classify(classifier, inputDirectory, outputDirectory)

  System.exit(0)

  def toInstances(file: File, arffDir: String): (Instances, Seq[QuestionSentence]) = {
    logger.info(s"Extracting test question+sentences from $file")
    //val questionSentences: List[QuestionSentence] = QuestionSentence.fromTrainingFile(file.getAbsolutePath, 0)
    //val questionSentences: List[QuestionSentence] = QuestionSentence.fromFileWithSids(file.getAbsolutePath, 0)
    val questionSentences: List[QuestionSentence] = QuestionSentence.fromFileWithSidsB(file.getAbsolutePath, 0)
    val featureMap: Map[QuestionSentence, Seq[Double]] = (questionSentences map {
      questionSentence =>
        (questionSentence, features(questionSentence))
    }).toMap
    val arffFile = arffDir + File.separator + file.getName + ".arff"
    toARFF(questionSentences, featureMap, arffFile)
    logger.info(s"WEKA: reading test data from $arffFile")
    val sourceTest: DataSource = new DataSource(arffFile)
    val dataTest: Instances = sourceTest.getDataSet
    if (dataTest.classIndex == -1)
      dataTest.setClassIndex(dataTest.numAttributes - 1)
    (dataTest, questionSentences)
  }

  def classify(classifier: Classifier, testInstances: Instances): Seq[Double] = {
    logger.info(s"WEKA: scoring -- computing class probability distribution for ${testInstances.numInstances} test instances")
    (0 to testInstances.numInstances - 1).map { i =>
      try {
        classifier.distributionForInstance(testInstances.instance(i))(0)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          println(s"Caught exception classifying test instance $i")
          -1d
      }
    }
  }

  def classify(classifier: Classifier, inputDirectory: String, outputDirectory: String): Unit = {
    val files = {
      new File(inputDirectory).listFiles().filter(_.getName.endsWith(".txt"))
    }

    files.take(1).foreach {
      file =>
        try {
          logger.info(s"Processing ${file.getName}")
          val outputFile = outputDirectory + File.separator + file.getName
          val writer = new PrintWriter(outputFile, "utf-8")
          writer.println(QuestionSentence.header)
          toInstances(file, arffDir) match {
            case (testInstances, testQuestionSentences) =>
              val classProbabilities = classify(classifier, testInstances)
              logger.info(s"WEKA: Class probabilities ${classProbabilities.size}, instances ${testInstances.numInstances}, and testQuestionSentences ${testQuestionSentences.size}")
              // sort test instances based on the predicted class probability (high to low)
              val testQuestionSentencesWithClassProb = (testQuestionSentences zip classProbabilities).sortBy(-_._2)
              testQuestionSentencesWithClassProb foreach {
                x => writer.println(f"${x._1.toString}\t${x._2}%.4f")
              }
              // compute average precision if the data is annotated
              var numPositiveExamples: Int = 0
              var numAnnotatedExamples: Int = 0
              var averagePrecisionAnnotated: Double = 0d // average precision over annotated examples
              var averagePrecisionAll: Double = 0d // average precision over all examples
              (0 to testQuestionSentencesWithClassProb.size - 1) map {
                i =>
                  val annotation = testQuestionSentencesWithClassProb(i)._1.annotationOpt.getOrElse(-1)
                  if (annotation >= 0)
                    numAnnotatedExamples += 1
                  if (annotation > 0) { // good = 1 or 2
                    numPositiveExamples += 1
                    averagePrecisionAnnotated += (numPositiveExamples.toDouble / numAnnotatedExamples * 100d)
                    averagePrecisionAll += (numPositiveExamples.toDouble / (i + 1) * 100d)
                  }
              }
              averagePrecisionAnnotated /= numPositiveExamples
              averagePrecisionAll /= numPositiveExamples
              logger.info(s"${numPositiveExamples} positive examples out of ${numAnnotatedExamples} annotated test instances out of a total of ${testQuestionSentencesWithClassProb.size}")
              logger.info(f"AVERAGE PRECISION (annotated examples) = ${averagePrecisionAnnotated}%.4f")
              logger.info(f"AVERAGE PRECISION (all examples) = ${averagePrecisionAll}%.4f")
            case _ =>
              logger.info("")
          }
          writer.close()
        } catch {
          case e: Exception =>
            e.printStackTrace()
            println(s"Caught exception processing input file ${file.getName}")
        }
    }
  }

  def features(questionSentence: QuestionSentence) = {
    import SimilarityMeasures._

    val questionKeywordsSet = Tokenizer.toKeywords(questionSentence.question).toSet
    val sentenceKeywordsSet = Tokenizer.toKeywords(questionSentence.sentence).toSet

    var features = Seq[Double]()
    // number of words in the sentence
    features :+= Math.log(questionSentence.sentence.split("\\s+").size.toDouble)
    // hypothesis coverage (absolute): number of question words that overlap with the sentence
    features :+= overlap(sentenceKeywordsSet, questionKeywordsSet).toDouble
    // hypothesis coverage (relative): fraction of question words that overlap with the sentence
    features :+= overlap(sentenceKeywordsSet, questionKeywordsSet).toDouble / questionKeywordsSet.size.toDouble
    // question to sentence wordnet entailment
    features :+= wordnetEntailment(questionSentence.sentence, questionSentence.question)
    // focus to sentence wordnet entailment
    features :+= wordnetEntailment(questionSentence.sentence, questionSentence.focus)
    // tfidf between sentence and question
    features :+= tfIdf(sentenceKeywordsSet, questionKeywordsSet)

    features
  }

  def toARFF(questionSentences: List[QuestionSentence], featureMap: Map[QuestionSentence, Seq[Double]], arffFile: String) = {
    val writer = new PrintWriter(arffFile)
    // add ARFF header
    writer.println("@relation SENTENCE_SELECTOR")
    writer.println("  @attribute sentence-length       numeric         % length of the sentence")
    writer.println("  @attribute word-overlap-num      numeric         % hypothesis coverage, absolute")
    writer.println("  @attribute word-overlap-frac     numeric         % hypothesis coverage, relative")
    writer.println("  @attribute question-ent-wordnet  numeric         % wordnet entailment for the entire question")
    writer.println("  @attribute focus-ent-wordnet     numeric         % wordnet entailment for the focus")
    writer.println("  @attribute tf-idf                numeric         % TF-IDF score for the question")
    writer.println("  @attribute class                 {1,0}           % BINARY LABEL: whether the sentence supports the question")
    writer.println("")
    // add ARFF data
    writer.println("@data")
    questionSentences.foreach {
      questionSentence =>
        val annotation = questionSentence.annotationOpt match {
          case Some(label: Int) => if (label > 0) "1" else "0"
          case None => "?"
        }
        writer.println(featureMap(questionSentence).mkString(",") + "," + annotation)
    }
    writer.close()
  }

  def buildClassifier(classifierName: String, arffTrain: String) = {
    logger.info(s"WEKA: reading training data from $arffTrain")
    val sourceTrain: DataSource = new DataSource(arffTrain)
    val dataTrain: Instances = sourceTrain.getDataSet
    if (dataTrain.classIndex == -1)
      dataTrain.setClassIndex(dataTrain.numAttributes - 1)
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
    logger.info(s"WEKA: training the classifier on $arffTrain")
    classifier.buildClassifier(dataTrain)
    classifier
  }
  def invokeClassifier(classifierName: String, arffTrain: String, arffValidationOpt: Option[String], questionSentencesValidationOpt: Option[List[QuestionSentence]]) = {
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

    val classProbabilitiesOpt: Option[Seq[Array[Double]]] = dataValidationOpt match {
      case Some(dataValidation: Instances) =>
        logger.info(s"WEKA: training the classifier on $arffTrain")
        classifier.buildClassifier(dataTrain)
        logger.info(s"WEKA: validating the classifier on $arffValidationOpt")
        eval.evaluateModel(classifier, dataValidation)

        logger.info("WARNING: duplicating the evaluation for now")
        logger.info(s"WEKA: extracting class probability distribution for each validation instance")
        val classProbabilities = (0 to dataValidation.numInstances - 1).map {
          i => classifier.distributionForInstance(dataValidation.instance(i))
        }
        Option(classProbabilities)
      case _ =>
        val nfolds: Int = 10
        logger.info(s"WEKA: training AND ${nfolds}-fold cross validating the classifier on $arffTrain")
        val random: Random = new Random(42)
        eval.crossValidateModel(classifier, dataTrain, 10, random)
        Option(null)
    }
    logger.info(eval.toSummaryString("\n======== RESULTS ========\n", false))
    //logger.info(s"\nf-measure = ${eval.fMeasure(0).toString}")

    classProbabilitiesOpt
  }
}
