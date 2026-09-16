package com.bigData.main.controller.MySQL;

import com.bigData.main.pojo.AutomobileBrandSalesRanking;
import com.bigData.main.service.MySQL.AutomobileBrandSalesRankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automobile_sales")
@CrossOrigin
public class AutomobileBrandSalesRankingController {

    @Autowired
    private AutomobileBrandSalesRankingService service;

    @GetMapping("/list")
    public List<AutomobileBrandSalesRanking> getAllBrands() {
        return service.getAllBrands();
    }
}
