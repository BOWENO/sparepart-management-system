-- 本文件仅保留为历史参考，不再由 application-local.yml 自动执行。
-- 业务数据不得在应用重启时自动插入或恢复。
INSERT INTO devices(device_code, device_name, line_name)
SELECT 'DEV-001', '冲压机1号', 'A线'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE device_code = 'DEV-001');
INSERT INTO devices(device_code, device_name, line_name)
SELECT 'DEV-002', '焊接机器人', 'B线'
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE device_code = 'DEV-002');

INSERT INTO spare_parts(part_no, name, spec, unit, location_code, min_stock, good_stock, scrap_stock, price)
SELECT 'SP-0001', '冲床模具', 'M30', '套', 'A-1-1', 5, 20, 0, 1200.00
WHERE NOT EXISTS (SELECT 1 FROM spare_parts WHERE part_no = 'SP-0001');
INSERT INTO spare_parts(part_no, name, spec, unit, location_code, min_stock, good_stock, scrap_stock, price)
SELECT 'SP-0002', '密封圈', 'O型50', '个', 'A-1-2', 50, 200, 0, 3.50
WHERE NOT EXISTS (SELECT 1 FROM spare_parts WHERE part_no = 'SP-0002');
INSERT INTO spare_parts(part_no, name, spec, unit, location_code, min_stock, good_stock, scrap_stock, price)
SELECT 'SP-0003', '液压油', '46#', '桶', 'A-2-1', 5, 3, 0, 680.00
WHERE NOT EXISTS (SELECT 1 FROM spare_parts WHERE part_no = 'SP-0003');
