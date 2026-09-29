package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    /** 查询归档表（动态表名，month 形如 YYYY_MM，白名单校验后传入） */
    @Select("SELECT * FROM operation_logs_${month} " +
            "WHERE (#{operator} IS NULL OR operator LIKE CONCAT('%',#{operator},'%')) " +
            "AND (#{module} IS NULL OR module = #{module}) " +
            "AND (#{start} IS NULL OR created_at >= #{start}) " +
            "AND (#{end} IS NULL OR created_at <= #{end}) " +
            "ORDER BY id DESC")
    List<OperationLog> selectArchive(@Param("month") String month, @Param("operator") String operator,
                                     @Param("module") String module, @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}