package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.PurchaseOrder;
import com.example.weather_order_assistant_system.entity.PurchaseOrderItem;
import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.PurchaseOrderItemRepository;
import com.example.weather_order_assistant_system.repository.PurchaseOrderRepository;
import com.example.weather_order_assistant_system.repository.SupplierRepository;

/**
 * 驗證入庫前先鎖採購單，再依 ID 鎖商品。
 * 真正的兩交易互斥與回滾，需在 MySQL 上另做整合測試。
 */
@ExtendWith(MockitoExtension.class)
class PurchaseReceiptConsistencyTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @InjectMocks
    private PurchaseOrderService purchaseOrderService;

    private Product product(long id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU" + id);
        product.setName("商品" + id);
        product.setCurrentStock(stock);
        return product;
    }

    private PurchaseOrder order(String status, Product... products) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(10L);
        order.setOrderStatus(status);
        order.setTotalAmount(BigDecimal.ZERO);
        Supplier supplier = new Supplier();
        supplier.setId(2L);
        order.setSupplier(supplier);

        List<PurchaseOrderItem> items = new java.util.ArrayList<>();
        for (Product product : products) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setId(product.getId());
            item.setProduct(product);
            item.setQuantity(3);
            item.setUnitPrice(BigDecimal.ONE);
            item.setSubtotal(BigDecimal.valueOf(3));
            item.setPurchaseOrder(order);
            items.add(item);
        }
        order.setItems(items);
        return order;
    }

    @Test
    void receiveLocksOrderAndProductsInIdOrder() {
        Product first = product(1L, 10);
        Product second = product(2L, 6);
        PurchaseOrder order = order("SUBMITTED", second, first);

        when(purchaseOrderRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(order));
        when(productRepository.findByIdForStockUpdate(1L))
                .thenReturn(Optional.of(first));
        when(productRepository.findByIdForStockUpdate(2L))
                .thenReturn(Optional.of(second));
        when(purchaseOrderRepository.saveAndFlush(any(PurchaseOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = purchaseOrderService.receiveOrder(10L);

        assertEquals("RECEIVED", result.getOrderStatus());
        assertEquals(13, first.getCurrentStock());
        assertEquals(9, second.getCurrentStock());
        var inOrder = inOrder(purchaseOrderRepository, productRepository);
        inOrder.verify(purchaseOrderRepository).findActiveByIdForUpdate(10L);
        inOrder.verify(productRepository).findByIdForStockUpdate(1L);
        inOrder.verify(productRepository).findByIdForStockUpdate(2L);
    }

    @Test
    void alreadyReceivedMustRejectBeforeAnyStockMutation() {
        PurchaseOrder order = order("RECEIVED", product(1L, 10));
        when(purchaseOrderRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(order));

        var exception = assertThrows(ResponseStatusException.class,
                () -> purchaseOrderService.receiveOrder(10L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).findByIdForStockUpdate(any());
    }

    @Test
    void stockOverflowMustNotMarkOrderReceived() {
        Product product = product(1L, Integer.MAX_VALUE);
        PurchaseOrder order = order("SUBMITTED", product);
        when(purchaseOrderRepository.findActiveByIdForUpdate(10L))
                .thenReturn(Optional.of(order));
        when(productRepository.findByIdForStockUpdate(1L))
                .thenReturn(Optional.of(product));

        var exception = assertThrows(ResponseStatusException.class,
                () -> purchaseOrderService.receiveOrder(10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("SUBMITTED", order.getOrderStatus());
        verify(purchaseOrderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
    }
}
