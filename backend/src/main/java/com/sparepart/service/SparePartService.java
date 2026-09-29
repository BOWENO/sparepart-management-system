package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.common.BizException;
import com.sparepart.entity.SparePart;
import com.sparepart.mapper.SparePartMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SparePartService {

    private final SparePartMapper sparePartMapper;

    public SparePartService(SparePartMapper sparePartMapper) {
        this.sparePartMapper = sparePartMapper;
    }

    /** 分页 + LIKE 模糊搜索（名称/规格/编码） */
    public Page<SparePart> page(Integer pageNum, Integer pageSize, String keyword) {
        LambdaQueryWrapper<SparePart> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            qw.and(w -> w.like(SparePart::getName, kw)
                    .or().like(SparePart::getSpec, kw)
                    .or().like(SparePart::getPartNo, kw));
        }
        qw.orderByDesc(SparePart::getId);
        return sparePartMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    public SparePart getById(Integer id) {
        return sparePartMapper.selectById(id);
    }

    @Transactional
    public SparePart save(SparePart part) {
        if (part.getId() == null) {
            insert(part);
        } else {
            update(part);
        }
        return part;
    }

    private void insert(SparePart part) {
        ensureCodeUnique(part.getPartNo(), null);
        if (part.getGoodStock() == null) part.setGoodStock(0);
        if (part.getScrapStock() == null) part.setScrapStock(0);
        if (part.getMinStock() == null) part.setMinStock(0);
        if (part.getStatus() == null) part.setStatus(1);
        if (part.getUnit() == null) part.setUnit("个");
        if (part.getGoodStock() < 0 || part.getScrapStock() < 0) {
            throw new BizException("库存不能为负数");
        }
        sparePartMapper.insert(part);
    }

    private void update(SparePart part) {
        SparePart db = sparePartMapper.selectById(part.getId());
        if (db == null) {
            throw new BizException("备品不存在");
        }
        ensureCodeUnique(part.getPartNo(), part.getId());
        part.setGoodStock(db.getGoodStock());
        part.setScrapStock(db.getScrapStock());
        sparePartMapper.updateById(part);
    }

    private void ensureCodeUnique(String code, Integer excludeId) {
        SparePart archived = sparePartMapper.findIncludingDeleted(code);
        if (archived != null && Integer.valueOf(1).equals(archived.getDeleted()))
            throw new BizException("备品编码已用于已删除档案，历史记录仍保留，请使用其他编码");
        LambdaQueryWrapper<SparePart> query = new LambdaQueryWrapper<SparePart>().eq(SparePart::getPartNo, code);
        if (excludeId != null) query.ne(SparePart::getId, excludeId);
        if (sparePartMapper.selectCount(query) > 0) throw new BizException("备品编码已存在");
    }

    /** 删除：仅从当前清单隐藏，保留全部历史流水、关联记录和操作日志。 */
    @Transactional
    public SparePart delete(Integer id) {
        SparePart part = sparePartMapper.selectById(id);
        if (part == null) throw new BizException("备品不存在或已删除");
        sparePartMapper.deleteById(id);
        part.setDeleted(1);
        return part;
    }
}
