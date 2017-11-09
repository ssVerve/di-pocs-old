package x

import org.apache.hadoop.io.compress.GzipCodec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import scala.collection.mutable.ListBuffer
import org.apache.commons.logging.LogFactory
import org.apache.hadoop.fs.{Path, FileSystem}
import org.apache.hadoop.conf.Configuration

import org.apache.spark._
import org.apache.spark.streaming._
import org.apache.spark.storage.{StorageLevel, StreamBlockId}
import org.apache.spark.streaming.receiver.{BlockGenerator, BlockGeneratorListener}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver
import java.io.{BufferedReader, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets
import org.apache.spark.internal.Logging
import com.github.mitallast.nsq._

class CustomReceiver(host: String, port: Int, topic: String) extends Receiver[String](StorageLevel.MEMORY_AND_DISK_2) with Logging {
  import scala.concurrent.duration._

  var client:NSQClient = null
  
  def onStart() {
    client = NSQClient(new NSQLookupDefault(List(s"http://$host:$port")))
    val consumer = client.consumer(topic=this.topic) { msg =>
        log.error("received: {}", msg)
        store(new String(msg.data))
        msg.fin()
    }
  }

  def onStop() {
    client.close()
  }
}

object SparkStreaming extends App {{ // double braces fixes NPE https://issues.apache.org/jira/browse/SPARK-4170
  val sc = new SparkContext(new SparkConf())
  
//  val conf = new SparkConf().setMaster("local[2]").setAppName("NetworkWordCount")
  val ssc = new StreamingContext(sc, Seconds(1))
  
  // Create a DStream that will connect to hostname:port, like localhost:9999
  val lines = ssc.receiverStream(new CustomReceiver("10.63.237.25", 4161, "test-ss"))

  // Split each line into words
  val words = lines.flatMap(_.split(" "))
  
  val pairs = words.map(word => (word, 1))
  val wordCounts = pairs.reduceByKey(_ + _)
  
  // Print the first ten elements of each RDD generated in this DStream to the console
  wordCounts.print()  
  
  ssc.start()             // Start the computation
  
  ssc.stop(stopSparkContext=false)
//  ssc.awaitTermination()  // Wait for the computation to terminate
  
}}
