package com.sparepart.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("technicians")
public class Technician {
    @TableId(type = IdType.AUTO) private Integer id;
    private String employeeNo;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
}
