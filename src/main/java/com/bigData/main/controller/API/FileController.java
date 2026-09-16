package com.bigData.main.controller.API;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@EnableWebSocket
public class FileController implements WebSocketConfigurer {

    private final Server server = new Server(); // 直接实例化 Server 类

    // 存储已连接的客户端信息
    private final Map<String, String> connectedClients = new ConcurrentHashMap<>();
    // 存储 WebSocket 会话
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> frontendSessions = new ConcurrentHashMap<>();

    // WebSocket 处理类
    public class FileWebSocketHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            String clientIp = session.getRemoteAddress().getAddress().getHostAddress();
            if (session.getUri().getPath().equals("/frontend")) {
                frontendSessions.put(clientIp, session);
                notifyConnectionUpdate(); // 通知前端更新连接列表
            } else {
                clientSessions.put(clientIp, session);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
            String clientIp = session.getRemoteAddress().getAddress().getHostAddress();
            if (frontendSessions.containsKey(clientIp)) {
                frontendSessions.remove(clientIp);
            } else if (clientSessions.containsKey(clientIp)) {
                clientSessions.remove(clientIp);
                connectedClients.remove(clientIp);
                notifyConnectionUpdate(); // 通知前端更新连接列表
            }
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            // 可根据需要处理客户端发送的消息
        }
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new FileWebSocketHandler(), "/websocket").setAllowedOrigins("*");
        registry.addHandler(new FileWebSocketHandler(), "/frontend").setAllowedOrigins("*");
    }

    // 主页（重定向到系统主页面，static 下无 Thymeleaf 模板）
    @GetMapping("/")
    public String index(Model model) {
        return "redirect:/IndexSystem.html";
    }

    // 客户端连接接口
    @PostMapping(value = "/connect", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String connect(@RequestParam("serverUrl") String serverUrl, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        boolean success = registerConnection(clientIp, serverUrl);
        if (success) {
            notifyConnectionUpdate(); // 通知前端更新连接列表
            return "连接成功";
        }
        return "连接失败";
    }

    // 获取已连接的客户端列表
    @GetMapping(value = "/getConnections", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public List<Map<String, String>> getConnections() {
        List<Map<String, String>> clients = new ArrayList<>();
        for (Map.Entry<String, String> entry : connectedClients.entrySet()) {
            Map<String, String> clientInfo = new HashMap<>();
            clientInfo.put("ip", entry.getKey());
            clients.add(clientInfo);
        }
        return clients;
    }

    // 客户端请求令牌
    @PostMapping(value = "/requestToken", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String requestToken(@RequestParam("request") String request, HttpServletRequest httpRequest) throws InterruptedException {
        String clientIp = httpRequest.getRemoteAddr();
        if ("111".equals(request)) {
            Thread.sleep(2000); // 模拟服务端2秒延迟
            notifyTokenRequest(clientIp); // 通知前端显示令牌请求
            return "等待服务端确认...";
        }
        return "无效请求";
    }

    // 服务端发送令牌给客户端
    @PostMapping(value = "/sendToken", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String sendToken(@RequestBody Map<String, String> request) {
        String clientIp = request.get("clientIp");
        String token = request.get("token");
        sendTokenToClient(clientIp, token); // 通过 WebSocket 发送令牌
        return "令牌已发送";
    }

    // 客户端发送文件
    @PostMapping(value = "/sendFile", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String sendFile(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String clientIp = request.getRemoteAddr();
        String fileName = file.getOriginalFilename();

        try {
            // 将 MultipartFile 保存为临时文件
            File tempFile = File.createTempFile("uploaded-", fileName);
            file.transferTo(tempFile);
            String filePath = tempFile.getAbsolutePath();

            // 调用 Server 的 saveFile 方法
            String result = server.saveFile(filePath);

            // 删除临时文件
            tempFile.delete();

            if ("文件保存成功".equals(result)) {
                notifyFileReceived(clientIp, fileName); // 通知前端文件接收
                return "文件发送成功: " + fileName;
            } else {
                return "文件发送失败: " + result;
            }
        } catch (IOException e) {
            return "文件发送失败: " + e.getMessage();
        }
    }

    // 辅助方法：注册客户端连接
    private boolean registerConnection(String clientIp, String serverUrl) {
        if (!connectedClients.containsKey(clientIp)) {
            connectedClients.put(clientIp, serverUrl);
            return true;
        }
        return false; // 已连接的客户端不再重复注册
    }

    // 辅助方法：通知前端更新连接列表
    private void notifyConnectionUpdate() {
        List<Map<String, String>> clients = getConnections();
        String message = String.format("{\"type\":\"connectionUpdate\",\"clients\":%s}",
                new org.json.JSONArray(clients).toString());
        broadcastToFrontend(message);
    }

    // 辅助方法：通知前端显示令牌请求
    private void notifyTokenRequest(String clientIp) {
        String message = String.format("{\"type\":\"tokenRequest\",\"clientIp\":\"%s\"}", clientIp);
        broadcastToFrontend(message);
    }

    // 辅助方法：发送令牌给客户端
    private void sendTokenToClient(String clientIp, String token) {
        WebSocketSession session = clientSessions.get(clientIp);
        if (session != null && session.isOpen()) {
            try {
                String message = String.format("{\"type\":\"token\",\"token\":\"%s\"}", token);
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 辅助方法：通知前端文件接收
    private void notifyFileReceived(String clientIp, String fileName) {
        String message = String.format("{\"type\":\"fileReceived\",\"clientIp\":\"%s\",\"fileName\":\"%s\"}",
                clientIp, fileName);
        broadcastToFrontend(message);
    }

    // 辅助方法：广播消息给前端
    private void broadcastToFrontend(String message) {
        for (WebSocketSession session : frontendSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

// 服务端核心逻辑类
class Server {
    public String saveFile(String filePath) {
        // 假设这里实现文件保存逻辑，例如保存到本地或 HDFS
        System.out.println("文件保存: " + filePath);
        return "文件保存成功";
    }
}