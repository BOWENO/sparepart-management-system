package com.sparepart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sparepart.entity.InternalMessage;
import com.sparepart.mapper.InternalMessageMapper;
import com.sparepart.mapper.MessageRecipientMapper;
import com.sparepart.mapper.UserMapper;
import com.sparepart.entity.MessageRecipient;
import com.sparepart.entity.User;
import com.sparepart.common.UserContext;
import com.sparepart.common.BizException;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InternalMessageService {

    private final InternalMessageMapper internalMessageMapper;
    private final MessageRecipientMapper recipientMapper;
    private final UserMapper userMapper;

    public InternalMessageService(InternalMessageMapper internalMessageMapper,
                                  MessageRecipientMapper recipientMapper, UserMapper userMapper) {
        this.internalMessageMapper = internalMessageMapper;
        this.recipientMapper = recipientMapper;
        this.userMapper = userMapper;
    }

    public Page<InternalMessage> page(Integer pageNum, Integer pageSize, Integer msgType, Integer isRead) {
        return internalMessageMapper.selectForUser(new Page<>(pageNum, pageSize), currentUserId(), msgType, isRead);
    }

    /** 首页消息速览：最新未读 N 条 */
    public java.util.List<InternalMessage> latestUnread(int limit) {
        return internalMessageMapper.latestUnreadForUser(currentUserId(), limit);
    }

    public void markRead(Long id) {
        MessageRecipient recipient = recipientMapper.selectOne(new LambdaQueryWrapper<MessageRecipient>()
                .eq(MessageRecipient::getMessageId, id).eq(MessageRecipient::getUserId, currentUserId()));
        if (recipient == null) throw new BizException("消息不存在或不属于当前用户");
        recipient.setIsRead(1);
        recipient.setReadAt(LocalDateTime.now());
        recipientMapper.updateById(recipient);
    }

    public long unreadCount() {
        return internalMessageMapper.unreadCountForUser(currentUserId());
    }

    /** 创建消息并为指定角色的全部启用用户生成独立收件记录。 */
    public InternalMessage createForRoles(Integer msgType, String title, String content, Collection<String> roles) {
        InternalMessage msg = new InternalMessage();
        msg.setMsgType(msgType);
        msg.setTitle(title);
        msg.setContent(content);
        msg.setIsRead(0);
        internalMessageMapper.insert(msg);
        for (User user : userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1).in(User::getRole, roles))) {
            MessageRecipient recipient = new MessageRecipient();
            recipient.setMessageId(msg.getId());
            recipient.setUserId(user.getId());
            recipient.setIsRead(0);
            recipientMapper.insert(recipient);
        }
        return msg;
    }

    private Integer currentUserId() {
        User user = UserContext.get();
        if (user == null) throw new BizException("未登录");
        return user.getId();
    }
}
