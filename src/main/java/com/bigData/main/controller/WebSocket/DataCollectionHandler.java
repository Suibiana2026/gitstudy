package com.bigData.main.controller.WebSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

@Component
public class DataCollectionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectionHandler.class);
    private final Random random = new Random();

    // 车辆品牌和型号数据
    private final String[] brands = {"Toyota", "Honda", "Ford", "BMW", "Mercedes", "Audi", "Tesla", "Volkswagen"};
    private final String[] models = {
            "Camry", "Accord", "F-150", "3 Series", "C-Class", "A4", "Model 3", "Golf",
            "Corolla", "Civic", "Mustang", "5 Series", "E-Class", "Q5", "Model S", "Passat"
    };
    private final String[] fuelTypes = {"Gasoline", "Diesel", "Electric", "Hybrid"};


    public String generateVehicleData() {
        String brand = brands[random.nextInt(brands.length)];
        String model = models[random.nextInt(models.length)];
        int year = 2010 + random.nextInt(14); // 2010-2023
        int horsepower = 100 + random.nextInt(500); // 100-600马力
        int torque = 150 + random.nextInt(450); // 150-600扭矩
        String fuelType = fuelTypes[random.nextInt(fuelTypes.length)];
        int length = 4000 + random.nextInt(3000); // 4000-7000mm
        int width = 1600 + random.nextInt(600); // 1600-2200mm
        int height = 1400 + random.nextInt(600); // 1400-2000mm

        return String.format("%s,%s,%s,%d,%d,%d,%s,%d,%d,%d",
                UUID.randomUUID().toString(),
                brand,
                model,
                year,
                horsepower,
                torque,
                fuelType,
                length,
                width,
                height
        );
    }


    public String generateCsvContent(int dataCount) {
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("ID,Brand,Model,Year,Horsepower,Torque,FuelType,Length(mm),Width(mm),Height(mm)\n");

        for (int i = 0; i < dataCount; i++) {
            csvContent.append(generateVehicleData()).append("\n");
        }

        return csvContent.toString();
    }

    public String generateFileName(String taskId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("WebSocket采集_%s_%s.csv", taskId, timestamp);
    }
}