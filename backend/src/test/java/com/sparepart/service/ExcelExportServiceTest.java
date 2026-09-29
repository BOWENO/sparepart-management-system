package com.sparepart.service;

import com.sparepart.dto.DeviceExcelDTO;
import com.sparepart.dto.SparePartExcelDTO;
import com.sparepart.entity.Device;
import com.sparepart.entity.SparePart;
import com.sparepart.mapper.DeviceMapper;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import com.sparepart.mapper.ExcelImportBatchMapper;
import com.sparepart.mapper.ExcelImportRowMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelExportServiceTest {

    @Test
    void sparePartExportContainsCurrentLedgerValues() {
        SparePartMapper mapper = mock(SparePartMapper.class);
        SparePart part = new SparePart();
        part.setPartNo("BP-001");
        part.setName("轴承");
        part.setSpec("6204");
        part.setUnit("个");
        part.setLocationCode("A-01");
        part.setGoodStock(18);
        part.setMinStock(5);
        part.setScrapStock(3);
        part.setStatus(1);
        when(mapper.selectList(any())).thenReturn(List.of(part));

        SparePartExcelService service = new SparePartExcelService(mapper, mock(StockFlowMapper.class),
                mock(ExcelImportBatchMapper.class), mock(ExcelImportRowMapper.class));
        List<SparePartExcelDTO> rows = service.buildExportRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPartNo()).isEqualTo("BP-001");
        assertThat(rows.get(0).getGoodStock()).isEqualTo(18);
        assertThat(rows.get(0).getMinStock()).isEqualTo(5);
        assertThat(rows.get(0).getScrapStock()).isEqualTo(3);
        assertThat(rows.get(0).getStatus()).isEqualTo("启用");
    }

    @Test
    void deviceExportContainsCurrentLedgerValues() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        Device device = new Device();
        device.setDeviceCode("SB-001");
        device.setLocalDeviceCode("SB-001");
        device.setDeviceName("包装机");
        device.setLineName("一号线");
        when(mapper.selectList(any())).thenReturn(List.of(device));

        DeviceExcelService service = new DeviceExcelService(mapper, mock(ExcelImportBatchMapper.class),
                mock(ExcelImportRowMapper.class), mock(com.sparepart.mapper.StockFlowMapper.class),
                mock(com.sparepart.mapper.ReplaceRecordMapper.class), mock(ProductionLineService.class));
        List<DeviceExcelDTO> rows = service.buildExportRows();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getDeviceCode()).isEqualTo("SB-001");
        assertThat(rows.get(0).getDeviceName()).isEqualTo("包装机");
        assertThat(rows.get(0).getLineName()).isEqualTo("一号线");
    }
}
