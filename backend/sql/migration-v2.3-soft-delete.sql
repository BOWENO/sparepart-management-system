-- 仅用于 MySQL 8 已有数据库，部署新 JAR 前执行一次。
-- Windows portable / H2 由新 JAR 启动时自动升级，不执行本脚本。
USE sparepart;
ALTER TABLE spare_parts ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1已删除';
ALTER TABLE devices ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1已删除';
CREATE INDEX idx_spare_part_deleted ON spare_parts(deleted, status);
CREATE INDEX idx_device_deleted ON devices(deleted, status);
