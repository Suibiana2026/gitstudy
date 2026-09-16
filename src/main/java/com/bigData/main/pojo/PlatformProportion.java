/**
 * 大数据展示系统
 * 各平台分类 实体类
 */

package com.bigData.main.pojo;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "platform_proportion")
@Data
public class PlatformProportion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String platform;
    private Double percentage;
}
