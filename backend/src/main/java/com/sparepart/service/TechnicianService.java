package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.entity.Technician;
import com.sparepart.mapper.TechnicianMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TechnicianService {
    private final TechnicianMapper mapper;
    public TechnicianService(TechnicianMapper mapper) { this.mapper = mapper; }
    public List<Technician> list(boolean activeOnly) {
        LambdaQueryWrapper<Technician> q = new LambdaQueryWrapper<>();
        if (activeOnly) q.eq(Technician::getStatus, 1);
        return mapper.selectList(q.orderByAsc(Technician::getEmployeeNo));
    }
    public Technician save(Technician row) {
        if (row.getEmployeeNo() == null || row.getEmployeeNo().isBlank() || row.getName() == null || row.getName().isBlank())
            throw new BizException("技术员工号和姓名不能为空");
        row.setEmployeeNo(row.getEmployeeNo().trim()); row.setName(row.getName().trim());
        if (row.getStatus() == null) row.setStatus(1);
        LambdaQueryWrapper<Technician> q = new LambdaQueryWrapper<Technician>().eq(Technician::getEmployeeNo,row.getEmployeeNo());
        if (row.getId()!=null) q.ne(Technician::getId,row.getId());
        if (mapper.selectCount(q)>0) throw new BizException("技术员工号已存在");
        if (row.getId()==null) mapper.insert(row); else if (mapper.updateById(row)==0) throw new BizException("技术员不存在");
        return row;
    }
    public void disable(Integer id) {
        Technician row=mapper.selectById(id); if(row==null) throw new BizException("技术员不存在");
        row.setStatus(0); mapper.updateById(row);
    }
}
