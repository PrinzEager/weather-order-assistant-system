package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.SalesRecord;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SalesRecordRepository;
import com.example.weather_order_assistant_system.repository.SalesReturnRepository;
import com.example.weather_order_assistant_system.request.SalesBatchRequest;
import com.example.weather_order_assistant_system.request.SalesRecordRequest;

/**
 * 驗證多商品銷售的核心行為。交易回滾仍需用 MySQL + Postman 完整驗收。
 */
@ExtendWith(MockitoExtension.class)
class SalesBatchServiceTest {

    @Mock private SalesRecordRepository salesRecordRepository;
    @Mock private ProductRepository productRepository;
    @Mock private SalesReturnRepository salesReturnRepository;
    @InjectMocks private SalesRecordService salesRecordService;

    private Product tea;
    private Product juice;

    @BeforeEach
    void setup() {
        tea = product(1L, "紅茶", 12);
        juice = product(2L, "果汁", 6);
    }

    private Product product(long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setSku("SKU" + id);
        p.setStatus(1);
        p.setCurrentStock(stock);
        return p;
    }

    private SalesRecordRequest line(long id, int qty, String price) {
        SalesRecordRequest req = new SalesRecordRequest();
        req.setProductId(id);
        req.setQuantity(qty);
        req.setUnitPrice(new BigDecimal(price));
        return req;
    }

    private SalesBatchRequest batch(SalesRecordRequest... lines) {
        SalesBatchRequest request = new SalesBatchRequest();
        request.setItems(List.of(lines));
        return request;
    }

    @Test
    void createBatch_shouldShareBatchIdAndDeductEachStock() {
        when(productRepository.findByIdForStockUpdate(1L)).thenReturn(Optional.of(tea));
        when(productRepository.findByIdForStockUpdate(2L)).thenReturn(Optional.of(juice));
        when(salesRecordRepository.save(any(SalesRecord.class))).thenAnswer(call -> call.getArgument(0));

        var result = salesRecordService.createSalesBatch(batch(
                line(2L, 3, "45.00"), line(1L, 5, "22.50")));

        assertEquals(2, result.size());
        assertNotNull(result.get(0).getBatchId());
        assertEquals(result.get(0).getBatchId(), result.get(1).getBatchId());
        assertEquals(result.get(0).getDateTime(), result.get(1).getDateTime());
        assertEquals(7, tea.getCurrentStock());
        assertEquals(3, juice.getCurrentStock());
        assertEquals(new BigDecimal("112.50"), result.get(0).getTotalAmount());
        assertEquals(new BigDecimal("135.00"), result.get(1).getTotalAmount());
    }

    @Test
    void createBatch_shouldRejectDuplicateProductsBeforeStockMutation() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> salesRecordService.createSalesBatch(batch(
                        line(1L, 1, "20"), line(1L, 2, "20"))));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createBatch_insufficientStockShouldThrowConflict() {
        when(productRepository.findByIdForStockUpdate(1L)).thenReturn(Optional.of(tea));
        when(productRepository.findByIdForStockUpdate(2L)).thenReturn(Optional.of(juice));
        when(salesRecordRepository.save(any(SalesRecord.class))).thenAnswer(call -> call.getArgument(0));

        var exception = assertThrows(ResponseStatusException.class,
                () -> salesRecordService.createSalesBatch(batch(
                        line(1L, 1, "15"), line(2L, 20, "15"))));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        // Mockito 未提供實際資料庫交易，不能用此測試冒充已驗證回滾。
        assertTrue(exception.getReason().contains("庫存不足"));
    }

    @Test
    void legacySingleSale_shouldNotRequireBatchId() {
        when(productRepository.findByIdForStockUpdate(1L)).thenReturn(Optional.of(tea));
        when(salesRecordRepository.save(any(SalesRecord.class))).thenAnswer(call -> call.getArgument(0));
        var result = salesRecordService.createSalesRecord(line(1L, 2, "20.00"));
        assertNull(result.getBatchId());
        assertEquals(10, tea.getCurrentStock());
    }
}
