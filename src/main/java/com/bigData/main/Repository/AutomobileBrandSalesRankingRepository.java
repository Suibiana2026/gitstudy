package com.bigData.main.Repository;

import com.bigData.main.pojo.AutomobileBrandSalesRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomobileBrandSalesRankingRepository extends JpaRepository<AutomobileBrandSalesRanking, Long> {
}
