/**
 * 大数据展示系统
 * 包裹量排名 实体类
 */

package com.bigData.main.pojo;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "parcel_rank")
public class ParcelRack {
    @Id
    private Long id;  // id 字段
    private String province;  // 省份
    private Integer parcelCount;  // 包裹数量

    // Getters 和 Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public Integer getParcelCount() {
        return parcelCount;
    }

    public void setParcelCount(Integer parcelCount) {
        this.parcelCount = parcelCount;
    }
}
