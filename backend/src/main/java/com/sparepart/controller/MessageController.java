package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.Result;
import com.sparepart.config.RequireRole;
import com.sparepart.entity.InternalMessage;
import com.sparepart.service.InternalMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final InternalMessageService messageService;

    public MessageController(InternalMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public Result<Page<InternalMessage>> page(@RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size,
                                              @RequestParam(required = false) Integer msgType,
                                              @RequestParam(required = false) Integer isRead) {
        return Result.success(messageService.page(page, size, msgType, isRead));
    }

    @PostMapping("/{id}/read")
    @RequireRole
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success();
    }
}