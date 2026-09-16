package com.bigData.main.Repository;

import com.bigData.main.pojo.MonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyStatisticsRepository extends JpaRepository<MonthlyStatistics, Long> {
}
