package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.ReplaceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReplaceRecordMapper extends BaseMapper<ReplaceRecord> {

    /**
     * 废品使用统计：按退回旧品（old_part_id）聚合，
     * 统计使用次数(换新次数)、累计报废数量、曾使用设备。
     */
    @Select("SELECT rr.old_part_id AS partId, p.part_no AS partNo, p.name AS partName, p.spec AS spec, " +
            "COUNT(*) AS useCount, SUM(COALESCE(rr.old_quantity,rr.quantity)) AS totalQty, " +
            "GROUP_CONCAT(DISTINCT d.device_name ORDER BY d.device_name SEPARATOR '、') AS devices " +
            "FROM replace_records rr " +
            "LEFT JOIN spare_parts p ON p.id = rr.old_part_id " +
            "LEFT JOIN devices d ON d.id = rr.device_id " +
            "WHERE rr.old_part_id IS NOT NULL AND COALESCE(rr.status,'ACTIVE')='ACTIVE' " +
            "GROUP BY rr.old_part_id, p.part_no, p.name, p.spec " +
            "ORDER BY useCount DESC")
    List<Map<String, Object>> scrapStats();

    /** 寿命监控：额定寿命、平均实用次数、达成率、故障提前报废数。 */
    @Select("SELECT rr.old_part_id AS partId, rr.device_id AS deviceId, d.device_code AS deviceCode, d.device_name AS deviceName, p.part_no AS partNo, p.name AS partName, " +
            "p.rated_life_count AS ratedLifeCount, SUM(COALESCE(rr.old_quantity,rr.quantity)) AS replacementCount, " +
            "ROUND(SUM(1.0 * rr.actual_usage_count * COALESCE(rr.old_quantity,rr.quantity)) / NULLIF(SUM(COALESCE(rr.old_quantity,rr.quantity)),0),0) AS avgActualUsage, " +
            "ROUND(SUM(1.0 * rr.actual_usage_count * COALESCE(rr.old_quantity,rr.quantity)) * 100.0 / NULLIF(SUM(COALESCE(rr.old_quantity,rr.quantity)),0) / NULLIF(p.rated_life_count,0),1) AS lifeAchievementRate, " +
            "SUM(CASE WHEN rr.replacement_reason='FAILURE' THEN COALESCE(rr.old_quantity,rr.quantity) ELSE 0 END) AS failureCount, " +
            "SUM(CASE WHEN rr.replacement_reason='LIFE_LIMIT' THEN COALESCE(rr.old_quantity,rr.quantity) ELSE 0 END) AS forcedCount " +
            "FROM replace_records rr JOIN spare_parts p ON p.id=rr.old_part_id LEFT JOIN devices d ON d.id=rr.device_id " +
            "WHERE rr.actual_usage_count IS NOT NULL AND COALESCE(rr.status,'ACTIVE')='ACTIVE' GROUP BY rr.old_part_id,rr.device_id,d.device_code,d.device_name,p.part_no,p.name,p.rated_life_count " +
            "ORDER BY replacementCount DESC")
    List<Map<String, Object>> lifeStats();
}
