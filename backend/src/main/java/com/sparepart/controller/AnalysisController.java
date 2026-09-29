package com.sparepart.controller;

import com.sparepart.common.Result;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.dto.ScrapStatExcelDTO;
import com.sparepart.service.AnalysisService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis/device-cost")
    public Result<List<Map<String, Object>>> deviceCost(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(analysisService.deviceCost(startDate, endDate));
    }

    @GetMapping("/analysis/unit-total")
    public Result<List<Map<String, Object>>> unitTotal() {
        return Result.success(analysisService.unitTotal());
    }

    @GetMapping("/analysis/zombie-parts")
    public Result<List<Map<String, Object>>> zombieParts(@RequestParam(defaultValue = "90") int days) {
        return Result.success(analysisService.zombieParts(days));
    }

    @GetMapping("/analysis/consume-trend")
    public Result<List<Map<String, Object>>> consumeTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(analysisService.consumeTrend(startDate, endDate));
    }

    @GetMapping("/analysis/device-trend")
    public Result<List<Map<String, Object>>> deviceTrend(@RequestParam Integer deviceId,
                                                         @RequestParam(defaultValue = "12") int months) {
        return Result.success(analysisService.deviceTrend(deviceId, months));
    }

    @GetMapping("/analysis/part-type")
    public Result<List<Map<String, Object>>> partType() {
        return Result.success(analysisService.partType());
    }

    /** 废品使用统计 */
    @GetMapping("/analysis/scrap-stats")
    public Result<List<Map<String, Object>>> scrapStats() {
        return Result.success(analysisService.scrapStats());
    }

    @GetMapping("/analysis/life-stats")
    public Result<List<Map<String, Object>>> lifeStats() {
        return Result.success(analysisService.lifeStats());
    }

    @GetMapping("/analysis/stock-forecast")
    public Result<List<Map<String, Object>>> stockForecast() {
        return Result.success(analysisService.stockForecast());
    }

    /** 废品使用统计一键导出 TXT */
    @GetMapping("/analysis/scrap-stats/export")
    public void scrapStatsExport(HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = analysisService.scrapStats();
        List<ScrapStatExcelDTO> rows = new ArrayList<>();
        for (Map<String, Object> m : list) {
            ScrapStatExcelDTO row = new ScrapStatExcelDTO();
            row.setPartNo(str(m.get("partNo")));
            row.setPartName(str(m.get("partName")));
            row.setSpec(str(m.get("spec")));
            row.setUseCount(intOf(m.get("useCount")));
            row.setTotalQty(intOf(m.get("totalQty")));
            row.setDevices(str(m.get("devices")));
            rows.add(row);
        }
        List<String> headers = List.of("备品编码", "备品名称", "规格型号", "使用次数", "使用总量", "涉及设备");
        var data = rows.stream().map(row -> List.of(text(row.getPartNo()), text(row.getPartName()),
                text(row.getSpec()), text(row.getUseCount()), text(row.getTotalQty()), text(row.getDevices()))).toList();
        TabularTextUtil.write(response, "废品使用统计", headers, data);
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer intOf(Object o) {
        if (o == null) return 0;
        return ((Number) o).intValue();
    }

    private String text(Object o) { return o == null ? "" : o.toString(); }

    @GetMapping("/dashboard/summary")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(analysisService.dashboardSummary());
    }
}
