package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.Result;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.dto.OperationLogExcelDTO;
import com.sparepart.entity.OperationLog;
import com.sparepart.service.LogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /** 热数据日志查询 */
    @GetMapping
    public Result<Page<OperationLog>> page(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String operator,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        return Result.success(logService.page(page, size, operator, module, start, end));
    }

    /** 归档表按月查询（冷数据） */
    @GetMapping("/archive")
    public Result<List<OperationLog>> archive(@RequestParam String month,
                                              @RequestParam(required = false) String operator,
                                              @RequestParam(required = false) String module) {
        return Result.success(logService.queryArchive(month, operator, module, null, null));
    }

    @GetMapping("/archive/export")
    public void exportArchive(@RequestParam String month,
                              @RequestParam(required = false) String operator,
                              @RequestParam(required = false) String module,
                              HttpServletResponse response) throws IOException {
        writeExcel(logService.queryArchive(month, operator, module, null, null), "操作日志归档_" + month, response);
    }

    /** 操作日志 TXT 导出（按当前查询条件，ALL） */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String operator,
                        @RequestParam(required = false) String module,
                        @RequestParam(required = false)
                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
                        @RequestParam(required = false)
                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
                        HttpServletResponse response) throws IOException {
        List<OperationLog> list = logService.listByCondition(operator, module, start, end);
        writeExcel(list, "操作日志", response);
    }

    private void writeExcel(List<OperationLog> list, String filename, HttpServletResponse response) throws IOException {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<OperationLogExcelDTO> rows = new ArrayList<>();
        for (OperationLog log : list) {
            OperationLogExcelDTO row = new OperationLogExcelDTO();
            row.setOperator(log.getOperator());
            row.setModule(log.getModule());
            row.setAction(log.getAction());
            row.setAfterJson(log.getAfterJson());
            row.setIp(log.getIp());
            row.setCreatedAt(log.getCreatedAt() == null ? null : log.getCreatedAt().format(fmt));
            rows.add(row);
        }
        List<String> headers = List.of("操作人", "模块", "操作", "变更数据", "IP", "时间");
        var data = rows.stream().map(row -> List.of(text(row.getOperator()), text(row.getModule()),
                text(row.getAction()), text(row.getAfterJson()), text(row.getIp()), text(row.getCreatedAt()))).toList();
        TabularTextUtil.write(response, filename, headers, data);
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
