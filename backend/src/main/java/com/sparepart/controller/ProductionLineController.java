package com.sparepart.controller;
import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.entity.ProductionLine;
import com.sparepart.service.ProductionLineService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/production-lines") public class ProductionLineController{
 private final ProductionLineService service;public ProductionLineController(ProductionLineService service){this.service=service;}
 @GetMapping public Result<List<ProductionLine>> list(@RequestParam(defaultValue="false")boolean activeOnly){return Result.success(service.list(activeOnly));}
 @PostMapping @OpLog(module="产线",action="新增/编辑") public Result<ProductionLine> save(@RequestBody ProductionLine row){return Result.success("保存成功",service.save(row));}
}
