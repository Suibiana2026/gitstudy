/**
 * 大数据展示系统
 * 汽车品牌销售排行榜 实体类
 */

package com.bigData.main.pojo;

import lombok.Data;
import javax.persistence.*;

@Entity
@Data
@Table(name = "automobile_brand_sales_ranking")
public class AutomobileBrandSalesRanking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "sales")
    private Integer sales;
}
