package com.sparepart.dto;

import lombok.Data;

/**
 * 操作日志文本导出映射
 */
@Data
public class OperationLogExcelDTO {

    private String operator;

    private String module;

    private String action;

    private String afterJson;

    private String ip;

    private String createdAt;
}
