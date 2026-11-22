package com.example.weather_order_assistant_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.entity.SupplierProduct;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SupplierProductRepository;
import com.example.weather_order_assistant_system.repository.SupplierRepository;
import com.example.weather_order_assistant_system.request.SupplierProductCreateRequest;
import com.example.weather_order_assistant_system.request.SupplierProductPriceRequest;

/**
 * 使用 Mockito 驗證供貨關聯的核心業務規則。
 * 不連接真實 MySQL，資料表及 API 仍需透過 Flyway、Postman 驗收。
 */
@ExtendWith(MockitoExtension.class)
class SupplierProductServiceTest {

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SupplierProductService supplierProductService;

    private Supplier supplier;
    private Product product;

    @BeforeEach
    void setUp() {
        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("測試供應商");
        supplier.setStatus(1);

        product = new Product();
        product.setId(5L);
        product.setName("冰紅茶");
        product.setSku("TEA001");
        product.setStatus(1);
    }

    @Test
    void create_shouldSaveRelationAndPrice() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(supplier));
        when(productRepository.findByIdAndDeletedAtIsNull(5L))
                .thenReturn(Optional.of(product));
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(1L, 5L))
                .thenReturn(Optional.empty());
        when(supplierProductRepository.save(any(SupplierProduct.class)))
                .thenAnswer(call -> {
                    SupplierProduct relation = call.getArgument(0);
                    relation.setId(9L);
                    return relation;
                });

        SupplierProductCreateRequest request = new SupplierProductCreateRequest();
        request.setProductId(5L);
        request.setPurchasePrice(new BigDecimal("15.50"));

        var result = supplierProductService.create(1L, request);
        assertEquals(9L, result.getId());
        assertEquals(1L, result.getSupplierId());
        assertEquals(5L, result.getProductId());
        assertEquals(new BigDecimal("15.50"), result.getPurchasePrice());
        assertEquals(1, result.getStatus());
    }

    @Test
    void create_shouldRejectDuplicateEvenWhenOriginalWasDisabled() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(supplier));
        when(productRepository.findByIdAndDeletedAtIsNull(5L))
                .thenReturn(Optional.of(product));
        SupplierProduct existing = new SupplierProduct();
        existing.setStatus(0);
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(1L, 5L))
                .thenReturn(Optional.of(existing));

        SupplierProductCreateRequest request = new SupplierProductCreateRequest();
        request.setProductId(5L);
        request.setPurchasePrice(new BigDecimal("15.00"));

        var error = assertThrows(ResponseStatusException.class,
                () -> supplierProductService.create(1L, request));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void updatePrice_shouldRejectNegativeValue() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(supplier));
        when(productRepository.findByIdAndDeletedAtIsNull(5L))
                .thenReturn(Optional.of(product));

        SupplierProductPriceRequest request = new SupplierProductPriceRequest();
        request.setPurchasePrice(new BigDecimal("-1"));

        var error = assertThrows(ResponseStatusException.class,
                () -> supplierProductService.updatePrice(1L, 5L, request));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void updateStatus_shouldNotAllowReactivationWhenSupplierIsDisabled() {
        supplier.setStatus(0);
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(supplier));

        SupplierProduct relation = new SupplierProduct();
        relation.setSupplier(supplier);
        relation.setProduct(product);
        relation.setStatus(0);
        when(supplierProductRepository.findBySupplier_IdAndProduct_Id(1L, 5L))
                .thenReturn(Optional.of(relation));

        var error = assertThrows(ResponseStatusException.class,
                () -> supplierProductService.updateStatus(1L, 5L, 1));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
