package com.sparepart.dto;

import lombok.Data;

/**
 * 废品使用统计文本导出映射
 */
@Data
public class ScrapStatExcelDTO {

    private String partNo;

    private String partName;

    private String spec;

    private Integer useCount;

    private Integer totalQty;

    private String devices;
}
