package com.bigData.main.service.MySQL;

import com.bigData.main.Repository.MonthlyStatisticsRepository;
import com.bigData.main.pojo.MonthlyStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonthlyStatisticsService {
    @Autowired
    private MonthlyStatisticsRepository repository;

    public List<MonthlyStatistics> getAllStatistics() {
        return repository.findAll();
    }
}
