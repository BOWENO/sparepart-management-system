package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.entity.ScrapRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface ScrapRecordMapper extends BaseMapper<ScrapRecord> {
    @Select("<script>SELECT sr.*, p.part_no AS partNo, p.name AS partName, p.spec AS partSpec, " +
            "d.device_code AS deviceCode, d.device_name AS deviceName " +
            "FROM scrap_records sr LEFT JOIN spare_parts p ON p.id=sr.part_id " +
            "LEFT JOIN devices d ON d.id=sr.device_id WHERE 1=1 " +
            "<if test='keyword != null and keyword != &quot;&quot;'> AND (p.part_no LIKE CONCAT('%',#{keyword},'%') OR p.name LIKE CONCAT('%',#{keyword},'%') OR sr.scrap_no LIKE CONCAT('%',#{keyword},'%'))</if> " +
            "<if test='status != null and status != &quot;&quot;'> AND sr.status=#{status}</if> " +
            "ORDER BY sr.received_at DESC, sr.id DESC</script>")
    Page<Map<String, Object>> selectArchive(Page<?> page, @Param("keyword") String keyword,
                                             @Param("status") String status);
}
