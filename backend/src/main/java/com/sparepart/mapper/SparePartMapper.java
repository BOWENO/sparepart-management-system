package com.sparepart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sparepart.entity.SparePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SparePartMapper extends BaseMapper<SparePart> {

    @Select("SELECT * FROM spare_parts WHERE part_no = #{code}")
    SparePart findIncludingDeleted(@Param("code") String code);

    /** 历史冲正允许调整已删除档案的库存，但不会恢复其清单可见性。 */
    @Update("UPDATE spare_parts SET good_stock=good_stock+#{good}, scrap_stock=scrap_stock+#{scrap} " +
            "WHERE id=#{id} AND good_stock+#{good}>=0 AND scrap_stock+#{scrap}>=0")
    int correctHistoricalStock(@Param("id") Integer id, @Param("good") int good, @Param("scrap") int scrap);

    /**
     * 条件扣减良品库存（行锁防超卖）：
     * 受影响行数=0 表示库存不足，返回即失败。
     */
    @Update("UPDATE spare_parts SET good_stock = good_stock - #{qty} " +
            "WHERE id = #{id} AND deleted = 0 AND good_stock >= #{qty}")
    int deductGoodStock(@Param("id") Integer id, @Param("qty") Integer qty);

    /** 条件增加报废库存 */
    @Update("UPDATE spare_parts SET scrap_stock = scrap_stock + #{qty} WHERE id = #{id} AND deleted = 0")
    int addScrapStock(@Param("id") Integer id, @Param("qty") Integer qty);

    /** 条件扣减报废库存，用于废品清运。 */
    @Update("UPDATE spare_parts SET scrap_stock = scrap_stock - #{qty} " +
            "WHERE id = #{id} AND deleted = 0 AND scrap_stock >= #{qty}")
    int deductScrapStock(@Param("id") Integer id, @Param("qty") Integer qty);

    /** 增加良品库存（入库/初始化用） */
    @Update("UPDATE spare_parts SET good_stock = good_stock + #{qty} WHERE id = #{id} AND deleted = 0")
    int addGoodStock(@Param("id") Integer id, @Param("qty") Integer qty);

    /** 盘点调账：条件更新为实盘数（>=0 兜底防负） */
    @Update("UPDATE spare_parts SET good_stock = #{actualQty} " +
            "WHERE id = #{id} AND deleted = 0 AND good_stock = #{bookQty} AND #{actualQty} >= 0")
    int reconcileStock(@Param("id") Integer id,
                       @Param("bookQty") Integer bookQty,
                       @Param("actualQty") Integer actualQty);

    /** 良品库存总量 */
    @Select("SELECT COALESCE(SUM(good_stock),0) FROM spare_parts WHERE status = 1 AND deleted = 0")
    Long sumGoodStock();

    /** 报废库存总量 */
    @Select("SELECT COALESCE(SUM(scrap_stock),0) FROM spare_parts WHERE status = 1 AND deleted = 0")
    Long sumScrapStock();

    /** 低于水位线的启用备品种类数 */
    @Select("SELECT COUNT(*) FROM spare_parts WHERE status = 1 AND deleted = 0 AND good_stock <= min_stock")
    Long countLowStock();
}
