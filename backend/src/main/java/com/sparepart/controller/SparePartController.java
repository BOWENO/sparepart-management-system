package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.common.UserContext;
import com.sparepart.entity.SparePart;
import com.sparepart.entity.User;
import com.sparepart.service.SparePartExcelService;
import com.sparepart.service.SparePartService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/parts")
public class SparePartController {

    private final SparePartService sparePartService;
    private final SparePartExcelService sparePartExcelService;

    public SparePartController(SparePartService sparePartService, SparePartExcelService sparePartExcelService) {
        this.sparePartService = sparePartService;
        this.sparePartExcelService = sparePartExcelService;
    }

    @GetMapping
    public Result<Page<SparePart>> page(@RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer size,
                                        @RequestParam(required = false) String keyword) {
        return Result.success(sparePartService.page(page, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<SparePart> get(@PathVariable Integer id) {
        return Result.success(sparePartService.getById(id));
    }

    @PostMapping
    @OpLog(module = "备品", action = "新增/编辑")
    public Result<SparePart> save(@RequestBody SparePart part) {
        return Result.success("保存成功", sparePartService.save(part));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "备品", action = "删除")
    public Result<SparePart> delete(@PathVariable Integer id) {
        return Result.success("删除成功", sparePartService.delete(id));
    }

    @PostMapping("/import")
    @OpLog(module = "备品", action = "TXT导入")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        User user = UserContext.get();
        String operator = user == null ? "system" : user.getUsername();
        Map<String, Object> stats = sparePartExcelService.importExcel(file, operator);
        return Result.success("导入成功：新增 " + stats.get("newCount") + " 条，更新 " + stats.get("updateCount") + " 条", stats);
    }

    @PostMapping("/import/{batchNo}/rollback")
    @OpLog(module = "备品", action = "回滚TXT导入")
    public Result<Void> rollbackImport(@PathVariable String batchNo) throws IOException {
        sparePartExcelService.rollback(batchNo);
        return Result.success("导入批次已回滚");
    }

    @PostMapping("/import/preview")
    public Result<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(sparePartExcelService.preview(file));
    }

    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        sparePartExcelService.exportTemplate(response);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        sparePartExcelService.exportLedger(response);
    }
}
