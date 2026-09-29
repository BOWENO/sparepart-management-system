package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.Device;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM devices WHERE device_code = #{code}")
    Device findIncludingDeleted(@org.apache.ibatis.annotations.Param("code") String code);
}
