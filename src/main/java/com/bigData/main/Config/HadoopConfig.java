package com.bigData.main.Config;

import org.apache.hadoop.fs.FileSystem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

@Configuration
public class HadoopConfig {
    @Value("${hadoop.fs.defaultFS}")
    private String hdfsDefaultFS;
    @Bean
    public FileSystem fileSystem() throws IOException {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", hdfsDefaultFS);
        // 必须用 newInstance 而不是 get：
        // FileSystem.get() 返回的是按 (scheme, authority, user) 缓存的【全局单例】，
        // 任何别处拿到同一实例后 close 掉，都会让注入本 Bean 的所有 Controller 永久抛
        // "Filesystem closed"。newInstance 会带唯一缓存键创建独立实例，只归本应用使用。
        return FileSystem.newInstance(conf);
    }
    @Bean
    public org.apache.hadoop.conf.Configuration hadoopConfiguration() {
        org.apache.hadoop.conf.Configuration conf = new org.apache.hadoop.conf.Configuration();
        conf.set("fs.defaultFS", hdfsDefaultFS);
        return conf;
    }
}