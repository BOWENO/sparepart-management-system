package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.BizException;
import com.sparepart.entity.OperationLog;
import com.sparepart.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class LogService {

    private final OperationLogMapper logMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 归档表名白名单：YYYY_MM */
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}_\\d{2}$");

    public LogService(OperationLogMapper logMapper, JdbcTemplate jdbcTemplate) {
        this.logMapper = logMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 热数据查询（operation_logs 表） */
    public Page<OperationLog> page(Integer pageNum, Integer pageSize, String operator, String module,
                                   LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator)) {
            qw.like(OperationLog::getOperator, operator);
        }
        if (StringUtils.hasText(module)) {
            qw.eq(OperationLog::getModule, module);
        }
        if (start != null) {
            qw.ge(OperationLog::getCreatedAt, start);
        }
        if (end != null) {
            qw.le(OperationLog::getCreatedAt, end);
        }
        qw.orderByDesc(OperationLog::getId);
        return logMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    /** 归档表按月查询（冷数据） */
    public List<OperationLog> queryArchive(String month, String operator, String module,
                                           LocalDateTime start, LocalDateTime end) {
        if (!MONTH_PATTERN.matcher(month).matches()) {
            throw new BizException("月份参数格式不正确");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME)=?",
                Integer.class, "operation_logs_" + month.toLowerCase());
        if (count == null || count == 0) throw new BizException("该月份没有归档日志");
        return logMapper.selectArchive(month, operator, module, start, end);
    }

    /** 按条件查询热数据全量（供导出） */
    public List<OperationLog> listByCondition(String operator, String module,
                                              LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator)) {
            qw.like(OperationLog::getOperator, operator);
        }
        if (StringUtils.hasText(module)) {
            qw.eq(OperationLog::getModule, module);
        }
        if (start != null) {
            qw.ge(OperationLog::getCreatedAt, start);
        }
        if (end != null) {
            qw.le(OperationLog::getCreatedAt, end);
        }
        qw.orderByDesc(OperationLog::getId);
        return logMapper.selectList(qw);
    }
}
