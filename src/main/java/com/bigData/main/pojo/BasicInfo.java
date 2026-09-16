/**
 * 大数据展现系统
 * 基本信息 实体类
 */


package com.bigData.main.pojo;

import javax.persistence.*;

@Entity
@Table(name = "basic_info")
public class BasicInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer onlineUsers;
    private Integer onlineMerchants;
    private Double transactionAmount;
    private Integer transactionCount;
    private Integer totalParcels;
    private Integer shippedParcels;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getOnlineUsers() {
        return onlineUsers;
    }

    public void setOnlineUsers(Integer onlineUsers) {
        this.onlineUsers = onlineUsers;
    }

    public Integer getOnlineMerchants() {
        return onlineMerchants;
    }

    public void setOnlineMerchants(Integer onlineMerchants) {
        this.onlineMerchants = onlineMerchants;
    }

    public Double getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(Double transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public Integer getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Integer transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Integer getTotalParcels() {
        return totalParcels;
    }

    public void setTotalParcels(Integer totalParcels) {
        this.totalParcels = totalParcels;
    }

    public Integer getShippedParcels() {
        return shippedParcels;
    }

    public void setShippedParcels(Integer shippedParcels) {
        this.shippedParcels = shippedParcels;
    }
}
