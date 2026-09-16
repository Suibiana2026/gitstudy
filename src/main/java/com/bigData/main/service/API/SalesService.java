package com.bigData.main.service.API;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SalesService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 按日期查询 car_sales 原始记录，支持 YYYY / YYYY-MM / YYYY-MM-DD 三种粒度。
     * 抽成独立方法，供销售方案生成与智能体工具复用。
     */
    public List<Map<String, Object>> querySales(String saleDate) {
        if (saleDate == null || saleDate.trim().isEmpty()) {
            throw new IllegalArgumentException("销售日期不能为空");
        }

        String d = saleDate.trim();
        String sql;
        Object param;

        if (d.length() == 10) {          // 完整日期，如 2025-03-15
            sql = "SELECT * FROM car_sales WHERE sale_date = ? ORDER BY model";
            param = d;
        } else if (d.length() == 7) {    // 年月，如 2025-03
            sql = "SELECT * FROM car_sales WHERE sale_date LIKE ? ORDER BY model";
            param = d + "%";
        } else if (d.length() == 4) {    // 年，如 2025
            sql = "SELECT * FROM car_sales WHERE sale_date LIKE ? ORDER BY model";
            param = d + "%";
        } else {
            throw new IllegalArgumentException("销售日期格式无效，支持格式：YYYY, YYYY-MM, YYYY-MM-DD");
        }

        return jdbcTemplate.queryForList(sql, param);
    }

    /**
     * 面向智能体的销售数据摘要。
     * 把明细行聚合成车型维度的紧凑文本，避免把几万行原始记录塞给大模型。
     */
    public String summarizeSales(String saleDate) {
        List<Map<String, Object>> rows = querySales(saleDate);
        if (rows.isEmpty()) {
            return "未找到 " + saleDate + " 的销售数据";
        }

        Map<String, int[]> byModel = new LinkedHashMap<String, int[]>();
        Map<String, Double> trendSum = new LinkedHashMap<String, Double>();
        Set<String> regions = new LinkedHashSet<String>();
        Set<String> customerTypes = new LinkedHashSet<String>();

        int totalVolume = 0;
        int totalInventory = 0;

        for (Map<String, Object> row : rows) {
            String model = row.get("model") == null ? "未知车型" : String.valueOf(row.get("model"));
            int volume = toInt(row.get("sales_volume"));
            int inventory = toInt(row.get("inventory"));
            double trend = toDouble(row.get("market_trend"));

            int[] agg = byModel.get(model);
            if (agg == null) {
                agg = new int[]{0, 0, 0};
                byModel.put(model, agg);
            }
            agg[0] += volume;
            agg[1] += inventory;
            agg[2] += 1;

            Double t = trendSum.get(model);
            trendSum.put(model, (t == null ? 0d : t) + trend);

            totalVolume += volume;
            totalInventory += inventory;

            if (row.get("region") != null) {
                regions.add(String.valueOf(row.get("region")));
            }
            if (row.get("customer_type") != null) {
                customerTypes.add(String.valueOf(row.get("customer_type")));
            }
        }

        List<Map.Entry<String, int[]>> sorted = new ArrayList<Map.Entry<String, int[]>>(byModel.entrySet());
        Collections.sort(sorted, new Comparator<Map.Entry<String, int[]>>() {
            @Override
            public int compare(Map.Entry<String, int[]> a, Map.Entry<String, int[]> b) {
                return b.getValue()[0] - a.getValue()[0];
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("查询区间：").append(saleDate)
                .append("，明细记录 ").append(rows.size()).append(" 条，覆盖 ")
                .append(byModel.size()).append(" 个车型\n");
        sb.append("覆盖区域：").append(join(regions)).append("\n");
        sb.append("客户类型：").append(join(customerTypes)).append("\n\n");
        sb.append("车型维度汇总（按销量降序）：\n");

        int rank = 1;
        for (Map.Entry<String, int[]> entry : sorted) {
            int[] agg = entry.getValue();
            double avgTrend = trendSum.get(entry.getKey()) / Math.max(1, agg[2]);
            sb.append(rank++).append(". ").append(entry.getKey())
                    .append(" | 销量 ").append(agg[0])
                    .append(" | 库存 ").append(agg[1])
                    .append(" | 平均市场趋势 ").append(round1(avgTrend)).append("%\n");
        }

        sb.append("\n合计销量 ").append(totalVolume).append(" 辆，合计库存 ").append(totalInventory).append(" 辆。");
        return sb.toString();
    }

    /**
     * 基于规则生成销售方案（纯本地计算，不依赖大模型，可在 AI 不可用时兜底）。
     */
    public String generateSalesTemplate(String saleDate) {
        List<Map<String, Object>> salesData;
        try {
            salesData = querySales(saleDate);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        if (salesData == null || salesData.isEmpty()) {
            return "未找到 " + saleDate + " 的销售数据";
        }

        StringBuilder template = new StringBuilder();
        template.append("销售方案 - ").append(saleDate).append("\n")
                .append("针对所有车型的综合销售计划如下：\n\n");

        int totalTargetSales = 0;
        int totalBudget = 0;

        for (Map<String, Object> data : salesData) {
            String model = (String) data.get("model");
            int salesVolume = toInt(data.get("sales_volume"));
            String customerType = (String) data.get("customer_type");
            String region = (String) data.get("region");
            float marketTrend = (float) toDouble(data.get("market_trend"));
            int inventory = toInt(data.get("inventory"));

            int targetSales = (int) (salesVolume * (1 + marketTrend / 100));
            int additionalSales = targetSales - salesVolume;
            int safetyStock = (int) (salesVolume * 0.1);
            int suggestedInventory = inventory + additionalSales + safetyStock;

            String marketingAction = getMarketingAction(customerType);
            String promotionMethod = getPromotionMethod(region);
            String salesChannel = salesVolume > 300 ? "4S店" : "电商平台";

            int marketingCost = additionalSales * 50;
            int discountCost = additionalSales * 20;
            int totalCost = marketingCost + discountCost;

            totalTargetSales += targetSales;
            totalBudget += totalCost;

            template.append("车型：").append(model).append("\n")
                    .append("1. 销售目标\n")
                    .append("   - 当前销量：").append(salesVolume).append(" 辆\n")
                    .append("   - 市场趋势：").append(marketTrend).append("% 的增长\n")
                    .append("   - 目标销量：在未来 3 个月内将 ").append(model).append(" 销量提升至 ")
                    .append(targetSales).append(" 辆，新增 ").append(additionalSales).append(" 辆。\n")
                    .append("2. 销售策略\n")
                    .append("   - 客户定位：针对 ").append(customerType).append("，推出 ").append(marketingAction).append("。\n")
                    .append("   - 区域推广：在 ").append(region).append(" 投放 ").append(promotionMethod).append("，吸引潜在客户。\n")
                    .append("   - 库存管理：当前库存 ").append(inventory).append(" 辆，建议补充至 ")
                    .append(suggestedInventory).append(" 辆，确保供应充足。\n")
                    .append("   - 渠道优化：通过 ").append(salesChannel).append(" 提升 ").append(model).append(" 的曝光率。\n")
                    .append("3. 预算估算\n")
                    .append("   - 营销费用：预计 ").append(marketingCost).append(" 元（基于新增销量，每辆车分配 50 元广告费用）。\n")
                    .append("   - 折扣成本：预计 ").append(discountCost).append(" 元（基于折扣政策，每辆车补贴 20 元）。\n")
                    .append("   - 总预算：").append(totalCost).append(" 元。\n")
                    .append("4. 执行时间表\n")
                    .append("   - 第 1 个月：启动 ").append(marketingAction).append(" 和 ").append(promotionMethod).append("，完成库存调整。\n")
                    .append("   - 第 2-3 个月：跟踪销售进展，优化推广策略，预期实现 ").append(additionalSales).append(" 辆新增销量。\n\n");
        }

        template.append("总结：\n")
                .append("- 总目标销量：").append(totalTargetSales).append(" 辆\n")
                .append("- 总预算：").append(totalBudget).append(" 元\n")
                .append("- 执行周期：3 个月\n");

        return template.toString();
    }

    private String getMarketingAction(String customerType) {
        if (customerType == null) {
            return "常规促销活动";
        }
        switch (customerType) {
            case "中小企业": return "批量采购享 5% 折扣";
            case "个人": return "首购客户享 10% 折扣";
            case "大企业": return "企业客户赠送一年保养服务";
            case "政府": return "政府采购提供定制化服务";
            default: return "常规促销活动";
        }
    }

    private String getPromotionMethod(String region) {
        if (region == null) {
            return "本地经销商活动";
        }
        switch (region) {
            case "北京": case "上海": case "广州": case "深圳": return "线上广告";
            case "成都": case "南京": case "杭州": return "线下车展";
            default: return "本地经销商活动";
        }
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static String join(Set<String> values) {
        if (values.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(v);
        }
        return sb.toString();
    }
}
