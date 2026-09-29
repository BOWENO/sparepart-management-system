-- V2.0 已有数据库升级到 V2.1（MySQL 8.0.29+）
USE sparepart;

-- 设备身份升级：设备主键保持不变，只把业务唯一标识升级为“产线-产线内编号”。
CREATE TABLE IF NOT EXISTS production_lines (
 id int NOT NULL AUTO_INCREMENT, line_code varchar(50) NOT NULL, line_name varchar(100),
 status tinyint DEFAULT 1, created_at datetime DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), UNIQUE KEY uk_line_code(line_code)
);
ALTER TABLE devices MODIFY COLUMN device_code varchar(120) NOT NULL;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS local_device_code varchar(50) DEFAULT NULL;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS line_id int DEFAULT NULL;
INSERT IGNORE INTO production_lines(line_code,line_name,status)
SELECT DISTINCT UPPER(TRIM(COALESCE(NULLIF(line_name,''),'UNASSIGNED'))),
       UPPER(TRIM(COALESCE(NULLIF(line_name,''),'UNASSIGNED'))),1 FROM devices;
UPDATE devices d JOIN production_lines p
  ON p.line_code=UPPER(TRIM(COALESCE(NULLIF(d.line_name,''),'UNASSIGNED')))
SET d.local_device_code=d.device_code,d.line_id=p.id,d.line_name=p.line_code,
    d.device_code=CONCAT(p.line_code,'-',d.device_code)
WHERE d.local_device_code IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_line_local_device ON devices(line_id,local_device_code);
CREATE INDEX IF NOT EXISTS idx_device_line ON devices(line_id);

ALTER TABLE stock_flow ADD COLUMN IF NOT EXISTS business_date date DEFAULT (CURRENT_DATE);
ALTER TABLE stock_flow ADD COLUMN IF NOT EXISTS reversed_flow_id bigint DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS old_quantity int DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS difference_reason varchar(255) DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS request_id varchar(64) DEFAULT NULL;
ALTER TABLE spare_parts ADD COLUMN IF NOT EXISTS rated_life_count bigint DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS replacement_reason varchar(32) DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS counter_start bigint DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS counter_end bigint DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS actual_usage_count bigint DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS status varchar(20) DEFAULT 'ACTIVE';
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancel_reason varchar(255) DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancelled_by varchar(50) DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancelled_at datetime DEFAULT NULL;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS technician_id int DEFAULT NULL;
CREATE TABLE IF NOT EXISTS technicians (
 id int NOT NULL AUTO_INCREMENT, employee_no varchar(50) NOT NULL, name varchar(100) NOT NULL,
 status tinyint DEFAULT 1, created_at datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
 UNIQUE KEY uk_technician_no(employee_no)
);
CREATE TABLE IF NOT EXISTS installed_parts (
 id bigint NOT NULL AUTO_INCREMENT, replace_record_id bigint NOT NULL, part_id int NOT NULL,
 device_id int NOT NULL, technician_id int NOT NULL, quantity int NOT NULL, in_use_quantity int NOT NULL,
 status varchar(20) DEFAULT 'IN_USE', issued_at datetime DEFAULT CURRENT_TIMESTAMP, removed_at datetime,
 PRIMARY KEY(id), KEY idx_installed_active(part_id,device_id,in_use_quantity)
);
CREATE TABLE IF NOT EXISTS installed_part_retirements (
 id bigint NOT NULL AUTO_INCREMENT, replace_record_id bigint NOT NULL, installed_part_id bigint NOT NULL,
 quantity int NOT NULL, PRIMARY KEY(id), KEY idx_retirement_replace(replace_record_id)
);
CREATE TABLE IF NOT EXISTS scrap_records (
 id bigint NOT NULL AUTO_INCREMENT, scrap_no varchar(40) NOT NULL, flow_no varchar(32) NOT NULL,
 part_id int NOT NULL, device_id int, quantity int NOT NULL, remaining_quantity int NOT NULL,
 actual_usage_count bigint, rated_life_count bigint, replacement_reason varchar(32), scrap_reason varchar(255),
 operator varchar(50) NOT NULL, status varchar(20) DEFAULT 'PENDING', disposal_batch varchar(64),
 received_at datetime DEFAULT CURRENT_TIMESTAMP, cleared_at datetime, PRIMARY KEY(id),
 UNIQUE KEY uk_scrap_no(scrap_no), KEY idx_scrap_part_status(part_id,status)
);
ALTER TABLE scrap_records ADD COLUMN IF NOT EXISTS replace_record_id bigint DEFAULT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_scrap_replace_record ON scrap_records(replace_record_id);
CREATE TABLE IF NOT EXISTS scrap_disposal_items (
 id bigint NOT NULL AUTO_INCREMENT, flow_id bigint NOT NULL, scrap_record_id bigint NOT NULL,
 quantity int NOT NULL, created_at datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(id),
 UNIQUE KEY uk_disposal_record(flow_id,scrap_record_id), KEY idx_disposal_flow(flow_id)
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts int DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until datetime DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_version int DEFAULT 0;

CREATE TABLE IF NOT EXISTS message_recipients (
  id bigint NOT NULL AUTO_INCREMENT, message_id bigint NOT NULL, user_id int NOT NULL,
  is_read tinyint DEFAULT 0, read_at datetime DEFAULT NULL, created_at datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_message_user (message_id,user_id)
);
CREATE TABLE IF NOT EXISTS request_dedup (
  request_id varchar(64) NOT NULL, flow_no varchar(32) NOT NULL, operator varchar(50) NOT NULL,
  created_at datetime DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (request_id)
);
CREATE TABLE IF NOT EXISTS excel_import_batches (
  batch_no varchar(32) NOT NULL, module varchar(20) NOT NULL, operator varchar(50) NOT NULL,
  file_name varchar(255), status varchar(20) NOT NULL, created_at datetime DEFAULT CURRENT_TIMESTAMP,
  rolled_back_at datetime, PRIMARY KEY (batch_no)
);
CREATE TABLE IF NOT EXISTS excel_import_rows (
  id bigint NOT NULL AUTO_INCREMENT, batch_no varchar(32) NOT NULL, record_id int NOT NULL,
  was_new tinyint NOT NULL, before_json json, PRIMARY KEY (id), KEY idx_import_batch (batch_no)
);
ALTER TABLE request_dedup ADD COLUMN IF NOT EXISTS payload_hash varchar(64) DEFAULT NULL;
ALTER TABLE request_dedup ADD COLUMN IF NOT EXISTS item_count int DEFAULT NULL;
ALTER TABLE excel_import_rows ADD COLUMN IF NOT EXISTS after_json json DEFAULT NULL;

-- 为升级前的历史消息补充个人收件记录。
INSERT IGNORE INTO message_recipients(message_id,user_id,is_read)
SELECT m.id,u.id,m.is_read FROM internal_messages m CROSS JOIN users u WHERE u.status=1;
