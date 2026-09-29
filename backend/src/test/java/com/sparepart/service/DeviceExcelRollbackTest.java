package com.sparepart.service;

import com.sparepart.common.BizException;
import com.sparepart.entity.Device;
import com.sparepart.entity.ExcelImportBatch;
import com.sparepart.entity.ExcelImportRow;
import com.sparepart.mapper.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceExcelRollbackTest {
    @Test
    void rollbackIsRejectedAfterDeviceBusinessOccurred() {
        DeviceMapper devices = mock(DeviceMapper.class);
        ExcelImportBatchMapper batches = mock(ExcelImportBatchMapper.class);
        ExcelImportRowMapper rows = mock(ExcelImportRowMapper.class);
        StockFlowMapper flows = mock(StockFlowMapper.class);
        ReplaceRecordMapper replacements = mock(ReplaceRecordMapper.class);
        DeviceExcelService service = new DeviceExcelService(devices, batches, rows, flows, replacements,
                mock(ProductionLineService.class));

        ExcelImportBatch batch = new ExcelImportBatch(); batch.setBatchNo("B1"); batch.setModule("DEVICE");
        batch.setStatus("SUCCESS"); batch.setCreatedAt(LocalDateTime.now().minusHours(1));
        ExcelImportRow row = new ExcelImportRow(); row.setRecordId(1); row.setWasNew(1);
        row.setAfterJson("{\"id\":1,\"deviceCode\":\"D1\",\"deviceName\":\"设备\",\"lineName\":\"一线\",\"status\":1}");
        Device current = new Device(); current.setId(1); current.setDeviceCode("D1");
        current.setDeviceName("设备"); current.setLineName("一线"); current.setStatus(1);
        when(batches.selectById("B1")).thenReturn(batch);
        when(rows.selectList(any())).thenReturn(List.of(row));
        when(devices.selectById(1)).thenReturn(current);
        when(flows.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () -> service.rollback("B1"));
        verify(devices, never()).updateById(any());
    }
}
