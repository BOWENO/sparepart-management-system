package com.sparepart.controller;

import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.entity.Technician;
import com.sparepart.service.TechnicianService;
import com.sparepart.service.TechnicianExcelService;
import com.sparepart.common.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/technicians")
public class TechnicianController {
    private final TechnicianService service;
    private final TechnicianExcelService excelService;
    public TechnicianController(TechnicianService service,TechnicianExcelService excelService){this.service=service;this.excelService=excelService;}
    @GetMapping public Result<List<Technician>> list(@RequestParam(defaultValue="false") boolean activeOnly){return Result.success(service.list(activeOnly));}
    @PostMapping @OpLog(module="技术员",action="新增/编辑") public Result<Technician> save(@RequestBody Technician row){return Result.success("保存成功",service.save(row));}
    @DeleteMapping("/{id}") @OpLog(module="技术员",action="停用") public Result<Void> disable(@PathVariable Integer id){service.disable(id);return Result.success("技术员已停用");}
    @GetMapping("/export") public void export(HttpServletResponse response)throws IOException{excelService.exportLedger(response);}
    @PostMapping("/import/preview") public Result<Map<String,Object>> preview(@RequestParam("file") MultipartFile file)throws IOException{return Result.success(excelService.preview(file));}
    @PostMapping("/import") @OpLog(module="技术员",action="TXT导入") public Result<Map<String,Object>> importExcel(@RequestParam("file") MultipartFile file)throws IOException{
        String operator=UserContext.get()==null?"system":UserContext.get().getUsername(); return Result.success("导入成功",excelService.importExcel(file,operator));
    }
    @PostMapping("/import/{batchNo}/rollback") @OpLog(module="技术员",action="回滚TXT导入") public Result<Void> rollback(@PathVariable String batchNo)throws IOException{excelService.rollback(batchNo);return Result.success("导入批次已回滚");}
}
