package org.supermarket.modules.purchase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.supermarket.common.exception.BusinessException;
import org.supermarket.modules.inventory.service.InventoryService;
import org.supermarket.modules.purchase.dto.PurchaseOrderDTO;
import org.supermarket.modules.purchase.entity.PurchaseOrder;
import org.supermarket.modules.purchase.entity.PurchaseOrderItem;
import org.supermarket.modules.purchase.mapper.PurchaseOrderItemMapper;
import org.supermarket.modules.purchase.mapper.PurchaseOrderMapper;
import org.supermarket.modules.purchase.service.PurchaseOrderService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements PurchaseOrderService {

    private final PurchaseOrderItemMapper itemMapper;
    private final InventoryService inventoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(PurchaseOrderDTO dto) {
        // 创建采购单
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("PO" + System.currentTimeMillis());
        order.setStoreId(dto.getStoreId());
        order.setSupplierId(dto.getSupplierId());
        order.setExpectedDate(dto.getExpectedDate());
        order.setRemark(dto.getRemark());
        order.setStatus(0); // 草稿

        // 计算总金额
        BigDecimal totalAmount = dto.getItems().stream()
                .map(item -> item.getPurchasePrice().multiply(item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);
        save(order);

        // 保存采购明细
        List<PurchaseOrderItem> items = dto.getItems().stream().map(itemDTO -> {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(order.getId());
            item.setProductId(itemDTO.getProductId());
            item.setPurchasePrice(itemDTO.getPurchasePrice());
            item.setQuantity(itemDTO.getQuantity());
            item.setReceivedQty(BigDecimal.ZERO);
            item.setSubtotal(itemDTO.getPurchasePrice().multiply(itemDTO.getQuantity()));
            item.setExpireDate(itemDTO.getExpireDate());
            item.setRemark(itemDTO.getRemark());
            return item;
        }).collect(Collectors.toList());

        items.forEach(itemMapper::insert);
    }

    @Override
    public void submitOrder(Long orderId) {
        PurchaseOrder order = getAndCheckStatus(orderId, 0, "只有草稿状态的采购单才能提交");
        order.setStatus(1); // 待审核
        updateById(order);
    }

    @Override
    public void approveOrder(Long orderId) {
        PurchaseOrder order = getAndCheckStatus(orderId, 1, "只有待审核的采购单才能审核");
        order.setStatus(2); // 已审核
        order.setApproveTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(Long orderId) {
        PurchaseOrder order = getAndCheckStatus(orderId, 2, "只有已审核的采购单才能入库");

        // 查询明细，逐条入库
        List<PurchaseOrderItem> items = itemMapper.selectByOrderId(orderId);
        items.forEach(item -> {
            inventoryService.inbound(
                    order.getStoreId(),
                    item.getProductId(),
                    item.getQuantity(),
                    order.getOrderNo());
            // 更新已收货数量
            item.setReceivedQty(item.getQuantity());
            itemMapper.updateById(item);
        });

        order.setStatus(3); // 已入库
        order.setActualDate(LocalDate.now());
        updateById(order);
    }

    @Override
    public void cancelOrder(Long orderId) {
        PurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购单不存在");
        if (order.getStatus() == 3) throw new BusinessException("已入库的采购单不能取消");
        order.setStatus(4); // 已取消
        updateById(order);
    }

    @Override
    public Page<PurchaseOrder> pageOrder(int pageNum, int pageSize,
                                         Long storeId, Integer status) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(storeId != null, PurchaseOrder::getStoreId, storeId);
        wrapper.eq(status != null, PurchaseOrder::getStatus, status);
        wrapper.orderByDesc(PurchaseOrder::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    // 查询采购单并校验状态
    private PurchaseOrder getAndCheckStatus(Long orderId, int expectedStatus, String errorMsg) {
        PurchaseOrder order = getById(orderId);
        if (order == null) throw new BusinessException("采购单不存在");
        if (order.getStatus() != expectedStatus) throw new BusinessException(errorMsg);
        return order;
    }
}