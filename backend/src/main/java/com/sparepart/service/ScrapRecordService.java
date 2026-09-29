package com.sparepart.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.mapper.ScrapRecordMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class ScrapRecordService {
    private final ScrapRecordMapper mapper;

    public ScrapRecordService(ScrapRecordMapper mapper) { this.mapper = mapper; }

    public Page<Map<String, Object>> page(int page, int size, String keyword, String status) {
        Page<Map<String, Object>> result = mapper.selectArchive(new Page<>(page, size), keyword == null ? null : keyword.trim(), status);
        result.setRecords(result.getRecords().stream().map(this::normalize).toList());
        return result;
    }

    private Map<String, Object> normalize(Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        String[] fields = { "id", "scrapNo", "flowNo", "partId", "deviceId", "quantity", "remainingQuantity",
                "actualUsageCount", "ratedLifeCount", "replacementReason", "scrapReason", "operator", "status",
                "disposalBatch", "receivedAt", "clearedAt", "partNo", "partName", "partSpec", "deviceCode", "deviceName" };
        for (String field : fields) row.put(field, find(source, field));
        return row;
    }

    private Object find(Map<String, Object> source, String field) {
        String compact = field.replace("_", "").toLowerCase();
        return source.entrySet().stream()
                .filter(e -> e.getKey().replace("_", "").equalsIgnoreCase(compact))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
