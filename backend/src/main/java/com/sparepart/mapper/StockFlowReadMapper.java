package com.sparepart.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.dto.StockFlowView;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 历史查询必须保留已删除档案的关联，不能使用带逻辑删除过滤的档案查询。 */
@Mapper
public interface StockFlowReadMapper {
    String QUERY = "SELECT f.*,p.part_no,p.name AS part_name,p.spec AS part_spec,p.unit," +
            "d.device_code,d.device_name,COALESCE(l.line_code,d.line_name) AS line_code,l.line_name," +
            "COALESCE(NULLIF(u.real_name,''),f.operator) AS operator_name " +
            "FROM stock_flow f LEFT JOIN spare_parts p ON p.id=f.part_id " +
            "LEFT JOIN devices d ON d.id=f.device_id LEFT JOIN production_lines l ON l.id=d.line_id " +
            "LEFT JOIN users u ON u.username=f.operator WHERE 1=1 " +
            "<if test='flowType != null'>AND f.flow_type=#{flowType} </if>" +
            "<if test='operator != null and operator != &quot;&quot;'>AND (f.operator LIKE CONCAT('%',#{operator},'%') OR u.real_name LIKE CONCAT('%',#{operator},'%')) </if>" +
            "<if test='partId != null'>AND f.part_id=#{partId} </if>" +
            "<if test='deviceId != null'>AND f.device_id=#{deviceId} </if>" +
            "ORDER BY f.id DESC";

    @Select("<script>" + QUERY + "</script>")
    Page<StockFlowView> page(Page<?> page, @Param("flowType") Integer flowType,
            @Param("operator") String operator, @Param("partId") Integer partId, @Param("deviceId") Integer deviceId);
    @Select("<script>" + QUERY + "</script>")
    List<StockFlowView> list(@Param("flowType") Integer flowType, @Param("operator") String operator,
            @Param("partId") Integer partId, @Param("deviceId") Integer deviceId);
}
