package com.ai.slp.order.service.atom.impl;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ai.slp.order.constants.OrdersConstants;
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

    @Override
    public int insertSelective(OrdOrder record) {
        return MapperFactory.getOrdOrderMapper().insertSelective(record);
    }

    @Override
    public OrdOrder selectByOrderId(String tenantId, long orderId) {
        OrdOrderCriteria example = new OrdOrderCriteria();
        example.createCriteria().andTenantIdEqualTo(tenantId).andOrderIdEqualTo(orderId);
        List<OrdOrder> ordOrders = MapperFactory.getOrdOrderMapper().selectByExample(example);
        return ordOrders == null || ordOrders.isEmpty() ? null : ordOrders.get(0);
    }

    @Override
    public int updateById(OrdOrder ordOrder) {
        return MapperFactory.getOrdOrderMapper().updateByPrimaryKey(ordOrder);
    }

	@Override
	public List<OrdOrder> selectChildOrder(String tenantId, long parentId) {
		 OrdOrderCriteria example = new OrdOrderCriteria();
	     example.createCriteria().andTenantIdEqualTo(tenantId).andParentOrderIdEqualTo(parentId);
	     return MapperFactory.getOrdOrderMapper().selectByExample(example);
	}

	@Override
	public void updateStateByOrderId(String tenantId, Long orderId,String state) {
		OrdOrder record = new OrdOrder();
		record.setState(state);
		record.setOrderId(orderId);
		MapperFactory.getOrdOrderMapper().updateByPrimaryKeySelective(record);
	}

	@Override
	public List<OrdOrder> selectByBatchNo(long orderId,String tenantId, long batchNo) {
		OrdOrderCriteria example = new OrdOrderCriteria();
        example.createCriteria().andTenantIdEqualTo(tenantId).andBatchNoEqualTo(batchNo).andOrderIdNotEqualTo(orderId)
        .andCusServiceFlagEqualTo(OrdersConstants.OrdOrder.cusServiceFlag.NO);
        return MapperFactory.getOrdOrderMapper().selectByExample(example);
	}
	
	@Override
	public List<OrdOrder> selectMergeOrderByBatchNo(long orderId,String tenantId, long batchNo,String state) {
		OrdOrderCriteria example = new OrdOrderCriteria();
		example.createCriteria().andTenantIdEqualTo(tenantId).andBatchNoEqualTo(batchNo).andOrderIdNotEqualTo(orderId)
		.andCusServiceFlagEqualTo(OrdersConstants.OrdOrder.cusServiceFlag.NO).andStateEqualTo(OrdersConstants.OrdOrder.State.WAIT_SEND);
		return MapperFactory.getOrdOrderMapper().selectByExample(example);
	}

	@Override
	public int updateByExampleSelective(OrdOrder record, OrdOrderCriteria example) {
		return MapperFactory.getOrdOrderMapper().updateByExampleSelective(record, example);
	}
	
}
