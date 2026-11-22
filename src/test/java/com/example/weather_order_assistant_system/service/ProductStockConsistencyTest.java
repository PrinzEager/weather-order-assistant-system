package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.example.weather_order_assistant_system.entity.ProductCategory;
import com.example.weather_order_assistant_system.repository.ProductCategoryRepository;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.request.ProductRequest;

/**
 * 驗證商品編輯的庫存快照及鎖定規則。
 * Mockito 無法模擬真正的資料庫鎖等待，仍需進行 MySQL 併發整合測試。
 */
@ExtendWith(MockitoExtension.class)
class ProductStockConsistencyTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private ProductCategory category;

    @BeforeEach
    void setUp() {
        ProductCategory parent = new ProductCategory();
        parent.setId(1L);
        category = new ProductCategory();
        category.setId(2L);
        category.setName("茶類");
        category.setStatus(1);
        category.setParent(parent);

        product = new Product();
        product.setId(3L);
        product.setCategory(category);
        product.setName("紅茶");
        product.setSku("TEA001");
        product.setPrice(new BigDecimal("25.00"));
        product.setCurrentStock(12);
        product.setStatus(1);
    }

    private ProductRequest request(Integer requestedStock, Integer expectedStock) {
        ProductRequest req = new ProductRequest();
        req.setCategoryId(2L);
        req.setSku("TEA001");
        req.setName("紅茶（新名稱）");
        req.setPrice(new BigDecimal("28.00"));
        req.setCurrentStock(requestedStock);
        req.setExpectedCurrentStock(expectedStock);
        req.setSafetyStock(5);
        return req;
    }

    private void stubEditableProduct() {
        when(productRepository.findByIdForStockUpdate(3L))
                .thenReturn(Optional.of(product));
        when(categoryRepository.findByIdAndDeletedAtIsNull(2L))
                .thenReturn(Optional.of(category));
    }

    @Test
    void currentStockCanChangeWhenSnapshotMatches() {
        stubEditableProduct();
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = productService.updateProduct(3L, request(20, 12));

        assertEquals(20, response.getCurrentStock());
        assertEquals("紅茶（新名稱）", response.getName());
        verify(productRepository).findByIdForStockUpdate(3L);
    }

    @Test
    void staleFormMustNotOverwriteStockChangedBySale() {
        stubEditableProduct();
        // 畫面開啟時庫存為 12，但銷售完成後最新庫存為 9。
        product.setCurrentStock(9);

        var exception = assertThrows(ResponseStatusException.class,
                () -> productService.updateProduct(3L, request(12, 12)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(9, product.getCurrentStock());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void legacyMetadataUpdateWithoutSnapshotCanKeepCurrentStock() {
        stubEditableProduct();
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = productService.updateProduct(3L, request(12, null));

        assertEquals(12, response.getCurrentStock());
    }

    @Test
    void legacyRequestCannotSetNewStockWithoutSnapshot() {
        stubEditableProduct();

        var exception = assertThrows(ResponseStatusException.class,
                () -> productService.updateProduct(3L, request(20, null)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void changingStatusAlsoLocksProductRow() {
        when(productRepository.findByIdForStockUpdate(3L))
                .thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProductStatus(3L, 0);

        verify(productRepository).findByIdForStockUpdate(3L);
        assertEquals(0, product.getStatus());
    }

    @Test
    void softDeleteAlsoLocksProductRow() {
        when(productRepository.findByIdForStockUpdate(3L))
                .thenReturn(Optional.of(product));

        productService.deleteProduct(3L);

        verify(productRepository).findByIdForStockUpdate(3L);
        verify(productRepository).save(product);
    }
}
