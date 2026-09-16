package com.bigData.main;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.bigData.main", "com.bigData.main.Config"})
public class applicationBoot {

    public static void main(String[] args) {
        SpringApplication.run(applicationBoot.class, args);
        System.out.println("SpringBoot启动!!!");
    }

    // TCP 数据采集服务端（Server）已通过 @Component + @PostConstruct 随应用自动启动，无需在此手动注册


//@Bean
//public CommandLineRunner run() {
//    return args -> {
//        EmailSender.sendEmail("1637753076@qq.com", "1137688936@qq.com", "smtp.qq.com", "agvkyvwhwlricaab"); // 修改为你的邮箱和密码
//        System.out.println("Email sent to notify project startup.");
//    };
//}

}