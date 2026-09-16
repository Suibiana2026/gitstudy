 /**
 * 大数据展示系统
 * 月统计 实体类
 */

 package com.bigData.main.pojo;
import javax.persistence.*;

@Entity
@Table(name = "monthly_statistics")
public class MonthlyStatistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String month;
    private Integer orderCount;
    private Integer paymentCount;

    @Override
    public String toString() {
        return "MonthlyStatistics{" +
                "month='" + month + '\'' +
                ", orderCount=" + orderCount +
                ", paymentCount=" + paymentCount +
                '}';
    }
}
