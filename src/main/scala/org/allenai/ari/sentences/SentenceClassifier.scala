package org.allenai.ari.sentences

import java.io.{File, PrintWriter}
import java.util.Random

import org.allenai.ari.solvers.inference.matching.{EntailmentService, EntailmentWrapper}
import org.allenai.ari.solvers.utils.Tokenizer
import org.allenai.common.Logging
import weka.classifiers.{Classifier, Evaluation}
import weka.classifiers.bayes._
import weka.classifiers.functions._
import weka.classifiers.meta._
import weka.classifiers.rules._
import weka.classifiers.trees._
import weka.core.Instances
import weka.core.converters.ConverterUtils.DataSource

import scala.io.Source
import scala.collection.immutable.IndexedSeq


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

  val teService: EntailmentService = {
    val wrapper = new EntailmentWrapper(configEntailmentUrl)
    wrapper.PredefinedEntails orElse wrapper.CachedEntails
  }
  
  logger.info(s"Extracting training question+sentences from $configTrainingFile")
  val questionSentencesTrain = QuestionSentence.fromTrainingFile(configTrainingFile)
  logger.info("Computing training sentence features")
  val featureMapTrain = questionSentencesTrain.map {
    questionSentence => (questionSentence, features(questionSentence))
  }.toMap

  logger.info(s"Writing training ARFF to file $configArffTrain")
  toARFF(questionSentencesTrain, featureMapTrain, configArffTrain)
  
  val classifier: Classifier = buildClassifier(configClassifierName, configArffTrain)

  classify(classifier, inputDirectory, outputDirectory)

  System.exit(0)



  def classify(classifier: Classifier,
               testInstances: Instances): Seq[Double] = {
    logger.info(s"WEKA: extracting class probability distribution for each test instance")
    (0 to testInstances.numInstances-1).map {i =>
      classifier.distributionForInstance(testInstances.instance(i))(0)
    }
  }

  def toInstances(file: File, arffDir: String): (Instances, Seq[QuestionSentence]) = {
    logger.info(s"Extracting test question+sentences from $file")
    val questionSentences: List[QuestionSentence] = QuestionSentence.fromFileWithSids(file.getAbsolutePath)
    val featureMap: Map[QuestionSentence, Seq[Double]] = (questionSentences map {
      questionSentence =>
        (questionSentence, features(questionSentence))
    }).toMap
    val arffFile = arffDir + File.separator + file.getName + ".arff"
    toARFF(questionSentences, featureMap, arffFile)
    logger.info(s"WEKA: reading test data from $arffFile")
    val sourceValidation: DataSource = new DataSource(arffFile)
    (sourceValidation.getDataSet, questionSentences)
  }

  def classify(classifier: Classifier, inputDirectory: String, outputDirectory: String): Unit = {
    val files = {
      new File(inputDirectory).listFiles().filter(_.getName.endsWith(".txt"))
    }

    files.take(2).foreach {
      file =>
        try {
          logger.info(s"Processing ${file.getName()}")
          val outputFile = outputDirectory + File.separator + file.getName
          val writer = new PrintWriter(outputFile, "utf-8")
          writer.println(QuestionSentence.header)
          toInstances(file, arffDir) match {
            case (testInstances, testQuestionSentences) =>
              val classProbabilities = classify(classifier, testInstances)
              logger.info(s"Class probabilities ${classProbabilities.size}, instances ${testInstances.numInstances()} , and testQuestionSentences ${testQuestionSentences.size}")
              (testQuestionSentences zip classProbabilities) foreach {
                x => writer.println(s"${x._1.toString}\t${x._2}%.4f")
              }
            case _ =>
              logger.info("")
          }
          writer.close()
        } catch {
          case e: Exception => println(s"Caught exception processing input file ${file.getName()}")
        }
    }
  }



  def features(questionSentence: QuestionSentence) = {
    import SimilarityMeasures._
    var features = Seq[Double]()
    // number of words in the sentence
    features :+= Math.log(questionSentence.sentence.split("\\s+").size.toDouble)
    // number of question words that overlap with the sentence
    features :+= ashish_overlap(questionSentence.sentence, questionSentence.question, false)
    // fraction of question words that overlap with the sentence
    features :+= ashish_overlap(questionSentence.sentence, questionSentence.question, true)
    // sentence and question entailment
    features :+= (teService(questionSentence.sentence, questionSentence.question) map (_.confidence)).getOrElse(0.0)         
    // sentence and focus entailment
    features :+= (teService(questionSentence.sentence, questionSentence.focus) map (_.confidence)).getOrElse(0.0)         
    
    features
  }

  def toARFF(questionSentences: List[QuestionSentence], featureMap: Map[QuestionSentence, Seq[Double]], arffFile: String) = {
    val writer = new PrintWriter(arffFile)
    // add ARFF header
    writer.println("@relation SENTENCE_SELECTOR")
    writer.println("  @attribute sentence-length       numeric         % length of the sentence")
    writer.println("  @attribute word-overlap-num      numeric         % number of question words that overlap the sentence")
    writer.println("  @attribute word-overlap-frac     numeric         % fraction of question words that overlap the sentence")
    writer.println("  @attribute question-ent-wordnet  numeric         % wordnet entailment for entire question")
    writer.println("  @attribute focus-ent-wordnet     numeric         % wordnet entailment for focus")
    writer.println("  @attribute class                 {true,false}    % BINARY LABEL: whether the sentence supports the question")
    writer.println("")
    // add ARFF data
    writer.println("@data")
    questionSentences.foreach {
      questionSentence =>
        val annotation = questionSentence.annotationOpt match {
          case Some(label: Int) => label > 0
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
        val classProbabilities = (0 to dataValidation.numInstances-1).map { 
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
