package com.sparepart.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.entity.ReplaceRecord;
import com.sparepart.entity.ScrapRecord;
import com.sparepart.entity.SparePart;
import com.sparepart.mapper.ReplaceRecordMapper;
import com.sparepart.mapper.ScrapRecordMapper;
import com.sparepart.mapper.SparePartMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 为报废明细功能上线前的正常换新记录补档。
 * 当前 scrap_stock 是权威汇总；补档只分配现存待处理数量，绝不修改库存。
 */
@Component
@Order(20)
public class ScrapArchiveBackfillRunner implements CommandLineRunner {
    private final ReplaceRecordMapper replaceMapper;
    private final ScrapRecordMapper scrapMapper;
    private final SparePartMapper partMapper;

    public ScrapArchiveBackfillRunner(ReplaceRecordMapper replaceMapper, ScrapRecordMapper scrapMapper,
                                      SparePartMapper partMapper) {
        this.replaceMapper = replaceMapper;
        this.scrapMapper = scrapMapper;
        this.partMapper = partMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(String... args) {
        List<ScrapRecord> archived = scrapMapper.selectList(null);
        Set<Long> archivedReplaceIds = new HashSet<>();
        archived.stream().filter(row -> row.getReplaceRecordId() != null)
                .forEach(row -> archivedReplaceIds.add(row.getReplaceRecordId()));

        for (SparePart part : partMapper.selectList(null)) {
            int alreadyPending = archived.stream()
                    .filter(row -> part.getId().equals(row.getPartId()))
                    .mapToInt(row -> row.getRemainingQuantity() == null ? 0 : row.getRemainingQuantity()).sum();
            int unarchivedStock = Math.max(0, (part.getScrapStock() == null ? 0 : part.getScrapStock()) - alreadyPending);
            List<ReplaceRecord> history = replaceMapper.selectList(new LambdaQueryWrapper<ReplaceRecord>()
                    .eq(ReplaceRecord::getOldPartId, part.getId())
                    .and(q -> q.isNull(ReplaceRecord::getStatus).or().eq(ReplaceRecord::getStatus, "ACTIVE"))
                    .orderByDesc(ReplaceRecord::getId));
            for (ReplaceRecord rr : history) {
                if (archivedReplaceIds.contains(rr.getId())) continue;
                ScrapRecord legacy = archived.stream().filter(row -> row.getReplaceRecordId() == null
                                && rr.getFlowNo().equals(row.getFlowNo()) && part.getId().equals(row.getPartId()))
                        .findFirst().orElse(null);
                if (legacy != null) {
                    legacy.setReplaceRecordId(rr.getId());
                    scrapMapper.updateById(legacy);
                    archivedReplaceIds.add(rr.getId());
                    continue;
                }
                int qty = rr.getOldQuantity() == null ? rr.getQuantity() : rr.getOldQuantity();
                int remaining = Math.min(qty, unarchivedStock);
                ScrapRecord record = new ScrapRecord();
                record.setScrapNo("BF-HIS-" + rr.getId()); record.setFlowNo(rr.getFlowNo());
                record.setReplaceRecordId(rr.getId());
                record.setPartId(part.getId()); record.setDeviceId(rr.getDeviceId());
                record.setQuantity(qty); record.setRemainingQuantity(remaining);
                record.setActualUsageCount(rr.getActualUsageCount()); record.setRatedLifeCount(part.getRatedLifeCount());
                record.setReplacementReason(rr.getReplacementReason());
                record.setScrapReason("历史换新数据补档"); record.setOperator("system-migration");
                record.setStatus(remaining == 0 ? "CLEARED" : remaining == qty ? "PENDING" : "PARTIAL");
                record.setReceivedAt(rr.getCreatedAt() == null ? LocalDateTime.now() : rr.getCreatedAt());
                scrapMapper.insert(record);
                archived.add(record);
                archivedReplaceIds.add(rr.getId());
                unarchivedStock -= remaining;
            }
        }
    }
}
