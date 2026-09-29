package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.entity.Device;
import com.sparepart.mapper.DeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final ProductionLineService lineService;

    public DeviceService(DeviceMapper deviceMapper, ProductionLineService lineService) {
        this.deviceMapper = deviceMapper;
        this.lineService = lineService;
    }

    /** 下拉列表专用：仅启用设备 */
    public List<Device> listAll() {
        return listAll(null);
    }
    public List<Device> listAll(Integer lineId) {
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<Device>().eq(Device::getStatus, 1);
        if (lineId != null) query.eq(Device::getLineId, lineId);
        return deviceMapper.selectList(query.orderByAsc(Device::getLocalDeviceCode));
    }

    /** 台账管理使用：包含启用和停用设备。 */
    public List<Device> ledger() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                .orderByAsc(Device::getDeviceCode));
    }

    public Device save(Device device) {
        com.sparepart.entity.ProductionLine line=lineService.requireActive(device.getLineId());
        String local=device.getLocalDeviceCode();
        if(local==null||local.isBlank())local=device.getDeviceCode();
        if(local==null||local.isBlank())throw new BizException("设备编号不能为空");
        local=local.trim().toUpperCase();device.setLocalDeviceCode(local);device.setLineId(line.getId());device.setLineName(line.getLineCode());
        device.setDeviceCode(line.getLineCode()+"-"+local);
        if (device.getStatus() == null) device.setStatus(1);
        if (device.getId() == null) {
            ensureCodeUnique(device.getDeviceCode(), null);
            deviceMapper.insert(device);
        } else {
            Device db = deviceMapper.selectById(device.getId());
            if (db == null) throw new BizException("设备不存在");
            ensureCodeUnique(device.getDeviceCode(), device.getId());
            deviceMapper.updateById(device);
        }
        return device;
    }

    private void ensureCodeUnique(String code, Integer excludeId) {
        Device archived = deviceMapper.findIncludingDeleted(code);
        if (archived != null && Integer.valueOf(1).equals(archived.getDeleted()))
            throw new BizException("设备编号已用于已删除档案，历史记录仍保留，请使用其他编号：" + code);
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<Device>().eq(Device::getDeviceCode, code);
        if (excludeId != null) query.ne(Device::getId, excludeId);
        if (deviceMapper.selectCount(query) > 0) throw new BizException("该产线内设备编号已存在：" + code);
    }

    @Transactional
    public Device delete(Integer id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) throw new BizException("设备不存在或已删除");
        deviceMapper.deleteById(id);
        device.setDeleted(1);
        return device;
    }
}
