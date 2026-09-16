package com.bigData.main.Test;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.Arrays;
public class TestSparkWord {
    public static void main(String[] args) {
        // 配置 Spark
        SparkConf conf = new SparkConf().setAppName("WordCount").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);
        // 读取文件
        JavaRDD<String> inputFile = sc.textFile("D://bigdata/data.txt");
        // 进行单词计数
        JavaPairRDD<String, Integer> wordCounts = inputFile
                .flatMap(line -> Arrays.asList(line.split(",")).iterator())
                .mapToPair(word -> new Tuple2<>(word, 1))
                .reduceByKey(Integer::sum);

        // 输出结果
        wordCounts.saveAsTextFile("D://output1");

        // 关闭上下文
        sc.close();

    }

}
