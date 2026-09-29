package com.sparepart.config;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.entity.User;
import com.sparepart.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name = "app.offline-password-reset", havingValue = "true")
public class OfflinePasswordResetRunner implements ApplicationRunner {
    private final UserMapper userMapper;
    public OfflinePasswordResetRunner(UserMapper userMapper) { this.userMapper = userMapper; }
    public void run(ApplicationArguments args) {
        String username = System.getenv("SPAREPART_RESET_USERNAME"), password = System.getenv("SPAREPART_RESET_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) throw new IllegalArgumentException("缺少离线重置账号或临时密码");
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).{6,}$")) throw new IllegalArgumentException("临时密码至少6位，且必须同时包含字母和数字");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) throw new IllegalArgumentException("账号不存在：" + username);
        user.setPasswordHash(new BCryptPasswordEncoder().encode(password)); user.setMustChangePwd(1);
        user.setFailedAttempts(0); user.setLockedUntil(null); user.setSessionVersion((user.getSessionVersion() == null ? 0 : user.getSessionVersion()) + 1);
        userMapper.updateById(user);
        System.out.println("密码已重置，账号 " + username + " 下次登录必须修改密码。");
    }
}
