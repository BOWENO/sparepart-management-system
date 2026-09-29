package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.common.UserContext;
import com.sparepart.dto.CheckRequest;
import com.sparepart.entity.InventoryCheck;
import com.sparepart.entity.User;
import com.sparepart.service.CheckService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checks")
public class CheckController {

    private final CheckService checkService;

    public CheckController(CheckService checkService) {
        this.checkService = checkService;
    }

    @PostMapping("/do-check")
    @OpLog(module = "盘点", action = "提交盘点")
    public Result<InventoryCheck> doCheck(@Valid @RequestBody CheckRequest req) {
        User user = UserContext.get();
        String operator = user == null ? "system" : user.getUsername();
        return Result.success("盘点完成", checkService.doCheck(req, operator));
    }

    @GetMapping
    public Result<Page<InventoryCheck>> page(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "10") Integer size,
                                             @RequestParam(required = false) Integer partId) {
        return Result.success(checkService.page(page, size, partId));
    }
}