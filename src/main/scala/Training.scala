import org.apache.spark.SparkConf
import org.apache.spark.ml.classification.NaiveBayes
import org.apache.spark.ml.feature._
import org.apache.spark.ml.{Pipeline, PipelineModel, PipelineStage}
import org.apache.spark.sql.{DataFrame, SparkSession}
import scopt.OptionParser

import scala.collection.mutable
import scala.util.Random

object Training {

  case class InputData(label: Double, text: String)
  case class Result(label: Double, text: String, prediction: Double)

  case class Config(inputData: String = "", outputPath: String = "")

  val parser: OptionParser[Config] = new scopt.OptionParser[Config]("Twitter Training") {

    arg[String]("<input-file>") action { (value, conf) =>
      conf.copy(inputData = value)
    } text "location of input file"

    arg[String]("<output-path>") action { (value, conf) =>
      conf.copy(outputPath = value)
    } text "output path of trained model"

  }

  def main(args: Array[String]): Unit = {

    val config = parser.parse(args, Config()).get

    val sparkConf = new SparkConf().setAppName("Twitter Training Application")

    // check Spark configuration for master URL, set it to local if not configured
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[*]")
    }

    implicit val spark: SparkSession = SparkSession.builder().master("local[*]").getOrCreate()

    import spark.implicits._

    // Data input: https://www.kaggle.com/kazanova/sentiment140
    val Array(inputData, testData) = spark.read
      .option("delimiter",",")
      .option("quote", "\"")
      .option("escape", "\"")
      .csv(config.inputData)
      .map(line => InputData(targetToLabel(line.getString(0).toInt), textCleanup(line.getString(5))))
      .withColumnRenamed("target", "label")
      .randomSplit(Array(0.8, 0.2), new Random(System.currentTimeMillis()).nextLong())

    val model = buildModel(inputData)

    testModel(model, testData)

    model.write.overwrite.save(config.outputPath)

  }

  def textCleanup(line: String): String = {

    line
      // Handle xml/html encoding
      .replaceAll("&lt;", "<")
      .replaceAll("&gt;", ">")
      .replaceAll("&amp;", " and ")
      .replaceAll("&quot;", "")
      // Strip twitter handles
      .replaceAll("\\s?@\\w{1,15}\\s?", "")
      // Strip urls
      .replaceAll("((http[s]?|ftp):\\/)?\\/?([^:\\/\\s]+)((\\/\\w+)*\\/)([\\w\\-\\.]+[^#?\\s]+)(.*)?(#[\\w\\-]+)?", "")
      // Happy emoji conversion
      .replaceAll("(\\:\\)|\\:D|;\\)|\\:-D|\\:\\-\\)|;\\-\\)|=\\)|=D|<3)", "_happy_emoji_")
      // Sad emoji conversion
      .replaceAll("(\\:\\(|D\\:|\\);|D\\-\\:|\\)\\-\\:|\\)\\-\\;|=\\(|D=|\\:'\\(|='\\()", "_sad_emoji_")
      .toLowerCase
      // Simplify repeated characters
      .replaceAll("(.)\\1{1,}", "$1$1")
      // Strip punctuation + hashtags
      .replaceAll("[,.:;#\"]", "")
      // Treat exclamations and question marks as words
      .replaceAll("!", " ! ")
      .replaceAll("\\?", " ? ")
      // Remove extra white space
      .replaceAll("\\s+", " ")
  }

  def targetToLabel(target: Int): Double = {
    if (target == 4) 1.0 else 0.0
  }

  def buildModel(trainingData: DataFrame)(implicit spark: SparkSession): PipelineModel = {

    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("words")

    val stemmer = new WritableStemmer().setInputCol("words").setOutputCol("stems").setLanguage("English")

    val ngrams = Array(new NGram().setN(1).setInputCol("stems").setOutputCol("uni_grams"),
      new NGram().setN(2).setInputCol("stems").setOutputCol("bi_grams"),
      new NGram().setN(3).setInputCol("stems").setOutputCol("tri_grams")
    )

    val countVectorizers = Array(new CountVectorizer().setInputCol("uni_grams").setOutputCol("uni_tf"),
      new CountVectorizer().setInputCol("bi_grams").setOutputCol("bi_tf"),
      new CountVectorizer().setInputCol("tri_grams").setOutputCol("tri_tf")
    )

    val inverseDocumentFrequencies = Array(
      new IDF().setInputCol("uni_tf").setOutputCol("uni_tfidf"),
      new IDF().setInputCol("bi_tf").setOutputCol("bi_tfidf"),
      new IDF().setInputCol("tri_tf").setOutputCol("tri_tfidf")
    )

    val assembler = new VectorAssembler().setInputCols(Array("uni_tfidf", "bi_tfidf", "tri_tfidf")).setOutputCol("idf_features")

    val binarizer: Binarizer = new Binarizer()
      .setInputCol("idf_features")
      .setOutputCol("features")
      .setThreshold(0.5)

    val nb = new NaiveBayes().setSmoothing(0.1).setModelType("bernoulli")

    val stages = mutable.ArrayBuffer.empty[PipelineStage]
    stages.append(tokenizer, stemmer)
    stages.appendAll(ngrams)
    stages.appendAll(countVectorizers)
    stages.appendAll(inverseDocumentFrequencies)
    stages.append(assembler, binarizer, nb)

    val pipeline = new Pipeline().setStages(stages.toArray)

    pipeline.fit(trainingData)
  }

  def testModel(model: PipelineModel, testData: DataFrame)(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val results = model.transform(testData).select("label", "text", "prediction")
      .as[Result]
      .map(result => (result.label, result.text, result.prediction))


    val total = testData.count()
    val positive = testData.filter(_.getDouble(0) == 1.0).count()
    val negative = testData.filter(_.getDouble(0) == 0.0).count()

    val resultTotal = results.count()
    val resultCorrect = results.filter(result => result._1 == result._3)count()

    val resultPositive = results.filter(result => result._3 == 1.0)count()
    val resultNegative = results.filter(result => result._3 == 0.0)count()

    println("Test Data Total: "+ total)
    println("Test Data Positive: " + positive)
    println("Test Data Negative: " + negative)


    println("Results Total: " + resultTotal)
    println("Results Correct: " + resultCorrect + "(" + (resultCorrect.toDouble / resultTotal.toDouble) * 100.0 + ")")
    println("Results Positive: " + resultPositive)
    println("Results Negative: " + resultNegative)
  }
}
