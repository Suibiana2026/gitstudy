package com.bigData.main.service.MySQL;

import com.bigData.main.Repository.AutomobileBrandSalesRankingRepository;
import com.bigData.main.pojo.AutomobileBrandSalesRanking;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutomobileBrandSalesRankingService {

    @Autowired
    private AutomobileBrandSalesRankingRepository repository;

    public List<AutomobileBrandSalesRanking> getAllBrands() {
        return repository.findAll();
    }
}
