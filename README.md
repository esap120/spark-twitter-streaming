# Streaming Twitter Sentiment Analysis with Apache Spark

A Twitter sentiment prediction pipeline using Spark's MLlib with a structured streaming application that prints
tweets matching a specified keyword and the corresponding sentiment.

This code can be used as template for training a model for sentiment analysis, using Spark's ML Pipeline structure,
DStreams, streaming tweets in Spark, or all of the above.

## Running + Building
Note: This project is design to work with Spark 2.2.1 other versions may not be compatible.

The applications can be run via an IDE such as Intellij as long as you specifiy to include the provided dependencies 
(in this case Spark).

The applications can also be run by building a jar using the following command from the root of the project: 
```
sbt clean assembly
```
The jar can then be found under the `target` directory. 
To run make sure Spark 2.2.1 is installed (download [here](https://spark.apache.org/downloads.html)).

### Running the Training application
__Note__: You may need to increase driver memory to prevent heap space or garbage collection errors
The training application can be run via spark-submit as shown:
```
spark-submit --class Training twitter_streaming-assembly-1.0.jar <input-file> <output-path>
```
Where `<input-file>` is the Twitter corpus and `<output-path>` is the directory where you wish to save the fitted model.


### Running the Twitter Streaming application
```
spark-submit --class Twitter twitter_streaming-assembly-1.0.jar <consumer-key> <consumer-secret> <access-token> <access-token-secret> <model-input> <keyword>
```
Where `<consumer-key> <consumer-secret> <access-token> <access-token-secret>` are your Twitter application credentials 
([_look here for info on setting up credentials_](https://developer.twitter.com/en/docs/basics/authentication/guides/access-tokens.html)) 
and `<keyword>` is the keyword you wish to run sentiment predictions on, 
passing a wildcard/asterisk (__*__) will display __all__ tweets. This application will print the tweets and their corresponding sentiment 
(`0.0` for negative and `1.0` for positive)


## Training
The model is built using [Spark's machine learning pipeline framework](https://spark.apache.org/docs/latest/ml-pipeline.html) 
which easily allows for the chaining of multiple algorithms into a single pipeline. 

###  Pipeline
![PlantUML model](http://www.plantuml.com/plantuml/png/NP71Ri8m38RlVGehdFi0kqoiHKn8m910xEnHhrW4fqeSDZRnxal7OScKs_VRl_qxqw3eDPvj5Kui0teymniUJhrtKRhMMUTXylGmlgmY7B5p7omzlACDb2buKBwmNk6x0cZ-6RqUMMdapdVaxwongQYwLLNgLrPs0WiPei9aoVrH6nifUPaxcw6YWTgFIlq8PeC-4BJRlO7gZ-xeKw6rX1A79DmoQxDQmeO7iYnaoX5rIYI_xUv4mbzKU6wLpWLOFwUumMIaJK4LzdHGvmjXDwyNitbE6XRqQKJ1o8kBrZAiaIy43Lufc6BxbwZvPSgrvwomyyZ2qjyFbQ-FoN7bEhAZuvIaIr_-0m00)

#### Input
**Input Data**

The model was tested using the Twitter corpus from: <https://www.kaggle.com/kazanova/sentiment140>.
The data contains 1.6 million labeled tweets with a sentiment of either positive or negative (represented as 4.0 or 0.0 respectively).

**Parsing**

The data is read in as a CSV file and converted to a Spark DataFrame.

**Text Cleanup**

The data is cleaned before feature extraction using the follow processing:
  * Converting the xml/html encodings in the data to the appropriate symbols
  * Stripping Twitter handles
  * Stripping URLs
  * Converting happy emojis to a single word (_happy_emoji_)
  * Converting sad emojis to a single word (_sad_emoji)
  * Simplify text to lowercase
  * Simplify repeated characters (a common thing in microblogging). For example "happpyyyyy" is simplified to "happyy" since extra character can still indicate increased sentiment on a topic
  * Question marks and Exclamations are treated as their own words (_surrounded with space_) since they can provide additional context
  * Symbols are removed (hashtags, commas, semicolons, colons, quotes, and periods)
  * Extra white space is removed

#### ML Pipeline
**Tokenize**

The tokenizer breaks the text into individual words. A sentence such as `"I like to walk my dogs"` becomes:
`["I", "like", "to", "walk", "my", "dogs"]`

**Stem**

[Stemming](https://en.wikipedia.org/wiki/Stemming) is the process of reducing a word to it's root, i.e 'worked' becomes 'work' or 'awfully' becomes 'awful'.
This allows for more count frequency which can result in a more accurate sentiment since there will be likelihood 
of the same stem structure appearing in the corpus than just the words themselves.
The [spark-stemming](https://github.com/master/spark-stemming) package is used for this.

**N-Grams**

Using a simple bag-of-words approach generally results in poor accuracy, so [N-Grams](https://en.wikipedia.org/wiki/N-gram) are used.
For better accuracy a combination of uni-grams, bi-grams and tri-grams are used. To do this a series of NGram steps are
run against the stemmed words. An example would be `"I hate Mondays"` as n-grams would be: `["I", "hate", "Mondays"]` (uni-grams), 
`["I hate", "hate Mondays"]` (bi-grams),
and `["I hate Mondays"]`

**Count Vectorizers**

[Count vectorizers](https://spark.apache.org/docs/latest/ml-features.html#countvectorizer) allow for the conversion from
a collection of ngrams to vectors of counts. This is a crucial step in many machine learning algorithms known as [feature extraction](https://en.wikipedia.org/wiki/Feature_extraction).
As a vector of counts the data can be described more simply than as a series of ngram collections.

**TFIDF**

[TFIDF](https://en.wikipedia.org/wiki/Tf%E2%80%93idf) stands for term frequency–inverse document frequency, it is a method for reflecting
the importance of different terms in a corpus. This is very important in sentiment analysis since words with a vary high frequency such as 
"a", "to", "the", etc. can be over-emphasised despite how little information is carried with these words. TFIDF down-weights these features, 
which allows for better accuracy when predicting sentiment.

**Binarize**

Since we are using Bernoulli Naive Bayes for simplicity and performance the features must be representing as binary variables. The binarizer categories the features into one of two buckets.

**Bernoulli Naive Bayes**

[Bernoulli Naive Bayes](https://en.wikipedia.org/wiki/Naive_Bayes_classifier#Bernoulli_naive_Bayes) 
is a probabilistic model that uses Bayes's Theorem with the assumption of independence 
between features (in our case of tweets this generally applies). As a classifier naive bayes classifiers scale 
very well and can quickly calculate predictions, useful not only for building the model but also running it against 
streaming tweets.

#### Accuracy
In testing, the model generally works with __~80%__ accuracy, while there is still room for this to be improved this is 
empirically considered to be near the upper bound for sentiment accuracy. Feel free to read more here:
  * [Understanding Sentiment Analysis and Sentiment Accuracy](https://blog.infegy.com/understanding-sentiment-analysis-and-sentiment-accuracy)
  * [Expert Analysis: Is Sentiment Analysis an 80% Solution?](https://www.informationweek.com/software/information-management/expert-analysis-is-sentiment-analysis-an-80-solution/d/d-id/1087919)
  * [Sentiment Analysis: Why It's Never 100% Accurate](https://brnrd.me/posts/sentiment-analysis-never-accurate)


## Twitter Streaming Application
This application allows for the streamed tweets to be predicted on with the model built from the training application.
To stream the tweets the [streaming-twitter](https://github.com/apache/bahir/tree/master/streaming-twitter) library is used from 
[Apache Bahir](http://bahir.apache.org/).

The code is fairly simple:
```
val window = tweets.window(Seconds(10))

val model: PipelineModel = PipelineModel.read.load(config.modelInput)

// Print tweet text + sentiment
tweets.map(_.getText).foreachRDD(x => {
  if (!x.isEmpty()) {
    val result = model.transform(x.toDF().withColumnRenamed("value", "text"))
      .select("text", "prediction")
    result.collect().foreach(println(_))
  }
})
```

Tweets are micro batched at 10 second intervals where the DStream is then converted into it's RDDs. 
From here each individual RDD is converted to a DataFrame and passed to the model for transformation. We can use the 
pipeline to not only predict but also featurize our input data the same way as our training data. The tweets are then
printed with the corresponding sentiment, as long as there were tweets in the batch (depending on your keyword you
may not get tweets every batch interval).

### Improvements

There are a number of improvements that can still be made, please feel free to open an issue or create a PR 

Some possible improvements are:
  * Better Emoji processing
  * More experimentation with different classification algorithms (deep learning, vector machines, etc.)
  * Improvement to feature creation using [word embedding](https://en.wikipedia.org/wiki/Word_embedding)
  * More robust Twitter streaming app (more detailed analysis on streamed data, saving data, etc.)
