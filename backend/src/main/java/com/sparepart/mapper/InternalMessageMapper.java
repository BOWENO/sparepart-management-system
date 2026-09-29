package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.InternalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

@Mapper
public interface InternalMessageMapper extends BaseMapper<InternalMessage> {
    @Select("SELECT COUNT(*) FROM internal_messages m JOIN message_recipients r ON r.message_id=m.id " +
            "WHERE m.msg_type = 1 AND r.is_read = 0 AND m.title = #{title}")
    long countUnreadWarning(@Param("title") String title);

    @Select("SELECT m.id,m.msg_type,m.title,m.content,r.is_read,m.created_at " +
            "FROM internal_messages m JOIN message_recipients r ON r.message_id=m.id " +
            "WHERE r.user_id=#{userId} " +
            "AND (#{msgType} IS NULL OR m.msg_type=#{msgType}) " +
            "AND (#{isRead} IS NULL OR r.is_read=#{isRead}) ORDER BY m.id DESC")
    Page<com.sparepart.entity.InternalMessage> selectForUser(Page<com.sparepart.entity.InternalMessage> page,
            @Param("userId") Integer userId, @Param("msgType") Integer msgType,
            @Param("isRead") Integer isRead);

    @Select("SELECT m.id,m.msg_type,m.title,m.content,r.is_read,m.created_at " +
            "FROM internal_messages m JOIN message_recipients r ON r.message_id=m.id " +
            "WHERE r.user_id=#{userId} AND r.is_read=0 ORDER BY m.id DESC LIMIT #{limit}")
    List<com.sparepart.entity.InternalMessage> latestUnreadForUser(@Param("userId") Integer userId,
                                                                   @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM message_recipients WHERE user_id=#{userId} AND is_read=0")
    long unreadCountForUser(@Param("userId") Integer userId);
}
