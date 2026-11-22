package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.PurchaseOrder;
import com.example.weather_order_assistant_system.entity.PurchaseOrderItem;
import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.entity.SupplierProduct;
import com.example.weather_order_assistant_system.repository.PurchaseOrderRepository;
import com.example.weather_order_assistant_system.repository.SupplierProductRepository;
import com.example.weather_order_assistant_system.request.PurchaseOrderBatchItemRequest;
import com.example.weather_order_assistant_system.request.PurchaseOrderBatchRequest;
import com.example.weather_order_assistant_system.response.PurchaseOrderBatchResponse;
import com.example.weather_order_assistant_system.response.PurchaseOrderBatchResponse.CreatedItem;
import com.example.weather_order_assistant_system.response.PurchaseOrderBatchResponse.CreatedOrder;

import lombok.RequiredArgsConstructor;

/**
 * 多供應商批次建立採購草稿。
 *
 * 重要規則：
 * 1. 每個商品指定一個啟用且有效的供應商。
 * 2. 單價只從 supplier_products 取得，不採信前端傳入的價格。
 * 3. 一次請求中，相同商品只允許出現一次。
 * 4. 依供應商分組，一個供應商建立一張 DRAFT。
 * 5. 同一個 @Transactional 中寫入所有草稿；任何錯誤即全部回滾。
 * 6. 不更新商品庫存；只建立草稿，尚未計入未到貨數量。
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderBatchService {

    private static final int ACTIVE = 1;
    private static final BigDecimal MAX_ORDER_TOTAL =
            new BigDecimal("9999999999.99");

    private final SupplierProductRepository supplierProductRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional
    public PurchaseOrderBatchResponse createDrafts(PurchaseOrderBatchRequest request) {
        if (request == null || request.getItems() == null
                || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "請至少選擇一項訂貨商品");
        }

        // 日期在同一批請求中只計算一次；12 點（含）後最早只能選後天。
        LocalDate earliestDate = PurchaseDeliveryDatePolicy.earliestDeliveryDate();
        LocalDate expectedDate = request.getExpectedDeliveryDate();
        if (expectedDate == null || expectedDate.isBefore(earliestDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "預計到貨日期不可早於 " + earliestDate + "（台灣時間中午 12 點截單）");
        }
        if (request.getRemark() != null && request.getRemark().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "採購備註最多 500 字");
        }

        // LinkedHashMap 保持使用者勾選順序，建立結果也依首次出現的供應商排序。
        Map<Long, DraftGroup> groups = new LinkedHashMap<>();
        Set<Long> seenProductIds = new HashSet<>();

        for (PurchaseOrderBatchItemRequest input : request.getItems()) {
            if (input == null || input.getProductId() == null
                    || input.getProductId() <= 0 || input.getSupplierId() == null
                    || input.getSupplierId() <= 0 || input.getQuantity() == null
                    || input.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "每筆訂貨明細都必須包含有效商品、供應商及正整數數量");
            }

            // 同一項商品不能因為跨頁選取或選擇不同供應商而重複建立。
            if (!seenProductIds.add(input.getProductId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "同一批採購草稿不可重複選擇商品 ID：" + input.getProductId());
            }

            // 直接讀取供貨關聯，並在交易內確認供應商、商品與供貨狀態。
            SupplierProduct offer = supplierProductRepository
                    .findBySupplier_IdAndProduct_Id(input.getSupplierId(), input.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "此供應商未提供商品 ID：" + input.getProductId()));
            Supplier supplier = offer.getSupplier();
            Product product = offer.getProduct();
            if (!Integer.valueOf(ACTIVE).equals(offer.getStatus())
                    || !Integer.valueOf(ACTIVE).equals(supplier.getStatus())
                    || supplier.getDeletedAt() != null
                    || !Integer.valueOf(ACTIVE).equals(product.getStatus())
                    || product.getDeletedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "供應商、商品或供貨關聯已停用或刪除，請重新選擇");
            }

            // 後端重新讀取供貨報價，禁止由前端決定採購單價。
            BigDecimal price = offer.getPurchasePrice();
            if (price == null || price.signum() < 0
                    || price.scale() > 2 || price.precision() - price.scale() > 8) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "供貨報價格式不正確，請先更新供應商採購單價");
            }

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(input.getQuantity()));
            if (subtotal.compareTo(MAX_ORDER_TOTAL) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "單項商品小計超過採購單金額上限");
            }

            DraftGroup group = groups.computeIfAbsent(supplier.getId(),
                    id -> new DraftGroup(supplier));
            group.addLine(product, input.getQuantity(), price, subtotal);
        }

        // 全部驗證完成之後，才開始建立資料庫中的草稿。
        List<CreatedOrder> createdOrders = new ArrayList<>();
        BigDecimal combinedTotal = BigDecimal.ZERO;

        for (DraftGroup group : groups.values()) {
            PurchaseOrder order = new PurchaseOrder();
            order.setSupplier(group.supplier);
            order.setSupplierNameSnapshot(group.supplier.getName());
            order.setSupplierContactPersonSnapshot(group.supplier.getContactPerson());
            order.setSupplierPhoneSnapshot(group.supplier.getPhone());
            order.setSupplierEmailSnapshot(group.supplier.getEmail());
            order.setSupplierAddressSnapshot(group.supplier.getAddress());
            order.setExpectedDeliveryDate(expectedDate);
            order.setRemark(request.getRemark());
            order.setOrderStatus("DRAFT");
            order.setTotalAmount(group.total);

            // 既有 PurchaseOrder.items 已配置 CascadeType.PERSIST。
            // 使用雙向關聯並讓訂單一次保存明細，不修改原本 Entity 結構。
            List<CreatedItem> itemResponses = new ArrayList<>();
            for (DraftLine line : group.lines) {
                PurchaseOrderItem item = new PurchaseOrderItem();
                item.setPurchaseOrder(order);
                item.setProduct(line.product);
                item.setQuantity(line.quantity);
                item.setUnitPrice(line.unitPrice);
                item.setSubtotal(line.subtotal);
                order.getItems().add(item);

                itemResponses.add(new CreatedItem(line.product.getId(),
                        line.product.getName(), line.product.getSku(), line.quantity,
                        line.unitPrice, line.subtotal));
            }

            // saveAndFlush 可提早反映資料庫限制；若失敗，整筆交易回滾。
            PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
            createdOrders.add(new CreatedOrder(saved.getId(), group.supplier.getId(),
                    group.supplier.getName(), "DRAFT", expectedDate,
                    group.total, List.copyOf(itemResponses)));
            combinedTotal = combinedTotal.add(group.total);
        }

        return new PurchaseOrderBatchResponse(createdOrders.size(), combinedTotal,
                List.copyOf(createdOrders));
    }

    // 將同一供應商的商品暫存於記憶體；這不是資料庫 Entity。
    private static final class DraftGroup {
        private final Supplier supplier;
        private final List<DraftLine> lines = new ArrayList<>();
        private BigDecimal total = BigDecimal.ZERO;

        private DraftGroup(Supplier supplier) {
            this.supplier = supplier;
        }

        private void addLine(Product product, Integer quantity,
                BigDecimal price, BigDecimal subtotal) {
            BigDecimal newTotal = total.add(subtotal);
            if (newTotal.compareTo(MAX_ORDER_TOTAL) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "同一供應商的採購總額超過金額上限");
            }
            lines.add(new DraftLine(product, quantity, price, subtotal));
            total = newTotal;
        }
    }

    private record DraftLine(Product product, Integer quantity,
            BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
