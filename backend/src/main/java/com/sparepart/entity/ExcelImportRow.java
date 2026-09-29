package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("excel_import_rows")
public class ExcelImportRow {
    @TableId(type = IdType.AUTO) private Long id;
    private String batchNo;
    private Integer recordId;
    private Integer wasNew;
    private String beforeJson;
    private String afterJson;
}
