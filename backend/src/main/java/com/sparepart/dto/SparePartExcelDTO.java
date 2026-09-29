package com.sparepart.dto;

import lombok.Data;


/**
 * 备品文本导入/导出映射
 */
@Data
public class SparePartExcelDTO {

    private String partNo;

    private String name;

    private String spec;

    private String unit;

    private String locationCode;

    private Integer goodStock;

    private Integer minStock;

    private Integer scrapStock;

    private String status;

    private Long ratedLifeCount;
}
