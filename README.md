# 生产车间备品备件管理系统

Java 17 / Spring Boot 后端与 Vue 3 / Vite 前端，实现备品档案、库存、领用更换、报废、设备台账、盘点和分析看板。

## 本地开发

1. 安装 Java 17、Maven、Node.js 和 npm。
2. 在 `backend` 目录执行 `mvn spring-boot:run -Dspring-boot.run.profiles=local`。本地模式使用 H2 数据库，监听 `7878` 端口。
3. 在 `frontend` 目录执行 `npm ci` 和 `npm run dev`。
4. 本地示例账号为 `admin-local/admin123`（首次登录改密）或 `test/test`。本地配置只用于开发，不应部署到公网。

## 生产配置

生产环境使用 MySQL 8。先执行 `backend/sql/init.sql`，通过环境变量设置 `DB_USERNAME`、`DB_PASSWORD`，并确保 `BOOTSTRAP_USERS_ENABLED` 默认关闭。首次创建管理员时，临时设置 `BOOTSTRAP_USERS_ENABLED=true`、`SPAREPART_BOOTSTRAP_ADMIN_USERNAME` 和 `SPAREPART_BOOTSTRAP_ADMIN_PASSWORD`；创建完成后关闭引导开关。首次登录会要求修改密码。

`backend/src/main/resources/data-production.sql` 和 `deployment/initial-data/` 是原项目的真实业务数据，未纳入公开仓库。构建产物、日志、数据库文件和运行时目录也不纳入版本控制。
