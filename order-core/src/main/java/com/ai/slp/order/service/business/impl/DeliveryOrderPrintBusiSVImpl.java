package com.ai.slp.order.service.business.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.SystemException;
import com.ai.opt.sdk.constants.ExceptCodeConstants;
import com.ai.opt.sdk.util.BeanUtils;
import com.ai.opt.sdk.util.CollectionUtil;
import com.ai.opt.sdk.util.DateUtil;
import com.ai.slp.order.api.deliveryorderprint.param.DeliveryOrderPrintInfosRequest;
import com.ai.slp.order.api.deliveryorderprint.param.DeliveryOrderPrintRequest;
import com.ai.slp.order.api.deliveryorderprint.param.DeliveryOrderPrintResponse;
import com.ai.slp.order.api.deliveryorderprint.param.DeliveryOrderQueryResponse;
import com.ai.slp.order.api.deliveryorderprint.param.DeliveryProdPrintVo;
import com.ai.slp.order.constants.OrdRuleConstants;
import com.ai.slp.order.constants.OrdersConstants;
import com.ai.slp.order.constants.OrdersConstants.OrdOdStateChg;
import com.ai.slp.order.dao.mapper.attach.OrdOrderProdAttach;
import com.ai.slp.order.dao.mapper.bo.DeliverInfoProd;
import com.ai.slp.order.dao.mapper.bo.DeliverInfoProdCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdDeliverInfo;
import com.ai.slp.order.dao.mapper.bo.OrdOdDeliverInfoCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdLogistics;
import com.ai.slp.order.dao.mapper.bo.OrdOdLogisticsCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdProd;
import com.ai.slp.order.dao.mapper.bo.OrdOdProdCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOrder;
import com.ai.slp.order.dao.mapper.bo.OrdRule;
import com.ai.slp.order.service.atom.interfaces.IDeliveryOrderPrintAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdLogisticsAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdProdAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOrderAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdRuleAtomSV;
import com.ai.slp.order.service.business.interfaces.IDeliveryOrderPrintBusiSV;
import com.ai.slp.order.service.business.interfaces.IOrderFrameCoreSV;
import com.ai.slp.order.util.CommonCheckUtils;
import com.ai.slp.order.util.SequenceUtil;

@Service
@Transactional
public class DeliveryOrderPrintBusiSVImpl implements IDeliveryOrderPrintBusiSV{
	
	private static final Logger logger=LoggerFactory.getLogger(DeliveryOrderPrintBusiSVImpl.class);

	@Autowired
	private IOrdOrderAtomSV ordOrderAtomSV;
	
	@Autowired
	private IOrdOdProdAtomSV ordOdProdAtomSV;
	
	@Autowired
	private IOrdOdLogisticsAtomSV ordOdLogisticsAtomSV;
	
	@Autowired
	private IDeliveryOrderPrintAtomSV deliveryOrderPrintAtomSV;
	
	@Autowired
	private IOrdRuleAtomSV ordRuleAtomSV;
	
	@Autowired
	private IOrderFrameCoreSV orderFrameCoreSV;
	
	@Override
	public DeliveryOrderQueryResponse query(DeliveryOrderPrintRequest request) {
		DeliveryOrderQueryResponse response=new DeliveryOrderQueryResponse();
		/* 参数校验及判断是否存在提货单打印信息*/
		List<OrdOdDeliverInfo> deliverInfos = this.checkParamAndQueryInfos(
				request.getOrderId(),request.getTenantId());
		if(CollectionUtil.isEmpty(deliverInfos)) {
			/* 没有提货单打印信息的话,则表示第一次打印*/
			OrdOrder order = ordOrderAtomSV.selectByOrderId(request.getTenantId(), request.getOrderId());
			if(order==null) {
				logger.warn("未能查询到指定的订单信息[订单id:"+request.getOrderId()+"]");
				throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, 
						"未能查询到指定的订单信息[订单id:"+request.getOrderId()+"]");
			} 
			/* 获取订单下的商品信息*/
			List<OrdOdProd> ordOdProds = getOrdOdProds(request);
			/* 根据订单规则获取合并时间*/
			Timestamp time = getOrderListInTime(OrdRuleConstants.MERGE_ORDER_SETTING_ID,order.getOrderTime());
			/* 组装订单商品信息集合*/
			List<OrdOrderProdAttach> originalAttachs = createOriginalAttachs(ordOdProds);
			List<Long> orderList =null;
			for (OrdOdProd ordOdProd : ordOdProds) {
				/* 多表多条件查询*/
				List<OrdOrderProdAttach> orderProdAttachs = deliveryOrderPrintAtomSV.query(request.getUserId(),
						request.getTenantId(),ordOdProd.getSkuId(),ordOdProd.getRouteId(),ordOdProd.getOrderId(),
						OrdersConstants.OrdOrder.State.WAIT_DISTRIBUTION,time,order.getOrderTime());
				if(!CollectionUtil.isEmpty(orderProdAttachs)) {
					/* 筛选不符合合并规则的订单*/
					orderList = this.judgeOrder(originalAttachs,orderProdAttachs,orderList);
					if(!CollectionUtil.isEmpty(orderProdAttachs)) {
						response.setFlag(true);
						break;
					}else {
						response.setFlag(false);
					}
				}else {
					response.setFlag(false);
				}
			}
		}else {
			/*表示提货单打印信息已存在*/
			for(int i=0;i<deliverInfos.size();i++) {
				if(deliverInfos.get(i).getHorOrderId()!=0) {
					response.setFlag(true);
					break;
				}else {
					response.setFlag(false);
				}
			}
		}
		return response;
	}
	
	@Override
	public DeliveryOrderPrintResponse display(DeliveryOrderPrintRequest request)
			throws BusinessException, SystemException {
		DeliveryOrderPrintResponse response=new DeliveryOrderPrintResponse();
		/* 参数校验及判断是否存在提货单打印信息*/
		List<OrdOdDeliverInfo> deliverInfos = this.checkParamAndQueryInfos(
				request.getOrderId(),request.getTenantId());
		/* 没有提货单打印信息的话,则表示第一次打印*/
		OrdOrder order = ordOrderAtomSV.selectByOrderId(request.getTenantId(), request.getOrderId());
		if(order==null) {
			logger.warn("未能查询到指定的订单信息[订单id:"+request.getOrderId()+"]");
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, 
					"未能查询到指定的订单信息[订单id:"+request.getOrderId()+"]");
		} 
		List<DeliveryProdPrintVo> list=new ArrayList<DeliveryProdPrintVo>();
		long sum = 0;
		if(CollectionUtil.isEmpty(deliverInfos)) {
			/* 获取订单下的商品信息*/
			List<OrdOdProd> ordOdProds = getOrdOdProds(request);
			/* 根据订单规则获取合并时间*/
			Timestamp time = getOrderListInTime(OrdRuleConstants.MERGE_ORDER_SETTING_ID,order.getOrderTime());
			/* 组装订单商品信息集合*/
			List<OrdOrderProdAttach> originalAttachs = createOriginalAttachs(ordOdProds);
			List<Long> orderList =null;
			for (OrdOdProd ordOdProd : ordOdProds) {
				long buySum=ordOdProd.getBuySum();
				sum+=buySum;
				long horOrderId=0;
				/* 多表多条件查询*/
				List<OrdOrderProdAttach> orderProdAttachs = deliveryOrderPrintAtomSV.query(request.getUserId(),
						request.getTenantId(),ordOdProd.getSkuId(),ordOdProd.getRouteId(),ordOdProd.getOrderId(),
						OrdersConstants.OrdOrder.State.WAIT_DISTRIBUTION,time,order.getOrderTime());
				if(!CollectionUtil.isEmpty(orderProdAttachs)) {
					/* 筛选不符合合并规则的订单*/
					orderList = this.judgeOrder(originalAttachs,orderProdAttachs,orderList);
					if(!CollectionUtil.isEmpty(orderProdAttachs)) {
						for (OrdOrderProdAttach ordOrderProdAttach : orderProdAttachs) {
							buySum+=ordOrderProdAttach.getBuySum();
							sum+=ordOrderProdAttach.getBuySum();
							horOrderId=ordOrderProdAttach.getOrderId();
						}
					}
				}
				/* 组装订单提货明细信息*/
				DeliveryProdPrintVo dpVo = this.createDeliverInfoProd(ordOdProd,buySum,horOrderId);
				list.add(dpVo);
			}
		}else {
			/*表示提货单打印信息已存在*/
			for (OrdOdDeliverInfo ordOdDeliverInfo : deliverInfos) {
				DeliverInfoProdCriteria example=new DeliverInfoProdCriteria();
				DeliverInfoProdCriteria.Criteria criteria = example.createCriteria();
				criteria.andDeliverInfoIdEqualTo(ordOdDeliverInfo.getDeliverInfoId());
				List<DeliverInfoProd> deliverInfoProds= deliveryOrderPrintAtomSV.selectByExample(example);
				DeliverInfoProd deliverInfoProd = deliverInfoProds.get(0);
				sum+=deliverInfoProd.getBuySum();
				DeliveryProdPrintVo dpVo=new DeliveryProdPrintVo();
				BeanUtils.copyProperties(dpVo, deliverInfoProd);
				dpVo.setHorOrderId(ordOdDeliverInfo.getHorOrderId());
				list.add(dpVo);
			}
		}
		/* 查询订单配送信息*/
		List<OrdOdLogistics> logistics = this.getOrdOdLogistics(order.getParentOrderId(), order.getTenantId());
		if(CollectionUtil.isEmpty(logistics)) {
			logger.warn("未能查询到指定的订单配送信息[订单id:"+order.getParentOrderId()+"]");
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL,
					"未能查询到指定的订单配送信息[订单id:"+order.getTenantId()+"]");
		}
		OrdOdLogistics ordOdLogistics = logistics.get(0);
		response.setContactName(ordOdLogistics.getContactName());
		response.setSum(sum);
		response.setOrderId(request.getOrderId());
		response.setDeliveryProdPrintVos(list);
		return response;
	}
	
	
	@Override
	public void print(DeliveryOrderPrintInfosRequest request) {
		/* 参数检验及 查询提货单打印信息*/
		List<OrdOdDeliverInfo> queryInfos = this.checkParamAndQueryInfos(
			request.getOrderId(),request.getTenantId());
		if(!CollectionUtil.isEmpty(queryInfos)) {
			logger.warn("提货单已经打印,不能重复打印[订单id:"+request.getOrderId()+"]");
			throw new BusinessException(ExceptCodeConstants.Special.NO_RESULT, 
					"提货单已经打印,不能重复打印[订单id:"+request.getOrderId()+"]");
		}
		List<DeliveryProdPrintVo> deliveryProdPrintVos = request.getDeliveryProdPrintVos();
		for (DeliveryProdPrintVo deliveryProdPrintVo : deliveryProdPrintVos) {
			  Long deliverInfoId = SequenceUtil.createdeliverInfoId();
			  OrdOdDeliverInfo record=new OrdOdDeliverInfo();
			  record.setOrderId(request.getOrderId());
			  record.setDeliverInfoId(deliverInfoId);
			  record.setHorOrderId(deliveryProdPrintVo.getHorOrderId());
			  record.setPrintInfo(OrdersConstants.OrdOdDeliverInfo.printInfo.ONE);
			  record.setUpdateTime(DateUtil.getSysDate());
			  deliveryOrderPrintAtomSV.insertSelective(record);
			  DeliverInfoProd deliverInfoProd=new DeliverInfoProd();
			  deliverInfoProd.setDeliverInfoId(deliverInfoId);
			  deliverInfoProd.setBuySum(deliveryProdPrintVo.getBuySum());
			  deliverInfoProd.setExtendInfo(deliveryProdPrintVo.getExtendInfo());
			  deliverInfoProd.setProdName(deliveryProdPrintVo.getProdName());
			  deliverInfoProd.setSkuId(deliveryProdPrintVo.getSkuId());
			  deliverInfoProd.setSalePrice(deliveryProdPrintVo.getSalePrice());
			  deliveryOrderPrintAtomSV.insertSelective(deliverInfoProd);
			  /* 更新合并订单状态并写入订单状态变化轨迹*/
			  this.updateOrderState(deliveryProdPrintVo.getHorOrderId(),request.getTenantId(), DateUtil.getSysDate());
		}
		/* 更新原订单状态并写入订单状态变化轨迹*/
		this.updateOrderState(request.getOrderId(),request.getTenantId(), DateUtil.getSysDate());
	}
	
	/**
	 * 参数检验
	 */
	private List<OrdOdDeliverInfo> checkParamAndQueryInfos(long orderId,String tenantId) {
		CommonCheckUtils.checkTenantId(tenantId, "");
		if(orderId==0) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, "订单id不能为空");
		}
		/* 判断是否存在提货单打印信息*/
		OrdOdDeliverInfoCriteria exampleDeliver=new OrdOdDeliverInfoCriteria();
		OrdOdDeliverInfoCriteria.Criteria criteriaDeliver = exampleDeliver.createCriteria();
		criteriaDeliver.andOrderIdEqualTo(orderId);
		criteriaDeliver.andPrintInfoEqualTo(OrdersConstants.OrdOdDeliverInfo.printInfo.ONE);
		List<OrdOdDeliverInfo> deliverInfos = deliveryOrderPrintAtomSV.selectByExample(exampleDeliver);
		return deliverInfos;
	}
	
	
  /**
   * 获取订单下的商品信息
   */
	private List<OrdOdProd> getOrdOdProds(DeliveryOrderPrintRequest request) {
		OrdOdProdCriteria example=new OrdOdProdCriteria();
		OrdOdProdCriteria.Criteria criteria = example.createCriteria();
		criteria.andTenantIdEqualTo(request.getTenantId());
		criteria.andOrderIdEqualTo(request.getOrderId());
		List<OrdOdProd> ordOdProds = ordOdProdAtomSV.selectByExample(example);
		if(CollectionUtil.isEmpty(ordOdProds)) {
			logger.warn("未能查询到指定的订单商品明细信息[订单id:"+request.getOrderId()+"]");
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, 
					"未能查询到指定的订单商品明细信息[订单id:"+request.getOrderId()+"]");
		}
		return ordOdProds;
	}
	
	/**
	 *  组装订单商品信息集合
	 */
	private List<OrdOrderProdAttach> createOriginalAttachs(List<OrdOdProd> ordOdProds) {
		List<OrdOrderProdAttach> originalAttachs=new ArrayList<OrdOrderProdAttach>();
		for (OrdOdProd ordOdProd : ordOdProds) {
			OrdOrderProdAttach attach=new OrdOrderProdAttach();
			attach.setSkuId(ordOdProd.getSkuId());
			attach.setRouteId(ordOdProd.getRouteId());
			originalAttachs.add(attach);
		}
		return originalAttachs;
	}

	/**
	  * 根据订单规则获取合并时间
	  */
	 public Timestamp getOrderListInTime(String buyIpMonitorId,Timestamp time) {
		OrdRule ordRule = ordRuleAtomSV.getOrdRule(buyIpMonitorId);
		if(ordRule==null) {
			throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, 
					"未能查询到指定的订单规则信息[订单规则id:"+buyIpMonitorId+"]");
		}
	 	Calendar calendar = Calendar.getInstance();
	 	calendar.setTime(time);
	 	switch(ordRule.getTimeType()) {
	 		case "D":
	 			calendar.add(Calendar.DAY_OF_MONTH, -(ordRule.getMonitorTime()));
	 			break;
	 		case "H":
	 			calendar.add(Calendar.HOUR, -(ordRule.getMonitorTime()));
	 			break;
	 		default:
	 			calendar.add(Calendar.MINUTE, -(ordRule.getMonitorTime()));
	 			break;
	 	}
        return new Timestamp(calendar.getTimeInMillis());
	 }
	 
	 
	 /**
	  * 查询订单配送信息
	  */
	 private List<OrdOdLogistics> getOrdOdLogistics(Long orderId,String tenantId) {
		OrdOdLogisticsCriteria exampleLogistics=new OrdOdLogisticsCriteria();
		OrdOdLogisticsCriteria.Criteria criteriaLogistics = exampleLogistics.createCriteria();
		criteriaLogistics.andTenantIdEqualTo(tenantId);
		criteriaLogistics.andOrderIdEqualTo(orderId);
		List<OrdOdLogistics> logistics = ordOdLogisticsAtomSV.selectByExample(exampleLogistics);
		return logistics;
	 }
	 
	 
	 /**
      * 更新订单状态
      * 
      */
	  private void updateOrderState(long orderId,String tenantId, Timestamp sysDate) {
		if(orderId==0) {
			return;
		}else {
			OrdOrder ordOrder = ordOrderAtomSV.selectByOrderId(tenantId, orderId);
			if(ordOrder==null) {
				logger.warn("未能查询到指定的订单信息[订单id:"+ orderId+"]");
				throw new BusinessException(ExceptCodeConstants.Special.PARAM_IS_NULL, 
						"未能查询到指定的订单信息[订单id:"+ orderId+"]");
			}
			String orgState = ordOrder.getState();
			if(OrdersConstants.OrdOrder.State.WAIT_DELIVERY.equals(orgState)) {
				return;
			}
			String state1 = OrdersConstants.OrdOrder.State.LADING_BILL_FINISH_PRINT;
			String state2 = OrdersConstants.OrdOrder.State.FINISH_DISTRIBUTION;
			String newState = OrdersConstants.OrdOrder.State.WAIT_DELIVERY;
			ordOrder.setState(newState);
			ordOrder.setStateChgTime(sysDate);
			ordOrderAtomSV.updateById(ordOrder);
			// 写入订单状态变化轨迹表
			orderFrameCoreSV.ordOdStateChg(ordOrder.getOrderId(), ordOrder.getTenantId(), orgState, state1,
					OrdOdStateChg.ChgDesc.ORDER_TO_PRINT, null, null, null, sysDate);
			orderFrameCoreSV.ordOdStateChg(ordOrder.getOrderId(), ordOrder.getTenantId(), state1, state2,
					OrdOdStateChg.ChgDesc.ORDER_TO_FINISH_DISTRIBUTION, null, null, null, sysDate);
			orderFrameCoreSV.ordOdStateChg(ordOrder.getOrderId(), ordOrder.getTenantId(), state2, newState,
					OrdOdStateChg.ChgDesc.ORDER_TO_WAIT_DELIVERY, null, null, null, sysDate);
		}
	 }
	  
	  /**
	   * 组装提货单信息明细
	 * @param horOrderId 
	   */
	  private DeliveryProdPrintVo createDeliverInfoProd(OrdOdProd ordOdProd,
			  long buySum, long horOrderId) {
		  DeliveryProdPrintVo dpVo=new DeliveryProdPrintVo();
		  dpVo.setBuySum(buySum);
		  dpVo.setHorOrderId(horOrderId);
		  dpVo.setExtendInfo(ordOdProd.getExtendInfo());
		  dpVo.setProdName(ordOdProd.getProdName());
		  dpVo.setSkuId(ordOdProd.getSkuId());
		  dpVo.setSalePrice(ordOdProd.getSalePrice());
		  return dpVo;
	  }
	  
	  
	 /**
	  * 筛选订单
	  */
	  private List<Long> judgeOrder(List<OrdOrderProdAttach> originalAttachs,
			  List<OrdOrderProdAttach> orderProdAttachs,List<Long> orderList) {
		  logger.info("筛选不符合合并规则的订单......");
		  if (null != orderProdAttachs && orderProdAttachs.size() > 0) {
			    Iterator<OrdOrderProdAttach> it = orderProdAttachs.iterator();  
			    while(it.hasNext()){
			    	OrdOrderProdAttach ordOrderProdAttach = (OrdOrderProdAttach)it.next(); 
			    	if(!CollectionUtil.isEmpty(orderList) && orderList.contains(ordOrderProdAttach.getOrderId())) {
			    		it.remove();
			    		continue;
			    	}
			    	OrdOdProdCriteria example=new OrdOdProdCriteria();
					OrdOdProdCriteria.Criteria criteria = example.createCriteria();
					criteria.andTenantIdEqualTo(ordOrderProdAttach.getTenantId());
					criteria.andOrderIdEqualTo(ordOrderProdAttach.getOrderId());
					List<OrdOdProd> ordOdProds = ordOdProdAtomSV.selectByExample(example);
					/* 1.个数筛选(合并之后的订单商品个数大于原来商品个数,则不合并)*/
					if(ordOdProds.size()>originalAttachs.size()) {
						it.remove();
						continue;
					}
					/* 2.商品包含筛选*/
					for (OrdOdProd ordOdProd : ordOdProds) {
						OrdOrderProdAttach oPAttach=new OrdOrderProdAttach();
						oPAttach.setSkuId(ordOdProd.getSkuId());
						oPAttach.setRouteId(ordOdProd.getRouteId());
						if(!originalAttachs.contains(oPAttach)) {
							if(CollectionUtil.isEmpty(orderList)) {
								orderList=new ArrayList<Long>();
							}
							orderList.add(Long.valueOf(ordOrderProdAttach.getOrderId()));
							it.remove();
							break;
						}
					}
			    }
		  }
		return orderList;
	  }
}
