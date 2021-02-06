import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.sql.functions.col

object LogCalculation {
  //  - データを読み込み
  //  - いらんカラムを削除する
  //  - バケット毎の指標を計算する
  //  - csvに吐き出す
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("LogCalculation").getOrCreate()

    import spark.implicits._

    // ↓job1
    val ds0: Dataset[ImpressionRawLog] = spark.read.format("csv").option("header", value = true).load("/Users/kinsho/Desktop/impressionLog.csv").as[ImpressionRawLog]


    // ↓job2
    // いらんカラムを削除する
    val dropColumns = Seq(
      "visitid",
      "ip",
      "publisherchanneltype",
      "documentcontent",
      "devicetype",
      "referer",
      "stanbybuckettype",
      "sourcerequestid",
      "systemid"
    )
    val df1 = ds0.drop(dropColumns: _*)


    // バケットタイプ毎の指標を計算する

    // まず型変換
    val df2 = df1.withColumn("contractcpc", col("contractcpc").cast("long"))
    val df3 = df2.withColumn("maxcpc", col("maxcpc").cast("long"))
    val df4 = df3.withColumn("bidcpc", col("bidcpc").cast("long"))
    val df5 = df4.withColumn("originalScore", col("originalScore").cast("long"))

    // 上記変換かけたカラムのnullデータを0に変換する
    val df6: DataFrame = df5.na.fill(0L, Seq("contractcpc", "maxcpc", "bidcpc", "originalScore"))

    // バケット毎に分ける
    // まずバケットタイプがullでないもの（ちゃんとバケット振り分けされているデータ）に絞る
    val df7 = df6.filter(col("buckettype").isNotNull).as[ImpressionDroppedLog]
    // それから、buckettypeにバケット名以外が入っているログもあるので、除外する
    val df8 = df7.filter(_.buckettype.contains("STANBY"))

    // 一旦ここまでの処理でキャッシュする
    // いっぱい処理しているから、groupByのタイミングでキャッシュする
    // キャッシュしてなかったら、groupByの次のactionであるsave csvタイミングでもjob1から処理してしまうので効率悪い

    // ↑ここでキャッシュしても意味なさそう。
    // 結局このコードは、actionがloadとsaveの二つしかないので
    // saveが呼ばれたタイミングでキャッシュされるだけなので、意味ない！！
    // df8.cache()

    // ↓ここのタイミングで、spark内部でシャッフルが行われる
    // これは、groupByなどの処理は各ノードに分散しているデータを集計する必要があるから
    // で、さらに、ここまではランダムにデータがパーティション化されていたが、シャッフルされることで
    // グルーピングされた単位でデータが再分配される！！！

    // シャッフルは、ディスクIOなど処理が必要になるためコストかかる処理になっている
    val df9 = df8.groupBy("buckettype").agg(Map("contractcpc" -> "sum", "originalScore" -> "avg", "contractcpc" -> "avg", "maxcpc" -> "max", "maxcpc" -> "min", "distributionid" -> "count"))

    val fd10 = df9.withColumnRenamed("avg(contractcpc)", "avg_cpc")
    val df11 = fd10.withColumnRenamed("avg(originalScore)", "avg_originalScore")
    val df12 = df11.withColumnRenamed("min(maxcpc)", "min_cpc")
    val df13 = df12.withColumnRenamed("count(distributionid)", "cnt_imps")

    df13.write.save("/Users/kinsho/Desktop/log")

    spark.close()
  }
}
