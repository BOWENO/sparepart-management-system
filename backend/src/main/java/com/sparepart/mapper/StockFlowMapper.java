package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.StockFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface StockFlowMapper extends BaseMapper<StockFlow> {
    /** 冲正时锁定原流水，串行化“检查是否已冲正”和库存调整。 */
    @Select("SELECT * FROM stock_flow WHERE id = #{id} FOR UPDATE")
    StockFlow selectForUpdate(@Param("id") Long id);

    @Select("SELECT part_id AS partId, SUM(quantity) AS usedQty FROM stock_flow " +
            "WHERE flow_type IN (3,8) AND business_date >= #{start} " +
            "AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=stock_flow.id) GROUP BY part_id")
    List<Map<String, Object>> replacementUsageSince(@org.apache.ibatis.annotations.Param("start") LocalDate start);

    /** 设备耗材成本排行（按出/换新/报废出库计算） */
    @Select("SELECT f.device_id AS deviceId, d.device_name AS deviceName, " +
            "SUM(f.quantity * f.unit_price) AS cost " +
            "FROM stock_flow f LEFT JOIN devices d ON d.id = f.device_id " +
            "WHERE f.flow_type IN (2,3,5) AND f.device_id IS NOT NULL " +
            "AND f.created_at >= #{start} AND f.created_at < #{end} " +
            "GROUP BY f.device_id, d.device_name ORDER BY cost DESC")
    List<Map<String, Object>> deviceCost(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 备品领用总量排行 */
    @Select("SELECT f.part_id AS partId, p.part_no AS partNo, p.name AS partName, " +
            "SUM(f.quantity) AS totalQty " +
            "FROM stock_flow f LEFT JOIN spare_parts p ON p.id = f.part_id " +
            "WHERE f.flow_type IN (2,3,8) AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id) " +
            "GROUP BY f.part_id, p.part_no, p.name ORDER BY totalQty DESC LIMIT 20")
    List<Map<String, Object>> unitTotal();

    /** 消耗趋势（按日期） */
    @Select("SELECT DATE(f.created_at) AS date, SUM(f.quantity) AS totalQty " +
            "FROM stock_flow f WHERE f.flow_type IN (2,3,8) AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id) " +
            "AND f.created_at >= #{start} AND f.created_at < #{end} " +
            "GROUP BY DATE(f.created_at) ORDER BY date")
    List<Map<String, Object>> consumeTrend(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 设备更换频率趋势（按月） */
    @Select("SELECT YEAR(f.created_at) AS yearNo, MONTH(f.created_at) AS monthNo, SUM(f.quantity) AS totalQty " +
            "FROM stock_flow f WHERE f.flow_type IN (2,3,8) AND f.device_id = #{deviceId} " +
            "AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id) " +
            "AND f.created_at >= #{start} " +
            "GROUP BY YEAR(f.created_at), MONTH(f.created_at) ORDER BY yearNo, monthNo")
    List<Map<String, Object>> deviceTrend(@Param("deviceId") Integer deviceId, @Param("start") LocalDate start);

    /** 备品类型偏好（各备品消耗量） */
    @Select("SELECT f.part_id AS partId, p.name AS partName, SUM(f.quantity) AS totalQty " +
            "FROM stock_flow f LEFT JOIN spare_parts p ON p.id = f.part_id " +
            "WHERE f.flow_type IN (2,3,8) AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id) " +
            "GROUP BY f.part_id, p.name ORDER BY totalQty DESC")
    List<Map<String, Object>> partType();

    /** 僵尸备品：从未领用或最近一次领用超过 thresholdDays 天 */
    @Select("SELECT p.id AS partId, p.part_no AS partNo, p.name AS partName, MAX(f.created_at) AS lastUsed " +
            "FROM spare_parts p LEFT JOIN stock_flow f ON f.part_id = p.id AND f.flow_type IN (2,3,8) " +
            "AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id) " +
            "WHERE p.status = 1 AND p.deleted = 0 AND p.good_stock > 0 " +
            "GROUP BY p.id, p.part_no, p.name " +
            "HAVING lastUsed IS NULL OR lastUsed < #{threshold} " +
            "ORDER BY lastUsed")
    List<Map<String, Object>> zombieParts(@Param("threshold") java.time.LocalDateTime threshold);

    /** 今日领用总数 */
    @Select("SELECT COALESCE(SUM(f.quantity),0) FROM stock_flow f " +
            "WHERE f.flow_type IN (2,3,8) AND f.created_at >= #{start} " +
            "AND NOT EXISTS (SELECT 1 FROM stock_flow r WHERE r.reversed_flow_id=f.id)")
    Long todayUsage(@Param("start") LocalDate start);
}
