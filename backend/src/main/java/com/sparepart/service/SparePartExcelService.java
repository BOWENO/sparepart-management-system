package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.dto.SparePartExcelDTO;
import com.sparepart.entity.SparePart;
import com.sparepart.entity.StockFlow;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparepart.mapper.ExcelImportBatchMapper;
import com.sparepart.mapper.ExcelImportRowMapper;
import com.sparepart.entity.ExcelImportBatch;
import com.sparepart.entity.ExcelImportRow;
import java.util.UUID;

/**
 * 备品 Excel 导入导出。
 * 导入逻辑：编码已存在则覆盖更新库存及基础信息，不存在则新增；
 * 初始库存生成 flow_type=0 初始化流水，不计入日常消耗统计。
 */
@Service
public class SparePartExcelService {

    private static final String[] COLUMN_NAMES = {
            "备品编码", "名称", "规格型号", "单位", "库位编码",
            "初始良品库存", "最低水位线", "报废库存", "状态（启用/停用）", "额定寿命次数"
    };

    private final SparePartMapper sparePartMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ExcelImportBatchMapper batchMapper;
    private final ExcelImportRowMapper importRowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public SparePartExcelService(SparePartMapper sparePartMapper, StockFlowMapper stockFlowMapper,
                                 ExcelImportBatchMapper batchMapper, ExcelImportRowMapper importRowMapper) {
        this.sparePartMapper = sparePartMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.batchMapper = batchMapper;
        this.importRowMapper = importRowMapper;
    }

    /** 导出空白导入模板。 */
    public void exportTemplate(HttpServletResponse response) throws IOException {
        TabularTextUtil.write(response, "备品空白模板", List.of(COLUMN_NAMES), List.of());
    }

    public void exportLedger(HttpServletResponse response) throws IOException {
        var data = buildExportRows().stream().map(row -> List.of(
                value(row.getPartNo()), value(row.getName()), value(row.getSpec()), value(row.getUnit()),
                value(row.getLocationCode()), value(row.getGoodStock()), value(row.getMinStock()),
                value(row.getScrapStock()), value(row.getStatus()), value(row.getRatedLifeCount()))).toList();
        TabularTextUtil.write(response, "备品当前台账", List.of(COLUMN_NAMES), data);
    }

    /** 导出当前台账；导出文件可直接修改后重新导入。 */
    List<SparePartExcelDTO> buildExportRows() {
        return sparePartMapper.selectList(new LambdaQueryWrapper<SparePart>()
                        .orderByAsc(SparePart::getPartNo))
                .stream()
                .map(part -> {
                    SparePartExcelDTO row = new SparePartExcelDTO();
                    row.setPartNo(part.getPartNo());
                    row.setName(part.getName());
                    row.setSpec(part.getSpec());
                    row.setUnit(part.getUnit());
                    row.setLocationCode(part.getLocationCode());
                    row.setGoodStock(part.getGoodStock());
                    row.setMinStock(part.getMinStock());
                    row.setRatedLifeCount(part.getRatedLifeCount());
                    row.setScrapStock(part.getScrapStock());
                    row.setStatus(Integer.valueOf(1).equals(part.getStatus()) ? "启用" : "停用");
                    return row;
                })
                .toList();
    }

    /** 导入备品，返回 {newCount, updateCount} */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importExcel(MultipartFile file, String operator) throws IOException {
        List<SparePartExcelDTO> rows = readAndValidate(file);
        String batchNo = UUID.randomUUID().toString().replace("-", "");
        createBatch(batchNo, operator, file.getOriginalFilename());
        int newCount = 0;
        int updateCount = 0;
        for (SparePartExcelDTO row : rows) {
            SparePart exists = findByCode(row.getPartNo());
            int initQty = row.getGoodStock() == null ? 0 : row.getGoodStock();
            if (exists == null) {
                SparePart part = new SparePart();
                applyBaseFields(part, row, false);
                part.setGoodStock(initQty);
                part.setScrapStock(0);
                sparePartMapper.insert(part);
                saveImportRow(batchNo, part.getId(), true, null);
                writeInitFlow(part.getId(), initQty, operator);
                newCount++;
            } else {
                saveImportRow(batchNo, exists.getId(), false, objectMapper.writeValueAsString(exists));
                applyBaseFields(exists, row, true);
                // 已有备品的良品/报废库存永不通过Excel覆盖。
                sparePartMapper.updateById(exists);
                updateCount++;
            }
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("batchNo", batchNo); result.put("newCount", newCount); result.put("updateCount", updateCount);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rollback(String batchNo) throws IOException {
        ExcelImportBatch batch = batchMapper.selectById(batchNo);
        if (batch == null || !"PART".equals(batch.getModule())) throw new BizException("导入批次不存在");
        if ("ROLLED_BACK".equals(batch.getStatus())) throw new BizException("该批次已经回滚");
        List<ExcelImportRow> rows = importRowMapper.selectList(new LambdaQueryWrapper<ExcelImportRow>()
                .eq(ExcelImportRow::getBatchNo, batchNo).orderByDesc(ExcelImportRow::getId));
        for (ExcelImportRow row : rows) {
            SparePart current = sparePartMapper.selectById(row.getRecordId());
            if (current == null) continue;
            if (Integer.valueOf(1).equals(row.getWasNew())) {
                long laterFlows = stockFlowMapper.selectCount(new LambdaQueryWrapper<StockFlow>()
                        .eq(StockFlow::getPartId, current.getId()).ne(StockFlow::getFlowType, 0));
                if (laterFlows > 0) throw new BizException("批次中的新增备品已发生后续库存业务，不能回滚");
                int qty = current.getGoodStock() == null ? 0 : current.getGoodStock();
                current.setGoodStock(0); current.setStatus(0); sparePartMapper.updateById(current);
                if (qty > 0) writeCorrectionFlow(current.getId(), qty, batch.getOperator(), batchNo);
            } else {
                SparePart before = objectMapper.readValue(row.getBeforeJson(), SparePart.class);
                before.setGoodStock(current.getGoodStock()); before.setScrapStock(current.getScrapStock());
                sparePartMapper.updateById(before);
            }
        }
        batch.setStatus("ROLLED_BACK"); batch.setRolledBackAt(LocalDateTime.now()); batchMapper.updateById(batch);
    }

    private void createBatch(String no, String operator, String fileName) {
        ExcelImportBatch batch = new ExcelImportBatch(); batch.setBatchNo(no); batch.setModule("PART");
        batch.setOperator(operator); batch.setFileName(fileName); batch.setStatus("SUCCESS"); batchMapper.insert(batch);
    }

    private void saveImportRow(String batchNo, Integer id, boolean isNew, String before) {
        ExcelImportRow row = new ExcelImportRow(); row.setBatchNo(batchNo); row.setRecordId(id);
        row.setWasNew(isNew ? 1 : 0); row.setBeforeJson(before); importRowMapper.insert(row);
    }

    private void writeCorrectionFlow(Integer partId, int qty, String operator, String batchNo) {
        StockFlow flow = new StockFlow(); flow.setFlowNo(UUID.randomUUID().toString().replace("-", ""));
        flow.setFlowType(10); flow.setPartId(partId); flow.setQuantity(qty); flow.setOperator(operator);
        flow.setRemark("回滚TXT导入批次：" + batchNo); flow.setIsException(0); stockFlowMapper.insert(flow);
    }

    public Map<String, Object> preview(MultipartFile file) throws IOException {
        List<SparePartExcelDTO> rows = readAndValidate(file);
        int newCount = 0;
        int updateCount = 0;
        for (SparePartExcelDTO row : rows) {
            if (findByCode(row.getPartNo()) == null) newCount++; else updateCount++;
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("totalCount", rows.size());
        result.put("newCount", newCount);
        result.put("updateCount", updateCount);
        result.put("rows", rows.stream().limit(100).toList());
        return result;
    }

    private List<SparePartExcelDTO> readAndValidate(MultipartFile file) throws IOException {
        List<TabularTextUtil.TextRow> textRows = TabularTextUtil.read(file, List.of(COLUMN_NAMES), 1000);
        List<SparePartExcelDTO> rows = textRows.stream().map(text -> {
            SparePartExcelDTO row = new SparePartExcelDTO();
            row.setPartNo(text.get(COLUMN_NAMES[0])); row.setName(text.get(COLUMN_NAMES[1]));
            row.setSpec(text.get(COLUMN_NAMES[2])); row.setUnit(text.get(COLUMN_NAMES[3]));
            row.setLocationCode(text.get(COLUMN_NAMES[4]));
            row.setGoodStock(TabularTextUtil.integer(text.get(COLUMN_NAMES[5]), text.lineNumber(), COLUMN_NAMES[5]));
            row.setMinStock(TabularTextUtil.integer(text.get(COLUMN_NAMES[6]), text.lineNumber(), COLUMN_NAMES[6]));
            row.setScrapStock(TabularTextUtil.integer(text.get(COLUMN_NAMES[7]), text.lineNumber(), COLUMN_NAMES[7]));
            row.setStatus(text.get(COLUMN_NAMES[8]));
            row.setRatedLifeCount(TabularTextUtil.longValue(text.get(COLUMN_NAMES[9]), text.lineNumber(), COLUMN_NAMES[9]));
            return row;
        }).toList();
        java.util.Set<String> codes = new java.util.HashSet<>();
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            SparePartExcelDTO row = rows.get(i);
            int excelRow = textRows.get(i).lineNumber();
            if (!StringUtils.hasText(row.getPartNo())) errors.add("第" + excelRow + "行：备品编码不能为空");
            if (!StringUtils.hasText(row.getName())) errors.add("第" + excelRow + "行：名称不能为空");
            String code = StringUtils.hasText(row.getPartNo()) ? row.getPartNo().trim() : "";
            if (!code.isEmpty() && !codes.add(code.toUpperCase())) errors.add("第" + excelRow + "行：备品编码在文件中重复：" + code);
            int initQty = row.getGoodStock() == null ? 0 : row.getGoodStock();
            if (initQty < 0) errors.add("第" + excelRow + "行：初始良品库存不能为负数");
            if (row.getMinStock() != null && row.getMinStock() < 0) errors.add("第" + excelRow + "行：最低水位线不能为负数");
            if (row.getRatedLifeCount() != null && row.getRatedLifeCount() <= 0) errors.add("第" + excelRow + "行：额定寿命次数必须大于0");
        }
        if (!errors.isEmpty()) throw new BizException(String.join("；", errors));
        return rows;
    }

    private static Object value(Object value) { return value == null ? "" : value; }

    private SparePart findByCode(String code) {
        SparePart part = sparePartMapper.findIncludingDeleted(code.trim());
        if (part != null && Integer.valueOf(1).equals(part.getDeleted()))
            throw new BizException("备品编码【" + code + "】属于已删除档案，请从导入文件移除该行或使用其他编码");
        return part;
    }

    private void applyBaseFields(SparePart part, SparePartExcelDTO row, boolean keepBlank) {
        part.setPartNo(row.getPartNo().trim());
        if (!keepBlank || StringUtils.hasText(row.getName())) part.setName(row.getName());
        if (!keepBlank || StringUtils.hasText(row.getSpec())) part.setSpec(row.getSpec());
        if (!keepBlank || StringUtils.hasText(row.getUnit())) part.setUnit(StringUtils.hasText(row.getUnit()) ? row.getUnit() : "个");
        if (!keepBlank || StringUtils.hasText(row.getLocationCode())) part.setLocationCode(row.getLocationCode());
        if (!keepBlank || row.getMinStock() != null) part.setMinStock(row.getMinStock() == null ? 0 : row.getMinStock());
        if (!keepBlank || row.getRatedLifeCount() != null) part.setRatedLifeCount(row.getRatedLifeCount());
        if (!keepBlank || StringUtils.hasText(row.getStatus())) part.setStatus("停用".equals(row.getStatus()) ? 0 : 1);
        if (part.getStatus() == null) part.setStatus(1);
        if (part.getPrice() == null) part.setPrice(BigDecimal.ZERO);
    }

    private void writeInitFlow(Integer partId, int qty, String operator) {
        StockFlow flow = new StockFlow();
        flow.setFlowNo(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        flow.setFlowType(0);
        flow.setPartId(partId);
        flow.setQuantity(qty);
        flow.setOperator(operator);
        flow.setRemark("TXT导入初始化");
        flow.setIsException(0);
        stockFlowMapper.insert(flow);
    }
}
