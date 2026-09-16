package com.bigData.main.Test.Data;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class CarSalesDataGenerator {

    private static final String[] MODELS = {"Model X", "Model Y", "Model Z", "Model S", "Model E", "Model T", "Model Q"};
    private static final String[] CUSTOMER_TYPES = {"中小企业", "个人", "大企业", "政府"};
    private static final String[] REGIONS = {"北京", "上海", "广州", "深圳", "成都", "南京", "杭州"};
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        int totalRecords = 50000; // 生成 5 万条记录
        String outputFile = "car_sales_inserts.sql";

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("INSERT INTO car_sales (sale_date, model, sales_volume, customer_type, region, market_trend, inventory, revenue, profit_margin, marketing_cost) VALUES\n");

            for (int i = 0; i < totalRecords; i++) {
                String insertStatement = generateInsertStatement();
                writer.write(insertStatement);
                if (i < totalRecords - 1) {
                    writer.write(",\n");
                } else {
                    writer.write(";\n");
                }
            }

            System.out.println("SQL 文件已生成: " + outputFile);
            importToMySQL(outputFile);

        } catch (IOException e) {
            System.err.println("生成 SQL 文件失败: " + e.getMessage());
        }
    }

    private static String generateInsertStatement() {
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate saleDate = startDate.plusDays(RANDOM.nextInt(90)); // 2025年1月到3月
        String model = MODELS[RANDOM.nextInt(MODELS.length)];
        int salesVolume = 50 + RANDOM.nextInt(501); // 50-550
        String customerType = CUSTOMER_TYPES[RANDOM.nextInt(CUSTOMER_TYPES.length)];
        String region = REGIONS[RANDOM.nextInt(REGIONS.length)];
        double marketTrend = -2.0 + (RANDOM.nextDouble() * 17.0); // -2.0 到 15.0
        int inventory = 40 + RANDOM.nextInt(261); // 40-300
        double revenue = salesVolume * (3000 + RANDOM.nextDouble() * 2000); // 每单位收入 3000-5000
        double profitMargin = 14.0 + (RANDOM.nextDouble() * 12.0); // 14.0-26.0
        double marketingCost = 10000 + (RANDOM.nextDouble() * 50000); // 10000-60000

        return String.format("('%s', '%s', %d, '%s', '%s', %.1f, %d, %.2f, %.1f, %.2f)",
                saleDate.format(DATE_FORMATTER), model, salesVolume, customerType, region,
                marketTrend, inventory, revenue, profitMargin, marketingCost);
    }

    private static void importToMySQL(String sqlFile) {
        String url = "jdbc:mysql://localhost:3306/car_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        String username = "root";
        String password = "123456";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            // 批量插入数据
            String insertSQL = "INSERT INTO car_sales (sale_date, model, sales_volume, customer_type, region, " +
                    "market_trend, inventory, revenue, profit_margin, marketing_cost) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertSQL);

            LocalDate startDate = LocalDate.of(2025, 1, 1);
            for (int i = 0; i < 50000; i++) {
                LocalDate saleDate = startDate.plusDays(RANDOM.nextInt(90));
                String model = MODELS[RANDOM.nextInt(MODELS.length)];
                int salesVolume = 50 + RANDOM.nextInt(501);
                String customerType = CUSTOMER_TYPES[RANDOM.nextInt(CUSTOMER_TYPES.length)];
                String region = REGIONS[RANDOM.nextInt(REGIONS.length)];
                double marketTrend = -2.0 + (RANDOM.nextDouble() * 17.0);
                int inventory = 40 + RANDOM.nextInt(261);
                double revenue = salesVolume * (3000 + RANDOM.nextDouble() * 2000);
                double profitMargin = 14.0 + (RANDOM.nextDouble() * 12.0);
                double marketingCost = 10000 + (RANDOM.nextDouble() * 50000);

                pstmt.setString(1, saleDate.format(DATE_FORMATTER));
                pstmt.setString(2, model);
                pstmt.setInt(3, salesVolume);
                pstmt.setString(4, customerType);
                pstmt.setString(5, region);
                pstmt.setDouble(6, marketTrend);
                pstmt.setInt(7, inventory);
                pstmt.setDouble(8, revenue);
                pstmt.setDouble(9, profitMargin);
                pstmt.setDouble(10, marketingCost);

                pstmt.addBatch();

                if (i % 1000 == 0) {
                    pstmt.executeBatch();
                    System.out.println("已插入 " + i + " 条记录");
                }
            }
            pstmt.executeBatch();
            System.out.println("数据导入 MySQL 完成");

        } catch (SQLException e) {
            System.err.println("导入 MySQL 失败: " + e.getMessage());
        }
    }
}