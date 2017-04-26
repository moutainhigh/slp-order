package com.ai.slp.order.service.business.impl;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.SystemException;
import com.ai.opt.base.vo.PageInfo;
import com.ai.opt.sdk.constants.ExceptCodeConstants;
import com.ai.opt.sdk.dubbo.util.DubboConsumerFactory;
import com.ai.opt.sdk.dubbo.util.HttpClientUtil;
import com.ai.opt.sdk.util.BeanUtils;
import com.ai.opt.sdk.util.CollectionUtil;
import com.ai.opt.sdk.util.ParseO2pDataUtil;
import com.ai.paas.ipaas.search.vo.Result;
import com.ai.paas.ipaas.search.vo.SearchCriteria;
import com.ai.paas.ipaas.search.vo.Sort;
import com.ai.paas.ipaas.search.vo.Sort.SortOrder;
import com.ai.platform.common.api.cache.interfaces.ICacheSV;
import com.ai.slp.order.api.orderlist.param.BehindOrdOrderVo;
import com.ai.slp.order.api.orderlist.param.BehindOrdProductVo;
import com.ai.slp.order.api.orderlist.param.BehindParentOrdOrderVo;
import com.ai.slp.order.api.orderlist.param.BehindQueryOrderListRequest;
import com.ai.slp.order.api.orderlist.param.BehindQueryOrderListResponse;
import com.ai.slp.order.api.orderlist.param.OrdOrderVo;
import com.ai.slp.order.api.orderlist.param.OrdProductVo;
import com.ai.slp.order.api.orderlist.param.ProductImage;
import com.ai.slp.order.api.orderlist.param.QueryOrderRequest;
import com.ai.slp.order.api.orderlist.param.QueryOrderResponse;
import com.ai.slp.order.api.sesdata.param.SesDataRequest;
import com.ai.slp.order.constants.OrdersConstants;
import com.ai.slp.order.constants.SearchFieldConfConstants;
import com.ai.slp.order.dao.mapper.bo.OrdOdProd;
import com.ai.slp.order.dao.mapper.bo.OrdOrder;
import com.ai.slp.order.search.bo.OrdProdExtend;
import com.ai.slp.order.search.bo.OrderInfo;
import com.ai.slp.order.search.bo.ProdInfo;
import com.ai.slp.order.search.dto.SearchCriteriaStructure;
import com.ai.slp.order.service.atom.interfaces.IOrdOdProdAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOrderAtomSV;
import com.ai.slp.order.service.business.impl.search.OrderSearchImpl;
import com.ai.slp.order.service.business.interfaces.IOrdOrderBusiSV;
import com.ai.slp.order.service.business.interfaces.search.IOrderIndexBusiSV;
import com.ai.slp.order.service.business.interfaces.search.IOrderSearch;
import com.ai.slp.product.api.product.interfaces.IProductServerSV;
import com.ai.slp.product.api.product.param.ProductSkuInfo;
import com.ai.slp.product.api.product.param.SkuInfoQuery;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@Service
@Transactional
public class OrdOrderBusiSVImpl implements IOrdOrderBusiSV {

	private static final Logger logger = LoggerFactory.getLogger(OrdOrderBusiSVImpl.class);
	@Autowired
	private IOrdOrderAtomSV ordOrderAtomSV;
	@Autowired
	private IOrdOdProdAtomSV ordOdProdAtomSV;
	@Autowired
	private IOrderIndexBusiSV orderIndexBusiSV;

	
	//订单详情查询
	@Override
	@Transactional(readOnly=true)
	public QueryOrderResponse queryOrder(QueryOrderRequest orderRequest ) 
			throws BusinessException, SystemException {
		ICacheSV iCacheSV=DubboConsumerFactory.getService(ICacheSV.class);
		logger.debug("开始订单详情查询..");
		QueryOrderResponse response = new QueryOrderResponse();
		
		IOrderSearch orderSearch = new OrderSearchImpl();
		Long orderId = orderRequest.getOrderId();
		List<SearchCriteria> orderSearchCriteria = SearchCriteriaStructure.
				queryOrderInfosByOrderId(orderId);
		Result<OrderInfo> result = orderSearch.search(orderSearchCriteria, 0, 1, null);
		List<OrderInfo> ordList = result.getContents();
		if(CollectionUtil.isEmpty(ordList)) {
			logger.info("搜索引擎无数据! 订单id为:"+orderId);
			throw new BusinessException("搜索引擎无数据! 订单id为:"+orderId);
		}
		OrderInfo orderInfo = ordList.get(0);
		OrdOrderVo ordOrderVo=new OrdOrderVo();
		BeanUtils.copyProperties(ordOrderVo, orderInfo);
		List<OrdProdExtend> ordextendes = orderInfo.getOrdextendes();
		List<OrdProductVo> productList = new ArrayList<OrdProductVo>();
		for (OrdProdExtend ordProdExtend : ordextendes) {
			if(orderId.equals(ordProdExtend.getOrderid())) {
				ordOrderVo.setOrderid(orderId);
				ordOrderVo.setOrigorderid(ordProdExtend.getOrigorderid());
				ordOrderVo.setBusicodename(ordProdExtend.getBusicodename());
				ordOrderVo.setState(ordProdExtend.getState());
				ordOrderVo.setStatename(ordProdExtend.getStatename());
				//TODO 路由id  是否翻译
				ordOrderVo.setRouteid(ordProdExtend.getRouteid());
				
				
				ordOrderVo.setParentorderid(ordProdExtend.getParentorderid());
				ordOrderVo.setAdjustfee(ordProdExtend.getAdjustfee());
				ordOrderVo.setDiscountfee(ordProdExtend.getDiscountfee());
				ordOrderVo.setOperdiscountfee(ordProdExtend.getOperdiscountfee());
				ordOrderVo.setOperdiscountdesc(ordProdExtend.getOperdiscountdesc());
				ordOrderVo.setPaidfee(ordProdExtend.getPaidfee());
				ordOrderVo.setPayfee(ordProdExtend.getPayfee());
				ordOrderVo.setTotalfee(ordProdExtend.getTotalfee());
				ordOrderVo.setFreight(ordProdExtend.getFreight()); // 运费 
				// 4.订单配送信息查询 
				if (!OrdersConstants.OrdOrder.BusiCode.NORMAL_ORDER.equals(ordProdExtend.getBusicode())) {
					// 售后单获取子订单配送信息
					ordOrderVo.setAftercontacttel(ordProdExtend.getAftercontactTel());
					ordOrderVo.setAftercontactinfo(ordProdExtend.getAftercontactinfo());
				}
				ordOrderVo.setProvincecode(ordOrderVo.getProvincecode() == null ? ""
						: iCacheSV.getAreaName(ordOrderVo.getProvincecode()));
				ordOrderVo.setCitycode(ordOrderVo.getCitycode() == null ? ""
						: iCacheSV.getAreaName(ordOrderVo.getCitycode()));
				ordOrderVo.setCountycode(ordOrderVo.getCountycode() == null ? ""
						: iCacheSV.getAreaName(ordOrderVo.getCountycode()));
				ordOrderVo.setPostcode(ordOrderVo.getPostcode());
				ordOrderVo.setAreacode(ordOrderVo.getAreacode() == null ? ""
						: iCacheSV.getAreaName(ordOrderVo.getAreacode()));
			
				//
				List<ProdInfo> prodinfos = ordProdExtend.getProdinfos();
				for (ProdInfo prodInfo : prodinfos) {
					OrdProductVo prodVo=new OrdProductVo(); 
					prodVo.setProddetalid(prodInfo.getProddetalid());
					prodVo.setOrderid(orderId);
					prodVo.setSkuid(prodInfo.getSkuid());
					prodVo.setProdname(prodInfo.getProdname());
					prodVo.setState(prodInfo.getState());
					prodVo.setBuysum(prodInfo.getBuysum());
					prodVo.setSaleprice(prodInfo.getSaleprice());
					prodVo.setTotalfee(prodInfo.getTotalfee());
					prodVo.setAdjustfee(prodInfo.getAdjustfee());
					prodVo.setOperdiscountfee(prodInfo.getOperdiscountfee());
					prodVo.setDiscountfee(prodInfo.getDiscountfee());
					
					//
				//	ProductImage productimage=new ProductImage();
				//	productimage.setVfsId(prodInfo.getVfsid());
				//	productimage.setPicType(prodInfo.getPictype());
				//	prodVo.setProductimage(productimage);
					prodVo.setCouponfee(prodInfo.getCouponfee());
					prodVo.setJffee(prodInfo.getJffee());
					prodVo.setCusserviceflag(prodInfo.getCusserviceflag());
					prodVo.setGivejf(prodInfo.getGivejf());
				//	prodVo.setProdcode(prodInfo.getProdcode());
					prodVo.setSkustorageid(prodInfo.getSkustorageid());
				
					
				//    private String imageurl;
				//    private String prodextendinfo;
					productList.add(prodVo);
				}
			}
		}
		ordOrderVo.setProductList(productList);
	
		response.setOrdOrderVo(ordOrderVo);
		return response;
	}

	//订单列表查询
	@Override
	@Transactional(readOnly=true)
	public BehindQueryOrderListResponse behindQueryOrderList(BehindQueryOrderListRequest orderListRequest)
			throws BusinessException, SystemException {
		// 调用搜索引擎进行查询
		int startSize = 1;
		int maxSize = 1;
		// 最大条数设置
		int pageNo = orderListRequest.getPageNo();
		int size = orderListRequest.getPageSize();
		if (pageNo == 1) {
			startSize = 0;
		} else {
			startSize = (pageNo - 1) * size;
		}
		maxSize = size;
		BehindQueryOrderListResponse response=new BehindQueryOrderListResponse();
		PageInfo<BehindParentOrdOrderVo> pageInfo=new PageInfo<BehindParentOrdOrderVo>();
		List<BehindParentOrdOrderVo> results = new ArrayList<BehindParentOrdOrderVo>();
		IOrderSearch orderSearch = new OrderSearchImpl();
		List<SearchCriteria> orderSearchCriteria = SearchCriteriaStructure.commonConditions(orderListRequest);
		//排序
		List<Sort> sortList = new ArrayList<Sort>();
		Sort sort = new Sort(SearchFieldConfConstants.ORDER_TIME, SortOrder.DESC);
		sortList.add(sort);
		Result<OrderInfo> result = orderSearch.search(orderSearchCriteria, startSize, maxSize, sortList);
		List<OrderInfo> ordList = result.getContents();
		for (OrderInfo orderInfo : ordList) {
			BehindParentOrdOrderVo vo=new BehindParentOrdOrderVo();
			BeanUtils.copyVO(vo, orderInfo);
			
			List<OrdProdExtend> ordextendes = orderInfo.getOrdextendes();
			List<BehindOrdOrderVo> destOrdextendes=new ArrayList<BehindOrdOrderVo>();
			for (OrdProdExtend ordProdExtend : ordextendes) {
				BehindOrdOrderVo destOrdOrderVo=new BehindOrdOrderVo();
				BeanUtils.copyProperties(destOrdOrderVo, ordProdExtend);
				
				List<ProdInfo> prodinfos = ordProdExtend.getProdinfos();
				List<BehindOrdProductVo> destOrdProductVos=new ArrayList<BehindOrdProductVo>();
				for (ProdInfo prodInfo : prodinfos) {
					BehindOrdProductVo destProdVo=new BehindOrdProductVo();
					BeanUtils.copyProperties(destProdVo, prodInfo);
					destOrdProductVos.add(destProdVo);
				}
				destOrdOrderVo.setProdinfos(destOrdProductVos);
				destOrdextendes.add(destOrdOrderVo);
			}
			vo.setOrdextendes(destOrdextendes);
			vo.setTenantId(OrdersConstants.TENANT_ID);
			results.add(vo);
		}
		pageInfo.setPageNo(pageNo);
		pageInfo.setPageSize(maxSize);
		pageInfo.setResult(results);
		pageInfo.setCount(Long.valueOf(result.getCount()).intValue());
		response.setPageInfo(pageInfo);
		return response;
	}
	
	
	/**
	 * 订单下面的商品信息
	 * @param orderId
	 * @return
	 * @author zhangxw
	 * @param tenantId
	 * @ApiDocMethod
	 */
	private List<OrdProductVo> getOrdProductList(String tenantId, long orderId) {
		List<OrdProductVo> productList = new ArrayList<OrdProductVo>();
		
		
		
		
		
		
		
		
		
		/*	List<OrdOdProd> ordOdProdList=ordOdProdAtomSV.selectByOrd(tenantId, orderId);
		if (!CollectionUtil.isEmpty(ordOdProdList)) {
			for (OrdOdProd ordOdProd : ordOdProdList) {
				OrdProductVo ordProductVo = new OrdProductVo();
				ordProductVo.setOrderId(orderId);
				ordProductVo.setProdDetalId(ordOdProd.getProdDetalId());
				ordProductVo.setSkuId(ordOdProd.getSkuId());
				ordProductVo.setState(ordOdProd.getState());
				ordProductVo.setProdName(ordOdProd.getProdName());
				ordProductVo.setSalePrice(ordOdProd.getSalePrice());
				ordProductVo.setBuySum(ordOdProd.getBuySum());
				ordProductVo.setTotalFee(ordOdProd.getTotalFee());
				ordProductVo.setDiscountFee(ordOdProd.getDiscountFee());
				ordProductVo.setAdjustFee(ordOdProd.getAdjustFee());
				ordProductVo.setOperDiscountFee(ordOdProd.getOperDiscountFee());
				ordProductVo.setCouponFee(ordOdProd.getCouponFee()); // 优惠费用
				ordProductVo.setCusServiceFlag(ordOdProd.getCusServiceFlag()); // 商品是否售后标识
				ordProductVo.setJfFee(ordOdProd.getJfFee()); // 消费积分
				ordProductVo.setGiveJF(ordOdProd.getJf()); // 赠送积分
				ordProductVo.setProdCode(ordOdProd.getProdCode()); // 商品编码
				ordProductVo.setSkuStorageId(ordOdProd.getSkuStorageId());
				ProductImage productImage = this.getProductImage(tenantId, ordOdProd.getSkuId());
				ordProductVo.setProductImage(productImage);
				ordProductVo.setImageUrl(ordOdProd.getProdDesc()); // 图片id
				ordProductVo.setProdExtendInfo(ordOdProd.getProdSn()); // 图片类型
				productList.add(ordProductVo);
			}
		}*/
		return productList;
	}

	/**
	 * 获取图片信息
	 * @param skuId
	 * @return
	 * @author zhangxw
	 * @ApiDocMethod
	 */
	private ProductImage getProductImage(String tenantId, String skuId) {
		ProductImage productImage = new ProductImage();
		SkuInfoQuery skuInfoQuery = new SkuInfoQuery();
		skuInfoQuery.setTenantId(tenantId);
		skuInfoQuery.setSkuId(skuId);
		IProductServerSV iProductServerSV = DubboConsumerFactory.getService(IProductServerSV.class);
		ProductSkuInfo productSkuInfo =iProductServerSV.queryProductSkuById4ShopCart(skuInfoQuery);
		productImage.setVfsId(productSkuInfo.getVfsId());
		productImage.setPicType(productSkuInfo.getPicType());
		return productImage;
	}

	//订单判断及状态修改
	@Override
	public int updateOrder(OrdOrder request) throws BusinessException, SystemException {
		/* 获取售后订单 */
		OrdOrder afterOrdOrder = ordOrderAtomSV.selectByOrderId(request.getTenantId(), request.getOrderId());
		if (afterOrdOrder == null) {
			throw new BusinessException(ExceptCodeConstants.Special.NO_RESULT,
					"订单信息不存在[订单id:" + request.getOrderId() + "]");
		}
		// 设置售后订单状态
		afterOrdOrder.setState(request.getState());
		// 处理中 退款失败状态 不修改子父订单状态
		if (!(OrdersConstants.OrdOrder.State.IN_PROCESS.equals(request.getState())
				|| OrdersConstants.OrdOrder.State.REFUND_FAILD.equals(request.getState()))) {
			if (OrdersConstants.OrdOrder.BusiCode.UNSUBSCRIBE_ORDER.equals(afterOrdOrder.getBusiCode())) {
				if (OrdersConstants.OrdOrder.State.EXCHANGE_AUDIT.equals(request.getState())) {
					afterOrdOrder.setState(OrdersConstants.OrdOrder.State.FINISH_REFUND); // 退货完成
				}
			}
			/* 获取子订单信息及子订单下的商品明细信息 */
			OrdOrder order = ordOrderAtomSV.selectByOrderId(request.getTenantId(), afterOrdOrder.getOrigOrderId());
			List<OrdOdProd> prodList = ordOdProdAtomSV.selectByOrd(request.getTenantId(),
					afterOrdOrder.getOrigOrderId());
			boolean cusFlag = false;
			for (OrdOdProd ordOdProd : prodList) {
				if (OrdersConstants.OrdOrder.cusServiceFlag.YES.equals(ordOdProd.getCusServiceFlag())) {
					cusFlag = true;
				} else {
					cusFlag = false;
					break;
				}
			}
			/* 获取子订单下的所有售后订单 */
			List<OrdOrder> orderList =ordOrderAtomSV.selectSubSaleOrder(afterOrdOrder.getOrigOrderId(),request.getOrderId());
			OrdOrder parentOrder = ordOrderAtomSV.selectByOrderId(request.getTenantId(), order.getParentOrderId()); // 父订单
			if (cusFlag) {
				if (CollectionUtil.isEmpty(orderList)) {
					// 一个商品时.没有售后订单,商品售后标识Y
					// 1.无Y --无售后订单 商品Y标识
					order.setState(OrdersConstants.OrdOrder.State.COMPLETED);
					ordOrderAtomSV.updateById(order);
					//判断父订单下的其它子订单状态  
					// 完成则为 父订单完成,否则父订单不变
					boolean stateFlag = this.judgeState(order);
					if(stateFlag) {
						parentOrder.setState(OrdersConstants.OrdOrder.State.COMPLETED);
						ordOrderAtomSV.updateById(parentOrder); 
					}
				} else {
					// 2.有Y --有售后订单,商品标识Y
					// 判断售后订单为已完成状态或者审核失败则改变状态
					boolean flag=false;
					for (OrdOrder ordOrder : orderList) {
						String state = ordOrder.getState();
						if (OrdersConstants.OrdOrder.State.FINISH_REFUND.equals(state)
								|| OrdersConstants.OrdOrder.State.EXCHANGE_AUDIT.equals(state)
								|| OrdersConstants.OrdOrder.State.REFUND_AUDIT.equals(state)
								|| OrdersConstants.OrdOrder.State.AUDIT_FAILURE.equals(state)
								|| OrdersConstants.OrdOrder.State.AUDIT_AGAIN_FAILURE.equals(state)) {
							flag=true;
		    			}else {
		    				flag=false;
		    				break;
		    			}
					}
					if(flag) {
						order.setState(OrdersConstants.OrdOrder.State.COMPLETED);
						ordOrderAtomSV.updateById(order);
						//判断父订单下的其它子订单状态  
    					// 完成则为 父订单完成,否则父订单不变
    					boolean stateFlag = this.judgeState(order);
    					if(stateFlag) {
    						parentOrder.setState(OrdersConstants.OrdOrder.State.COMPLETED);
    						ordOrderAtomSV.updateById(parentOrder); 
    					}
					}
				}
			} else if (!cusFlag) {
				// 发货后状态
				if (!(OrdersConstants.OrdOrder.State.WAIT_DISTRIBUTION.equals(order.getState())
						|| OrdersConstants.OrdOrder.State.WAIT_DELIVERY.equals(order.getState())
						|| OrdersConstants.OrdOrder.State.WAIT_SEND.equals(order.getState()))) {
					// 1.无N --无售后订单,存在商品标识N
					// 发货后改变状态
					if (CollectionUtil.isEmpty(orderList)) {
						order.setState(OrdersConstants.OrdOrder.State.COMPLETED);
						ordOrderAtomSV.updateById(order);
						//判断父订单下的其它子订单状态  
    					// 完成则为 父订单完成,否则父订单不变
    					boolean stateFlag = this.judgeState(order);
    					if(stateFlag) {
    						parentOrder.setState(OrdersConstants.OrdOrder.State.COMPLETED);
    						ordOrderAtomSV.updateById(parentOrder); 
    					}
						// 4.有N --有售后订单 存在商品标识N
						// 发货后状态
						// 判断售后订单为已完成状态或者审核失败则 改变状态
					} else {
						for (OrdOrder ordOrder : orderList) {
							String state = ordOrder.getState();
							// 表示售后订单为已完成状态或者审核失败
							if (OrdersConstants.OrdOrder.State.FINISH_REFUND.equals(state)
									|| OrdersConstants.OrdOrder.State.EXCHANGE_AUDIT.equals(state)
									|| OrdersConstants.OrdOrder.State.REFUND_AUDIT.equals(state)
									|| OrdersConstants.OrdOrder.State.AUDIT_FAILURE.equals(state)
									|| OrdersConstants.OrdOrder.State.AUDIT_AGAIN_FAILURE.equals(state)) {
								order.setState(OrdersConstants.OrdOrder.State.COMPLETED);
								ordOrderAtomSV.updateById(order);
								//判断父订单下的其它子订单状态  
		    					// 完成则为 父订单完成,否则父订单不变
		    					boolean stateFlag = this.judgeState(order);
		    					if(stateFlag) {
		    						parentOrder.setState(OrdersConstants.OrdOrder.State.COMPLETED);
		    						ordOrderAtomSV.updateById(parentOrder); 
		    					}
							}
						}
					}
				}
			}
		}
		int updateById = ordOrderAtomSV.updateById(afterOrdOrder);
		
		// 刷新搜索引擎数据
    	SesDataRequest sesReq=new SesDataRequest();
    	sesReq.setTenantId(request.getTenantId());
    	sesReq.setParentOrderId(afterOrdOrder.getParentOrderId());
    	this.orderIndexBusiSV.insertSesData(sesReq);
		
		return updateById;
	}
	
	
	 /**
     * 判断父订单下面其它子订单状态
     */
    private boolean judgeState(OrdOrder order) {
    	//父订单下的其它子订单
        List<OrdOrder> childOrders = ordOrderAtomSV.selectOtherOrders(order);
	    if(!CollectionUtil.isEmpty(childOrders)) {
	    	for (OrdOrder ordOrder : childOrders) {
	    		//其它子订单状态不是'完成'
				if(!OrdersConstants.OrdOrder.State.COMPLETED.equals(ordOrder.getState())) {
					return false;
				}
			}
	    }
	    return true;
    }

	/**
	 * OFC订单查询
	 */
	public static JSONObject queryOFC(OrdOrder ordOrder) throws BusinessException, SystemException {
		List<String> orderNoList = new ArrayList<String>();
		orderNoList.add(String.valueOf(ordOrder.getOrderId()));
		Map<String, Object> mapField = new HashMap<String, Object>();
		mapField.put("OrderNoList", orderNoList);
		// mapField.put("ShopName", "长虹官方旗舰店");
		mapField.put("PageIndex", "1");
		mapField.put("PageSize", "1");
		String params = JSON.toJSONString(mapField);
		Map<String, String> header = new HashMap<String, String>();
		header.put("appkey", OrdersConstants.OFC_APPKEY);
		JSONObject object = null;
		// 发送Post请求,并返回信息
		try {
			String strData = HttpClientUtil.sendPost(OrdersConstants.OFC_QUERY_URL, params, header);
			object = ParseO2pDataUtil.getData(strData);
			// TODO 是否判断
			boolean val = object.getBooleanValue("IsValid");// 操作是否成功
			if (!val) {
				throw new BusinessException("", "OFC订单查询失败");
			}
		} catch (IOException | URISyntaxException e) {
			logger.error(e.getMessage());
			throw new SystemException("", "OFC订单查询出现异常");
		}
		return object;
	}
}
