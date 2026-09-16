/**
 * 大数据展示系统
 * 包裹量排名
 * GET 请求
 * url: /api/get_parcel_back
 */

package com.bigData.main.controller.MySQL;

import com.bigData.main.pojo.ParcelRack;
import com.bigData.main.service.MySQL.ParcelRackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ParcelRackController {

    @Autowired
    private ParcelRackService parcelBackService;

    @GetMapping("/api/get_parcel_back")
    public List<ParcelRack> getParcelBackData() {
        return parcelBackService.getAllParcelData();  // 返回所有数据
    }
}
