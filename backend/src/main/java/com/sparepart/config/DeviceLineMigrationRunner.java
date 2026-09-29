package com.sparepart.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.entity.Device;
import com.sparepart.entity.ProductionLine;
import com.sparepart.mapper.DeviceMapper;
import com.sparepart.mapper.ProductionLineMapper;
import com.sparepart.service.ProductionLineService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 保留设备主键，无损补充产线关联和“产线-设备编号”组合标识。 */
@Component @Order(10)
public class DeviceLineMigrationRunner implements CommandLineRunner {
    private final DeviceMapper devices; private final ProductionLineMapper lines;
    public DeviceLineMigrationRunner(DeviceMapper devices,ProductionLineMapper lines){this.devices=devices;this.lines=lines;}
    @Override @Transactional(rollbackFor=Exception.class)
    public void run(String...args){
        for(Device device:devices.selectList(new LambdaQueryWrapper<Device>().isNull(Device::getLocalDeviceCode).orderByAsc(Device::getId))){
            String lineCode=ProductionLineService.normalize(device.getLineName());if(lineCode==null||lineCode.isBlank())lineCode="UNASSIGNED";
            ProductionLine line=lines.selectOne(new LambdaQueryWrapper<ProductionLine>().eq(ProductionLine::getLineCode,lineCode));
            if(line==null){line=new ProductionLine();line.setLineCode(lineCode);line.setLineName(lineCode);line.setStatus(1);lines.insert(line);}
            String local=device.getDeviceCode().trim();device.setLocalDeviceCode(local);device.setLineId(line.getId());device.setLineName(line.getLineCode());
            device.setDeviceCode(line.getLineCode()+"-"+local);devices.updateById(device);
        }
    }
}
