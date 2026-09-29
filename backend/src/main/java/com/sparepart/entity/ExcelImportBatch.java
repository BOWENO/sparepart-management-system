package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("excel_import_batches")
public class ExcelImportBatch {
    @TableId private String batchNo;
    private String module;
    private String operator;
    private String fileName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime rolledBackAt;
}
