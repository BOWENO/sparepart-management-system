package com.sparepart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparepart.entity.Device;
import com.sparepart.entity.SparePart;
import com.sparepart.mapper.DeviceMapper;
import com.sparepart.mapper.SparePartMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:soft-delete-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SoftDeleteIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired SparePartMapper sparePartMapper;
    @Autowired DeviceMapper deviceMapper;
    @Autowired com.sparepart.service.FlowService flowService;
    @Autowired com.sparepart.service.DeviceExcelService deviceExcelService;

    @Test
    void deletionHidesArchiveButKeepsHistoryAndOperationLogs() throws Exception {
        MockHttpSession session = loginAsTestAdmin();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        String partJson = objectMapper.writeValueAsString(Map.of(
                "partNo", "SD-P-" + suffix,
                "name", "软删除测试备品",
                "unit", "个",
                "goodStock", 8,
                "scrapStock", 0,
                "minStock", 0,
                "status", 1));
        String partResponse = mvc.perform(post("/api/v1/parts").session(session)
                        .contentType("application/json").content(partJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        int partId = objectMapper.readTree(partResponse).path("data").path("id").asInt();

        String lineCode = "SD-L-" + suffix;
        jdbcTemplate.update("INSERT INTO production_lines(line_code,line_name,status) VALUES (?,?,1)",
                lineCode, "软删除测试产线");
        Integer lineId = jdbcTemplate.queryForObject(
                "SELECT id FROM production_lines WHERE line_code=?", Integer.class, lineCode);
        String deviceCode = lineCode + "-D01";
        jdbcTemplate.update("INSERT INTO devices(device_code,local_device_code,device_name,line_name,line_id,status,deleted) " +
                        "VALUES (?,?,?,?,?,1,0)",
                deviceCode, "D01", "软删除测试设备", lineCode, lineId);
        Integer deviceId = jdbcTemplate.queryForObject(
                "SELECT id FROM devices WHERE device_code=?", Integer.class, deviceCode);

        jdbcTemplate.update("INSERT INTO stock_flow(flow_no,flow_type,part_id,device_id,quantity,operator,business_date) " +
                        "VALUES (?,1,?,?,1,'test',CURRENT_DATE)",
                "SD-" + suffix, partId, deviceId);

        mvc.perform(delete("/api/v1/parts/{id}", partId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(delete("/api/v1/devices/{id}", deviceId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM spare_parts WHERE id=?", Integer.class, partId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT deleted FROM devices WHERE id=?", Integer.class, deviceId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_flow WHERE part_id=? AND device_id=?",
                Integer.class, partId, deviceId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE module='备品' AND action='删除' AND after_json LIKE '%软删除测试备品%'",
                Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE module='设备' AND action='删除' AND after_json LIKE '%软删除测试设备%'",
                Integer.class));

        SparePart deletedPart = sparePartMapper.selectById(partId);
        Device deletedDevice = deviceMapper.selectById(deviceId);
        assertNull(deletedPart);
        assertNull(deletedDevice);
        mvc.perform(get("/api/v1/flows").param("partId",String.valueOf(partId)).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].partName").value("软删除测试备品"))
                .andExpect(jsonPath("$.data.records[0].deviceName").value("软删除测试设备"))
                .andExpect(jsonPath("$.data.records[0].lineCode").value(lineCode))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("测试账号"))
                .andExpect(jsonPath("$.data.records[0].recipientName").value("不涉及领用"));
        String flowExport=mvc.perform(get("/api/v1/flows/export").param("partId",String.valueOf(partId)).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(flowExport.contains("软删除测试备品")); assertTrue(flowExport.contains("软删除测试设备"));
        assertTrue(flowExport.contains("备品入库")); assertFalse(flowExport.contains("备品ID")); assertFalse(flowExport.contains("设备ID"));
        assertEquals(8, sparePartMapper.findIncludingDeleted("SD-P-" + suffix).getGoodStock());
        org.junit.jupiter.api.Assertions.assertThrows(com.sparepart.common.BizException.class,
                () -> flowService.stockIn(partId, 1, "test", "已删除档案不能入库", null));
        Long flowId = jdbcTemplate.queryForObject("SELECT id FROM stock_flow WHERE flow_no=?", Long.class, "SD-" + suffix);
        flowService.reverseFlow(flowId, "test", "删除后仍可冲正历史入库");
        assertEquals(7, sparePartMapper.findIncludingDeleted("SD-P-" + suffix).getGoodStock());
        assertNull(sparePartMapper.selectById(partId));
        String imported = "产线编号\t设备编号（产线内）\t设备名称\t状态（启用/停用）\n" + lineCode + "\tD01\t旧设备\t启用\n";
        var upload = new org.springframework.mock.web.MockMultipartFile("file", "devices.txt", "text/plain",
                imported.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var error = org.junit.jupiter.api.Assertions.assertThrows(com.sparepart.common.BizException.class,
                () -> deviceExcelService.importExcel(upload, "test"));
        assertTrue(error.getMessage().contains("已删除档案"));
        assertNull(deviceMapper.selectById(deviceId));

        mvc.perform(get("/api/v1/parts").param("keyword", "SD-P-" + suffix).session(session))
                .andExpect(jsonPath("$.data.total").value(0));
        String devices = mvc.perform(get("/api/v1/devices").session(session))
                .andReturn().getResponse().getContentAsString();
        assertFalse(devices.contains(deviceCode));
        String choices = mvc.perform(get("/api/v1/devices/list").param("lineId", lineId.toString()).session(session))
                .andReturn().getResponse().getContentAsString();
        assertFalse(choices.contains(deviceCode));
        for (String module : new String[]{"parts", "devices"}) {
            String exported = mvc.perform(get("/api/v1/" + module + "/export").session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(exported.contains("软删除测试"));
        }
        String logs = mvc.perform(get("/api/v1/logs").param("module", "备品").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(logs.contains("软删除测试备品"));
        mvc.perform(post("/api/v1/parts").session(session).contentType("application/json").content(partJson))
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("已删除档案")));
    }

    @Test
    void viewerCannotDeleteAndCanReadLogs() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"viewer-local\",\"password\":\"viewer123\"}"))
                .andExpect(jsonPath("$.code").value(0)).andReturn().getRequest().getSession(false);
        mvc.perform(delete("/api/v1/parts/1").session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/devices/1").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/logs").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private MockHttpSession loginAsTestAdmin() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getRequest().getSession(false);
    }
}
