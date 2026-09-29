package com.sparepart.service;

import com.sparepart.mapper.ReplaceRecordMapper;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sparepart.entity.SparePart;

@Service
public class AnalysisService {

    private final StockFlowMapper stockFlowMapper;
    private final SparePartMapper sparePartMapper;
    private final ReplaceRecordMapper replaceRecordMapper;
    private final InternalMessageService internalMessageService;

    public AnalysisService(StockFlowMapper stockFlowMapper, SparePartMapper sparePartMapper,
                           ReplaceRecordMapper replaceRecordMapper, InternalMessageService internalMessageService) {
        this.stockFlowMapper = stockFlowMapper;
        this.sparePartMapper = sparePartMapper;
        this.replaceRecordMapper = replaceRecordMapper;
        this.internalMessageService = internalMessageService;
    }

    public List<Map<String, Object>> deviceCost(LocalDate start, LocalDate end) {
        return List.of();
    }

    public List<Map<String, Object>> unitTotal() {
        return normalize(stockFlowMapper.unitTotal());
    }

    public List<Map<String, Object>> zombieParts(int days) {
        return normalize(stockFlowMapper.zombieParts(LocalDateTime.now().minusDays(days)));
    }

    public List<Map<String, Object>> consumeTrend(LocalDate start, LocalDate end) {
        return normalize(stockFlowMapper.consumeTrend(start, end.plusDays(1)));
    }

    public List<Map<String, Object>> deviceTrend(Integer deviceId, int months) {
        return stockFlowMapper.deviceTrend(deviceId, LocalDate.now().minusMonths(months)).stream().map(row -> {
            Map<String, Object> result = new HashMap<>(row);
            int year = number(row, "yearNo").intValue();
            int month = number(row, "monthNo").intValue();
            result.put("month", String.format("%04d-%02d", year, month));
            return normalizeRow(result);
        }).toList();
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        if (value == null) {
            value = row.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(key))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        if (!(value instanceof Number number)) throw new IllegalStateException("统计字段缺失：" + key);
        return number;
    }

    private List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        return rows.stream().map(this::normalizeRow).toList();
    }

    private Map<String, Object> normalizeRow(Map<String, Object> source) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> result.put(canonicalKey(key), value));
        return result;
    }

    private String canonicalKey(String key) {
        String compact = key.replace("_", "").toLowerCase();
        return switch (compact) {
            case "partid" -> "partId"; case "partno" -> "partNo"; case "partname" -> "partName";
            case "totalqty" -> "totalQty"; case "lastused" -> "lastUsed"; case "usecount" -> "useCount";
            case "deviceid" -> "deviceId"; case "devicecode" -> "deviceCode"; case "devicename" -> "deviceName";
            case "ratedlifecount" -> "ratedLifeCount"; case "avgactualusage" -> "avgActualUsage";
            case "lifeachievementrate" -> "lifeAchievementRate"; case "failurecount" -> "failureCount";
            case "forcedcount" -> "forcedCount"; case "replacementcount" -> "replacementCount";
            case "usedqty" -> "usedQty"; case "yearno" -> "yearNo"; case "monthno" -> "monthNo";
            default -> key;
        };
    }

    public List<Map<String, Object>> partType() {
        return normalize(stockFlowMapper.partType());
    }

    /** 废品使用统计 */
    public List<Map<String, Object>> scrapStats() {
        return normalize(replaceRecordMapper.scrapStats());
    }

    public List<Map<String, Object>> lifeStats() {
        return normalize(replaceRecordMapper.lifeStats());
    }

    /** 以最近90天实际换新消耗预测未来30天水位，并附20%安全系数。 */
    public List<Map<String, Object>> stockForecast() {
        Map<Integer, Long> usage = new HashMap<>();
        for (Map<String, Object> row : stockFlowMapper.replacementUsageSince(LocalDate.now().minusDays(89))) {
            usage.put(number(row, "partId").intValue(), number(row, "usedQty").longValue());
        }
        return sparePartMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SparePart>()
                .eq(SparePart::getStatus, 1)).stream().map(part -> {
            long used90 = usage.getOrDefault(part.getId(), 0L);
            double daily = used90 / 90.0;
            long recommended = (long) Math.ceil(daily * 30 * 1.2);
            Map<String, Object> row = new HashMap<>();
            row.put("partId", part.getId()); row.put("partNo", part.getPartNo()); row.put("partName", part.getName());
            row.put("used90Days", used90); row.put("goodStock", part.getGoodStock());
            row.put("recommendedMinStock", recommended);
            row.put("estimatedAvailableDays", daily == 0 ? null : (long) Math.floor(part.getGoodStock() / daily));
            row.put("needsAdjustment", part.getMinStock() == null || part.getMinStock() < recommended);
            return row;
        }).toList();
    }

    /** 首页 4 指标卡 + 消息速览 */
    public Map<String, Object> dashboardSummary() {
        Map<String, Object> map = new HashMap<>();
        map.put("todayUsage", stockFlowMapper.todayUsage(LocalDate.now()));
        map.put("goodStock", sparePartMapper.sumGoodStock());
        map.put("lowStockCount", sparePartMapper.countLowStock());
        map.put("scrapStock", sparePartMapper.sumScrapStock());
        map.put("unreadMsgCount", internalMessageService.unreadCount());
        map.put("latestUnread", internalMessageService.latestUnread(5));
        return map;
    }
}
