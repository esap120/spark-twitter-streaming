import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}
import org.apache.spark.mllib.feature.Stemmer

class WritableStemmer (override val uid: String) extends Stemmer with DefaultParamsWritable {

  def this() = this(Identifiable.randomUID("writable_stemmer"))
}

object WritableStemmer extends DefaultParamsReadable[WritableStemmer] {

  override def load(path: String): WritableStemmer = super.load(path)
}

