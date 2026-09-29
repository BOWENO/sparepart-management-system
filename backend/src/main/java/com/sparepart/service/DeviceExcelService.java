package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.dto.DeviceExcelDTO;
import com.sparepart.entity.Device;
import com.sparepart.mapper.DeviceMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.sparepart.mapper.ExcelImportBatchMapper;
import com.sparepart.mapper.ExcelImportRowMapper;
import com.sparepart.entity.ExcelImportBatch;
import com.sparepart.entity.ExcelImportRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparepart.entity.StockFlow;
import com.sparepart.entity.ReplaceRecord;
import com.sparepart.mapper.StockFlowMapper;
import com.sparepart.mapper.ReplaceRecordMapper;
import java.util.UUID;

/**
 * 设备 Excel 导入导出。
 * 导入逻辑：产线编号 + 产线内设备编号已存在则更新，不存在则新增。
 */
@Service
public class DeviceExcelService {
    private static final List<String> HEADERS = List.of("产线编号", "设备编号（产线内）", "设备名称", "状态（启用/停用）");

    private final DeviceMapper deviceMapper;
    private final ExcelImportBatchMapper batchMapper;
    private final ExcelImportRowMapper importRowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final StockFlowMapper stockFlowMapper;
    private final ReplaceRecordMapper replaceRecordMapper;
    private final ProductionLineService lineService;

    public DeviceExcelService(DeviceMapper deviceMapper, ExcelImportBatchMapper batchMapper,
                              ExcelImportRowMapper importRowMapper, StockFlowMapper stockFlowMapper,
                              ReplaceRecordMapper replaceRecordMapper, ProductionLineService lineService) {
        this.deviceMapper = deviceMapper;
        this.batchMapper = batchMapper;
        this.importRowMapper = importRowMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.replaceRecordMapper = replaceRecordMapper;
        this.lineService = lineService;
    }

    /** 导出导入模板 */
    public void exportTemplate(HttpServletResponse response) throws IOException {
        TabularTextUtil.write(response, "设备空白模板", HEADERS, List.of());
    }

    public void exportLedger(HttpServletResponse response) throws IOException {
        var data = buildExportRows().stream().map(row -> List.of(
                text(row.getLineName()), text(row.getDeviceCode()), text(row.getDeviceName()), text(row.getStatus()))).toList();
        TabularTextUtil.write(response, "设备当前台账", HEADERS, data);
    }

    /** 导出当前设备台账；导出文件可直接修改后重新导入。 */
    List<DeviceExcelDTO> buildExportRows() {
        return deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                        .orderByAsc(Device::getDeviceCode))
                .stream()
                .map(device -> {
                    DeviceExcelDTO row = new DeviceExcelDTO();
                    row.setDeviceCode(device.getLocalDeviceCode() == null ? device.getDeviceCode() : device.getLocalDeviceCode());
                    row.setDeviceName(device.getDeviceName());
                    row.setLineName(device.getLineName());
                    row.setStatus(Integer.valueOf(1).equals(device.getStatus()) ? "启用" : "停用");
                    return row;
                })
                .toList();
    }

    /** 导入设备，返回 {newCount, updateCount} */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, String operator) throws IOException {
        List<DeviceExcelDTO> rows = readAndValidate(file);
        return applyRows(rows, operator, file.getOriginalFilename());
    }

    public Map<String, Object> preview(MultipartFile file) throws IOException {
        List<DeviceExcelDTO> rows = readAndValidate(file);
        int newCount = 0;
        for (DeviceExcelDTO row : rows) if (find(row.getLineName(), row.getDeviceCode()) == null) newCount++;
        return Map.of("totalCount", rows.size(), "newCount", newCount,
                "updateCount", rows.size() - newCount, "rows", rows.stream().limit(100).toList());
    }

    private List<DeviceExcelDTO> readAndValidate(MultipartFile file) throws IOException {
        List<TabularTextUtil.TextRow> textRows = TabularTextUtil.read(file, HEADERS, 1000);
        List<DeviceExcelDTO> rows = textRows.stream().map(text -> {
            DeviceExcelDTO row = new DeviceExcelDTO(); row.setLineName(text.get(HEADERS.get(0)));
            row.setDeviceCode(text.get(HEADERS.get(1))); row.setDeviceName(text.get(HEADERS.get(2)));
            row.setStatus(text.get(HEADERS.get(3))); return row;
        }).toList();
        java.util.Set<String> codes = new java.util.HashSet<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            DeviceExcelDTO row = rows.get(i);
            int excelRow = textRows.get(i).lineNumber();
            if (!StringUtils.hasText(row.getLineName())) errors.add("第" + excelRow + "行：产线编号不能为空");
            if (!StringUtils.hasText(row.getDeviceCode())) errors.add("第" + excelRow + "行：设备编号不能为空");
            if (!StringUtils.hasText(row.getDeviceName())) errors.add("第" + excelRow + "行：设备名称不能为空");
            String code = StringUtils.hasText(row.getDeviceCode()) ? row.getDeviceCode().trim().toUpperCase() : "";
            String line = StringUtils.hasText(row.getLineName()) ? row.getLineName().trim().toUpperCase() : "";
            if (!code.isEmpty() && !line.isEmpty() && !codes.add(line + "-" + code)) errors.add("第" + excelRow + "行：产线与设备编号组合在文件中重复：" + line + "-" + code);
        }
        if (!errors.isEmpty()) throw new BizException(String.join("；", errors));
        return rows;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private Map<String, Object> applyRows(List<DeviceExcelDTO> rows, String operator, String fileName) throws IOException {
        String batchNo = UUID.randomUUID().toString().replace("-", "");
        ExcelImportBatch batch = new ExcelImportBatch(); batch.setBatchNo(batchNo); batch.setModule("DEVICE");
        batch.setOperator(operator); batch.setFileName(fileName); batch.setStatus("SUCCESS"); batchMapper.insert(batch);
        int newCount = 0;
        int updateCount = 0;
        for (DeviceExcelDTO row : rows) {
            if (row.getDeviceCode() == null || !StringUtils.hasText(row.getDeviceCode())) {
                continue;
            }
            com.sparepart.entity.ProductionLine line = lineService.findOrCreate(row.getLineName());
            String localCode = row.getDeviceCode().trim().toUpperCase();
            Device exists = find(line.getLineCode(), localCode);
            if (exists == null) {
                Device device = new Device();
                device.setLocalDeviceCode(localCode);
                device.setDeviceCode(line.getLineCode() + "-" + localCode);
                device.setDeviceName(row.getDeviceName());
                device.setLineId(line.getId()); device.setLineName(line.getLineCode());
                device.setStatus(1);
                if ("停用".equals(row.getStatus())) device.setStatus(0);
                deviceMapper.insert(device);
                saveRow(batchNo, device.getId(), true, null, objectMapper.writeValueAsString(device));
                newCount++;
            } else {
                String before = objectMapper.writeValueAsString(exists);
                if (StringUtils.hasText(row.getDeviceName())) exists.setDeviceName(row.getDeviceName());
                exists.setLineId(line.getId()); exists.setLineName(line.getLineCode());
                exists.setLocalDeviceCode(localCode); exists.setDeviceCode(line.getLineCode()+"-"+localCode);
                if (StringUtils.hasText(row.getStatus())) exists.setStatus("停用".equals(row.getStatus()) ? 0 : 1);
                deviceMapper.updateById(exists);
                saveRow(batchNo, exists.getId(), false, before, objectMapper.writeValueAsString(exists));
                updateCount++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("batchNo", batchNo);
        result.put("newCount", newCount);
        result.put("updateCount", updateCount);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(String batchNo) throws IOException {
        ExcelImportBatch batch = batchMapper.selectById(batchNo);
        if (batch == null || !"DEVICE".equals(batch.getModule())) throw new BizException("导入批次不存在");
        if ("ROLLED_BACK".equals(batch.getStatus())) throw new BizException("该批次已经回滚");
        for (ExcelImportRow row : importRowMapper.selectList(new LambdaQueryWrapper<ExcelImportRow>()
                .eq(ExcelImportRow::getBatchNo, batchNo).orderByDesc(ExcelImportRow::getId))) {
            Device current = deviceMapper.selectById(row.getRecordId());
            if (current == null) continue;
            if (row.getAfterJson() != null && !sameSnapshot(current, row.getAfterJson())) {
                throw new BizException("设备【" + current.getDeviceCode() + "】导入后又被人工修改，不能自动回滚");
            }
            long laterFlows = stockFlowMapper.selectCount(new LambdaQueryWrapper<StockFlow>()
                    .eq(StockFlow::getDeviceId, current.getId()).gt(StockFlow::getCreatedAt, batch.getCreatedAt()));
            long laterReplacements = replaceRecordMapper.selectCount(new LambdaQueryWrapper<ReplaceRecord>()
                    .eq(ReplaceRecord::getDeviceId, current.getId()).gt(ReplaceRecord::getCreatedAt, batch.getCreatedAt()));
            if (laterFlows > 0 || laterReplacements > 0) {
                throw new BizException("设备【" + current.getDeviceCode() + "】已经发生换新或库存业务，不能回滚导入");
            }
            if (Integer.valueOf(1).equals(row.getWasNew())) current.setStatus(0);
            else current = objectMapper.readValue(row.getBeforeJson(), Device.class);
            deviceMapper.updateById(current);
        }
        batch.setStatus("ROLLED_BACK"); batch.setRolledBackAt(LocalDateTime.now()); batchMapper.updateById(batch);
    }

    private void saveRow(String batchNo, Integer id, boolean isNew, String before, String after) {
        ExcelImportRow row = new ExcelImportRow(); row.setBatchNo(batchNo); row.setRecordId(id);
        row.setWasNew(isNew ? 1 : 0); row.setBeforeJson(before); row.setAfterJson(after); importRowMapper.insert(row);
    }

    private boolean sameSnapshot(Device current, String json) throws IOException {
        Device imported = objectMapper.readValue(json, Device.class);
        return java.util.Objects.equals(current.getDeviceCode(), imported.getDeviceCode())
                && java.util.Objects.equals(current.getDeviceName(), imported.getDeviceName())
                && java.util.Objects.equals(current.getLocalDeviceCode(), imported.getLocalDeviceCode())
                && java.util.Objects.equals(current.getLineId(), imported.getLineId())
                && java.util.Objects.equals(current.getLineName(), imported.getLineName())
                && java.util.Objects.equals(current.getStatus(), imported.getStatus());
    }

    private Device find(String lineCode, String localCode) {
        String code = ProductionLineService.normalize(lineCode)+"-"+localCode.trim().toUpperCase();
        Device device = deviceMapper.findIncludingDeleted(code);
        if (device != null && Integer.valueOf(1).equals(device.getDeleted()))
            throw new BizException("设备编号【" + code + "】属于已删除档案，请从导入文件移除该行或使用其他编号");
        return device;
    }
}
