package com.bigData.main.Test.Data;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class RandomDataGenerator {
    private static final String[] NAMES = {"张伟", "王芳", "李娜", "赵强", "刘洋", "陈杰", "孙丽", "黄敏", "周涛", "吴军"};
    private static final String[] GENDERS = {"男", "女"};
    private static final String[] PROFESSIONS = {"工程师", "教师", "医生", "销售", "程序员", "司机", "厨师", "设计师", "公务员", "学生"};
    private static final String[] INCOME_LEVELS = {"3000-5000", "5000-10000", "10000-20000", "20000-50000", "50000以上"};
    private static final String[] FAMILY_STRUCTURES = {"单身", "已婚无子女", "已婚有子女", "大家庭"};
    private static final String[] CITIES = {"北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "重庆", "西安"};
    private static final String[] USER_TYPES = {"城市用户", "农村用户"};

    private static final Random random = new Random();

    public static void main(String[] args) {
        String txtFilePath = "D:/tmp/用户信息表.txt"; // 输出的txt文件路径
        String csvFilePath = "D:/tmp/用户信息表.csv"; // 输出的csv文件路径
        int dataCount = 1000; // 生成数据条数

        try (
                BufferedWriter txtWriter = new BufferedWriter(new FileWriter(txtFilePath));
                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFilePath))
        ) {
            // 写入CSV文件头
            csvWriter.write("姓名,年龄,性别,职业,收入水平,家庭结构,城市,用户类型,电话号码,邮箱");
            csvWriter.newLine();

            for (int i = 0; i < dataCount; i++) {
                String name = NAMES[random.nextInt(NAMES.length)];
                int age = random.nextInt(43) + 18; // 18-60岁
                String gender = GENDERS[random.nextInt(GENDERS.length)];
                String profession = PROFESSIONS[random.nextInt(PROFESSIONS.length)];
                String income = INCOME_LEVELS[random.nextInt(INCOME_LEVELS.length)];
                String family = FAMILY_STRUCTURES[random.nextInt(FAMILY_STRUCTURES.length)];
                String city = CITIES[random.nextInt(CITIES.length)];
                String userType = USER_TYPES[random.nextInt(USER_TYPES.length)];
                String phone = generatePhoneNumber();
                String email = generateEmail(name);

                // 组合数据行
                String line = String.join(",", name, String.valueOf(age), gender, profession, income, family, city, userType, phone, email);

                // 写入txt文件
                txtWriter.write(line);
                txtWriter.newLine();

                // 写入csv文件
                csvWriter.write(line);
                csvWriter.newLine();
            }
//
            System.out.println("数据生成完成，文件已保存到：");
            System.out.println("TXT 文件: " + txtFilePath);
            System.out.println("CSV 文件: " + csvFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 生成随机电话号码
    private static String generatePhoneNumber() {
        String[] prefixes = {"130", "131", "132", "133", "134", "135", "136", "137", "138", "139", "150", "151", "152", "153", "155", "156", "157", "158", "159"};
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String number = String.valueOf(random.nextInt(90000000) + 10000000); // 8位随机数字
        return prefix + number;
    }

    // 生成随机邮箱
    private static String generateEmail(String name) {
        String[] domains = {"@qq.com", "@163.com", "@gmail.com", "@outlook.com", "@sina.com", "@yahoo.com"};
        String domain = domains[random.nextInt(domains.length)];
        return pinyinFromChinese(name) + random.nextInt(1000) + domain;
    }

    // 简单转换中文姓名拼音（不精准，仅作示例）
    private static String pinyinFromChinese(String chineseName) {
        return chineseName.replaceAll("[张王李赵刘陈孙黄周吴]", "zhangwanglizhaoliuchenhuangzhouwu").toLowerCase().substring(0, 4);
    }
}
