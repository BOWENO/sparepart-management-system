package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.BizException;
import com.sparepart.dto.CheckRequest;
import com.sparepart.entity.InternalMessage;
import com.sparepart.entity.InventoryCheck;
import com.sparepart.entity.SparePart;
import com.sparepart.entity.StockFlow;
import com.sparepart.mapper.InternalMessageMapper;
import com.sparepart.mapper.InventoryCheckMapper;
import com.sparepart.mapper.SparePartMapper;
import com.sparepart.mapper.StockFlowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Service
public class CheckService {

    private final SparePartMapper sparePartMapper;
    private final StockFlowMapper stockFlowMapper;
    private final InventoryCheckMapper inventoryCheckMapper;
    private final InternalMessageMapper internalMessageMapper;
    private final InternalMessageService messageService;

    public CheckService(SparePartMapper sparePartMapper, StockFlowMapper stockFlowMapper,
                        InventoryCheckMapper inventoryCheckMapper, InternalMessageMapper internalMessageMapper,
                        InternalMessageService messageService) {
        this.sparePartMapper = sparePartMapper;
        this.stockFlowMapper = stockFlowMapper;
        this.inventoryCheckMapper = inventoryCheckMapper;
        this.internalMessageMapper = internalMessageMapper;
        this.messageService = messageService;
    }

    /** 提交盘点：自动调账、写差异流水、留痕、通知主管 */
    @Transactional(rollbackFor = Exception.class)
    public InventoryCheck doCheck(CheckRequest req, String operator) {
        SparePart part = sparePartMapper.selectById(req.getPartId());
        if (part == null) {
            throw new BizException("备品不存在");
        }
        int bookQty = part.getGoodStock() == null ? 0 : part.getGoodStock();
        int actualQty = req.getActualQty();
        int diff = actualQty - bookQty;

        // 调账（>=0 兜底防负）
        int affected = sparePartMapper.reconcileStock(part.getId(), bookQty, actualQty);
        if (affected == 0) {
            throw new BizException("库存已被其他操作修改，请刷新后重新盘点");
        }

        // 写入差异流水（盘盈 type6 / 盘亏 type7）
        if (diff != 0) {
            int flowType = diff > 0 ? 6 : 7;
            writeFlow(flowType, part.getId(), Math.abs(diff), operator);
        }

        // 保存盘点差异单
        InventoryCheck check = new InventoryCheck();
        check.setPartId(part.getId());
        check.setBookQty(bookQty);
        check.setActualQty(actualQty);
        check.setDiffQty(diff);
        check.setOperator(operator);
        inventoryCheckMapper.insert(check);

        // 生成待阅消息给主管
        String title = diff == 0 ? "盘点一致通知：" + part.getName() : "盘点差异通知：" + part.getName();
        messageService.createForRoles(2, title,
                "备品【" + part.getPartNo() + " " + part.getName() + "】盘点：账面 "
                        + bookQty + "，实盘 " + actualQty + "，差异 " + (diff >= 0 ? "+" : "") + diff,
                List.of("ROLE_ADMIN", "ROLE_VIEWER"));

        // 盘亏后可能跌破水位
        if (diff < 0) {
            checkAndCreateWarning(part.getId());
        }
        return check;
    }

    public Page<InventoryCheck> page(Integer pageNum, Integer pageSize, Integer partId) {
        LambdaQueryWrapper<InventoryCheck> qw = new LambdaQueryWrapper<>();
        if (partId != null) {
            qw.eq(InventoryCheck::getPartId, partId);
        }
        qw.orderByDesc(InventoryCheck::getId);
        return inventoryCheckMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    private void writeFlow(Integer flowType, Integer partId, int qty, String operator) {
        StockFlow flow = new StockFlow();
        flow.setFlowNo(UUID.randomUUID().toString().replace("-", ""));
        flow.setFlowType(flowType);
        flow.setPartId(partId);
        flow.setQuantity(qty);
        flow.setOperator(operator);
        flow.setRemark(flowType == 6 ? "盘点盘盈" : "盘点盘亏");
        flow.setIsException(0);
        stockFlowMapper.insert(flow);
    }

    private void checkAndCreateWarning(Integer partId) {
        SparePart part = sparePartMapper.selectById(partId);
        if (part == null) return;
        int minStock = part.getMinStock() == null ? 0 : part.getMinStock();
        int goodStock = part.getGoodStock() == null ? 0 : part.getGoodStock();
        if (goodStock <= minStock) {
            String title = "库存预警：" + part.getName();
            if (internalMessageMapper.countUnreadWarning(title) > 0) return;
            messageService.createForRoles(1, title,
                    "备品【" + part.getPartNo() + " " + part.getName() + "】当前良品库存 "
                            + goodStock + "，已达最低水位线 " + minStock,
                    List.of("ROLE_ADMIN", "ROLE_VIEWER"));
        }
    }
}
