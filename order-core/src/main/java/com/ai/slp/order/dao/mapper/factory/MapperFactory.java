package com.ai.slp.order.dao.mapper.factory;

import javax.annotation.PostConstruct;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ai.slp.order.dao.mapper.interfaces.OrdOdExtendMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdFeeOffsetMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdFeeTotalMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdInvoiceMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdLogisticsMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdProdMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOdStateChgMapper;
import com.ai.slp.order.dao.mapper.interfaces.OrdOrderMapper;

@Component
public class MapperFactory {

    @Autowired
    private SqlSessionTemplate st;

    private static SqlSessionTemplate sqlSessionTemplate;

    @PostConstruct
    void init() {
        setSqlSessionTemplate(st);
    }

    public static void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
        MapperFactory.sqlSessionTemplate = sqlSessionTemplate;
    }

    public static OrdOdExtendMapper getOrdOdExtendMapper() {
        return sqlSessionTemplate.getMapper(OrdOdExtendMapper.class);
    }

    public static OrdOdFeeOffsetMapper getOrdOdFeeOffsetMapper()

    {
        return sqlSessionTemplate.getMapper(OrdOdFeeOffsetMapper.class);
    }

    public static OrdOdFeeTotalMapper getOrdOdFeeTotalMapper() {
        return sqlSessionTemplate.getMapper(OrdOdFeeTotalMapper.class);
    }

    public static OrdOdInvoiceMapper getOrdOdInvoiceMapper() {
        return sqlSessionTemplate.getMapper(OrdOdInvoiceMapper.class);
    }

    public static OrdOdLogisticsMapper getOrdOdLogisticsMapper() {
        return sqlSessionTemplate.getMapper(OrdOdLogisticsMapper.class);
    }

    public static OrdOdProdMapper getOrdOdProdMapper() {
        return sqlSessionTemplate.getMapper(OrdOdProdMapper.class);
    }

    public static OrdOdStateChgMapper getOrdOdStateChgMapper() {
        return sqlSessionTemplate.getMapper(OrdOdStateChgMapper.class);
    }

    public static OrdOrderMapper getOrdOrderMapper() {
        return sqlSessionTemplate.getMapper(OrdOrderMapper.class);
    }

}
