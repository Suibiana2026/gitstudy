package com.bigData.main.controller.MySQL;

import com.bigData.main.pojo.BasicInfo;
import com.bigData.main.service.MySQL.BasicInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BasicInfoController {

    @Autowired
    private BasicInfoService basicInfoService;

    @GetMapping("/api/get_basic_info")
    public BasicInfo getBasicInfo() {
//        System.out.println("收到请求：/api/get_basic_info");  // 打印请求日志

        BasicInfo basicInfo = null;

        try {
            // 尝试从服务层获取数据
            basicInfo = basicInfoService.getBasicInfo();
//            System.out.println("从服务层获取到的数据： " + basicInfo);  // 打印从服务层返回的数据

            // 如果返回的对象为 null，说明未找到基本信息
            if (basicInfo == null) {
                System.out.println("未找到基本信息，返回 404 错误");  // 数据为空时的调试信息
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Basic info not found");
            }

        } catch (Exception e) {
            // 异常捕获并打印
            System.err.println("获取基本信息时发生异常: " + e.getMessage());  // 异常信息
            e.printStackTrace();  // 打印堆栈跟踪
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }

        return basicInfo;  // 返回基本信息
    }
}
