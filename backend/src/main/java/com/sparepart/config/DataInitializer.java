package com.sparepart.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.entity.User;
import com.sparepart.mapper.UserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 启动数据初始化：创建并一次性校准指定岗位账号（密码用 BCrypt 加密存储）。
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap-users-enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final Environment environment;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DataInitializer(UserMapper userMapper, Environment environment) {
        this.userMapper = userMapper;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        // Public source does not contain organization-specific accounts or passwords.
        // Set both variables during initial deployment, then disable bootstrap.
        String adminUsername = System.getenv("SPAREPART_BOOTSTRAP_ADMIN_USERNAME");
        String adminPassword = System.getenv("SPAREPART_BOOTSTRAP_ADMIN_PASSWORD");
        if (adminUsername != null && !adminUsername.isBlank()
                && adminPassword != null && !adminPassword.isBlank()) {
            ensureConfiguredUser(adminUsername, adminPassword, "管理员", "ROLE_ADMIN", 1);
        }

        if (!environment.acceptsProfiles(Profiles.of("local"))) return;

        // Local profile uses sample accounts only; production bootstrap remains opt-in.
        ensureConfiguredUser("admin-local", "admin123", "本地管理员", "ROLE_ADMIN", 1);
        ensureConfiguredUser("viewer-local", "viewer123", "本地主管", "ROLE_VIEWER", 0);
        ensureConfiguredUser("viewer2-local", "viewer123", "本地主管二", "ROLE_VIEWER", 0);
        Long testCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "test"));
        if (testCount == null || testCount == 0) {
            insertUser("test", "test", "测试账号", "ROLE_ADMIN", 0);
        }
    }

    private void insertUser(String username, String rawPassword, String realName, String role, Integer mustChangePwd) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        user.setMustChangePwd(mustChangePwd);
        userMapper.insert(user);
    }

    private void ensureConfiguredUser(String username, String rawPassword, String realName, String role, Integer mustChangePwd) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            insertUser(username, rawPassword, realName, role, mustChangePwd);
            return;
        }
        if (realName.equals(user.getRealName())) return;
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRealName(realName);
        user.setRole(role);
        user.setStatus(1);
        user.setMustChangePwd(mustChangePwd);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setSessionVersion((user.getSessionVersion() == null ? 0 : user.getSessionVersion()) + 1);
        userMapper.updateById(user);
    }
}
