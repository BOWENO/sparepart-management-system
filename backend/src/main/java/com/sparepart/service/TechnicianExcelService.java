package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparepart.common.BizException;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.dto.TechnicianExcelDTO;
import com.sparepart.entity.ExcelImportBatch;
import com.sparepart.entity.ExcelImportRow;
import com.sparepart.entity.Technician;
import com.sparepart.mapper.ExcelImportBatchMapper;
import com.sparepart.mapper.ExcelImportRowMapper;
import com.sparepart.mapper.TechnicianMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TechnicianExcelService {
    private static final List<String> HEADERS = List.of("工号", "姓名", "状态（启用/停用）");
    private final TechnicianMapper technicianMapper;
    private final ExcelImportBatchMapper batchMapper;
    private final ExcelImportRowMapper rowMapper;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    public TechnicianExcelService(TechnicianMapper technicianMapper, ExcelImportBatchMapper batchMapper,
                                  ExcelImportRowMapper rowMapper) {
        this.technicianMapper=technicianMapper; this.batchMapper=batchMapper; this.rowMapper=rowMapper;
    }

    public void exportLedger(HttpServletResponse response) throws IOException {
        List<TechnicianExcelDTO> rows=technicianMapper.selectList(new LambdaQueryWrapper<Technician>()
                .orderByAsc(Technician::getEmployeeNo)).stream().map(t->{
            TechnicianExcelDTO dto=new TechnicianExcelDTO(); dto.setEmployeeNo(t.getEmployeeNo()); dto.setName(t.getName());
            dto.setStatus(Integer.valueOf(1).equals(t.getStatus())?"启用":"停用"); return dto;
        }).toList();
        var data=rows.stream().map(row->List.of(text(row.getEmployeeNo()),text(row.getName()),text(row.getStatus()))).toList();
        TabularTextUtil.write(response,"技术员当前台账",HEADERS,data);
    }

    public Map<String,Object> preview(MultipartFile file) throws IOException {
        List<TechnicianExcelDTO> rows=read(file); int added=0;
        for(TechnicianExcelDTO row:rows) if(find(row.getEmployeeNo())==null) added++;
        return Map.of("totalCount",rows.size(),"newCount",added,"updateCount",rows.size()-added,"rows",rows.stream().limit(100).toList());
    }

    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> importExcel(MultipartFile file,String operator) throws IOException {
        List<TechnicianExcelDTO> rows=read(file); String batchNo=UUID.randomUUID().toString().replace("-","");
        ExcelImportBatch batch=new ExcelImportBatch(); batch.setBatchNo(batchNo); batch.setModule("TECHNICIAN");
        batch.setOperator(operator); batch.setFileName(file.getOriginalFilename()); batch.setStatus("SUCCESS"); batchMapper.insert(batch);
        int added=0,updated=0;
        for(TechnicianExcelDTO dto:rows){
            Technician current=find(dto.getEmployeeNo()); String before=current==null?null:json.writeValueAsString(current);
            if(current==null){
                if(!StringUtils.hasText(dto.getName())) throw new BizException("新增技术员【"+dto.getEmployeeNo()+"】必须填写姓名");
                current=new Technician(); current.setEmployeeNo(dto.getEmployeeNo().trim()); current.setName(dto.getName().trim());
                current.setStatus(parseStatus(dto.getStatus(),1)); technicianMapper.insert(current); added++;
            } else {
                // 空白单元格表示保持原值，绝不清空现有姓名或状态。
                if(StringUtils.hasText(dto.getName())) current.setName(dto.getName().trim());
                if(StringUtils.hasText(dto.getStatus())) current.setStatus(parseStatus(dto.getStatus(),current.getStatus()));
                technicianMapper.updateById(current); updated++;
            }
            ExcelImportRow saved=new ExcelImportRow(); saved.setBatchNo(batchNo); saved.setRecordId(current.getId());
            saved.setWasNew(before==null?1:0); saved.setBeforeJson(before); saved.setAfterJson(json.writeValueAsString(current)); rowMapper.insert(saved);
        }
        return Map.of("batchNo",batchNo,"newCount",added,"updateCount",updated);
    }

    @Transactional(rollbackFor=Exception.class)
    public void rollback(String batchNo) throws IOException {
        ExcelImportBatch batch=batchMapper.selectById(batchNo);
        if(batch==null||!"TECHNICIAN".equals(batch.getModule())) throw new BizException("技术员导入批次不存在");
        if("ROLLED_BACK".equals(batch.getStatus())) throw new BizException("该批次已经回滚");
        List<ExcelImportRow> rows=rowMapper.selectList(new LambdaQueryWrapper<ExcelImportRow>()
                .eq(ExcelImportRow::getBatchNo,batchNo).orderByDesc(ExcelImportRow::getId));
        for(ExcelImportRow row:rows){
            Technician current=technicianMapper.selectById(row.getRecordId()); if(current==null) continue;
            Technician after=json.readValue(row.getAfterJson(),Technician.class);
            if(!same(current,after)) throw new BizException("技术员【"+current.getEmployeeNo()+"】导入后又被修改，不能自动回滚");
            if(Integer.valueOf(1).equals(row.getWasNew())) { current.setStatus(0); technicianMapper.updateById(current); }
            else technicianMapper.updateById(json.readValue(row.getBeforeJson(),Technician.class));
        }
        batch.setStatus("ROLLED_BACK"); batch.setRolledBackAt(LocalDateTime.now()); batchMapper.updateById(batch);
    }

    private List<TechnicianExcelDTO> read(MultipartFile file) throws IOException {
        List<TabularTextUtil.TextRow> textRows=TabularTextUtil.read(file,HEADERS,2000);
        List<TechnicianExcelDTO> rows=textRows.stream().map(text->{TechnicianExcelDTO row=new TechnicianExcelDTO();
            row.setEmployeeNo(text.get(HEADERS.get(0)));row.setName(text.get(HEADERS.get(1)));row.setStatus(text.get(HEADERS.get(2)));return row;}).toList();
        Set<String> numbers=new HashSet<>(); List<String> errors=new ArrayList<>();
        for(int i=0;i<rows.size();i++){
            TechnicianExcelDTO row=rows.get(i); int excelRow=textRows.get(i).lineNumber();
            if(!StringUtils.hasText(row.getEmployeeNo())) errors.add("第"+excelRow+"行：工号不能为空");
            else { row.setEmployeeNo(row.getEmployeeNo().trim()); if(!numbers.add(row.getEmployeeNo().toUpperCase())) errors.add("第"+excelRow+"行：工号在文件中重复"); }
            if(StringUtils.hasText(row.getStatus())&&!List.of("启用","停用").contains(row.getStatus().trim())) errors.add("第"+excelRow+"行：状态只能填写启用或停用");
        }
        if(!errors.isEmpty()) throw new BizException(String.join("；",errors)); return rows;
    }
    private Technician find(String no){return technicianMapper.selectOne(new LambdaQueryWrapper<Technician>().eq(Technician::getEmployeeNo,no.trim()));}
    private int parseStatus(String text,int fallback){if(!StringUtils.hasText(text))return fallback;return "停用".equals(text.trim())?0:1;}
    private boolean same(Technician a,Technician b){return Objects.equals(a.getEmployeeNo(),b.getEmployeeNo())&&Objects.equals(a.getName(),b.getName())&&Objects.equals(a.getStatus(),b.getStatus());}
    private static String text(Object value){return value==null?"":String.valueOf(value);}
}
