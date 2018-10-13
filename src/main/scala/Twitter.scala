import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.ml.PipelineModel
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.twitter._
import org.apache.spark.streaming.{Seconds, StreamingContext}
import scopt.OptionParser

object Twitter {

  case class Config(consumerKey: String = "", consumerSecret: String = "",
                    accessToken: String = "", accessTokenSecret: String = "",
                    modelInput: String = "", keyword: String = "")

  val parser: OptionParser[Config] = new scopt.OptionParser[Config]("Twitter Sentiment") {

    arg[String]("<consumer-key>") action { (value, conf) =>
      conf.copy(consumerKey = value)
    } text "twitter application consumer key"

    arg[String]("<consumer-secret>") action { (value, conf) =>
      conf.copy(consumerSecret = value)
    } text "twitter application consumer secret"

    arg[String]("<access-token>") action { (value, conf) =>
      conf.copy(accessToken = value)
    } text "twitter application access token"

    arg[String]("<access-token-secret>") action { (value, conf) =>
      conf.copy(accessTokenSecret = value)
    } text "twitter application access token secret"

    arg[String]("<model-input>") action { (value, conf) =>
      conf.copy(modelInput = value)
    } text "input path to built model"

    arg[String]("<keyword>") action { (value, conf) =>
      conf.copy(keyword = value)
    } text "keyword in twitter steam"

  }

  def main(args: Array[String]): Unit = {

    // Set logging level if log4j not configured (override by adding log4j.properties to classpath)
    if (!Logger.getRootLogger.getAllAppenders.hasMoreElements) {
      Logger.getRootLogger.setLevel(Level.ERROR)
    }
    Logger.getLogger("org").setLevel(Level.OFF)
    Logger.getLogger("akka").setLevel(Level.OFF)

    val config = parser.parse(args, Config()).get

    System.setProperty("twitter4j.oauth.consumerKey", config.consumerKey)
    System.setProperty("twitter4j.oauth.consumerSecret", config.consumerSecret)
    System.setProperty("twitter4j.oauth.accessToken", config.accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", config.accessTokenSecret)

    val sparkConf = new SparkConf().setAppName("Twitter Streaming Sentiment Application")
      .set("spark.ui.showConsoleProgress", "False")

    // check Spark configuration for master URL, set it to local if not configured
    if (!sparkConf.contains("spark.master")) {
      sparkConf.setMaster("local[*]")
    }

    val ssc = new StreamingContext(sparkConf, Seconds(10))

    implicit val spark: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()
    import spark.implicits._

    val stream = TwitterUtils.createStream(ssc, None)

    val tweets = stream.filter(status => {
      val contains =
        if (config.keyword == "*") true
        else status.getText.toLowerCase.contains(config.keyword.toLowerCase)

      contains && !status.isRetweet && status.getLang == "en"
    })

    val model: PipelineModel = PipelineModel.read.load(config.modelInput)

    val positiveSentimentAccumulator = ssc.sparkContext.longAccumulator("total tweets with positive sentiment")
    val negativeSentimentAccumulator = ssc.sparkContext.longAccumulator("total tweets with negative sentiment")

    // Print tweet text + sentiment
    tweets.map(_.getText).foreachRDD(x => {
      if (!x.isEmpty()) {
        println("==============")
        val result = model.transform(x.toDF().withColumnRenamed("value", "text"))
          .select("text", "prediction")
        result.collect().foreach(tweet => {
          if (tweet.getDouble(1) == 1.0) positiveSentimentAccumulator.add(1L) else negativeSentimentAccumulator.add(1L)
          println(tweet.getString(0) + " , " + tweet.getDouble(1))
        })
        println("==============")
        println()
        println(s"Positive Tweets: ${positiveSentimentAccumulator.count}")
        println(s"Negative Tweets: ${negativeSentimentAccumulator.count}")
        println()
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
