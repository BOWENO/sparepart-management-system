-- V2.1 已有数据库升级到 V2.2（MySQL 8.0.29+）
-- 删除设备和备品仅做归档标记：当前清单不再展示，历史流水、关联记录和操作日志全部保留。
USE sparepart;

ALTER TABLE spare_parts ADD COLUMN IF NOT EXISTS deleted tinyint DEFAULT 0 COMMENT '0正常 1已删除（仅从当前清单隐藏）';
ALTER TABLE devices ADD COLUMN IF NOT EXISTS deleted tinyint DEFAULT 0 COMMENT '0正常 1已删除（仅从当前清单隐藏）';

CREATE INDEX IF NOT EXISTS idx_spare_part_deleted ON spare_parts(deleted, status);
CREATE INDEX IF NOT EXISTS idx_device_deleted ON devices(deleted, status);
