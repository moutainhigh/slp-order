package com.ai.slp.order.service.business.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.SystemException;
import com.ai.opt.sdk.constants.ExceptCodeConstants;
import com.ai.opt.sdk.util.StringUtil;
import com.ai.opt.sdk.util.UUIDUtil;
import com.ai.slp.order.api.ofc.params.OfcCodeRequst;
import com.ai.slp.order.api.ofc.params.OrdOdProdVo;
import com.ai.slp.order.api.ofc.params.OrderOfcVo;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeTotal;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeTotalCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdLogistics;
import com.ai.slp.order.dao.mapper.bo.OrdOdLogisticsCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdProd;
import com.ai.slp.order.dao.mapper.bo.OrdOdProdCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOrder;
import com.ai.slp.order.dao.mapper.bo.OrdOrderCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdParam;
import com.ai.slp.order.dao.mapper.bo.OrdParamCriteria;
import com.ai.slp.order.service.atom.interfaces.IOrdOdFeeTotalAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdLogisticsAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdProdAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOrderAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdParamAtomSV;
import com.ai.slp.order.service.business.interfaces.IOfcBusiSV;
import com.ai.slp.order.util.SequenceUtil;
import com.alibaba.fastjson.JSON;

@Service
@Transactional
public class OfcBusiSVImpl implements IOfcBusiSV {

	private static final Logger LOG = LoggerFactory.getLogger(OfcBusiSVImpl.class);

	@Autowired
	private IOrdOrderAtomSV ordOrderAtomSV;

	@Autowired
	private IOrdOdFeeTotalAtomSV ordOdFeeTotalAtomSV;
	@Autowired
	private IOrdOdLogisticsAtomSV ordOdLogisticsAtomSV;

	@Autowired
	private IOrdOdProdAtomSV ordOdProdAtomSV;

	@Autowired
	private IOrdParamAtomSV ordParamAtomSV;

	@Override
	public void insertOrdOrder(OrderOfcVo request) throws BusinessException, SystemException {
		if (StringUtil.isBlank(request.getOrOrderOfcVo().getTenantId() + request.getOrdOdLogisticsVo().getTenantId()
				+ request.getOrdOdFeeTotalVo().getTenantId())) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "订单信息租户Id不能为空");
		}
		if (StringUtil.isBlank("" + request.getOrOrderOfcVo().getOrderId() + request.getOrdOdLogisticsVo().getOrderId()
				+ request.getOrOrderOfcVo().getOrderId())) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "订单信息订单Id不能为空");
		}
		// 订单信息
		LOG.info("++++++++++++++++++请求数据+++++++++++++++" + JSON.toJSONString(request));
		OrdOrderCriteria orderNumExample = new OrdOrderCriteria();
		OrdOrderCriteria.Criteria orderNumCriteria = orderNumExample.createCriteria();
		orderNumCriteria.andTenantIdEqualTo(request.getOrOrderOfcVo().getTenantId());
		orderNumCriteria.andOrderIdEqualTo(request.getOrOrderOfcVo().getOrderId());
		List<OrdOrder> orderList = ordOrderAtomSV.selectByExample(orderNumExample);
		OrdOrder ordOrder = new OrdOrder();
		BeanUtils.copyProperties(request.getOrOrderOfcVo(), ordOrder);
		if (orderList.isEmpty()) {
			ordOrderAtomSV.insertSelective(ordOrder);
		} else {
			OrdOrderCriteria orderUpdateExample = new OrdOrderCriteria();
			OrdOrderCriteria.Criteria orderUpdateCriteria = orderUpdateExample.createCriteria();
			orderUpdateCriteria.andTenantIdEqualTo(request.getOrOrderOfcVo().getTenantId());
			orderUpdateCriteria.andOrderIdEqualTo(request.getOrOrderOfcVo().getOrderId());
			ordOrderAtomSV.updateByExampleSelective(ordOrder, orderUpdateExample);
		}

		// 订单费用信息
		OrdOdFeeTotalCriteria ordOdFeeNumExample = new OrdOdFeeTotalCriteria();
		OrdOdFeeTotalCriteria.Criteria ordOdFeeNumCriteria = ordOdFeeNumExample.createCriteria();
		ordOdFeeNumCriteria.andTenantIdEqualTo(request.getOrdOdFeeTotalVo().getTenantId());
		ordOdFeeNumCriteria.andOrderIdEqualTo(request.getOrdOdFeeTotalVo().getOrderId());
		List<OrdOdFeeTotal> ordOdFeeList = ordOdFeeTotalAtomSV.selectByExample(ordOdFeeNumExample);
		OrdOdFeeTotal ordOdFeeTotal = new OrdOdFeeTotal();
		BeanUtils.copyProperties(request.getOrdOdFeeTotalVo(), ordOdFeeTotal);
		if (ordOdFeeList.isEmpty()) {
			ordOdFeeTotalAtomSV.insertSelective(ordOdFeeTotal);
		} else {
			OrdOdFeeTotalCriteria ordOdFeeupdateExample = new OrdOdFeeTotalCriteria();
			OrdOdFeeTotalCriteria.Criteria ordOdFeeupdateCriteria = ordOdFeeupdateExample.createCriteria();
			ordOdFeeupdateCriteria.andTenantIdEqualTo(request.getOrdOdFeeTotalVo().getTenantId());
			ordOdFeeupdateCriteria.andOrderIdEqualTo(request.getOrdOdFeeTotalVo().getOrderId());
			ordOdFeeTotalAtomSV.updateByExampleSelective(ordOdFeeTotal, ordOdFeeupdateExample);
		}

		// 订单出货信息
		OrdOdLogisticsCriteria ordOdLogisticsExample = new OrdOdLogisticsCriteria();
		OrdOdLogisticsCriteria.Criteria criteria = ordOdLogisticsExample.createCriteria();
		criteria.andTenantIdEqualTo(request.getOrdOdLogisticsVo().getTenantId());
		criteria.andOrderIdEqualTo(request.getOrdOdLogisticsVo().getOrderId());
		List<OrdOdLogistics> ordOdLogisticsList = ordOdLogisticsAtomSV.selectByExample(ordOdLogisticsExample);
		OrdOdLogistics ordOdLogistics = new OrdOdLogistics();
		BeanUtils.copyProperties(request.getOrdOdLogisticsVo(), ordOdLogistics);
		ordOdLogistics.setLogisticsId(UUIDUtil.genShortId());
		if (ordOdLogisticsList.isEmpty()) {
			ordOdLogisticsAtomSV.insertSelective(ordOdLogistics);
		} else {
			OrdOdLogisticsCriteria ordExample = new OrdOdLogisticsCriteria();
			OrdOdLogisticsCriteria.Criteria ordOdLogisticsCriteria = ordExample.createCriteria();
			ordOdLogisticsCriteria.andTenantIdEqualTo(request.getOrdOdLogisticsVo().getTenantId());
			ordOdLogisticsCriteria.andOrderIdEqualTo(request.getOrdOdLogisticsVo().getOrderId());
			ordOdLogisticsAtomSV.updateByExampleSelective(ordOdLogistics, ordExample);
		}

	}

	@Override
	public int insertOrdOdProdOfc(OrdOdProdVo request) throws BusinessException, SystemException {
		if (StringUtil.isBlank(request.getTenantId())) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "租户Id不能为空");
		}
		if (StringUtil.isBlank(request.getOrderId() + "")) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "订单Id不能为空");
		}
		if (StringUtil.isBlank(request.getProdCode())) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "商品编码不能为空");
		}
		OrdOdProdCriteria example = new OrdOdProdCriteria();
		OrdOdProdCriteria.Criteria criteria = example.createCriteria();
		criteria.andTenantIdEqualTo(request.getTenantId());
		criteria.andOrderIdEqualTo(request.getOrderId());
		criteria.andProdCodeEqualTo(request.getProdCode());
		List<OrdOdProd> list = ordOdProdAtomSV.selectByExample(example);
		OrdOdProd ordOdProd = new OrdOdProd();
		BeanUtils.copyProperties(request, ordOdProd);
		ordOdProd.setProdDetalId(SequenceUtil.createProdDetailId());
		LOG.info("++++++++++++++++++请求数据+++++++++++++++" + JSON.toJSONString(ordOdProd));
		if (list.isEmpty()) {
			return ordOdProdAtomSV.insertSelective(ordOdProd);
		} else {
			OrdOdProdCriteria prodExample = new OrdOdProdCriteria();
			OrdOdProdCriteria.Criteria prodCriteria = prodExample.createCriteria();
			prodCriteria.andTenantIdEqualTo(ordOdProd.getTenantId());
			prodCriteria.andProdCodeEqualTo(ordOdProd.getProdCode());
			prodCriteria.andOrderIdEqualTo(ordOdProd.getOrderId());
			return ordOdProdAtomSV.updateByExampleSelective(ordOdProd, prodExample);
		}
	}

	@Override
	public String parseOfcCode(OfcCodeRequst request) throws BusinessException, SystemException {
		OrdParamCriteria example = new OrdParamCriteria();
		OrdParamCriteria.Criteria criteria = example.createCriteria();
		criteria.andTenantIdEqualTo(request.getTenantId());
		criteria.andSystemIdEqualTo(request.getSystemId());
		criteria.andOutCodeEqualTo(request.getOutCode().trim());
		List<OrdParam> list = ordParamAtomSV.selectByExample(example);
		if (!list.isEmpty()) {
			return list.get(0).getCode();
		}
		return null;
	}

}