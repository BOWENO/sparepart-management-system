package com.sparepart.controller;

import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.entity.Device;
import com.sparepart.common.UserContext;
import com.sparepart.service.DeviceExcelService;
import com.sparepart.service.DeviceService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceExcelService deviceExcelService;

    public DeviceController(DeviceService deviceService, DeviceExcelService deviceExcelService) {
        this.deviceService = deviceService;
        this.deviceExcelService = deviceExcelService;
    }

    @GetMapping("/list")
    public Result<List<Device>> list(@RequestParam(required=false) Integer lineId) {
        return Result.success(deviceService.listAll(lineId));
    }

    @GetMapping
    public Result<List<Device>> ledger() {
        return Result.success(deviceService.ledger());
    }

    @PostMapping
    @OpLog(module = "设备", action = "新增/编辑")
    public Result<Device> save(@RequestBody Device device) {
        return Result.success("保存成功", deviceService.save(device));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "设备", action = "删除")
    public Result<Device> delete(@PathVariable Integer id) {
        return Result.success("删除成功", deviceService.delete(id));
    }

    /** 设备 TXT 导入 */
    @PostMapping("/import")
    @OpLog(module = "设备", action = "TXT导入")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        String operator = UserContext.get() == null ? "system" : UserContext.get().getUsername();
        Map<String, Object> stats = deviceExcelService.importExcel(file, operator);
        return Result.success("导入成功：新增 " + stats.get("newCount") + " 条，更新 " + stats.get("updateCount") + " 条", stats);
    }

    @PostMapping("/import/{batchNo}/rollback")
    @OpLog(module = "设备", action = "回滚TXT导入")
    public Result<Void> rollbackImport(@PathVariable String batchNo) throws IOException {
        deviceExcelService.rollback(batchNo);
        return Result.success("导入批次已回滚");
    }

    @PostMapping("/import/preview")
    public Result<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(deviceExcelService.preview(file));
    }

    /** 设备导入模板导出 */
    @GetMapping("/template")
    public void template(HttpServletResponse response) throws IOException {
        deviceExcelService.exportTemplate(response);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response) throws IOException {
        deviceExcelService.exportLedger(response);
    }
}
