package com.bigData.main.Repository;


import com.bigData.main.pojo.BasicInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BasicInfoRepository extends JpaRepository<BasicInfo, Long> {
    // 你可以在这里定义自定义查询方法
}
