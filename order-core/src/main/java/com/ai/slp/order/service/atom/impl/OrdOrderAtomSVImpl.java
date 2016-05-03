package com.ai.slp.order.service.atom.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ai.slp.order.dao.mapper.bo.OrdOrder;
import com.ai.slp.order.dao.mapper.bo.OrdOrderCriteria;
import com.ai.slp.order.dao.mapper.factory.MapperFactory;
import com.ai.slp.order.service.atom.interfaces.IOrdOrderAtomSV;

@Component
public class OrdOrderAtomSVImpl implements IOrdOrderAtomSV {

    @Override
    public List<OrdOrder> selectByExample(OrdOrderCriteria example) {
        return MapperFactory.getOrdOrderMapper().selectByExample(example);
    }

    @Override
    public int countByExample(OrdOrderCriteria example) {
        return MapperFactory.getOrdOrderMapper().countByExample(example);
    }

}
