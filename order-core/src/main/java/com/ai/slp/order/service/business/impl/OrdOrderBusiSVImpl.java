package com.ai.slp.order.service.business.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.opt.base.exception.BusinessException;
import com.ai.opt.base.exception.SystemException;
import com.ai.opt.base.vo.PageInfo;
import com.ai.opt.sdk.dubbo.util.DubboConsumerFactory;
import com.ai.opt.sdk.util.CollectionUtil;
import com.ai.slp.order.api.orderlist.param.OrdOrderVo;
import com.ai.slp.order.api.orderlist.param.OrdProductVo;
import com.ai.slp.order.api.orderlist.param.OrderPayVo;
import com.ai.slp.order.api.orderlist.param.ProductImage;
import com.ai.slp.order.api.orderlist.param.QueryOrderListRequest;
import com.ai.slp.order.api.orderlist.param.QueryOrderListResponse;
import com.ai.slp.order.api.orderlist.param.QueryOrderRequest;
import com.ai.slp.order.api.orderlist.param.QueryOrderResponse;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeProd;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeProdCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeTotal;
import com.ai.slp.order.dao.mapper.bo.OrdOdFeeTotalCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdProd;
import com.ai.slp.order.dao.mapper.bo.OrdOdProdCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOdProdExtend;
import com.ai.slp.order.dao.mapper.bo.OrdOdProdExtendCriteria;
import com.ai.slp.order.dao.mapper.bo.OrdOrder;
import com.ai.slp.order.dao.mapper.bo.OrdOrderCriteria;
import com.ai.slp.order.service.atom.interfaces.IOrdOdFeeProdAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdFeeTotalAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdProdAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOdProdExtendAtomSV;
import com.ai.slp.order.service.atom.interfaces.IOrdOrderAtomSV;
import com.ai.slp.order.service.business.interfaces.IOrdOrderBusiSV;
import com.ai.slp.order.util.DateUtils;
import com.ai.slp.order.vo.InfoJsonVo;
import com.ai.slp.order.vo.ProdAttrInfoVo;
import com.ai.slp.order.vo.ProdExtendInfoVo;
import com.ai.slp.product.api.product.interfaces.IProductServerSV;
import com.ai.slp.product.api.product.param.ProductSkuInfo;
import com.ai.slp.product.api.product.param.SkuInfoQuery;
import com.alibaba.fastjson.JSON;

@Service
@Transactional
public class OrdOrderBusiSVImpl implements IOrdOrderBusiSV {

    private static final Log LOG = LogFactory.getLog(OrdOrderBusiSVImpl.class);

    @Autowired
    private IOrdOrderAtomSV ordOrderAtomSV;

    @Autowired
    private IOrdOdFeeTotalAtomSV ordOdFeeTotalAtomSV;

    @Autowired
    private IOrdOdFeeProdAtomSV ordOdFeeProdAtomSV;

    @Autowired
    private IOrdOdProdAtomSV ordOdProdAtomSV;

    @Autowired
    private IOrdOdProdExtendAtomSV ordOdProdExtendAtomSV;

    @Override
    public QueryOrderListResponse queryOrderList(QueryOrderListRequest orderListRequest)
            throws BusinessException, SystemException {
        LOG.debug("开始订单列表查询..");
        /* 1.订单信息查询 */
        QueryOrderListResponse response = new QueryOrderListResponse();
        PageInfo<OrdOrderVo> pageInfo = new PageInfo<OrdOrderVo>();
        OrdOrderCriteria example = new OrdOrderCriteria();
        OrdOrderCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(orderListRequest.getTenantId());
        if (orderListRequest.getOrderId() != null && orderListRequest.getOrderId().intValue() != 0) {
            criteria.andOrderIdEqualTo(orderListRequest.getOrderId());
        }
        if (!StringUtils.isBlank(orderListRequest.getOrderType())) {
            criteria.andOrderTypeEqualTo(orderListRequest.getOrderType());
        }
        if (!StringUtils.isBlank(orderListRequest.getUserId())) {
            criteria.andOrderTypeEqualTo(orderListRequest.getUserId());
        }
        if (!StringUtils.isBlank(orderListRequest.getState())) {
            criteria.andStateEqualTo(orderListRequest.getState());
        }
        if ((orderListRequest.getOrderTimeBegin() != null)
                && (orderListRequest.getOrderTimeEnd() != null)) {
            criteria.andOrderTimeBetween(DateUtils.getTimestamp(
                    orderListRequest.getOrderTimeBegin(), "yyyy-MM-dd HH:mm:ss"), DateUtils
                    .getTimestamp(orderListRequest.getOrderTimeEnd(), "yyyy-MM-dd HH:mm:ss"));
        }
        example.setLimitStart((orderListRequest.getPageNo() - 1) * orderListRequest.getPageSize());
        example.setLimitEnd(orderListRequest.getPageSize());
        List<OrdOrder> list = ordOrderAtomSV.selectByExample(example);
        List<OrdOrderVo> ordOrderList = new ArrayList<OrdOrderVo>();
        for (OrdOrder order : list) {
            /* 2.订单费用信息查询 */
            List<OrdOdFeeTotal> orderFeeTotalList = this.getOrderFeeTotalList(order.getTenantId(),
                    order.getOrderId(), orderListRequest.getPayStyle());
            if (!CollectionUtil.isEmpty(orderFeeTotalList)) {
                OrdOdFeeTotal ordOdFeeTotal = orderFeeTotalList.get(0);
                OrdOrderVo ordOrderVo = new OrdOrderVo();
                ordOrderVo.setOrderId(order.getOrderId());
                ordOrderVo.setOrderType(order.getOrderType());
                ordOrderVo.setBusiCode(order.getBusiCode());
                ordOrderVo.setState(order.getState());
                ordOrderVo.setStateName("");
                ordOrderVo.setOrderTime(order.getOrderTime());
                ordOrderVo.setAdjustFee(ordOdFeeTotal.getAdjustFee());
                ordOrderVo.setDiscountFee(ordOdFeeTotal.getDiscountFee());
                ordOrderVo.setPaidFee(ordOdFeeTotal.getPaidFee());
                ordOrderVo.setPayFee(ordOdFeeTotal.getPayFee());
                ordOrderVo.setPayStyle(ordOdFeeTotal.getPayStyle());
                ordOrderVo.setPayStyleName("");
                ordOrderVo.setPayTime(ordOdFeeTotal.getUpdateTime());
                ordOrderVo.setTotalFee(ordOdFeeTotal.getTotalFee());
                int phoneCount = this.getProdExtendInfo(orderListRequest.getTenantId(),
                        order.getOrderId());
                ordOrderVo.setPhoneCount(phoneCount);
                /* 3.订单费用明细查询 */
                List<OrderPayVo> orderFeeProdList = this.getOrderFeeProdList(order.getOrderId());
                ordOrderVo.setPayDataList(orderFeeProdList);
                /* 4.订单费用明细查询 */
                List<OrdProductVo> productList = this.getOrdProductList(order.getTenantId(),
                        order.getOrderId());
                ordOrderVo.setProductList(productList);
                ordOrderList.add(ordOrderVo);
            }

        }
        pageInfo.setPageNo(orderListRequest.getPageNo());
        pageInfo.setPageSize(orderListRequest.getPageSize());
        pageInfo.setResult(ordOrderList);
        pageInfo.setCount(ordOrderList.size());
        response.setPageInfo(pageInfo);
        return response;

    }

    /**
     * 订单费用信息查询
     * 
     * @param tenantId
     * @param orderId
     * @param payStyle
     * @return
     * @author zhangxw
     * @ApiDocMethod
     */
    private List<OrdOdFeeTotal> getOrderFeeTotalList(String tenantId, long orderId, String payStyle) {
        OrdOdFeeTotalCriteria example = new OrdOdFeeTotalCriteria();
        OrdOdFeeTotalCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(tenantId);
        criteria.andOrderIdEqualTo(orderId);
        if (!StringUtils.isBlank(payStyle)) {
            criteria.andPayStyleEqualTo(payStyle);
        }
        List<OrdOdFeeTotal> orderFeeTotalList = ordOdFeeTotalAtomSV.selectByExample(example);
        return orderFeeTotalList;
    }

    /**
     * 订单费用明细查询
     * 
     * @param orderId
     * @return
     * @author zhangxw
     * @ApiDocMethod
     */
    private List<OrderPayVo> getOrderFeeProdList(long orderId) {
        List<OrderPayVo> payDataList = null;
        OrdOdFeeProdCriteria example = new OrdOdFeeProdCriteria();
        OrdOdFeeProdCriteria.Criteria criteria = example.createCriteria();
        criteria.andOrderIdEqualTo(orderId);
        List<OrdOdFeeProd> orderFeeProdList = ordOdFeeProdAtomSV.selectByExample(example);
        if (!CollectionUtil.isEmpty(orderFeeProdList)) {
            for (OrdOdFeeProd ordOdFeeProd : orderFeeProdList) {
                payDataList = new ArrayList<OrderPayVo>();
                OrderPayVo orderPayVo = new OrderPayVo();
                orderPayVo.setPayStyle(ordOdFeeProd.getPayStyle());
                orderPayVo.setPaidFee(ordOdFeeProd.getPaidFee());
                orderPayVo.setPayStyleName("");
                payDataList.add(orderPayVo);
            }
        }
        return payDataList;
    }

    /**
     * 商品集合
     * 
     * @param orderId
     * @return
     * @author zhangxw
     * @param tenantId
     * @ApiDocMethod
     */
    private List<OrdProductVo> getOrdProductList(String tenantId, long orderId) {
        List<OrdProductVo> productList = new ArrayList<OrdProductVo>();
        OrdOdProdCriteria example = new OrdOdProdCriteria();
        OrdOdProdCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(tenantId);
        criteria.andOrderIdEqualTo(orderId);
        List<OrdOdProd> ordOdProdList = ordOdProdAtomSV.selectByExample(example);
        if (!CollectionUtil.isEmpty(ordOdProdList)) {
            for (OrdOdProd ordOdProd : ordOdProdList) {
                OrdProductVo ordProductVo = new OrdProductVo();
                ordProductVo.setOrderId(orderId);
                ordProductVo.setSkuId(ordOdProd.getSkuId());
                ordProductVo.setProdName(ordOdProd.getProdName());
                ordProductVo.setSalePrice(ordOdProd.getSalePrice());
                ordProductVo.setBuySum(ordOdProd.getBuySum());
                ordProductVo.setTotalFee(ordOdProd.getTotalFee());
                ordProductVo.setDiscountFee(ordOdProd.getDiscountFee());
                ordProductVo.setAdjustFee(ordOdProd.getAdjustFee());
                ordProductVo.setOperDiscountFee(ordOdProd.getOperDiscountFee());
                String extendInfo = ordOdProd.getExtendInfo();
                ProdAttrInfoVo prodAttrInfoVo = JSON.parseObject(extendInfo, ProdAttrInfoVo.class);
                ordProductVo.setBasicOrgId(prodAttrInfoVo.getBasicOrgId());
                ordProductVo.setBasicOrgName("");
                ordProductVo.setProvinceCode(prodAttrInfoVo.getProvinceCode());
                ordProductVo.setProvinceName("");
                ordProductVo.setChargeFee(prodAttrInfoVo.getChargeFee());
                ProductImage productImage = this.getProductImage(tenantId, ordOdProd.getSkuId());
                ordProductVo.setProductImage(productImage);
                ordProductVo.setProdExtendInfo(this.getProdExtendInfo(tenantId, orderId,
                        ordOdProd.getProdDetalId()));
                productList.add(ordProductVo);
            }

        }
        return productList;
    }

    /**
     * 
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
        IProductServerSV iProductServerSV = DubboConsumerFactory.getService("iProductServerSV");
        ProductSkuInfo productSkuInfo = iProductServerSV.queryProductSkuById(skuInfoQuery);
        productImage.setVfsId(productSkuInfo.getVfsId());
        productImage.setPicType(productSkuInfo.getPicType());
        return productImage;
    }

    /**
     * 获得手机号串
     * 
     * @param tenantId
     * @param orderId
     * @param prodDetailId
     * @return
     * @author zhangxw
     * @ApiDocMethod
     */
    private String getProdExtendInfo(String tenantId, long orderId, long prodDetailId) {
        /* 1.查询商品明细拓展表 */
        String prodExtendInfo = "";
        OrdOdProdExtendCriteria example = new OrdOdProdExtendCriteria();
        OrdOdProdExtendCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(tenantId);
        criteria.andOrderIdEqualTo(orderId);
        criteria.andProdDetalExtendIdEqualTo(prodDetailId);
        List<OrdOdProdExtend> ordOdProdExtendList = ordOdProdExtendAtomSV.selectByExample(example);
        /* 2.遍历取出值信息 */
        StringBuffer sb = new StringBuffer();
        if (!CollectionUtil.isEmpty(ordOdProdExtendList)) {
            OrdOdProdExtend ordOdProdExtend = ordOdProdExtendList.get(0);
            String infoJson = ordOdProdExtend.getInfoJson();
            InfoJsonVo infoJsonVo = JSON.parseObject(infoJson, InfoJsonVo.class);
            List<ProdExtendInfoVo> prodExtendInfoVoList = infoJsonVo.getProdExtendInfoVoList();
            for (ProdExtendInfoVo prodExtendInfoVo : prodExtendInfoVoList) {
                sb.append(prodExtendInfoVo.getProdExtendInfoValue()).append(",");
            }
            prodExtendInfo = sb.substring(0, sb.length() - 1);
        }
        return prodExtendInfo;

    }

    /**
     * 获得手机号数量
     * 
     * @param tenantId
     * @param orderId
     * @param prodDetailId
     * @return
     * @author zhangxw
     * @ApiDocMethod
     */
    private int getProdExtendInfo(String tenantId, long orderId) {
        /* 1.查询商品明细拓展表 */
        OrdOdProdExtendCriteria example = new OrdOdProdExtendCriteria();
        OrdOdProdExtendCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(tenantId);
        criteria.andOrderIdEqualTo(orderId);
        List<OrdOdProdExtend> ordOdProdExtendList = ordOdProdExtendAtomSV.selectByExample(example);
        /* 2.遍历取出值信息 */
        int phoneNum = 0;
        if (!CollectionUtil.isEmpty(ordOdProdExtendList)) {
            for (OrdOdProdExtend ordOdProdExtend : ordOdProdExtendList) {
                String infoJson = ordOdProdExtend.getInfoJson();
                InfoJsonVo infoJsonVo = JSON.parseObject(infoJson, InfoJsonVo.class);
                List<ProdExtendInfoVo> prodExtendInfoVoList = infoJsonVo.getProdExtendInfoVoList();
                phoneNum = prodExtendInfoVoList.size() + phoneNum;
            }
        }
        return phoneNum;

    }

    @Override
    public QueryOrderResponse queryOrder(QueryOrderRequest orderRequest) throws BusinessException,
            SystemException {
        LOG.debug("开始订单详情询..");
        /* 1.订单信息查询 */
        QueryOrderResponse response = new QueryOrderResponse();
        OrdOrderCriteria example = new OrdOrderCriteria();
        OrdOrderCriteria.Criteria criteria = example.createCriteria();
        criteria.andTenantIdEqualTo(orderRequest.getTenantId());
        if (orderRequest.getOrderId() != 0) {
            criteria.andOrderIdEqualTo(orderRequest.getOrderId());
        }
        List<OrdOrder> list = ordOrderAtomSV.selectByExample(example);
        OrdOrderVo ordOrderVo = null;
        if (!CollectionUtil.isEmpty(list)) {
            OrdOrder order = list.get(0);
            /* 2.订单费用信息查询 */
            List<OrdOdFeeTotal> orderFeeTotalList = this.getOrderFeeTotalList(order.getTenantId(),
                    order.getOrderId(), "");
            if (!CollectionUtil.isEmpty(orderFeeTotalList)) {
                OrdOdFeeTotal ordOdFeeTotal = orderFeeTotalList.get(0);
                ordOrderVo = new OrdOrderVo();
                ordOrderVo.setOrderId(order.getOrderId());
                ordOrderVo.setOrderType(order.getOrderType());
                ordOrderVo.setBusiCode(order.getBusiCode());
                ordOrderVo.setState(order.getState());
                ordOrderVo.setStateName("");
                ordOrderVo.setOrderTime(order.getOrderTime());
                ordOrderVo.setAdjustFee(ordOdFeeTotal.getAdjustFee());
                ordOrderVo.setDiscountFee(ordOdFeeTotal.getDiscountFee());
                ordOrderVo.setOperDiscountFee(ordOdFeeTotal.getOperDiscountFee());
                ordOrderVo.setPaidFee(ordOdFeeTotal.getPaidFee());
                ordOrderVo.setPayFee(ordOdFeeTotal.getPayFee());
                ordOrderVo.setPayStyle(ordOdFeeTotal.getPayStyle());
                ordOrderVo.setPayStyleName("");
                ordOrderVo.setPayTime(ordOdFeeTotal.getUpdateTime());
                ordOrderVo.setTotalFee(ordOdFeeTotal.getTotalFee());
                int phoneCount = this.getProdExtendInfo(orderRequest.getTenantId(),
                        order.getOrderId());
                ordOrderVo.setPhoneCount(phoneCount);
                /* 3.订单费用明细查询 */
                List<OrderPayVo> orderFeeProdList = this.getOrderFeeProdList(order.getOrderId());
                ordOrderVo.setPayDataList(orderFeeProdList);
                /* 4.订单费用明细查询 */
                List<OrdProductVo> productList = this.getOrdProductList(order.getTenantId(),
                        order.getOrderId());
                ordOrderVo.setProductList(productList);
            }

        }
        response.setOrdOrderVo(ordOrderVo);
        return response;

    }

}
