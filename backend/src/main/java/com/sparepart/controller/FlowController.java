package com.sparepart.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.aspect.OpLog;
import com.sparepart.common.Result;
import com.sparepart.common.TabularTextUtil;
import com.sparepart.common.UserContext;
import com.sparepart.dto.ReplaceBatchRequest;
import com.sparepart.entity.User;
import com.sparepart.service.FlowService;
import com.sparepart.service.StockFlowReadService;
import com.sparepart.dto.StockFlowView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.time.LocalDate;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.format.annotation.DateTimeFormat;

@RestController
@RequestMapping("/api/v1/flows")
@Validated
public class FlowController {

    private final FlowService flowService;
    private final StockFlowReadService reader;

    public FlowController(FlowService flowService, StockFlowReadService reader) {
        this.flowService = flowService;
        this.reader = reader;
    }

    /** 批量一键换新（核心） */
    @PostMapping("/replace-batch")
    @OpLog(module = "换新", action = "批量换新")
    public Result<Map<String, Object>> replaceBatch(@Valid @RequestBody ReplaceBatchRequest req) {
        return Result.success("换新成功", flowService.replaceBatch(req, currentOperator()));
    }

    /** 从登录会话取真实操作人，防客户端伪造 */
    private String currentOperator() {
        User user = UserContext.get();
        return user == null ? "system" : user.getUsername();
    }

    /** 普通入库 */
    @PostMapping("/in")
    @OpLog(module = "出入库", action = "入库")
    public Result<Void> in(@RequestParam @NotNull(message = "备品ID不能为空") Integer partId,
                           @RequestParam @Min(value = 1, message = "数量必须大于0") Integer qty,
                           @RequestParam(required = false) String remark,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                           LocalDate businessDate) {
        flowService.stockIn(partId, qty, currentOperator(), remark, businessDate);
        return Result.success("入库成功");
    }

    /** 普通出库 */
    @PostMapping("/out")
    @OpLog(module = "出入库", action = "出库")
    public Result<Void> out(@RequestParam @NotNull(message = "备品ID不能为空") Integer partId,
                            @RequestParam @NotNull(message = "设备ID不能为空") Integer deviceId,
                            @RequestParam @Min(value = 1, message = "数量必须大于0") Integer qty,
                            @RequestParam(required = false) String remark) {
        flowService.stockOut(partId, deviceId, qty, currentOperator(), remark);
        return Result.success("出库成功");
    }

    /** 报废出库 */
    @PostMapping("/scrap-out")
    @OpLog(module = "出入库", action = "报废出库")
    public Result<Void> scrapOut(@RequestParam @NotNull(message = "备品ID不能为空") Integer partId,
                                 @RequestParam(required = false) Integer deviceId,
                                 @RequestParam @Min(value = 1, message = "数量必须大于0") Integer qty,
                                 @RequestParam(required = false) String remark) {
        flowService.scrapOut(partId, deviceId, qty, currentOperator(), remark);
        return Result.success("报废出库成功");
    }

    /** 良品转报废。 */
    @PostMapping("/good-to-scrap")
    @OpLog(module = "出入库", action = "良品转报废")
    public Result<Void> goodToScrap(@RequestParam @NotNull(message = "备品ID不能为空") Integer partId,
                                    @RequestParam @Min(value = 1, message = "数量必须大于0") Integer qty,
                                    @RequestParam(required = false) String remark) {
        flowService.goodToScrap(partId, qty, currentOperator(), remark);
        return Result.success("良品转报废成功");
    }

    /** 流水历史查询 */
    @GetMapping
    public Result<Page<StockFlowView>> flows(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size,
                                         @RequestParam(required = false) Integer flowType,
                                         @RequestParam(required = false) String operator,
                                         @RequestParam(required = false) Integer partId,
                                         @RequestParam(required = false) Integer deviceId) {
        return Result.success(reader.page(page,size,flowType,operator,partId,deviceId));
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) Integer partId,
                       @RequestParam(required = false) Integer deviceId,
                       @RequestParam(required = false) Integer flowType,
                       @RequestParam(required = false) String operator,
                       HttpServletResponse response) throws IOException {
        var rows=reader.list(flowType,operator,partId,deviceId);
        List<String> headers=List.of("流水号","业务类型","产线编号","产线名称","设备编号","设备名称",
                "领用人","备品编码","备品名称","规格","数量","单位","录入人","说明","业务日期","操作时间");
        var data=rows.stream().map(row->List.of(text(row.getFlowNo()),StockFlowReadService.typeName(row.getFlowType()),
                text(row.getLineCode()),text(row.getLineName()),text(row.getDeviceCode()),text(row.getDeviceName()),
                text(row.getRecipientName()),text(row.getPartNo()),text(row.getPartName()),text(row.getPartSpec()),
                text(row.getQuantity()),text(row.getUnit()),text(row.getOperatorName()),text(row.getRemark()),
                text(row.getBusinessDate()),text(row.getCreatedAt()))).toList();
        TabularTextUtil.write(response, "流水下钻明细", headers, data);
    }

    @PostMapping("/{id}/reverse")
    @OpLog(module = "出入库", action = "流水冲正")
    public Result<Void> reverse(@PathVariable Long id, @RequestParam String reason) {
        flowService.reverseFlow(id, currentOperator(), reason);
        return Result.success("冲正成功");
    }

    /** 换新是一个整体业务单，只允许整单撤销。 */
    @PostMapping("/replacements/{flowNo}/cancel")
    @OpLog(module = "换新", action = "撤销换新单")
    public Result<Void> cancelReplacement(@PathVariable String flowNo, @RequestParam String reason) {
        flowService.cancelReplacement(flowNo, currentOperator(), reason);
        return Result.success("换新单已撤销");
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
