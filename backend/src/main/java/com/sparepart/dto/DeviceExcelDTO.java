package com.sparepart.dto;

import lombok.Data;

/**
 * 设备 Excel 导入/导出模板映射
 */
@Data
public class DeviceExcelDTO {

    private String lineName;

    private String deviceCode;

    private String deviceName;

    private String status;
}
