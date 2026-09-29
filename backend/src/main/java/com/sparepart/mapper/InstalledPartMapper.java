package com.sparepart.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.entity.InstalledPart;
import com.sparepart.dto.InstalledPartView;
import com.sparepart.dto.InstalledDeviceSummary;
import org.apache.ibatis.annotations.*;
@Mapper public interface InstalledPartMapper extends BaseMapper<InstalledPart> {
    @Select("<script>SELECT ip.id,ip.replace_record_id,rr.flow_no,ip.part_id,p.part_no,p.name AS part_name,p.spec AS part_spec," +
            "ip.quantity,ip.in_use_quantity,ip.device_id,d.device_code,d.device_name,ip.technician_id," +
            "t.employee_no,t.name AS technician_name,ip.status,ip.issued_at,ip.removed_at " +
            "FROM installed_parts ip JOIN spare_parts p ON p.id=ip.part_id JOIN devices d ON d.id=ip.device_id " +
            "JOIN technicians t ON t.id=ip.technician_id LEFT JOIN replace_records rr ON rr.id=ip.replace_record_id " +
            "WHERE ip.in_use_quantity&gt;0 " +
            "<if test='partId != null'>AND ip.part_id=#{partId}</if> " +
            "<if test='deviceId != null'>AND ip.device_id=#{deviceId}</if> " +
            "<if test='keyword != null and keyword != &quot;&quot;'>AND (p.part_no LIKE CONCAT('%',#{keyword},'%') OR p.name LIKE CONCAT('%',#{keyword},'%') OR d.device_name LIKE CONCAT('%',#{keyword},'%') OR t.name LIKE CONCAT('%',#{keyword},'%'))</if> " +
            "ORDER BY ip.issued_at DESC,ip.id DESC</script>")
    Page<InstalledPartView> selectInUse(Page<?> page,@Param("partId") Integer partId,@Param("deviceId") Integer deviceId,@Param("keyword") String keyword);

    @Select("<script>SELECT ip.device_id,d.device_code,d.device_name,COUNT(DISTINCT ip.part_id) AS part_type_count," +
            "SUM(ip.in_use_quantity) AS total_in_use_quantity,MAX(ip.issued_at) AS latest_issued_at " +
            "FROM installed_parts ip JOIN spare_parts p ON p.id=ip.part_id JOIN devices d ON d.id=ip.device_id " +
            "LEFT JOIN technicians t ON t.id=ip.technician_id WHERE ip.in_use_quantity&gt;0 " +
            "<if test='keyword != null and keyword != &quot;&quot;'>AND (d.device_code LIKE CONCAT('%',#{keyword},'%') OR d.device_name LIKE CONCAT('%',#{keyword},'%') OR p.part_no LIKE CONCAT('%',#{keyword},'%') OR p.name LIKE CONCAT('%',#{keyword},'%') OR t.name LIKE CONCAT('%',#{keyword},'%'))</if> " +
            "GROUP BY ip.device_id,d.device_code,d.device_name ORDER BY d.device_code</script>")
    Page<InstalledDeviceSummary> selectDeviceSummaries(Page<?> page,@Param("keyword") String keyword);

    @Select("SELECT ip.id,ip.replace_record_id,rr.flow_no,ip.part_id,p.part_no,p.name AS part_name,p.spec AS part_spec," +
            "ip.quantity,ip.in_use_quantity,ip.device_id,d.device_code,d.device_name,ip.technician_id," +
            "t.employee_no,t.name AS technician_name,ip.status,ip.issued_at,ip.removed_at " +
            "FROM installed_parts ip JOIN spare_parts p ON p.id=ip.part_id JOIN devices d ON d.id=ip.device_id " +
            "JOIN technicians t ON t.id=ip.technician_id LEFT JOIN replace_records rr ON rr.id=ip.replace_record_id " +
            "WHERE ip.device_id=#{deviceId} ORDER BY ip.issued_at DESC,ip.id DESC")
    java.util.List<InstalledPartView> selectDeviceHistory(@Param("deviceId") Integer deviceId);
}
