package com.sparepart.service;

import com.sparepart.common.BizException;
import com.sparepart.dto.ReplaceBatchRequest;
import com.sparepart.dto.ReplaceItem;
import com.sparepart.entity.InternalMessage;
import com.sparepart.entity.ReplaceRecord;
import com.sparepart.entity.SparePart;
import com.sparepart.entity.StockFlow;
import com.sparepart.mapper.InternalMessageMapper;
import com.sparepart.mapper.DeviceMapper;
import com.sparepart.mapper.RequestDedupMapper;
import com.sparepart.entity.Device;
import com.sparepart.entity.RequestDedup;
import com.sparepart.mapper.ReplaceRecordMapper;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import com.sparepart.mapper.ScrapRecordMapper;
import com.sparepart.entity.ScrapRecord;
import com.sparepart.entity.ScrapDisposalItem;
import com.sparepart.mapper.ScrapDisposalItemMapper;
import com.sparepart.entity.Technician;
import com.sparepart.entity.InstalledPart;
import com.sparepart.entity.InstalledPartRetirement;
import com.sparepart.mapper.TechnicianMapper;
import com.sparepart.mapper.InstalledPartMapper;
import com.sparepart.mapper.InstalledPartRetirementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import java.security.MessageDigest;

@Service
public class FlowService {
    private static final ObjectMapper IDEMPOTENCY_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();

    private final SparePartMapper sparePartMapper;
    private final StockFlowMapper stockFlowMapper;
    private final ReplaceRecordMapper replaceRecordMapper;
    private final InternalMessageMapper internalMessageMapper;
    private final DeviceMapper deviceMapper;
    private final RequestDedupMapper requestDedupMapper;
    private final InternalMessageService messageService;
    private final ScrapRecordMapper scrapRecordMapper;
    private final ScrapDisposalItemMapper scrapDisposalItemMapper;
    private final TechnicianMapper technicianMapper;
    private final InstalledPartMapper installedPartMapper;
    private final InstalledPartRetirementMapper installedPartRetirementMapper;

    public FlowService(SparePartMapper sparePartMapper, StockFlowMapper stockFlowMapper,
                       ReplaceRecordMapper replaceRecordMapper, InternalMessageMapper internalMessageMapper,
                       DeviceMapper deviceMapper, RequestDedupMapper requestDedupMapper,
                       InternalMessageService messageService, ScrapRecordMapper scrapRecordMapper,
                       ScrapDisposalItemMapper scrapDisposalItemMapper, TechnicianMapper technicianMapper,
                       InstalledPartMapper installedPartMapper,
                       InstalledPartRetirementMapper installedPartRetirementMapper) {
        this.sparePartMapper = sparePartMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.replaceRecordMapper = replaceRecordMapper;
        this.internalMessageMapper = internalMessageMapper;
        this.deviceMapper = deviceMapper;
        this.requestDedupMapper = requestDedupMapper;
        this.messageService = messageService;
        this.scrapRecordMapper = scrapRecordMapper;
        this.scrapDisposalItemMapper = scrapDisposalItemMapper;
        this.technicianMapper = technicianMapper;
        this.installedPartMapper = installedPartMapper;
        this.installedPartRetirementMapper = installedPartRetirementMapper;
    }

    /** 批量一键换新（核心，事务原子性，行级错误回滚）。operator 由 Controller 从会话传入。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replaceBatch(ReplaceBatchRequest req, String operator) {
        if (req.getItems().size() > 5) throw new BizException("单次批量换新最多5行");
        String payloadHash = payloadHash(req);
        RequestDedup existingRequest = requestDedupMapper.selectById(req.getRequestId());
        if (existingRequest != null) {
            return duplicateResult(existingRequest, operator, payloadHash, req.getItems().size());
        }
        String flowNo = genFlowNo();
        RequestDedup dedup = new RequestDedup();
        dedup.setRequestId(req.getRequestId());
        dedup.setFlowNo(flowNo);
        dedup.setOperator(operator);
        dedup.setPayloadHash(payloadHash);
        dedup.setItemCount(req.getItems().size());
        try {
            requestDedupMapper.insert(dedup);
        } catch (DuplicateKeyException e) {
            RequestDedup concurrent = requestDedupMapper.selectById(req.getRequestId());
            if (concurrent != null) return duplicateResult(concurrent, operator, payloadHash, req.getItems().size());
            throw new BizException("该换新请求正在处理中，请勿重复提交");
        }
        int index = 0;
        for (ReplaceItem item : req.getItems()) {
            SparePart newPart = sparePartMapper.selectById(item.getNewPartId());
            if (newPart == null || !Integer.valueOf(1).equals(newPart.getStatus())) {
                throw new BizException("第" + (index + 1) + "行：新备品不存在", index);
            }
            requireActiveDevice(item.getDeviceId(), index);
            requireActiveTechnician(item.getTechnicianId(), index);
            boolean exception = Boolean.TRUE.equals(item.getIsException());
            SparePart oldPart = null;
            if (exception) {
                if (item.getExceptionRemark() == null || item.getExceptionRemark().isBlank()) {
                    throw new BizException("第" + (index + 1) + "行：特殊出库必须填写遗失/损毁原因", index);
                }
            } else {
                if (item.getOldPartId() == null) {
                    throw new BizException("第" + (index + 1) + "行：正常换新必须选择退回旧品", index);
                }
                oldPart = sparePartMapper.selectById(item.getOldPartId());
                if (oldPart == null || !Integer.valueOf(1).equals(oldPart.getStatus())) {
                    throw new BizException("第" + (index + 1) + "行：退回旧品不存在", index);
                }
                int oldQty = item.getOldQuantity() == null ? item.getQuantity() : item.getOldQuantity();
                boolean differs = !item.getNewPartId().equals(item.getOldPartId()) || oldQty != item.getQuantity();
                if (differs && (item.getDifferenceReason() == null || item.getDifferenceReason().isBlank())) {
                    throw new BizException("第" + (index + 1) + "行：新旧备品或数量不同时必须填写原因", index);
                }
                validateLifeData(item, oldPart, index);
            }
            int affected = sparePartMapper.deductGoodStock(item.getNewPartId(), item.getQuantity());
            if (affected == 0) {
                throw new BizException("第" + (index + 1) + "行：备品【" + newPart.getName() + "】库存不足", index);
            }
            BigDecimal unitPrice = newPart.getPrice() == null ? BigDecimal.ZERO : newPart.getPrice();
            if (exception) {
                StockFlow issued = writeFlow(flowNo, 8, item.getNewPartId(), item.getDeviceId(), item.getQuantity(),
                        operator, null, 1, item.getExceptionRemark(), unitPrice);
                ReplaceRecord replaceRecord = writeReplaceRecord(flowNo, req.getRequestId(), item.getNewPartId(), null,
                        item.getDeviceId(), item.getQuantity(), null, item.getExceptionRemark(), item);
                issued.setReplaceRecordId(replaceRecord.getId()); stockFlowMapper.updateById(issued);
                createInstalledPart(replaceRecord, item);
            } else {
                int oldQty = item.getOldQuantity() == null ? item.getQuantity() : item.getOldQuantity();
                sparePartMapper.addScrapStock(item.getOldPartId(), oldQty);
                StockFlow issued = writeFlow(flowNo, 3, item.getNewPartId(), item.getDeviceId(), item.getQuantity(),
                        operator, operator + " 换新出", 0, null, unitPrice);
                StockFlow returned = writeFlow(flowNo, 4, item.getOldPartId(), item.getDeviceId(), oldQty,
                        operator, "换新入废", 0, null,
                        oldPart.getPrice() == null ? BigDecimal.ZERO : oldPart.getPrice());
                ReplaceRecord replaceRecord = writeReplaceRecord(flowNo, req.getRequestId(), item.getNewPartId(), item.getOldPartId(),
                        item.getDeviceId(), item.getQuantity(), oldQty, item.getDifferenceReason(), item);
                createScrapRecord(flowNo, replaceRecord.getId(), oldPart, item, oldQty, operator);
                issued.setReplaceRecordId(replaceRecord.getId()); stockFlowMapper.updateById(issued);
                returned.setReplaceRecordId(replaceRecord.getId()); stockFlowMapper.updateById(returned);
                retireInstalledParts(replaceRecord, item.getOldPartId(), item.getDeviceId(), oldQty);
                createInstalledPart(replaceRecord, item);
            }
            checkAndCreateWarning(item.getNewPartId());
            index++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("flowNo", flowNo);
        result.put("succeeded", index);
        return result;
    }

    private Map<String, Object> duplicateResult(RequestDedup existing, String operator,
                                                 String payloadHash, int requestedCount) {
        if (!operator.equals(existing.getOperator())) {
            throw new BizException("请求编号已被其他操作人使用，请刷新页面后重新提交");
        }
        if (existing.getPayloadHash() != null && !existing.getPayloadHash().equals(payloadHash)) {
            throw new BizException("重复请求的换新明细与原请求不一致，请刷新页面后重新提交");
        }
        Map<String, Object> duplicate = new HashMap<>();
        duplicate.put("flowNo", existing.getFlowNo());
        duplicate.put("succeeded", existing.getItemCount() == null ? requestedCount : existing.getItemCount());
        duplicate.put("duplicate", true);
        return duplicate;
    }

    private String payloadHash(ReplaceBatchRequest req) {
        try {
            byte[] bytes = IDEMPOTENCY_MAPPER.writeValueAsBytes(req.getItems());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new BizException("换新请求校验失败，请刷新页面后重试");
        }
    }

    /** 普通入库 */
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Integer partId, Integer qty, String operator, String remark, LocalDate businessDate) {
        SparePart part = requirePart(partId);
        requireActivePart(part);
        sparePartMapper.addGoodStock(partId, qty);
        writeFlow(genFlowNo(), 1, partId, null, qty, operator, remark, 0, null, BigDecimal.ZERO,
                businessDate == null ? LocalDate.now() : businessDate);
    }

    /** 普通出库 */
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Integer partId, Integer deviceId, Integer qty, String operator, String remark) {
        throw new BizException("设备备件不得普通出库，请使用“以旧换新”；无旧件时请选择异常换新并填写原因");
    }

    /** 报废品清运：仅扣减报废库存。 */
    @Transactional(rollbackFor = Exception.class)
    public void scrapOut(Integer partId, Integer deviceId, Integer qty, String operator, String remark) {
        SparePart part = requirePart(partId);
        List<ScrapRecord> available = pendingScrapRecords(partId);
        int archivedQty = available.stream().mapToInt(row -> row.getRemainingQuantity() == null ? 0 : row.getRemainingQuantity()).sum();
        if (archivedQty < qty) {
            throw new BizException("报废品档案待处理数量不足：档案剩余" + archivedQty + "，本次清运" + qty + "。请先核对报废库存与档案");
        }
        int affected = sparePartMapper.deductScrapStock(partId, qty);
        if (affected == 0) {
            throw new BizException("备品【" + part.getName() + "】报废库存不足");
        }
        StockFlow disposalFlow = writeFlow(genFlowNo(), 9, partId, deviceId, qty, operator, remark, 0, null,
                part.getPrice() == null ? BigDecimal.ZERO : part.getPrice());
        clearScrapRecords(available, qty, operator, disposalFlow);
    }

    /** 良品转报废。 */
    @Transactional(rollbackFor = Exception.class)
    public void goodToScrap(Integer partId, Integer qty, String operator, String remark) {
        if (remark == null || remark.isBlank()) throw new BizException("库内损坏报废必须填写损坏原因");
        SparePart part = requirePart(partId);
        requireActivePart(part);
        int affected = sparePartMapper.deductGoodStock(partId, qty);
        if (affected == 0) throw new BizException("备品【" + part.getName() + "】良品库存不足");
        sparePartMapper.addScrapStock(partId, qty);
        String flowNo = genFlowNo();
        writeFlow(flowNo, 5, partId, null, qty, operator, remark, 0, null, BigDecimal.ZERO);
        ScrapRecord record = new ScrapRecord();
        record.setScrapNo("BF-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 4));
        record.setFlowNo(flowNo); record.setPartId(partId); record.setQuantity(qty); record.setRemainingQuantity(qty);
        record.setRatedLifeCount(part.getRatedLifeCount()); record.setReplacementReason("WAREHOUSE_DAMAGE");
        record.setScrapReason(remark); record.setOperator(operator); record.setStatus("PENDING");
        record.setReceivedAt(java.time.LocalDateTime.now()); scrapRecordMapper.insert(record);
        checkAndCreateWarning(partId);
    }

    /** 通过反向流水冲正错误库存操作，原流水永久保留。 */
    @Transactional(rollbackFor = Exception.class)
    public void reverseFlow(Long flowId, String operator, String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("冲正原因不能为空");
        StockFlow original = stockFlowMapper.selectForUpdate(flowId);
        if (original == null) throw new BizException("原流水不存在");
        if (stockFlowMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockFlow>()
                .eq(StockFlow::getReversedFlowId, flowId)) > 0) throw new BizException("该流水已经冲正");
        int qty = original.getQuantity();
        switch (original.getFlowType()) {
            case 1, 6 -> requireAffected(sparePartMapper.correctHistoricalStock(original.getPartId(), -qty, 0), "当前良品库存不足，不能冲正");
            case 2, 7 -> requireAffected(sparePartMapper.correctHistoricalStock(original.getPartId(), qty, 0), "备品历史档案不存在，不能冲正");
            case 3, 4, 8 -> throw new BizException("换新流水属于同一张业务单，不能单独冲正，请使用“撤销换新单”");
            case 5 -> {
                reverseScrapRecord(original, qty);
                requireAffected(sparePartMapper.correctHistoricalStock(original.getPartId(), qty, -qty), "当前报废库存不足，不能冲正");
            }
            case 9 -> restoreDisposalRecords(original);
            default -> throw new BizException("该类型流水不支持冲正");
        }
        StockFlow correction = new StockFlow();
        correction.setFlowNo(genFlowNo()); correction.setFlowType(10); correction.setPartId(original.getPartId());
        correction.setDeviceId(original.getDeviceId()); correction.setQuantity(qty); correction.setOperator(operator);
        correction.setRemark("冲正流水" + original.getFlowNo() + "：" + reason); correction.setIsException(0);
        correction.setBusinessDate(LocalDate.now()); correction.setReversedFlowId(original.getId());
        correction.setReplaceRecordId(original.getReplaceRecordId());
        stockFlowMapper.insert(correction);
    }

    /** 整张换新单原子撤销；正常换新的旧品尚未被清运时才允许撤销。 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelReplacement(String flowNo, String operator, String reason) {
        if (reason == null || reason.isBlank()) throw new BizException("撤销原因不能为空");
        List<ReplaceRecord> records = replaceRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReplaceRecord>()
                        .eq(ReplaceRecord::getFlowNo, flowNo).orderByAsc(ReplaceRecord::getId));
        if (records.isEmpty()) throw new BizException("换新单不存在");
        if (records.stream().anyMatch(row -> "CANCELLED".equals(row.getStatus()))) {
            throw new BizException("该换新单已经撤销");
        }
        List<ScrapRecord> scrapRows = scrapRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScrapRecord>()
                        .eq(ScrapRecord::getFlowNo, flowNo));
        for (ScrapRecord row : scrapRows) {
            if (!java.util.Objects.equals(row.getQuantity(), row.getRemainingQuantity())) {
                throw new BizException("该换新单的旧件已经部分或全部清运，不能撤销");
            }
        }
        for (ReplaceRecord record : records) {
            List<InstalledPart> issued = installedPartMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstalledPart>()
                            .eq(InstalledPart::getReplaceRecordId, record.getId()));
            if (issued == null) continue;
            for (InstalledPart row : issued) {
                if (java.util.Objects.equals(row.getInUseQuantity(), row.getQuantity())) continue;
                List<InstalledPartRetirement> links = installedPartRetirementMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstalledPartRetirement>()
                                .eq(InstalledPartRetirement::getInstalledPartId, row.getId()));
                int retiredInSameOrder = 0;
                if (links != null) for (InstalledPartRetirement link : links) {
                    ReplaceRecord consumer = replaceRecordMapper.selectById(link.getReplaceRecordId());
                    if (consumer == null || !flowNo.equals(consumer.getFlowNo())) {
                        throw new BizException("本单发出的备品已被后续业务使用，不能撤销换新单");
                    }
                    retiredInSameOrder += link.getQuantity();
                }
                if (row.getQuantity() == null || row.getInUseQuantity() == null
                        || row.getQuantity() - row.getInUseQuantity() != retiredInSameOrder) {
                    throw new BizException("本单发出的备品在用数量异常，不能撤销换新单");
                }
            }
        }
        // 同一业务单内后面的明细可能退役前面明细发出的备品，需逆序恢复。
        for (int recordIndex = records.size() - 1; recordIndex >= 0; recordIndex--) {
            ReplaceRecord record = records.get(recordIndex);
            requireAffected(sparePartMapper.correctHistoricalStock(record.getNewPartId(), record.getQuantity(), 0),
                    "备品历史档案不存在，不能撤销换新单");
            if (record.getOldPartId() != null) {
                int oldQty = record.getOldQuantity() == null ? record.getQuantity() : record.getOldQuantity();
                requireAffected(sparePartMapper.correctHistoricalStock(record.getOldPartId(), 0, -oldQty),
                        "当前报废库存不足，不能撤销换新单");
            }
            record.setStatus("CANCELLED"); record.setCancelReason(reason); record.setCancelledBy(operator);
            record.setCancelledAt(java.time.LocalDateTime.now()); replaceRecordMapper.updateById(record);
            cancelInstalledChanges(record.getId());
        }
        for (ScrapRecord row : scrapRows) {
            row.setRemainingQuantity(0); row.setStatus("REVERSED"); row.setDisposalBatch(null);
            row.setClearedAt(null); scrapRecordMapper.updateById(row);
        }
        for (StockFlow original : stockFlowMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StockFlow>()
                        .eq(StockFlow::getFlowNo, flowNo).in(StockFlow::getFlowType, 3, 4, 8))) {
            StockFlow correction = new StockFlow(); correction.setFlowNo(genFlowNo()); correction.setFlowType(10);
            correction.setPartId(original.getPartId()); correction.setDeviceId(original.getDeviceId());
            correction.setQuantity(original.getQuantity()); correction.setOperator(operator);
            correction.setRemark("撤销换新单" + flowNo + "：" + reason); correction.setIsException(0);
            correction.setBusinessDate(LocalDate.now()); correction.setReversedFlowId(original.getId());
            correction.setReplaceRecordId(original.getReplaceRecordId());
            stockFlowMapper.insert(correction);
        }
    }

    private void requireAffected(int affected, String message) {
        if (affected == 0) throw new BizException(message);
    }

    /** 库存预警：good_stock <= min_stock 时写入内部消息 */
    public void checkAndCreateWarning(Integer partId) {
        SparePart part = sparePartMapper.selectById(partId);
        if (part == null) {
            return;
        }
        int minStock = part.getMinStock() == null ? 0 : part.getMinStock();
        int goodStock = part.getGoodStock() == null ? 0 : part.getGoodStock();
        if (goodStock <= minStock) {
            String title = "库存预警：" + part.getName();
            if (internalMessageMapper.countUnreadWarning(title) > 0) {
                return;
            }
            messageService.createForRoles(1, title,
                    "备品【" + part.getPartNo() + " " + part.getName() + "】当前良品库存 "
                            + goodStock + "，已达最低水位线 " + minStock,
                    List.of("ROLE_ADMIN", "ROLE_VIEWER"));
        }
    }

    private SparePart requirePart(Integer partId) {
        SparePart part = sparePartMapper.selectById(partId);
        if (part == null) {
            throw new BizException("备品不存在");
        }
        return part;
    }

    private void requireActivePart(SparePart part) {
        if (!Integer.valueOf(1).equals(part.getStatus())) throw new BizException("备品已停用，不能办理库存业务");
    }

    private Device requireActiveDevice(Integer deviceId, Integer rowIndex) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || !Integer.valueOf(1).equals(device.getStatus())) {
            if (rowIndex == null) throw new BizException("关联设备不存在或已停用");
            throw new BizException("第" + (rowIndex + 1) + "行：关联设备不存在或已停用", rowIndex);
        }
        return device;
    }

    private Technician requireActiveTechnician(Integer technicianId, int rowIndex) {
        Technician row = technicianMapper.selectById(technicianId);
        if (row == null || !Integer.valueOf(1).equals(row.getStatus()))
            throw new BizException("第" + (rowIndex + 1) + "行：领用人不存在或已停用", rowIndex);
        return row;
    }

    private StockFlow writeFlow(String flowNo, Integer flowType, Integer partId, Integer deviceId, Integer qty,
                           String operator, String remark, Integer isException, String exceptionRemark,
                           BigDecimal unitPrice) {
        return writeFlow(flowNo, flowType, partId, deviceId, qty, operator, remark, isException,
                exceptionRemark, unitPrice, LocalDate.now());
    }

    private StockFlow writeFlow(String flowNo, Integer flowType, Integer partId, Integer deviceId, Integer qty,
                           String operator, String remark, Integer isException, String exceptionRemark,
                           BigDecimal unitPrice, LocalDate businessDate) {
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setFlowType(flowType);
        flow.setPartId(partId);
        flow.setDeviceId(deviceId);
        flow.setQuantity(qty);
        flow.setOperator(operator);
        flow.setRemark(remark);
        flow.setIsException(isException);
        flow.setExceptionRemark(exceptionRemark);
        flow.setUnitPrice(unitPrice);
        flow.setBusinessDate(businessDate);
        stockFlowMapper.insert(flow);
        return flow;
    }

    private ReplaceRecord writeReplaceRecord(String flowNo, String requestId, Integer newPartId, Integer oldPartId,
                                    Integer deviceId, Integer qty, Integer oldQty, String reason) {
        return writeReplaceRecord(flowNo, requestId, newPartId, oldPartId, deviceId, qty, oldQty, reason, null);
    }

    private ReplaceRecord writeReplaceRecord(String flowNo, String requestId, Integer newPartId, Integer oldPartId,
                                    Integer deviceId, Integer qty, Integer oldQty, String reason, ReplaceItem item) {
        ReplaceRecord record = new ReplaceRecord();
        record.setFlowNo(flowNo);
        record.setNewPartId(newPartId);
        record.setOldPartId(oldPartId);
        record.setDeviceId(deviceId);
        record.setTechnicianId(item == null ? null : item.getTechnicianId());
        record.setQuantity(qty);
        record.setOldQuantity(oldQty);
        record.setDifferenceReason(reason);
        record.setRequestId(requestId);
        record.setStatus("ACTIVE");
        if (item != null) {
            record.setReplacementReason(item.getReplacementReason());
            record.setCounterStart(item.getCounterStart());
            record.setCounterEnd(item.getCounterEnd());
            record.setActualUsageCount(resolveActualUsage(item));
        }
        replaceRecordMapper.insert(record);
        return record;
    }

    private void createInstalledPart(ReplaceRecord record, ReplaceItem item) {
        InstalledPart installed = new InstalledPart(); installed.setReplaceRecordId(record.getId());
        installed.setPartId(item.getNewPartId()); installed.setDeviceId(item.getDeviceId());
        installed.setTechnicianId(item.getTechnicianId()); installed.setQuantity(item.getQuantity());
        installed.setInUseQuantity(item.getQuantity()); installed.setStatus("IN_USE");
        installed.setIssuedAt(java.time.LocalDateTime.now()); installedPartMapper.insert(installed);
    }

    /** 旧系统可能没有在用台账，因此只核销能够匹配到的历史在用数量。 */
    private void retireInstalledParts(ReplaceRecord replacement, Integer partId, Integer deviceId, int quantity) {
        int remaining = quantity;
        List<InstalledPart> rows = installedPartMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstalledPart>()
                        .eq(InstalledPart::getPartId, partId).eq(InstalledPart::getDeviceId, deviceId)
                        .gt(InstalledPart::getInUseQuantity, 0).orderByAsc(InstalledPart::getIssuedAt).orderByAsc(InstalledPart::getId));
        if (rows == null) rows = List.of();
        for (InstalledPart row : rows) {
            if (remaining == 0) break;
            int retired = Math.min(remaining, row.getInUseQuantity());
            row.setInUseQuantity(row.getInUseQuantity() - retired);
            if (row.getInUseQuantity() == 0) { row.setStatus("REMOVED"); row.setRemovedAt(java.time.LocalDateTime.now()); }
            installedPartMapper.updateById(row);
            InstalledPartRetirement link = new InstalledPartRetirement(); link.setReplaceRecordId(replacement.getId());
            link.setInstalledPartId(row.getId()); link.setQuantity(retired); installedPartRetirementMapper.insert(link);
            remaining -= retired;
        }
    }

    private void cancelInstalledChanges(Long replaceRecordId) {
        List<InstalledPart> issued = installedPartMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstalledPart>()
                        .eq(InstalledPart::getReplaceRecordId, replaceRecordId));
        if (issued == null) issued = List.of();
        for (InstalledPart row : issued) {
            if (!java.util.Objects.equals(row.getInUseQuantity(), row.getQuantity())) {
                throw new BizException("本单发出的备品已被后续业务使用，不能撤销换新单");
            }
            row.setInUseQuantity(0); row.setStatus("CANCELLED"); row.setRemovedAt(java.time.LocalDateTime.now());
            installedPartMapper.updateById(row);
        }
        List<InstalledPartRetirement> links = installedPartRetirementMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InstalledPartRetirement>()
                        .eq(InstalledPartRetirement::getReplaceRecordId, replaceRecordId));
        if (links == null) links = List.of();
        for (InstalledPartRetirement link : links) {
            InstalledPart old = installedPartMapper.selectById(link.getInstalledPartId());
            if (old != null) {
                old.setInUseQuantity((old.getInUseQuantity() == null ? 0 : old.getInUseQuantity()) + link.getQuantity());
                old.setStatus("IN_USE"); old.setRemovedAt(null); installedPartMapper.updateById(old);
            }
            installedPartRetirementMapper.deleteById(link.getId());
        }
    }

    private void validateLifeData(ReplaceItem item, SparePart oldPart, int index) {
        String reason = item.getReplacementReason();
        if (reason == null || !List.of("FAILURE", "LIFE_LIMIT", "OTHER").contains(reason)) {
            throw new BizException("第" + (index + 1) + "行：请选择故障报废、寿命到期强换或其他更换原因", index);
        }
        if (("FAILURE".equals(reason) || "OTHER".equals(reason))
                && (item.getScrapReason() == null || item.getScrapReason().isBlank())) {
            throw new BizException("第" + (index + 1) + "行：请填写故障或报废原因", index);
        }
        Long actual = resolveActualUsage(item);
        if (actual == null) {
            throw new BizException("第" + (index + 1) + "行：请填写旧件实际使用次数，或同时填写设备起始/结束计数", index);
        }
        if ("LIFE_LIMIT".equals(reason) && oldPart.getRatedLifeCount() != null
                && actual < oldPart.getRatedLifeCount()) {
            throw new BizException("第" + (index + 1) + "行：实际次数未达到额定寿命，不能标记为寿命到期强换", index);
        }
        // 一行代表实际使用次数相同的一批旧件；次数不同的旧件按组分行登记。
    }

    private Long resolveActualUsage(ReplaceItem item) {
        if (item.getCounterStart() != null || item.getCounterEnd() != null) {
            if (item.getCounterStart() == null || item.getCounterEnd() == null
                    || item.getCounterEnd() < item.getCounterStart()) {
                throw new BizException("设备结束计数必须大于等于起始计数");
            }
            return item.getCounterEnd() - item.getCounterStart();
        }
        return item.getActualUsageCount();
    }

    private void createScrapRecord(String flowNo, Long replaceRecordId, SparePart oldPart, ReplaceItem item, int oldQty, String operator) {
        ScrapRecord record = new ScrapRecord();
        record.setScrapNo("BF-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now()) + "-" + UUID.randomUUID().toString().substring(0, 4));
        record.setFlowNo(flowNo); record.setReplaceRecordId(replaceRecordId);
        record.setPartId(oldPart.getId()); record.setDeviceId(item.getDeviceId());
        record.setQuantity(oldQty); record.setRemainingQuantity(oldQty);
        record.setActualUsageCount(resolveActualUsage(item)); record.setRatedLifeCount(oldPart.getRatedLifeCount());
        record.setReplacementReason(item.getReplacementReason());
        record.setScrapReason(item.getScrapReason());
        record.setOperator(operator); record.setStatus("PENDING"); record.setReceivedAt(java.time.LocalDateTime.now());
        scrapRecordMapper.insert(record);
    }

    private List<ScrapRecord> pendingScrapRecords(Integer partId) {
        return scrapRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScrapRecord>()
                        .eq(ScrapRecord::getPartId, partId).gt(ScrapRecord::getRemainingQuantity, 0)
                        .orderByAsc(ScrapRecord::getReceivedAt).orderByAsc(ScrapRecord::getId));
    }

    private void clearScrapRecords(List<ScrapRecord> rows, int quantity, String operator, StockFlow disposalFlow) {
        int remaining = quantity;
        for (ScrapRecord row : rows) {
            if (remaining <= 0) break;
            int consume = Math.min(remaining, row.getRemainingQuantity());
            row.setRemainingQuantity(row.getRemainingQuantity() - consume);
            row.setStatus(row.getRemainingQuantity() == 0 ? "CLEARED" : "PARTIAL");
            row.setDisposalBatch(disposalFlow.getFlowNo());
            if (row.getRemainingQuantity() == 0) row.setClearedAt(java.time.LocalDateTime.now());
            scrapRecordMapper.updateById(row);
            ScrapDisposalItem item = new ScrapDisposalItem(); item.setFlowId(disposalFlow.getId());
            item.setScrapRecordId(row.getId()); item.setQuantity(consume); scrapDisposalItemMapper.insert(item);
            remaining -= consume;
        }
        if (remaining != 0) throw new BizException("报废品清运档案分配失败，请重试");
    }

    private void restoreDisposalRecords(StockFlow original) {
        List<ScrapDisposalItem> items = scrapDisposalItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScrapDisposalItem>()
                        .eq(ScrapDisposalItem::getFlowId, original.getId()));
        if (items.isEmpty()) throw new BizException("该历史清运流水没有档案分配明细，不能自动撤销，请由管理员核对处理");
        int restored = 0;
        for (ScrapDisposalItem item : items) {
            ScrapRecord row = scrapRecordMapper.selectById(item.getScrapRecordId());
            if (row == null) throw new BizException("清运关联的报废品档案不存在，不能撤销");
            int remaining = (row.getRemainingQuantity() == null ? 0 : row.getRemainingQuantity()) + item.getQuantity();
            if (remaining > row.getQuantity()) throw new BizException("报废品档案数量异常，不能撤销清运");
            row.setRemainingQuantity(remaining); row.setStatus(remaining == row.getQuantity() ? "PENDING" : "PARTIAL");
            row.setDisposalBatch(null); row.setClearedAt(null); scrapRecordMapper.updateById(row);
            restored += item.getQuantity();
        }
        if (restored != original.getQuantity()) throw new BizException("清运分配明细数量不完整，不能撤销");
        requireAffected(sparePartMapper.correctHistoricalStock(original.getPartId(), 0, restored),
                "备品历史档案不存在，不能撤销清运");
    }

    private void reverseScrapRecord(StockFlow original, int qty) {
        ScrapRecord row = scrapRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScrapRecord>()
                        .eq(ScrapRecord::getFlowNo, original.getFlowNo())
                        .eq(ScrapRecord::getPartId, original.getPartId()).last("LIMIT 1"));
        if (row == null) return; // 兼容升级前没有明细档案的历史流水。
        if (row.getRemainingQuantity() < qty) throw new BizException("该报废品已经部分或全部清运，不能直接冲正");
        row.setRemainingQuantity(row.getRemainingQuantity() - qty);
        row.setStatus(row.getRemainingQuantity() == 0 ? "REVERSED" : "PARTIAL");
        scrapRecordMapper.updateById(row);
    }

    private String genFlowNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
