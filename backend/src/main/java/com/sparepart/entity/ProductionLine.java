package com.sparepart.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("production_lines")
public class ProductionLine {
    @TableId(type=IdType.AUTO) private Integer id;
    private String lineCode;
    private String lineName;
    private Integer status;
    private LocalDateTime createdAt;
}
