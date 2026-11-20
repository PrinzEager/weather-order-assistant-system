package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.SalesRecord;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SalesRecordRepository;
import com.example.weather_order_assistant_system.request.SalesRecordRequest;
import com.example.weather_order_assistant_system.response.SalesRecordResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesRecordService {

    // 銷售紀錄 Repository
    private final SalesRecordRepository salesRecordRepository;

    // 商品 Repository
    private final ProductRepository productRepository;

    // 商品啟用狀態
    private static final Integer ACTIVE = 1;

    // 查詢全部未軟刪除的銷售紀錄
    public List<SalesRecordResponse> findAllSalesRecords() {

        return salesRecordRepository
                .findByDeletedAtIsNullOrderByDateTimeDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 依 ID 查詢單筆有效銷售紀錄
    public SalesRecordResponse findSalesRecordById(Long id) {

        SalesRecord salesRecord = salesRecordRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "銷售紀錄不存在或已退貨"
                ));

        return toResponse(salesRecord);
    }

    // 新增銷售紀錄
    public SalesRecordResponse createSalesRecord(
            SalesRecordRequest request
    ) {

        // 查詢商品，確認商品存在且未軟刪除
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        // 停用商品不可建立新銷售紀錄
        if (!ACTIVE.equals(product.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品已停用，無法新增銷售紀錄"
            );
        }

        // 再次檢查銷售數量
        // 即使 Request 有 Validation，
        // Service 仍保留必要的業務規則檢查
        Integer quantity = request.getQuantity();

        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "銷售數量必須大於 0"
            );
        }

        // 驗證銷售單價
        BigDecimal unitPrice = request.getUnitPrice();

        if (unitPrice == null
                || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "銷售單價不可小於 0"
            );
        }

        // 確認商品目前庫存是否足夠
        if (product.getCurrentStock() == null
                || product.getCurrentStock() < quantity) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "商品庫存不足，無法完成銷售"
            );
        }

        // 計算銷售總金額
        BigDecimal totalAmount = unitPrice.multiply(
                BigDecimal.valueOf(quantity)
        );

        // 建立銷售紀錄
        SalesRecord salesRecord = new SalesRecord();

        salesRecord.setProduct(product);
        salesRecord.setQuantity(quantity);
        salesRecord.setUnitPrice(unitPrice);
        salesRecord.setTotalAmount(totalAmount);

        // 以系統目前時間作為實際銷售時間
        salesRecord.setDateTime(LocalDateTime.now());

        // 銷售成功，扣除商品庫存
        product.setCurrentStock(
                product.getCurrentStock() - quantity
        );

        productRepository.save(product);

        // 儲存銷售紀錄
        SalesRecord savedRecord = salesRecordRepository.save(
                salesRecord
        );

        // 回傳新增結果
        return toResponse(savedRecord);
    }

    // 整筆退貨：軟刪除銷售紀錄並恢復商品庫存
    public void deleteSalesRecord(Long id) {

        // 只允許退貨尚未軟刪除的銷售紀錄
        SalesRecord salesRecord = salesRecordRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "銷售紀錄不存在或已退貨"
                ));

        // 取得原本銷售的商品
        Product product = salesRecord.getProduct();

        // 恢復原本銷售的數量
        // 即使商品目前已停用，也可以辦理歷史銷售退貨
        long restoredStock = (long) product.getCurrentStock()
                + salesRecord.getQuantity();

        // 避免庫存超出 Integer 可儲存的範圍
        if (restoredStock > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "退貨後庫存超出允許範圍"
            );
        }

        product.setCurrentStock((int) restoredStock);

        productRepository.save(product);

        // 設定軟刪除時間
        // 不實際刪除資料庫中的銷售紀錄
        salesRecord.setDeletedAt(LocalDateTime.now());

        salesRecordRepository.save(salesRecord);
    }

    // 將 SalesRecord Entity 轉換為 Response DTO
    private SalesRecordResponse toResponse(
            SalesRecord salesRecord
    ) {

        Product product = salesRecord.getProduct();

        return new SalesRecordResponse(
                salesRecord.getId(),
                product.getId(),
                product.getName(),
                product.getSku(),
                salesRecord.getQuantity(),
                salesRecord.getUnitPrice(),
                salesRecord.getTotalAmount(),
                salesRecord.getDateTime(),
                salesRecord.getCreatedAt()
        );
    }
}