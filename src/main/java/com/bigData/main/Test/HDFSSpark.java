package com.bigData.main.Test;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.PairFunction;
import scala.Tuple2;
import java.util.Arrays;
import java.util.Iterator;
import java.util.regex.Pattern;
public class HDFSSpark {
    private static final Pattern SPACE = Pattern.compile(" ");
    public static void main(String[] args) {
        // 初始化Spark配置并创建JavaSparkContext对象
        SparkConf conf = new SparkConf().setAppName("HdfsWordCount").setMaster("local"); // 如果是集群模式，这里可以设置成"spark://master:7077"等
        JavaSparkContext sc = new JavaSparkContext(conf);
        sc.hadoopConfiguration().set("fs.defaultFS", "hdfs://zyh120:8020"); // 设置HDFS地址
        sc.hadoopConfiguration().set("dfs.replication", "1"); // 设置HDFS复制因子，根据需要设置

        // 从HDFS读取文件路径，这里假设你的HDFS文件路径是"/user/hadoop/wordcount/input.txt"
        JavaRDD<String> input = sc.textFile("hdfs://zyh120:8020/cuihua/s.txt");
        JavaRDD<String> words = input.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public Iterator<String> call(String s) throws Exception {
                return Arrays.asList(SPACE.split(s)).iterator();
            }
        });
        JavaPairRDD<String, Integer> ones = words.mapToPair(new PairFunction<String, String, Integer>() {
            @Override
            public Tuple2<String, Integer> call(String s) throws Exception {
                return new Tuple2<>(s, 1);
            }
        });
        JavaPairRDD<String, Integer> counts = ones.reduceByKey((a, b) -> a + b); // 使用Scala风格的reduceByKey方法进行聚合操作
       // counts.saveAsTextFile("hdfs://192.168.150.129:9000/cuihua/output"); // 将结果保存到HDFS上指定的路径
        counts.saveAsTextFile("D://cuihua/output"); // 将结果保存到HDFS上指定的路径
        sc.close(); // 关闭SparkContext，释放资源


    }
}
