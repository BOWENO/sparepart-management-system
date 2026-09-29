CREATE TABLE IF NOT EXISTS spare_parts (
  id INT AUTO_INCREMENT PRIMARY KEY, part_no VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL, spec VARCHAR(100), unit VARCHAR(10) DEFAULT '个',
  location_code VARCHAR(50), min_stock INT DEFAULT 0, good_stock INT DEFAULT 0,
  scrap_stock INT DEFAULT 0, price DECIMAL(10,2) DEFAULT 0, status TINYINT DEFAULT 1,
  deleted TINYINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_spare_stock CHECK (good_stock >= 0 AND scrap_stock >= 0)
);
CREATE TABLE IF NOT EXISTS devices (
  id INT AUTO_INCREMENT PRIMARY KEY, device_code VARCHAR(120) NOT NULL UNIQUE,
  device_name VARCHAR(100) NOT NULL, line_name VARCHAR(50), status TINYINT DEFAULT 1,
  deleted TINYINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS production_lines (
  id INT AUTO_INCREMENT PRIMARY KEY, line_code VARCHAR(50) NOT NULL UNIQUE,
  line_name VARCHAR(100), status TINYINT DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS local_device_code VARCHAR(50);
ALTER TABLE devices ADD COLUMN IF NOT EXISTS line_id INT;
ALTER TABLE spare_parts ADD COLUMN IF NOT EXISTS deleted TINYINT DEFAULT 0;
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted TINYINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_spare_part_deleted ON spare_parts(deleted, status);
CREATE INDEX IF NOT EXISTS idx_device_deleted ON devices(deleted, status);
CREATE TABLE IF NOT EXISTS stock_flow (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, flow_no VARCHAR(32) NOT NULL, flow_type TINYINT NOT NULL,
  part_id INT NOT NULL, device_id INT, quantity INT NOT NULL, operator VARCHAR(50) NOT NULL,
  remark VARCHAR(255), is_exception TINYINT DEFAULT 0, exception_remark VARCHAR(255),
  unit_price DECIMAL(10,2), business_date DATE DEFAULT CURRENT_DATE,
  reversed_flow_id BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE stock_flow ADD COLUMN IF NOT EXISTS replace_record_id BIGINT;
CREATE TABLE IF NOT EXISTS replace_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, flow_no VARCHAR(32) NOT NULL, new_part_id INT NOT NULL,
  old_part_id INT, device_id INT NOT NULL, quantity INT NOT NULL,
  old_quantity INT, difference_reason VARCHAR(255), request_id VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS inventory_checks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, part_id INT NOT NULL, book_qty INT NOT NULL,
  actual_qty INT NOT NULL, diff_qty INT NOT NULL, operator VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, operator VARCHAR(50) NOT NULL,
  module VARCHAR(50) NOT NULL, action VARCHAR(50) NOT NULL, before_json VARCHAR(4000),
  after_json VARCHAR(4000), ip VARCHAR(45), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS internal_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, msg_type TINYINT NOT NULL, title VARCHAR(100) NOT NULL,
  content VARCHAR(4000), is_read TINYINT DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS message_recipients (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, message_id BIGINT NOT NULL, user_id INT NOT NULL,
  is_read TINYINT DEFAULT 0, read_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_message_user UNIQUE(message_id, user_id)
);
CREATE TABLE IF NOT EXISTS request_dedup (
  request_id VARCHAR(64) PRIMARY KEY, flow_no VARCHAR(32) NOT NULL,
  operator VARCHAR(50) NOT NULL, payload_hash VARCHAR(64), item_count INT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS excel_import_batches (
  batch_no VARCHAR(32) PRIMARY KEY, module VARCHAR(20) NOT NULL, operator VARCHAR(50) NOT NULL,
  file_name VARCHAR(255), status VARCHAR(20) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  rolled_back_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS excel_import_rows (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_no VARCHAR(32) NOT NULL, record_id INT NOT NULL,
  was_new TINYINT NOT NULL, before_json VARCHAR(4000), after_json VARCHAR(4000)
);
CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL, real_name VARCHAR(50) NOT NULL,
  role VARCHAR(20) NOT NULL, status TINYINT DEFAULT 1, must_change_pwd TINYINT DEFAULT 0,
  failed_attempts INT DEFAULT 0, locked_until TIMESTAMP, session_version INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE stock_flow ADD COLUMN IF NOT EXISTS business_date DATE DEFAULT CURRENT_DATE;
ALTER TABLE stock_flow ADD COLUMN IF NOT EXISTS reversed_flow_id BIGINT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS old_quantity INT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS difference_reason VARCHAR(255);
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS request_id VARCHAR(64);
ALTER TABLE spare_parts ADD COLUMN IF NOT EXISTS rated_life_count BIGINT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS replacement_reason VARCHAR(32);
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS counter_start BIGINT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS counter_end BIGINT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS actual_usage_count BIGINT;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(255);
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(50);
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;
ALTER TABLE replace_records ADD COLUMN IF NOT EXISTS technician_id INT;
CREATE TABLE IF NOT EXISTS technicians (
  id INT AUTO_INCREMENT PRIMARY KEY, employee_no VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL, status TINYINT DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS installed_parts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, replace_record_id BIGINT NOT NULL,
  part_id INT NOT NULL, device_id INT NOT NULL, technician_id INT NOT NULL,
  quantity INT NOT NULL, in_use_quantity INT NOT NULL, status VARCHAR(20) DEFAULT 'IN_USE',
  issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, removed_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_installed_active ON installed_parts(part_id,device_id,in_use_quantity);
CREATE TABLE IF NOT EXISTS installed_part_retirements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, replace_record_id BIGINT NOT NULL,
  installed_part_id BIGINT NOT NULL, quantity INT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_retirement_replace ON installed_part_retirements(replace_record_id);
CREATE TABLE IF NOT EXISTS scrap_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, scrap_no VARCHAR(40) NOT NULL UNIQUE,
  flow_no VARCHAR(32) NOT NULL, replace_record_id BIGINT, part_id INT NOT NULL, device_id INT,
  quantity INT NOT NULL, remaining_quantity INT NOT NULL,
  actual_usage_count BIGINT, rated_life_count BIGINT, replacement_reason VARCHAR(32),
  scrap_reason VARCHAR(255), operator VARCHAR(50) NOT NULL, status VARCHAR(20) DEFAULT 'PENDING',
  disposal_batch VARCHAR(64), received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, cleared_at TIMESTAMP
);
CREATE TABLE IF NOT EXISTS scrap_disposal_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, flow_id BIGINT NOT NULL,
  scrap_record_id BIGINT NOT NULL, quantity INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_disposal_record UNIQUE(flow_id, scrap_record_id),
  CONSTRAINT chk_disposal_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_stock_flow_no ON stock_flow(flow_no);
CREATE INDEX IF NOT EXISTS idx_scrap_part_status ON scrap_records(part_id, status, received_at);
CREATE INDEX IF NOT EXISTS idx_replace_flow_no ON replace_records(flow_no);
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS session_version INT DEFAULT 0;
ALTER TABLE request_dedup ADD COLUMN IF NOT EXISTS payload_hash VARCHAR(64);
ALTER TABLE request_dedup ADD COLUMN IF NOT EXISTS item_count INT;
ALTER TABLE excel_import_rows ADD COLUMN IF NOT EXISTS after_json VARCHAR(4000);
ALTER TABLE scrap_records ADD COLUMN IF NOT EXISTS replace_record_id BIGINT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_scrap_replace_record ON scrap_records(replace_record_id);
