package com.bigData.main.service.MySQL;

import com.bigData.main.Repository.BasicInfoRepository;
import com.bigData.main.pojo.BasicInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class BasicInfoService {

    @Autowired
    private BasicInfoRepository basicInfoRepository;

    // 获取基本信息
    public BasicInfo getBasicInfo() {
        // 打印日志：服务层请求开始
//        System.out.println("开始从 BasicInfoRepository 获取基本信息...");

        BasicInfo basicInfo = null;

        try {
            // 这里假设只会有一条数据，因此根据 ID 获取
            Optional<BasicInfo> result = basicInfoRepository.findById(1L);
            if (result.isPresent()) {
                basicInfo = result.get();
                // 数据存在时打印日志
//                System.out.println("从数据库获取到的数据： " + basicInfo);
            } else {
                // 数据不存在时打印日志
                System.out.println("未找到对应 ID 的基本信息数据");
            }
        } catch (Exception e) {
            // 异常处理，打印错误日志
            System.err.println("从数据库获取基本信息时发生异常: " + e.getMessage());
            e.printStackTrace();  // 打印堆栈信息
        }

        // 如果返回为空，打印警告信息
        if (basicInfo == null) {
            System.out.println("基本信息为空，返回 null");
        }

        return basicInfo;
    }
}
