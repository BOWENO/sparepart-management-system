package com.sparepart;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class SoftDeleteMigrationTest {
    @Test void existingDatabaseUpgradesIdempotentlyWithoutChangingLedgerOrFlows() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:archive-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            // 原发布包中的真实建表脚本，保留为升级回归测试输入。
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-before-soft-delete.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO spare_parts(id,part_no,name,good_stock,scrap_stock) VALUES(9001,'MIG-P','原有备品',13,7)");
                statement.executeUpdate("INSERT INTO devices(id,device_code,device_name) VALUES(9001,'MIG-D','原有设备')");
                statement.executeUpdate("INSERT INTO stock_flow(flow_no,flow_type,part_id,device_id,quantity,operator) VALUES('MIG-F',1,9001,9001,13,'原操作员')");
            }
            for (int restart=0; restart<2; restart++) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-local.sql"));
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "SELECT p.good_stock,p.scrap_stock,p.deleted,d.deleted,f.quantity FROM stock_flow f " +
                        "JOIN spare_parts p ON p.id=f.part_id JOIN devices d ON d.id=f.device_id WHERE f.flow_no='MIG-F'")) {
                    assertTrue(rows.next());
                    assertEquals(13,rows.getInt(1)); assertEquals(7,rows.getInt(2));
                    assertEquals(0,rows.getInt(3)); assertEquals(0,rows.getInt(4)); assertEquals(13,rows.getInt(5));
                    assertFalse(rows.next());
                }
            }
        }
    }
}
