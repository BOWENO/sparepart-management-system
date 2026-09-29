package com.sparepart.service;

import com.sparepart.common.BizException;
import com.sparepart.dto.ReplaceBatchRequest;
import com.sparepart.dto.ReplaceItem;
import com.sparepart.entity.SparePart;
import com.sparepart.mapper.InternalMessageMapper;
import com.sparepart.mapper.ReplaceRecordMapper;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import com.sparepart.mapper.DeviceMapper;
import com.sparepart.mapper.RequestDedupMapper;
import com.sparepart.mapper.ScrapRecordMapper;
import com.sparepart.mapper.ScrapDisposalItemMapper;
import com.sparepart.entity.Device;
import com.sparepart.entity.ReplaceRecord;
import com.sparepart.entity.ScrapRecord;
import com.sparepart.entity.StockFlow;
import com.sparepart.entity.InstalledPart;
import com.sparepart.entity.RequestDedup;
import com.sparepart.entity.Technician;
import com.sparepart.mapper.TechnicianMapper;
import com.sparepart.mapper.InstalledPartMapper;
import com.sparepart.mapper.InstalledPartRetirementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlowServiceTest {
    private SparePartMapper parts;
    private StockFlowMapper flows;
    private ReplaceRecordMapper replacements;
    private InternalMessageMapper messages;
    private FlowService service;
    private DeviceMapper devices;
    private RequestDedupMapper dedup;
    private ScrapRecordMapper scraps;
    private ScrapDisposalItemMapper disposalItems;
    private TechnicianMapper technicians;
    private InstalledPartMapper installedParts;

    @BeforeEach
    void setUp() {
        parts = mock(SparePartMapper.class);
        flows = mock(StockFlowMapper.class);
        replacements = mock(ReplaceRecordMapper.class);
        messages = mock(InternalMessageMapper.class);
        devices = mock(DeviceMapper.class);
        dedup = mock(RequestDedupMapper.class);
        scraps = mock(ScrapRecordMapper.class);
        disposalItems = mock(ScrapDisposalItemMapper.class);
        technicians = mock(TechnicianMapper.class);
        installedParts = mock(InstalledPartMapper.class);
        when(devices.selectById(10)).thenReturn(device());
        Technician technician = new Technician(); technician.setId(20); technician.setStatus(1);
        when(technicians.selectById(20)).thenReturn(technician);
        service = new FlowService(parts, flows, replacements, messages, devices, dedup,
                mock(InternalMessageService.class), scraps, disposalItems, technicians,
                installedParts, mock(InstalledPartRetirementMapper.class));
    }

    @Test
    void normalReplacementAddsScrapToReturnedOldPart() {
        SparePart newPart = part(1, "新件");
        SparePart oldPart = part(2, "旧件");
        when(parts.selectById(1)).thenReturn(newPart, newPart);
        when(parts.selectById(2)).thenReturn(oldPart);
        when(parts.deductGoodStock(1, 2)).thenReturn(1);

        service.replaceBatch(request(item(1, 2, false, null)), "admin");

        verify(parts).addScrapStock(2, 2);
        verify(parts, never()).addScrapStock(1, 2);
        verify(flows, times(2)).insert(any());
        verify(replacements).insert(argThat(r -> r.getOldPartId().equals(2)));
        verify(installedParts).insert(argThat(r -> r.getPartId().equals(1)
                && r.getDeviceId().equals(10) && r.getTechnicianId().equals(20)
                && r.getInUseQuantity().equals(2)));
    }

    @Test
    void normalReplacementRequiresOldPart() {
        when(parts.selectById(1)).thenReturn(part(1, "新件"));
        when(parts.deductGoodStock(1, 2)).thenReturn(1);
        assertThrows(BizException.class,
                () -> service.replaceBatch(request(item(1, null, false, null)), "admin"));
        verify(parts, never()).deductGoodStock(anyInt(), anyInt());
        verify(parts, never()).addScrapStock(anyInt(), anyInt());
    }

    @Test
    void exceptionalReplacementRequiresReason() {
        when(parts.selectById(1)).thenReturn(part(1, "新件"));
        when(parts.deductGoodStock(1, 2)).thenReturn(1);
        assertThrows(BizException.class,
                () -> service.replaceBatch(request(item(1, null, true, " ")), "admin"));
        verify(parts, never()).deductGoodStock(anyInt(), anyInt());
        verify(flows, never()).insert(any());
    }

    @Test
    void replacementChildFlowCannotBeReversedAlone() {
        StockFlow flow = new StockFlow(); flow.setId(1L); flow.setFlowType(3); flow.setQuantity(1); flow.setPartId(1);
        when(flows.selectForUpdate(1L)).thenReturn(flow);
        assertThrows(BizException.class, () -> service.reverseFlow(1L, "admin", "录入错误"));
        verify(parts, never()).addGoodStock(anyInt(), anyInt());
    }

    @Test
    void normalReplacementIsCancelledAsOneBusinessOrder() {
        ReplaceRecord record = new ReplaceRecord(); record.setId(11L); record.setFlowNo("F1");
        record.setNewPartId(1); record.setOldPartId(2); record.setQuantity(1); record.setOldQuantity(1);
        ScrapRecord scrap = new ScrapRecord(); scrap.setId(21L); scrap.setFlowNo("F1"); scrap.setPartId(2);
        scrap.setQuantity(1); scrap.setRemainingQuantity(1); scrap.setStatus("PENDING");
        StockFlow newOut = new StockFlow(); newOut.setId(31L); newOut.setFlowNo("F1");
        newOut.setFlowType(3); newOut.setPartId(1); newOut.setQuantity(1);
        when(replacements.selectList(any())).thenReturn(List.of(record));
        when(scraps.selectList(any())).thenReturn(List.of(scrap));
        when(flows.selectList(any())).thenReturn(List.of(newOut));
        when(parts.correctHistoricalStock(1, 1, 0)).thenReturn(1);
        when(parts.correctHistoricalStock(2, 0, -1)).thenReturn(1);

        service.cancelReplacement("F1", "admin", "录入错误");

        verify(parts).correctHistoricalStock(1, 1, 0);
        verify(parts).correctHistoricalStock(2, 0, -1);
        verify(replacements).updateById(argThat(row -> "CANCELLED".equals(row.getStatus())));
        verify(scraps).updateById(argThat(row -> "REVERSED".equals(row.getStatus()) && row.getRemainingQuantity() == 0));
    }

    @Test
    void cancellationRejectsIssuedPartUsedByLaterReplacement() {
        ReplaceRecord record = new ReplaceRecord(); record.setId(11L); record.setFlowNo("F1");
        record.setNewPartId(1); record.setQuantity(2);
        InstalledPart issued = new InstalledPart(); issued.setQuantity(2); issued.setInUseQuantity(1);
        when(replacements.selectList(any())).thenReturn(List.of(record));
        when(scraps.selectList(any())).thenReturn(List.of());
        when(installedParts.selectList(any())).thenReturn(List.of(issued));

        assertThrows(BizException.class, () -> service.cancelReplacement("F1", "admin", "录入错误"));
        verify(parts, never()).correctHistoricalStock(anyInt(), anyInt(), anyInt());
    }

    @Test
    void scrapDisposalRejectsSummaryArchiveMismatch() {
        when(parts.selectById(2)).thenReturn(part(2, "旧件"));
        when(scraps.selectList(any())).thenReturn(List.of());
        assertThrows(BizException.class, () -> service.scrapOut(2, null, 1, "admin", "清运"));
        verify(parts, never()).deductScrapStock(anyInt(), anyInt());
    }

    @Test
    void reusedRequestIdWithDifferentPayloadIsRejected() {
        RequestDedup existing = new RequestDedup(); existing.setRequestId("REQ-1");
        existing.setFlowNo("OLD-FLOW"); existing.setOperator("admin"); existing.setPayloadHash("different");
        existing.setItemCount(1); when(dedup.selectById("REQ-1")).thenReturn(existing);
        assertThrows(BizException.class,
                () -> service.replaceBatch(request(item(1, 2, false, null)), "admin"));
        verify(parts, never()).deductGoodStock(anyInt(), anyInt());
    }

    private static ReplaceBatchRequest request(ReplaceItem item) {
        ReplaceBatchRequest request = new ReplaceBatchRequest();
        request.setItems(List.of(item));
        request.setRequestId("REQ-1");
        return request;
    }

    private static ReplaceItem item(Integer newId, Integer oldId, boolean exception, String reason) {
        ReplaceItem item = new ReplaceItem();
        item.setNewPartId(newId);
        item.setOldPartId(oldId);
        item.setDeviceId(10);
        item.setTechnicianId(20);
        item.setQuantity(2);
        item.setIsException(exception);
        item.setExceptionRemark(reason);
        if (!exception) {
            item.setReplacementReason("FAILURE");
            item.setActualUsageCount(12000L);
            item.setScrapReason("损坏");
        }
        if (!exception && oldId != null && !oldId.equals(newId)) item.setDifferenceReason("型号替代");
        return item;
    }

    private static SparePart part(int id, String name) {
        SparePart part = new SparePart();
        part.setId(id);
        part.setPartNo("P-" + id);
        part.setName(name);
        part.setGoodStock(10);
        part.setMinStock(0);
        part.setPrice(BigDecimal.TEN);
        part.setStatus(1);
        return part;
    }

    private static Device device() {
        Device d = new Device(); d.setId(10); d.setStatus(1); return d;
    }
}
