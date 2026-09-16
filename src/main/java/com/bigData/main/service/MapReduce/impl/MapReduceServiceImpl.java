package com.bigData.main.service.MapReduce.impl;
import com.bigData.main.service.MapReduce.MapReduceService;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

@Service
public class MapReduceServiceImpl implements MapReduceService {

    private static final Logger logger = LoggerFactory.getLogger(MapReduceServiceImpl.class);
    private static final String HDFS_URI = "hdfs://zyh120:8020";

    @Override
    public Map<String, Map<String, Integer>> wordFrequencyStatistics(String filePaths, String field) {
        try {
            Configuration conf = new Configuration();
            conf.set("field", field);

            // 验证输入路径
            String[] paths = filePaths.split(",");
            validateInputPaths(paths);

            Job job = Job.getInstance(conf, "word frequency statistics");
            job.setJarByClass(MapReduceServiceImpl.class);
            job.setMapperClass(WordCountMapper.class);
            job.setCombinerClass(WordCountReducer.class);
            job.setReducerClass(WordCountReducer.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(IntWritable.class);

            // 设置输入路径
            for (String path : paths) {
                FileInputFormat.addInputPath(job, new Path(path.trim()));
            }

            // 设置输出路径
// 在设置输出路径时也使用HDFS URI
            String outputPath = HDFS_URI + "/temp/wordcount_" + System.currentTimeMillis();
            FileOutputFormat.setOutputPath(job, new Path(outputPath));

            if (!job.waitForCompletion(true)) {
                throw new RuntimeException("MapReduce job failed");
            }

            // 读取结果并返回
            return readWordCountResults(outputPath);
        } catch (Exception e) {
            logger.error("Word frequency statistics failed", e);
            throw new RuntimeException("Word frequency statistics failed", e);
        }
    }

    @Override
    public String duplicationEliminating(String filePaths, String dedupFields) {
        try {
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", HDFS_URI); // 确保使用HDFS
            conf.set("dedupFields", dedupFields);

            Job job = Job.getInstance(conf, "duplication eliminating");
            job.setJarByClass(MapReduceServiceImpl.class);
            job.setMapperClass(DeduplicateMapper.class);
            job.setReducerClass(DeduplicateReducer.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);

            // 设置输入路径
            String[] paths = filePaths.split(",");
            for (String path : paths) {
                FileInputFormat.addInputPath(job, new Path(path.trim()));
            }

            // 设置输出路径
            String outputPath = HDFS_URI + "/processed_data/deduplicated_" + System.currentTimeMillis();
            FileOutputFormat.setOutputPath(job, new Path(outputPath));

            if (!job.waitForCompletion(true)) {
                throw new RuntimeException("MapReduce job failed");
            }

            return outputPath;
        } catch (Exception e) {
            logger.error("Duplication eliminating failed", e);
            throw new RuntimeException("Duplication eliminating failed", e);
        }
    }

    @Override
    public String saveProcessedFile(MultipartFile file, String operationType, String field) {
        try {
            Configuration conf = new Configuration();
            FileSystem fs = FileSystem.get(URI.create(HDFS_URI), conf);

            // 创建processed_data目录（如果不存在）
            Path processDir = new Path("/processed_data");
            if (!fs.exists(processDir)) {
                fs.mkdirs(processDir);
                logger.info("创建HDFS目录: {}", processDir);
            }

            // 生成有意义的文件名
            String fileName = String.format("%s_%s_%d.csv",
                    operationType,
                    field.replaceAll("[^a-zA-Z0-9]", "_"),
                    System.currentTimeMillis());
            Path filePath = new Path(processDir, fileName);

            // 写入文件到HDFS
            try (OutputStream os = fs.create(filePath)) {
                os.write(file.getBytes());
                logger.info("文件保存成功: {}", filePath);
            }

            return filePath.toString();
        } catch (Exception e) {
            logger.error("保存处理文件失败", e);
            throw new RuntimeException("保存处理文件失败", e);
        }
    }

    private Map<String, Map<String, Integer>> readWordCountResults(String outputPath) throws IOException {
        // 实现读取HDFS上MapReduce输出结果的逻辑
        // 这里简化为返回一个模拟结果
        Map<String, Map<String, Integer>> result = new HashMap<>();
        // 实际实现应该读取HDFS上的文件并解析
        return result;
    }

    // WordCount Mapper
    public static class WordCountMapper extends Mapper<Object, Text, Text, IntWritable> {
        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();
        private String field;

        @Override
        protected void setup(Context context) {
            field = context.getConfiguration().get("field");
        }

        @Override
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            // 实现根据指定字段进行词频统计的逻辑
            // 这里简化为简单的单词分割
            StringTokenizer itr = new StringTokenizer(value.toString());
            while (itr.hasMoreTokens()) {
                word.set(itr.nextToken());
                context.write(word, one);
            }
        }
    }

    // WordCount Reducer
    public static class WordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        private IntWritable result = new IntWritable();

        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int sum = 0;
            for (IntWritable val : values) {
                sum += val.get();
            }
            result.set(sum);
            context.write(key, result);
        }
    }

    // Deduplicate Mapper
    public static class DeduplicateMapper extends Mapper<Object, Text, Text, Text> {
        private Text keyFields = new Text();
        private String[] dedupFields;

        @Override
        protected void setup(Context context) {
            String fields = context.getConfiguration().get("dedupFields");
            dedupFields = fields.split(",");
        }

        @Override
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            // 实现根据指定字段生成去重键的逻辑
            // 这里简化为使用整行作为键
            keyFields.set(value.toString());
            context.write(keyFields, value);
        }
    }

    // Deduplicate Reducer
    public static class DeduplicateReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            // 只输出第一个值（去重）
            context.write(null, values.iterator().next());
        }
    }

    private void validateInputPaths(String[] paths) throws IOException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", HDFS_URI);
        FileSystem fs = FileSystem.get(conf);

        for (String path : paths) {
            Path hdfsPath = new Path(path.trim());
            if (!fs.exists(hdfsPath)) {
                throw new IOException("Input path does not exist in HDFS: " + path);
            }
        }
    }
}