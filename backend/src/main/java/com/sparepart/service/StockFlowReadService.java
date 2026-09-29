package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.dto.StockFlowView;
import com.sparepart.entity.*;
import com.sparepart.mapper.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockFlowReadService {
    private final StockFlowReadMapper reader;
    private final ReplaceRecordMapper replacements;
    private final TechnicianMapper technicians;
    private final StockFlowMapper flows;
    public StockFlowReadService(StockFlowReadMapper reader, ReplaceRecordMapper replacements,
                               TechnicianMapper technicians, StockFlowMapper flows) {
        this.reader=reader; this.replacements=replacements; this.technicians=technicians; this.flows=flows;
    }
    public Page<StockFlowView> page(int page,int size,Integer type,String operator,Integer part,Integer device) {
        var result=reader.page(new Page<>(page,size),type,operator,part,device);
        enrich(result.getRecords()); return result;
    }
    public List<StockFlowView> list(Integer type,String operator,Integer part,Integer device) {
        var result=reader.list(type,operator,part,device); enrich(result); return result;
    }
    private void enrich(List<StockFlowView> rows) {
        if(rows.isEmpty()) return;
        Set<Long> reversed=rows.stream().map(StockFlow::getReversedFlowId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long,StockFlow> originals=reversed.isEmpty()?Map.of():flows.selectBatchIds(reversed).stream()
                .collect(Collectors.toMap(StockFlow::getId,r->r));
        List<StockFlow> sources=rows.stream().map(r->r.getReversedFlowId()==null?(StockFlow)r:originals.getOrDefault(r.getReversedFlowId(),r)).toList();
        Set<String> numbers=sources.stream().map(StockFlow::getFlowNo).collect(Collectors.toSet());
        var records=replacements.selectList(new LambdaQueryWrapper<ReplaceRecord>().in(ReplaceRecord::getFlowNo,numbers));
        Set<Integer> ids=records.stream().map(ReplaceRecord::getTechnicianId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer,Technician> people=ids.isEmpty()?Map.of():technicians.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Technician::getId,t->t));
        Map<String,List<ReplaceRecord>> byFlow=records.stream().collect(Collectors.groupingBy(ReplaceRecord::getFlowNo));
        for(int i=0;i<rows.size();i++) {
            StockFlow source=sources.get(i); StockFlowView row=rows.get(i);
            if(!Set.of(3,4,8).contains(source.getFlowType())) {
                row.setRecipientName(source.getFlowType()==2?"未登记":"不涉及领用"); continue;
            }
            var candidates=byFlow.getOrDefault(source.getFlowNo(),List.of()).stream().filter(r -> {
                if(source.getReplaceRecordId()!=null) return source.getReplaceRecordId().equals(r.getId());
                Integer part=source.getFlowType()==4?r.getOldPartId():r.getNewPartId();
                Integer qty=source.getFlowType()==4 && r.getOldQuantity()!=null?r.getOldQuantity():r.getQuantity();
                return Objects.equals(part,source.getPartId()) && Objects.equals(r.getDeviceId(),source.getDeviceId())
                        && Objects.equals(qty,source.getQuantity());
            }).toList();
            Set<Integer> recipientIds=candidates.stream().map(ReplaceRecord::getTechnicianId).collect(Collectors.toSet());
            if(recipientIds.size()!=1 || recipientIds.contains(null)) {
                row.setRecipientName(recipientIds.size()>1?"同单多人，待核对":"未登记"); continue;
            }
            Technician person=people.get(recipientIds.iterator().next());
            row.setRecipientName(person==null?"人员档案缺失":person.getName());
        }
    }
    public static String typeName(Integer type) {
        if(type==null) return "未知类型";
        return switch(type) {case 0->"初始化";case 1->"备品入库";case 2->"历史普通出库";case 3->"更换领新";
            case 4->"更换旧件入废";case 5->"库内损坏报废";case 6->"盘盈";case 7->"盘亏";
            case 8->"异常更换";case 9->"报废品清运";case 10->"冲正";default->"未知类型";};
    }
}
