import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

object LogTransform {
  def main(args: Array[String]): Unit = {
    // やるコト
    // - いらんカラムを削除
    // - 新しいカラムを追加(GSPロジックを当てているかどうかのbooleanカラムを追加)
    // - csvファイルにアウトプットする
    implicit val spark: SparkSession =
      SparkSession.builder().appName("logTransform").getOrCreate()
    import spark.implicits._

    // transformとactionの見分け方
    // transform: 既存のDataFrameから新しいDataFrameを作成する操作。actionが実行されるまで評価は遅延される
    // action   : DataFrameの実際の値を返す操作
    // => なので、メソッドの戻り値を見てDataFrameを返しているかどうかで判断できる

    // actionが呼び出されると、そこまで遅延評価されていた処理を順番に実行していく
    // この単位をjobと呼ぶ

    val impressionDs0 =
      spark.read
        .format("csv")
        .option("header", value = true) // csvの先頭行がheaderの場合にそれを明示的に教えてあげる
        .option("inferSchema", value = false)
        // csvファイルの型を推測をoffにする。offにすることでcsv読み込みもlazyになると思ったが
        // load操作もactionっぽい。loadのタイミングでjobが作成されているので！
        .load("/Users/kinsho/Desktop/impressionLog.csv")
        .as[ImpressionRawLog]

    // csvから読み込んだDataFrameをメモリにキャッシュする
    // クラスタ横断でメモリ上にキャッシュできる（各ノードがパーティションをメモリ上に保存する）
    // 実際にキャッシュされるタイミングは、actionが実行された時
    // なので、今回の場合で言うとimpressionDs3.write.save("/Users/kinsho/Desktop/log")のタイミング

    // 上記の場合では、キャッシュするタイミングが遅れるので、count() actionを明示的に指定するのが良くするらしい　impressionDs0.cache().count()　みたいに
    // 参考: https://stackoverflow.com/questions/44002128/when-are-cache-and-persist-executed-since-they-dont-seem-like-actions

    // actionを実行するたびに、dataを読み込む処理をするので、それを避けるためにキャッシュする！！！

    // cacheが有効活用されるのは、action操作が複数回される場合である・
    // なぜなら、キャッシュしていないとactionが呼ばれるたびに、データロードから行われることになる
    // impressionDs0.cache()

    // いらんカラムを削除
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

    val impressionDs1 = impressionDs0.drop(dropColumns: _*)

    // 新しいカラムを追加
    // nullの場合があるのでOptionに変換している
    val isGspUdf = udf((buckettype: String) => {
      Option(buckettype) match {
        case None            => false
        case Some(bucketStr) => bucketStr.contains("GSP")
      }
    })
    val impressionDs2: DataFrame =
      impressionDs1.withColumn("isGsp", isGspUdf(col("buckettype")))

    // datasetに変換する
    val impressionDs3: Dataset[ImpressionStructLog] =
      impressionDs2.as[ImpressionStructLog]

    // csvに吐き出す
    impressionDs3.write.save("/Users/kinsho/Desktop/log")

    spark.close()
  }
}

case class ImpressionRawLog(distributionid: String,
                            advertiserid: String,
                            distributionhourjst: String,
                            distributiondatetime: String,
                            requestid: String,
                            uid: String,
                            visitid: String,
                            uavalue: String,
                            uacategory: String,
                            ip: String,
                            referer: String,
                            devicetype: String,
                            documentid: String,
                            documenturl: String,
                            sitecode: String,
                            documenttitle: String,
                            documentcontent: String,
                            campaignid: String,
                            campaigntype: String,
                            publisherid: String,
                            publisherchannelid: String,
                            publisherchanneltype: String,
                            maxcpc: String,
                            bidcpc: String,
                            contractcpc: String,
                            finalscore: String,
                            originalscore: String,
                            distributionorder: String,
                            israndom: String,
                            jobchanneltype: String,
                            sourcerequestid: String,
                            systemid: String,
                            buckettype: String,
                            stanbybuckettype: String,
                            predictctr: String,
                            dt: String)

case class ImpressionDroppedLog(distributionid: String,
                            advertiserid: String,
                            distributionhourjst: String,
                            distributiondatetime: String,
                            requestid: String,
                            uid: String,
                            uavalue: String,
                            uacategory: String,
                            documentid: String,
                            documenturl: String,
                            sitecode: String,
                            documenttitle: String,
                            campaignid: String,
                            campaigntype: String,
                            publisherid: String,
                            publisherchannelid: String,
                            maxcpc: Long,
                            bidcpc: Long,
                            contractcpc: Long,
                            finalscore: String,
                            originalscore: Long,
                            distributionorder: String,
                            israndom: String,
                            jobchanneltype: String,
                            buckettype: String,
                            predictctr: String,
                            dt: String)


case class ImpressionStructLog(distributionid: String,
                               advertiserid: String,
                               distributionhourjst: String,
                               distributiondatetime: String,
                               requestid: String,
                               uid: String,
                               uavalue: String,
                               uacategory: String,
                               documentid: String,
                               documenturl: String,
                               sitecode: String,
                               documenttitle: String,
                               campaignid: String,
                               campaigntype: String,
                               publisherid: String,
                               publisherchannelid: String,
                               maxcpc: String,
                               bidcpc: String,
                               contractcpc: String,
                               finalscore: String,
                               originalscore: String,
                               distributionorder: String,
                               israndom: String,
                               jobchanneltype: String,
                               buckettype: String,
                               predictctr: String,
                               dt: String)
