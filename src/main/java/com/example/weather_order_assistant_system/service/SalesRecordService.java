package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.SalesRecord;
import com.example.weather_order_assistant_system.entity.SalesReturn;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.SalesRecordRepository;
import com.example.weather_order_assistant_system.repository.SalesReturnRepository;
import com.example.weather_order_assistant_system.request.SalesBatchRequest;
import com.example.weather_order_assistant_system.request.SalesRecordRequest;
import com.example.weather_order_assistant_system.request.SalesReturnRequest;
import com.example.weather_order_assistant_system.response.SalesRecordResponse;
import com.example.weather_order_assistant_system.response.SalesReturnResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SalesRecordService {

    // 銷售紀錄 Repository
    private final SalesRecordRepository salesRecordRepository;

    // 商品 Repository
    private final ProductRepository productRepository;

    // 每次退貨均由同一筆交易保存退貨紀錄與庫存變化。
    private final SalesReturnRepository salesReturnRepository;

    // 商品啟用狀態
    private static final Integer ACTIVE = 1;

    // 本 Demo 一般退貨期限：從銷售時間起 7 x 24 小時。
    private static final Duration RETURN_WINDOW = Duration.ofDays(7);

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

    // 舊版單項銷售 API 保持相容；不指定 batchId。
    public SalesRecordResponse createSalesRecord(SalesRecordRequest request) {
        return createSalesRecordItem(request, null, LocalDateTime.now());
    }

    /**
     * 多項銷售使用一個 @Transactional 交易提交，任何商品庫存不足都會整批回滾。
     * 依商品 ID 排序後依序加鎖，降低與其他批次互相等待造成死結的機會。
     * 保留每一項為獨立 SalesRecord，原退貨 API、銷售統計與歷史資料不用修改。
     */
    public List<SalesRecordResponse> createSalesBatch(SalesBatchRequest request) {
        if (request == null || request.getItems() == null
                || request.getItems().isEmpty() || request.getItems().size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "一次銷售請選擇 1 至 50 項商品");
        }
        Set<Long> seen = new HashSet<>();
        for (SalesRecordRequest item : request.getItems()) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0
                    || !seen.add(item.getProductId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一次銷售不可重複選擇商品，且商品編號必須有效");
            }
        }
        String batchId = UUID.randomUUID().toString();
        LocalDateTime soldAt = LocalDateTime.now();
        return request.getItems().stream()
                .sorted(Comparator.comparing(SalesRecordRequest::getProductId))
                .map(item -> createSalesRecordItem(item, batchId, soldAt))
                .toList();
    }

    // 扣庫存與儲存明細的共用核心，必須由外部帶有交易的 public 方法呼叫。
    private SalesRecordResponse createSalesRecordItem(
            SalesRecordRequest request, String batchId, LocalDateTime soldAt) {

        // 查詢商品，確認商品存在且未軟刪除
        Product product = productRepository
                .findByIdForStockUpdate(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        // 軟刪除的商品不可再建立新銷售紀錄。
        if (product.getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在或已刪除");
        }

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
        salesRecord.setBatchId(batchId);
        salesRecord.setQuantity(quantity);
        salesRecord.setUnitPrice(unitPrice);
        salesRecord.setTotalAmount(totalAmount);

        // 以系統目前時間作為實際銷售時間
        salesRecord.setDateTime(soldAt);

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

    // 查詢指定銷售紀錄的退貨歷史。
    // 每筆明細的 returnedQuantity / remainingReturnableQuantity
    // 表示「該次退貨完成當下」的累計與剩餘數量，而不是目前主檔最新狀態。
    @Transactional(readOnly = true)
    public List<SalesReturnResponse> findReturns(Long salesRecordId) {
        SalesRecord record = salesRecordRepository.findByIdAndDeletedAtIsNull(salesRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "銷售紀錄不存在或已刪除"));

        // Repository 原本依退貨時間及 ID 由新到舊排列。
        List<SalesReturn> returns = new ArrayList<>(salesReturnRepository
                .findBySalesRecord_IdOrderByReturnedAtDescIdDesc(salesRecordId));

        // 改成由舊到新逐筆加總，才能正確還原每次退貨後的數量。
        Collections.reverse(returns);
        List<SalesReturnResponse> responses = new ArrayList<>();
        int cumulativeReturned = 0;

        for (SalesReturn item : returns) {
            cumulativeReturned = Math.addExact(cumulativeReturned, item.getQuantity());
            responses.add(new SalesReturnResponse(
                    item.getId(),
                    record.getId(),
                    item.getQuantity(),
                    item.getReturnAmount(),
                    item.getReason(),
                    item.getReturnedAt(),
                    cumulativeReturned,
                    record.getQuantity() - cumulativeReturned
            ));
        }

        // 保持原 API 的排序：最新退貨在最前面。
        Collections.reverse(responses);
        return responses;
    }

    // 部分退貨：所有業務檢查、紀錄與庫存回補都在同一交易內完成。
    public SalesReturnResponse createSalesReturn(Long salesRecordId, SalesReturnRequest request) {
        // 先讀取銷售以取得商品 ID；此時尚不使用資料做退貨數量判斷。
        SalesRecord initial = salesRecordRepository.findByIdAndDeletedAtIsNull(salesRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "銷售紀錄不存在或已刪除"));

        // 全部庫存異動都先鎖商品，再鎖銷售紀錄，維持固定鎖定順序。
        Product product = productRepository.findByIdForStockUpdate(initial.getProduct().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "商品不存在"));
        SalesRecord salesRecord = salesRecordRepository.findActiveByIdForUpdate(salesRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "銷售紀錄不存在或已刪除"));

        Integer quantity = request.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退貨數量必須大於 0");
        }

        LocalDateTime now = LocalDateTime.now();
        if (salesRecord.getDateTime() == null
                || now.isBefore(salesRecord.getDateTime())
                || now.isAfter(salesRecord.getDateTime().plus(RETURN_WINDOW))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已超過 7 天退貨期限");
        }

        int returned = salesRecord.getReturnedQuantity() == null
                ? 0 : salesRecord.getReturnedQuantity();
        int remaining = salesRecord.getQuantity() - returned;
        if (remaining < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "退貨數量超過剩餘可退數量");
        }

        // 本版僅支援可重新販售商品的退貨，因此數量全數回補庫存。
        if (product.getCurrentStock() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "商品庫存資料不正確");
        }
        long stockAfterReturn = (long) product.getCurrentStock() + quantity;
        if (stockAfterReturn > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "退貨後庫存超出允許範圍");
        }

        SalesReturn salesReturn = new SalesReturn();
        salesReturn.setSalesRecord(salesRecord);
        salesReturn.setQuantity(quantity);
        salesReturn.setReturnAmount(salesRecord.getUnitPrice()
                .multiply(BigDecimal.valueOf(quantity)));
        salesReturn.setReason(request.getReason() == null
                ? null : request.getReason().trim());
        salesReturn.setReturnedAt(now);

        product.setCurrentStock((int) stockAfterReturn);
        salesRecord.setReturnedQuantity(returned + quantity);
        productRepository.save(product);
        salesRecordRepository.save(salesRecord);
        SalesReturn saved = salesReturnRepository.save(salesReturn);

        return toReturnResponse(saved, salesRecord);
    }

    // 停用舊 DELETE 退貨入口，避免繞過退貨期限和數量驗證。
    // 前端需改為 POST /api/sales-records/{id}/returns。
    public void deleteSalesRecord(Long id) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "請使用 POST /api/sales-records/{id}/returns 辦理退貨");
    }

    private SalesReturnResponse toReturnResponse(SalesReturn item, SalesRecord record) {
        int returned = record.getReturnedQuantity() == null ? 0 : record.getReturnedQuantity();
        return new SalesReturnResponse(
                item.getId(), record.getId(), item.getQuantity(),
                item.getReturnAmount(), item.getReason(), item.getReturnedAt(),
                returned, record.getQuantity() - returned);
    }

    // 將 SalesRecord Entity 轉換為 Response DTO
    private SalesRecordResponse toResponse(
            SalesRecord salesRecord
    ) {

        Product product = salesRecord.getProduct();

        SalesRecordResponse response = new SalesRecordResponse(
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
        int returned = salesRecord.getReturnedQuantity() == null
                ? 0 : salesRecord.getReturnedQuantity();
        response.setBatchId(salesRecord.getBatchId());
        response.setReturnedQuantity(returned);
        response.setRemainingReturnableQuantity(salesRecord.getQuantity() - returned);
        return response;
    }
}
