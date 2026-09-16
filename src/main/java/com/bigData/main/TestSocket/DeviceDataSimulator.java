package com.bigData.main.TestSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * 设备数据上报客户端（模拟真实设备通过 TCP Socket 上报数据）
 * <p>
 * 可直接运行本类的 main 方法，模拟一台设备连接采集服务端并持续上报车辆数据：
 * java -cp ... com.bigData.main.TestSocket.DeviceDataSimulator 127.0.0.1 9999 10 5
 * 参数含义：host port 上报秒数 每秒条数
 * <p>
 * 上报协议：文件名行(\n) -> CSV 表头 -> 数据行 -> END_OF_FILE
 */
public class DeviceDataSimulator {

    private static final Logger logger = LoggerFactory.getLogger(DeviceDataSimulator.class);

    private static final byte[] END_OF_FILE = "END_OF_FILE".getBytes(StandardCharsets.UTF_8);

    private static final String[] BRANDS = {"Toyota", "Honda", "Ford", "BMW", "Mercedes", "Audi", "Tesla", "Volkswagen"};
    private static final String[] MODELS = {
            "Camry", "Accord", "F-150", "3 Series", "C-Class", "A4", "Model 3", "Golf",
            "Corolla", "Civic", "Mustang", "5 Series", "E-Class", "Q5", "Model S", "Passat"
    };
    private static final String[] FUEL_TYPES = {"Gasoline", "Diesel", "Electric", "Hybrid"};
    private static final String CSV_HEADER =
            "ID,Brand,Model,Year,Horsepower,Torque,FuelType,Length(mm),Width(mm),Height(mm)";

    /**
     * 通过 TCP 向采集服务端上报数据
     *
     * @param host        服务端地址
     * @param port        服务端端口
     * @param durationSec 上报持续秒数（每秒上报 rowsPerSec 条）
     * @param rowsPerSec  每秒上报条数
     * @param fileName    上报的文件名
     * @return 上报结果描述（含服务端回执）
     */
    public static String send(String host, int port, int durationSec, int rowsPerSec, String fileName) throws Exception {
        Random random = new Random();
        int totalRows = Math.max(durationSec, 1) * Math.max(rowsPerSec, 1);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(30000);
            logger.info("【设备端】已连接采集服务端 {}:{}，准备上报 {} 条数据", host, port, totalRows);

            try (OutputStream out = socket.getOutputStream();
                 BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {

                // 1. 文件名
                out.write((fileName + "\n").getBytes(StandardCharsets.UTF_8));

                // 2. 表头
                out.write((CSV_HEADER + "\n").getBytes(StandardCharsets.UTF_8));

                // 3. 数据行：按秒分批上报，模拟真实设备的实时数据流
                StringBuilder batch = new StringBuilder();
                for (int i = 0; i < totalRows; i++) {
                    batch.append(buildRow(random)).append('\n');
                    boolean lastRow = (i == totalRows - 1);
                    boolean secFinished = ((i + 1) % Math.max(rowsPerSec, 1) == 0);
                    if (lastRow || secFinished) {
                        out.write(batch.toString().getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        batch.setLength(0);
                        if (!lastRow) {
                            Thread.sleep(1000L);
                        }
                    }
                }

                // 4. 结束标记
                out.write(END_OF_FILE);
                out.flush();

                // 5. 读取服务端回执
                byte[] ackBuffer = new byte[256];
                int len = in.read(ackBuffer);
                String ack = len > 0 ? new String(ackBuffer, 0, len, StandardCharsets.UTF_8) : "";
                logger.info("【设备端】上报完成，服务端回执: {}", ack);
                return ack;
            }
        }
    }

    private static String buildRow(Random random) {
        return String.format("%d,%s,%s,%d,%d,%d,%s,%d,%d,%d",
                System.nanoTime() % 1000000,
                BRANDS[random.nextInt(BRANDS.length)],
                MODELS[random.nextInt(MODELS.length)],
                2010 + random.nextInt(14),
                100 + random.nextInt(500),
                150 + random.nextInt(450),
                FUEL_TYPES[random.nextInt(FUEL_TYPES.length)],
                4000 + random.nextInt(3000),
                1600 + random.nextInt(600),
                1400 + random.nextInt(600));
    }

    /**
     * 独立运行：模拟一台设备向采集服务端上报数据
     * 用法：java com.bigData.main.TestSocket.DeviceDataSimulator [host] [port] [秒数] [每秒条数]
     */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9999;
        int durationSec = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        int rowsPerSec = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        String fileName = "socket_device_" + System.currentTimeMillis() + ".csv";

        try {
            String ack = send(host, port, durationSec, rowsPerSec, fileName);
            System.out.println("上报完成，服务端回执: " + ack);
        } catch (Exception e) {
            System.err.println("上报失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
