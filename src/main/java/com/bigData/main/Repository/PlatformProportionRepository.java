package com.bigData.main.Repository;

import com.bigData.main.pojo.PlatformProportion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformProportionRepository extends JpaRepository<PlatformProportion, Long> {
}
