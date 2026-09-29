package com.sparepart.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.entity.ProductionLine;
import com.sparepart.mapper.ProductionLineMapper;
import org.springframework.stereotype.Service;
import java.util.List;
@Service public class ProductionLineService {
    private final ProductionLineMapper mapper;
    public ProductionLineService(ProductionLineMapper mapper){this.mapper=mapper;}
    public List<ProductionLine> list(boolean activeOnly){LambdaQueryWrapper<ProductionLine> q=new LambdaQueryWrapper<>();if(activeOnly)q.eq(ProductionLine::getStatus,1);return mapper.selectList(q.orderByAsc(ProductionLine::getLineCode));}
    public ProductionLine save(ProductionLine row){
        if(row.getLineCode()==null||row.getLineCode().isBlank())throw new BizException("产线编号不能为空");
        row.setLineCode(normalize(row.getLineCode())); if(row.getStatus()==null)row.setStatus(1);
        if(row.getId()!=null){ProductionLine stored=mapper.selectById(row.getId());if(stored==null)throw new BizException("产线不存在");if(!stored.getLineCode().equals(row.getLineCode()))throw new BizException("产线编号创建后不可修改，可修改产线名称或状态");}
        LambdaQueryWrapper<ProductionLine> q=new LambdaQueryWrapper<ProductionLine>().eq(ProductionLine::getLineCode,row.getLineCode());if(row.getId()!=null)q.ne(ProductionLine::getId,row.getId());
        if(mapper.selectCount(q)>0)throw new BizException("产线编号已存在");
        if(row.getId()==null)mapper.insert(row);else if(mapper.updateById(row)==0)throw new BizException("产线不存在");return row;
    }
    public ProductionLine requireActive(Integer id){ProductionLine row=mapper.selectById(id);if(row==null||!Integer.valueOf(1).equals(row.getStatus()))throw new BizException("产线不存在或已停用");return row;}
    public ProductionLine findOrCreate(String code){String normalized=normalize(code);ProductionLine row=mapper.selectOne(new LambdaQueryWrapper<ProductionLine>().eq(ProductionLine::getLineCode,normalized));if(row==null){row=new ProductionLine();row.setLineCode(normalized);row.setLineName(normalized);row.setStatus(1);mapper.insert(row);}return row;}
    public static String normalize(String code){return code==null?null:code.trim().toUpperCase();}
}
