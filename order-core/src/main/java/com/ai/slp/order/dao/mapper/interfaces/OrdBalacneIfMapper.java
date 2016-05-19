package com.ai.slp.order.dao.mapper.interfaces;

import com.ai.slp.order.dao.mapper.bo.OrdBalacneIf;
import com.ai.slp.order.dao.mapper.bo.OrdBalacneIfCriteria;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OrdBalacneIfMapper {
    int countByExample(OrdBalacneIfCriteria example);

    int deleteByExample(OrdBalacneIfCriteria example);

    int insert(OrdBalacneIf record);

    int insertSelective(OrdBalacneIf record);

    List<OrdBalacneIf> selectByExample(OrdBalacneIfCriteria example);

    int updateByExampleSelective(@Param("record") OrdBalacneIf record, @Param("example") OrdBalacneIfCriteria example);

    int updateByExample(@Param("record") OrdBalacneIf record, @Param("example") OrdBalacneIfCriteria example);
}