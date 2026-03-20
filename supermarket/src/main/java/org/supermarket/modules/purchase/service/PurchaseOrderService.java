package org.supermarket.modules.purchase.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.supermarket.modules.purchase.dto.PurchaseOrderDTO;
import org.supermarket.modules.purchase.entity.PurchaseOrder;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    // 创建采购单
    void createOrder(PurchaseOrderDTO dto);

    // 提交审核
    void submitOrder(Long orderId);

    // 审核通过
    void approveOrder(Long orderId);

    // 确认入库（审核通过后才能入库）
    void receiveOrder(Long orderId);

    // 取消采购单
    void cancelOrder(Long orderId);

    // 分页查询
    Page<PurchaseOrder> pageOrder(int pageNum, int pageSize, Long storeId, Integer status);
}