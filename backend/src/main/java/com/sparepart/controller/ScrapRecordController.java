package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.Result;
import com.sparepart.service.ScrapRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/scrap-records")
public class ScrapRecordController {
    private final ScrapRecordService service;
    public ScrapRecordController(ScrapRecordService service) { this.service = service; }

    @GetMapping
    public Result<Page<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(service.page(page, size, keyword, status));
    }
}
