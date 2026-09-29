package com.sparepart.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sparepart.common.BizException;
import com.sparepart.dto.UserAccountResponse;
import com.sparepart.entity.User;
import com.sparepart.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class UserAccountService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public UserAccountService(UserMapper userMapper) { this.userMapper = userMapper; }
    public List<UserAccountResponse> list() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByAsc(User::getUsername)).stream()
            .map(u -> new UserAccountResponse(u.getId(), u.getUsername(), u.getRealName(), u.getRole(), u.getStatus(), u.getMustChangePwd())).toList();
    }
    public void resetPassword(Integer id, String temporaryPassword) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BizException("账号不存在");
        user.setPasswordHash(encoder.encode(temporaryPassword)); user.setMustChangePwd(1);
        user.setFailedAttempts(0); user.setLockedUntil(null);
        user.setSessionVersion((user.getSessionVersion() == null ? 0 : user.getSessionVersion()) + 1);
        userMapper.updateById(user);
    }
}
