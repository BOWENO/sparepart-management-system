-- =====================================================================
-- 生产车间备品备件管理系统 数据库初始化脚本 (MySQL 8.0)
-- 执行前请先创建数据库: CREATE DATABASE sparepart DEFAULT CHARACTER SET utf8mb4;
-- 说明：
--   1. 已按需求采用 LIKE 模糊搜索，不再需要全文索引（ngram 对单字不友好）。
--   2. 库存禁止为负：CHECK 约束（仅 MySQL 8.0.16+ 强制，更早版本需用触发器）。
--   3. 默认账号(admin/manager)由后端启动时自动创建（BCrypt 加密），无需在此插入。
-- =====================================================================

USE sparepart;

-- 1. 备品档案表
CREATE TABLE IF NOT EXISTS `spare_parts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `part_no` varchar(50) NOT NULL COMMENT '备品编码',
  `name` varchar(100) NOT NULL COMMENT '备品名称',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(10) DEFAULT '个' COMMENT '单位',
  `location_code` varchar(50) DEFAULT NULL COMMENT '库位编码 (如 A-3-2)',
  `min_stock` int DEFAULT '0' COMMENT '最低水位线',
  `good_stock` int DEFAULT '0' COMMENT '良品库存',
  `scrap_stock` int DEFAULT '0' COMMENT '报废库存',
  `rated_life_count` bigint DEFAULT NULL COMMENT '额定寿命/设备动作次数',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '单价（成本分析用）',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `deleted` tinyint DEFAULT '0' COMMENT '0正常 1已删除（仅从当前清单隐藏）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_no` (`part_no`),
  KEY `idx_spare_part_deleted` (`deleted`,`status`),
  CONSTRAINT `chk_spare_stock` CHECK (`good_stock` >= 0 AND `scrap_stock` >= 0)
) COMMENT '备品档案';

-- 2. 产线档案与设备台账表
CREATE TABLE IF NOT EXISTS `production_lines` (
  `id` int NOT NULL AUTO_INCREMENT,
  `line_code` varchar(50) NOT NULL COMMENT '产线编号',
  `line_name` varchar(100) DEFAULT NULL COMMENT '产线名称',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_line_code` (`line_code`)
) COMMENT '产线档案';

CREATE TABLE IF NOT EXISTS `devices` (
  `id` int NOT NULL AUTO_INCREMENT,
  `device_code` varchar(120) NOT NULL COMMENT '唯一设备标识（产线编号-产线内设备编号）',
  `local_device_code` varchar(50) NOT NULL COMMENT '产线内设备编号',
  `device_name` varchar(100) NOT NULL COMMENT '设备名称',
  `line_name` varchar(50) DEFAULT NULL COMMENT '所属产线编号（兼容展示）',
  `line_id` int NOT NULL COMMENT '产线ID',
  `status` tinyint DEFAULT '1' COMMENT '1运行 0停用',
  `deleted` tinyint DEFAULT '0' COMMENT '0正常 1已删除（仅从当前清单隐藏）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_code` (`device_code`),
  UNIQUE KEY `uk_line_local_device` (`line_id`,`local_device_code`),
  KEY `idx_device_line` (`line_id`),
  KEY `idx_device_deleted` (`deleted`,`status`)
) COMMENT '设备台账';

-- 3. 出入库流水表 (含异常标记)
CREATE TABLE IF NOT EXISTS `stock_flow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_no` varchar(32) NOT NULL COMMENT '流水单号',
  `flow_type` tinyint NOT NULL COMMENT '0初始化 1入库 2出库 3换新出 4换新入废 5良品转报废 6盘盈 7盘亏 8异常换新 9报废品清运',
  `part_id` int NOT NULL COMMENT '备品ID',
  `device_id` int DEFAULT NULL COMMENT '关联设备ID',
  `quantity` int NOT NULL COMMENT '变动数量',
  `operator` varchar(50) NOT NULL COMMENT '操作人',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_exception` tinyint(1) DEFAULT '0' COMMENT '0正常 1异常(无旧件标记)',
  `exception_remark` varchar(255) DEFAULT NULL COMMENT '异常备注(遗失/损毁说明)',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '历史兼容字段，V2.1不再用于成本分析',
  `business_date` date DEFAULT (CURRENT_DATE) COMMENT '业务日期',
  `reversed_flow_id` bigint DEFAULT NULL COMMENT '冲正对应原流水',
  `replace_record_id` bigint DEFAULT NULL COMMENT '对应换新明细，用于准确显示领用人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_flow_no` (`flow_no`),
  KEY `idx_part_device` (`part_id`, `device_id`),
  KEY `idx_created` (`created_at`)
) COMMENT '出入库流水';

-- 4. 换新关联表 (记录配对)
CREATE TABLE IF NOT EXISTS `replace_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `flow_no` varchar(32) NOT NULL COMMENT '关联换新流水号',
  `new_part_id` int NOT NULL COMMENT '新备品ID',
  `old_part_id` int DEFAULT NULL COMMENT '旧备品ID (异常换新时为空)',
  `device_id` int NOT NULL COMMENT '绑定设备',
  `technician_id` int DEFAULT NULL COMMENT '领用技术员',
  `quantity` int NOT NULL COMMENT '新品数量',
  `old_quantity` int DEFAULT NULL COMMENT '退回旧品数量',
  `difference_reason` varchar(255) DEFAULT NULL COMMENT '型号或数量差异原因',
  `request_id` varchar(64) DEFAULT NULL COMMENT '客户端幂等请求编号',
  `replacement_reason` varchar(32) DEFAULT NULL COMMENT 'FAILURE故障/LIFE_LIMIT寿命强换/OTHER其他',
  `counter_start` bigint DEFAULT NULL COMMENT '设备累计计数起点',
  `counter_end` bigint DEFAULT NULL COMMENT '设备累计计数终点',
  `actual_usage_count` bigint DEFAULT NULL COMMENT '旧件实际使用次数',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE有效/CANCELLED已撤销',
  `cancel_reason` varchar(255) DEFAULT NULL,
  `cancelled_by` varchar(50) DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_flow_no` (`flow_no`)
) COMMENT '换新关联';

CREATE TABLE IF NOT EXISTS `technicians` (
  `id` int NOT NULL AUTO_INCREMENT, `employee_no` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL, `status` tinyint DEFAULT 1,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
  UNIQUE KEY `uk_technician_no` (`employee_no`)
) COMMENT '技术员档案';
CREATE TABLE IF NOT EXISTS `installed_parts` (
  `id` bigint NOT NULL AUTO_INCREMENT, `replace_record_id` bigint NOT NULL,
  `part_id` int NOT NULL, `device_id` int NOT NULL, `technician_id` int NOT NULL,
  `quantity` int NOT NULL, `in_use_quantity` int NOT NULL, `status` varchar(20) DEFAULT 'IN_USE',
  `issued_at` datetime DEFAULT CURRENT_TIMESTAMP, `removed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_installed_active` (`part_id`,`device_id`,`in_use_quantity`)
) COMMENT '在使用备品';
CREATE TABLE IF NOT EXISTS `installed_part_retirements` (
  `id` bigint NOT NULL AUTO_INCREMENT, `replace_record_id` bigint NOT NULL,
  `installed_part_id` bigint NOT NULL, `quantity` int NOT NULL, PRIMARY KEY (`id`),
  KEY `idx_retirement_replace` (`replace_record_id`)
) COMMENT '换新退回在用件明细';

CREATE TABLE IF NOT EXISTS `scrap_records` (
  `id` bigint NOT NULL AUTO_INCREMENT, `scrap_no` varchar(40) NOT NULL,
  `flow_no` varchar(32) NOT NULL, `replace_record_id` bigint DEFAULT NULL,
  `part_id` int NOT NULL, `device_id` int DEFAULT NULL,
  `quantity` int NOT NULL, `remaining_quantity` int NOT NULL,
  `actual_usage_count` bigint DEFAULT NULL, `rated_life_count` bigint DEFAULT NULL,
  `replacement_reason` varchar(32) DEFAULT NULL, `scrap_reason` varchar(255) DEFAULT NULL,
  `operator` varchar(50) NOT NULL, `status` varchar(20) DEFAULT 'PENDING',
  `disposal_batch` varchar(64) DEFAULT NULL, `received_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `cleared_at` datetime DEFAULT NULL, PRIMARY KEY (`id`), UNIQUE KEY `uk_scrap_no` (`scrap_no`),
  KEY `idx_scrap_part_status` (`part_id`,`status`),
  UNIQUE KEY `uk_scrap_replace_record` (`replace_record_id`)
) COMMENT '报废品明细档案';

CREATE TABLE IF NOT EXISTS `scrap_disposal_items` (
  `id` bigint NOT NULL AUTO_INCREMENT, `flow_id` bigint NOT NULL,
  `scrap_record_id` bigint NOT NULL, `quantity` int NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
  UNIQUE KEY `uk_disposal_record` (`flow_id`,`scrap_record_id`),
  KEY `idx_disposal_flow` (`flow_id`)
) COMMENT '报废品处置分配明细';

-- 5. 盘点差异单
CREATE TABLE IF NOT EXISTS `inventory_checks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `part_id` int NOT NULL,
  `book_qty` int NOT NULL COMMENT '账面数',
  `actual_qty` int NOT NULL COMMENT '实盘数',
  `diff_qty` int NOT NULL COMMENT '差异 (正=盘盈, 负=盘亏)',
  `operator` varchar(50) NOT NULL,
  `payload_hash` varchar(64) DEFAULT NULL,
  `item_count` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT '盘点差异单';

-- 6. 操作日志 (热数据)
CREATE TABLE IF NOT EXISTS `operation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator` varchar(50) NOT NULL,
  `module` varchar(50) NOT NULL COMMENT '模块名',
  `action` varchar(50) NOT NULL COMMENT '操作类型',
  `before_json` json DEFAULT NULL COMMENT '变更前数据',
  `after_json` json DEFAULT NULL COMMENT '变更后数据',
  `ip` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator_time` (`operator`, `created_at`)
) COMMENT '操作日志(热)';

-- 7. 内部消息表
CREATE TABLE IF NOT EXISTS `internal_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `msg_type` tinyint NOT NULL COMMENT '1库存预警 2盘点差异',
  `title` varchar(100) NOT NULL,
  `content` text,
  `is_read` tinyint DEFAULT '0' COMMENT '0未读 1已读',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT '内部消息';

CREATE TABLE IF NOT EXISTS `message_recipients` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` bigint NOT NULL,
  `user_id` int NOT NULL,
  `is_read` tinyint DEFAULT 0,
  `read_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_message_user` (`message_id`,`user_id`)
) COMMENT '个人消息收件与已读状态';

CREATE TABLE IF NOT EXISTS `request_dedup` (
  `request_id` varchar(64) NOT NULL,
  `flow_no` varchar(32) NOT NULL,
  `operator` varchar(50) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_id`)
) COMMENT '业务请求幂等记录';

CREATE TABLE IF NOT EXISTS `excel_import_batches` (
  `batch_no` varchar(32) NOT NULL,
  `module` varchar(20) NOT NULL,
  `operator` varchar(50) NOT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `rolled_back_at` datetime DEFAULT NULL,
  PRIMARY KEY (`batch_no`)
) COMMENT 'Excel导入批次';

CREATE TABLE IF NOT EXISTS `excel_import_rows` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(32) NOT NULL,
  `record_id` int NOT NULL,
  `was_new` tinyint NOT NULL,
  `before_json` json DEFAULT NULL,
  `after_json` json DEFAULT NULL,
  PRIMARY KEY (`id`), KEY `idx_import_batch` (`batch_no`)
) COMMENT 'Excel导入回滚快照';

-- 8. 用户表（登录/角色数据来源；默认账号由后端启动初始化）
CREATE TABLE IF NOT EXISTS `users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(100) NOT NULL COMMENT 'BCrypt密码哈希',
  `real_name` varchar(50) NOT NULL COMMENT '姓名',
  `role` varchar(20) NOT NULL COMMENT 'ROLE_ADMIN 仓管员 / ROLE_VIEWER 主管',
  `status` tinyint DEFAULT '1' COMMENT '1启用 0停用',
  `must_change_pwd` tinyint DEFAULT '0' COMMENT '1需强制改密 0正常',
  `failed_attempts` int DEFAULT 0,
  `locked_until` datetime DEFAULT NULL,
  `session_version` int DEFAULT 0,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) COMMENT '用户表';

-- ============ 示例基础数据 ============
INSERT IGNORE INTO `devices` (`device_code`, `device_name`, `line_name`) VALUES
('DEV-001', '冲压机1号', 'A线'),
('DEV-002', '冲压机2号', 'A线'),
('DEV-003', '焊接机器人', 'B线'),
('DEV-004', '注塑机1号', 'B线');

INSERT IGNORE INTO `spare_parts` (`part_no`, `name`, `spec`, `unit`, `location_code`, `min_stock`, `good_stock`, `scrap_stock`, `price`) VALUES
('SP-0001', '冲床模具', 'M30', '套', 'A-1-1', 5, 20, 0, 1200.00),
('SP-0002', '密封圈', 'O型-50', '个', 'A-1-2', 50, 200, 0, 3.50),
('SP-0003', '液压油', '46#', '桶', 'A-2-1', 5, 3, 0, 680.00),
('SP-0004', '轴承', '6205', '个', 'B-1-1', 20, 15, 0, 45.00),
('SP-0005', 'PLC模块', 'S7-200', '块', 'B-2-1', 3, 2, 0, 3200.00);
