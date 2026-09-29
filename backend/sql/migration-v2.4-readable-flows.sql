-- MySQL 已有库从 v2.3 升级时执行一次；便携 H2 会自动增量升级，不执行此文件。
USE sparepart;
ALTER TABLE stock_flow ADD COLUMN replace_record_id BIGINT DEFAULT NULL COMMENT '对应换新明细';
