package com.sparepart;

import com.sparepart.common.BizException;
import com.sparepart.dto.ReplaceBatchRequest;
import com.sparepart.dto.ReplaceItem;
import com.sparepart.entity.*;
import com.sparepart.mapper.*;
import com.sparepart.service.FlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:batch-life-update;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
class BatchLifeIntegrationTest {
    @Autowired FlowService service;
    @Autowired SparePartMapper parts;
    @Autowired ProductionLineMapper lines;
    @Autowired DeviceMapper devices;
    @Autowired TechnicianMapper technicians;
    @Autowired ReplaceRecordMapper records;
    @Autowired com.sparepart.service.StockFlowReadService flowReader;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    SparePart part; Device device; Technician technician;

    @BeforeEach void setup() {
        String key=UUID.randomUUID().toString();
        ProductionLine line=new ProductionLine(); line.setLineCode(key); line.setStatus(1); lines.insert(line);
        device=new Device(); device.setLineId(line.getId()); device.setDeviceCode(key); device.setLocalDeviceCode("1"); device.setDeviceName("测试设备"); device.setStatus(1); devices.insert(device);
        technician=new Technician(); technician.setEmployeeNo(key); technician.setName("测试员"); technician.setStatus(1); technicians.insert(technician);
        part=new SparePart(); part.setPartNo(key); part.setName("测试件"); part.setStatus(1); part.setGoodStock(100); part.setScrapStock(0); part.setMinStock(0); part.setRatedLifeCount(100L); parts.insert(part);
    }
    ReplaceItem item(int qty,int oldQty,long count) {
        ReplaceItem item=new ReplaceItem(); item.setNewPartId(part.getId()); item.setOldPartId(part.getId());
        item.setDeviceId(device.getId()); item.setTechnicianId(technician.getId()); item.setQuantity(qty); item.setOldQuantity(oldQty);
        item.setActualUsageCount(count); item.setReplacementReason("LIFE_LIMIT"); item.setDifferenceReason("测试数量差异"); return item;
    }
    ReplaceBatchRequest request(ReplaceItem... items) { ReplaceBatchRequest r=new ReplaceBatchRequest();r.setRequestId(UUID.randomUUID().toString());r.setItems(List.of(items));return r; }
    Number field(Map<String,Object> row,String name) {return (Number)row.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).findFirst().orElseThrow().getValue();}
    Map<String,Object> stat(boolean life) {return (life?records.lifeStats():records.scrapStats()).stream().filter(r->field(r,"partId").intValue()==part.getId()).findFirst().orElseThrow();}

    @Test void batchLifeWeightedStatisticsAndCancellation() {
        var first=service.replaceBatch(request(item(10,10,100)),"test");
        var second=service.replaceBatch(request(item(1,1,210)),"test");
        assertEquals(89,parts.selectById(part.getId()).getGoodStock());
        assertEquals(11,parts.selectById(part.getId()).getScrapStock());
        assertEquals(110,field(stat(true),"avgActualUsage").intValue());
        assertEquals(11,field(stat(true),"forcedCount").intValue());
        assertThrows(BizException.class, () ->
                service.cancelReplacement((String)first.get("flowNo"),"test","测试撤销"));
        service.cancelReplacement((String)second.get("flowNo"),"test","撤销后续换新");
        service.cancelReplacement((String)first.get("flowNo"),"test","测试撤销");
        assertEquals(100,parts.selectById(part.getId()).getGoodStock());
        assertEquals(0,parts.selectById(part.getId()).getScrapStock());
    }
    @Test void mismatchedQuantitiesUseOldQuantityAndRetryIsIdempotent() {
        var req=request(item(3,2,100)); service.replaceBatch(req,"test");
        assertEquals(true,service.replaceBatch(req,"test").get("duplicate"));
        assertEquals(97,parts.selectById(part.getId()).getGoodStock());
        assertEquals(2,field(stat(false),"totalQty").intValue());
    }
    @Test void archivedPartsAndDevicesKeepHistoryAndCanCancelReplacement() {
        var result=service.replaceBatch(request(item(3,3,100)),"test");
        parts.deleteById(part.getId()); devices.deleteById(device.getId());
        assertNull(parts.selectById(part.getId()));
        assertEquals(3,field(stat(true),"forcedCount").intValue());
        assertThrows(BizException.class,()->service.replaceBatch(request(item(1,1,100)),"test"));
        service.cancelReplacement((String)result.get("flowNo"),"test","删除后的历史撤销");
        SparePart archived=parts.findIncludingDeleted(part.getPartNo());
        assertEquals(100,archived.getGoodStock());
        assertEquals(0,archived.getScrapStock());
        assertEquals(1,archived.getDeleted());
        assertNull(devices.selectById(device.getId()));
    }
    @Test void failingSecondRowRollsBackEntireBatch() {
        assertThrows(BizException.class,()->service.replaceBatch(request(item(10,10,100),item(999,1,100)),"test"));
        assertEquals(100,parts.selectById(part.getId()).getGoodStock());
        assertEquals(0,parts.selectById(part.getId()).getScrapStock());
    }
    @Test void thresholdStillEnforcedAndFailureSupportsBatch() {
        assertThrows(BizException.class,()->service.replaceBatch(request(item(2,2,99)),"test"));
        ReplaceItem failure=item(2,2,90); failure.setReplacementReason("FAILURE"); failure.setScrapReason("故障");
        service.replaceBatch(request(failure),"test");
        assertEquals(2,field(stat(true),"failureCount").intValue());
    }
    @Test void readableFlowsIdentifyEachRecipientEvenForIdenticalRowsAndKeepDeletedNames() {
        technician.setName("领用人甲"); technicians.updateById(technician);
        Technician second=new Technician(); second.setEmployeeNo(UUID.randomUUID().toString()); second.setName("领用人乙"); second.setStatus(1); technicians.insert(second);
        ReplaceItem one=item(2,2,100), two=item(2,2,100); two.setTechnicianId(second.getId());
        var result=service.replaceBatch(request(one,two),"test");
        var rows=flowReader.list(3,null,part.getId(),device.getId());
        assertEquals(2,rows.size());
        assertEquals(Set.of("领用人甲","领用人乙"),rows.stream().map(r->r.getRecipientName()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rows.stream().allMatch(r->"测试设备".equals(r.getDeviceName()) && "测试件".equals(r.getPartName()) && r.getLineCode()!=null));
        assertEquals(2,flowReader.page(1,1,3,null,part.getId(),device.getId()).getTotal());
        assertEquals(1,flowReader.page(1,1,3,null,part.getId(),device.getId()).getRecords().size());
        parts.deleteById(part.getId()); devices.deleteById(device.getId());
        assertEquals("测试件",flowReader.list(3,null,part.getId(),device.getId()).get(0).getPartName());
        service.cancelReplacement((String)result.get("flowNo"),"test","测试");
        assertEquals(Set.of("领用人甲","领用人乙"),flowReader.list(10,null,part.getId(),device.getId()).stream()
                .map(r->r.getRecipientName()).collect(java.util.stream.Collectors.toSet()));
        jdbc.update("UPDATE stock_flow SET replace_record_id=NULL WHERE flow_no=?",result.get("flowNo"));
        assertTrue(flowReader.list(3,null,part.getId(),device.getId()).stream().allMatch(r->"同单多人，待核对".equals(r.getRecipientName())));
    }
    @Test void legacyFlowWithOneRecipientCanShowNameWithoutConfusingOperator() {
        var result=service.replaceBatch(request(item(2,2,100)),"test");
        jdbc.update("UPDATE stock_flow SET replace_record_id=NULL WHERE flow_no=?",result.get("flowNo"));
        var rows=flowReader.list(3,null,part.getId(),device.getId());
        assertEquals("测试员",rows.get(0).getRecipientName());
        assertEquals("测试账号",rows.get(0).getOperatorName());
        assertEquals("更换领新",rows.get(0).getFlowTypeName());
    }
}
