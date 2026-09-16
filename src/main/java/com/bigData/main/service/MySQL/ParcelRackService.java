package com.bigData.main.service.MySQL;

import com.bigData.main.Repository.ParcelRackRepository;
import com.bigData.main.pojo.ParcelRack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParcelRackService {

    @Autowired
    private ParcelRackRepository parcelBackRepository;

    public List<ParcelRack> getAllParcelData() {
        return parcelBackRepository.findAll();  // 获取所有数据
    }
}
