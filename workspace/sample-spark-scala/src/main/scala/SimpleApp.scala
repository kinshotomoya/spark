import org.apache.spark.sql.SparkSession

object SimpleApp {
  def main(args: Array[String]): Unit = {
    val logFile = "/Users/kinsho/workspace/spark-3.0.1/README.md"
    val spark = SparkSession.builder().appName("SimpleApp").getOrCreate()
    val logData = spark.read.textFile(logFile)
    val numA = logData.filter(_.contains("a")).count()
    val numB = logData.filter(_.contains("b")).count()
    println(s"numA: $numA, numB: $numB")
    spark.stop()
  }
}
