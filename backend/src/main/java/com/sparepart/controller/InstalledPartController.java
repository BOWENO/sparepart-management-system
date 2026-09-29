package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.Result;
import com.sparepart.mapper.InstalledPartMapper;
import org.springframework.web.bind.annotation.*;
import com.sparepart.dto.InstalledPartView;
import com.sparepart.dto.InstalledDeviceSummary;

@RestController @RequestMapping("/api/v1/installed-parts")
public class InstalledPartController {
    private final InstalledPartMapper mapper;
    public InstalledPartController(InstalledPartMapper mapper){this.mapper=mapper;}
    @GetMapping public Result<Page<InstalledPartView>> list(@RequestParam(defaultValue="1") long page,
            @RequestParam(defaultValue="10") long size,@RequestParam(required=false) Integer partId,
            @RequestParam(required=false) Integer deviceId,@RequestParam(required=false) String keyword){return Result.success(mapper.selectInUse(new Page<>(page,size),partId,deviceId,keyword));}

    @GetMapping("/devices") public Result<Page<InstalledDeviceSummary>> devices(
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="10") long size,
            @RequestParam(required=false) String keyword){return Result.success(mapper.selectDeviceSummaries(new Page<>(page,size),keyword));}
    @GetMapping("/devices/{deviceId}/history") public Result<java.util.List<InstalledPartView>> history(@PathVariable Integer deviceId){
        return Result.success(mapper.selectDeviceHistory(deviceId));
    }
}
