package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.PurchaseOrder;
import com.example.weather_order_assistant_system.entity.PurchaseOrderItem;
import com.example.weather_order_assistant_system.entity.Supplier;

import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.repository.PurchaseOrderRepository;
import com.example.weather_order_assistant_system.repository.SupplierRepository;
import com.example.weather_order_assistant_system.repository.PurchaseOrderItemRepository;

import com.example.weather_order_assistant_system.request.PurchaseOrderItemRequest;
import com.example.weather_order_assistant_system.request.PurchaseOrderRequest;
import com.example.weather_order_assistant_system.request.PurchaseOrderUpdateRequest;

import com.example.weather_order_assistant_system.response.PurchaseOrderItemResponse;
import com.example.weather_order_assistant_system.response.PurchaseOrderResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseOrderService {

    // =====================================================
    // Repository 注入
    // =====================================================

    // 採購單 Repository
    private final PurchaseOrderRepository purchaseOrderRepository;

    // 供應商 Repository
    private final SupplierRepository supplierRepository;

    // 商品 Repository
    private final ProductRepository productRepository;

    // 採購明細 Repository
    // 負責修改草稿時刪除舊明細、儲存新明細
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    // =====================================================
    // 共用常數
    // =====================================================

    // 啟用狀態
    private static final Integer ACTIVE = 1;

    // 採購單草稿狀態
    private static final String DRAFT = "DRAFT";

    // DECIMAL(12,2) 可儲存的最大金額
    private static final BigDecimal MAX_TOTAL_AMOUNT =
            new BigDecimal("9999999999.99");

    // =====================================================
    // 1. 查詢所有未軟刪除採購單
    // =====================================================
    // 依照訂貨日期由新到舊排序。
    //
    // 不限制供應商目前的狀態。
    // 即使供應商已停用或軟刪除，
    // 仍允許查詢之前建立的歷史採購單。
    // =====================================================
    public List<PurchaseOrderResponse> findAllOrders() {

        List<PurchaseOrder> orders =
                purchaseOrderRepository
                        .findByDeletedAtIsNullOrderByOrderDateDesc();

        return orders.stream()
                .map(this::toResponse)
                .toList();
    }

    // =====================================================
    // 2. 依 ID 查詢採購單
    // =====================================================
    // 只排除採購單本身已軟刪除的資料。
    //
    // 不會因為供應商已終止合作，
    // 就拒絕查詢歷史採購單。
    // =====================================================
    public PurchaseOrderResponse findOrderById(Long id) {

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        return toResponse(order);
    }

    // =====================================================
    // 3. 新增採購單
    // =====================================================
    // 執行流程：
    //
    // 1. 驗證供應商存在且有效
    // 2. 建立採購單主檔
    // 3. 複製供應商當下的歷史資訊快照
    // 4. 逐筆驗證商品與訂貨數量
    // 5. 檢查同一張採購單不可重複商品
    // 6. 取得商品當下售價
    // 7. 計算每筆明細小計
    // 8. 計算採購單總金額
    // 9. 儲存採購單與所有明細
    //
    // 使用 @Transactional：
    // 主檔與明細必須一起成功，
    // 如果中途發生錯誤，整筆交易回滾。
    // =====================================================
    public PurchaseOrderResponse createOrder(
            PurchaseOrderRequest request
    ) {

        // -------------------------------------------------
        // 第一步：驗證供應商
        // -------------------------------------------------

        Supplier supplier = supplierRepository
                .findByIdAndDeletedAtIsNull(request.getSupplierId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "供應商不存在或已刪除"
                        )
                );

        // 停用供應商不可建立新的採購單
        if (!ACTIVE.equals(supplier.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "供應商已停用，無法建立採購單"
            );
        }

        // -------------------------------------------------
        // 第二步：建立採購單主檔
        // -------------------------------------------------

        PurchaseOrder order = new PurchaseOrder();

        order.setSupplier(supplier);

        // 新增採購單一律先建立為 DRAFT
        order.setOrderStatus(DRAFT);

        // 設定訂貨日期
        // 一律記錄台灣當地的訂貨時間。
        // LocalDateTime 沒有時區資訊，避免日後部署到 UTC 伺服器時日期偏移。
        order.setOrderDate(LocalDateTime.now(PurchaseOrderTimePolicy.TAIPEI));

        // 設定預計到貨日期
        order.setExpectedDeliveryDate(
                request.getExpectedDeliveryDate()
        );

        // 設定備註
        order.setRemark(request.getRemark());

        // -------------------------------------------------
        // 第三步：保存供應商歷史資訊快照
        // -------------------------------------------------
        // 必須在建立採購單當下複製資料。
        //
        // 不可等到查詢採購單時，
        // 才重新取得 Supplier 的目前資料。
        //
        // 這樣才能確保供應商後續更名、修改資料、
        // 停用或軟刪除，都不會影響舊採購單。
        // -------------------------------------------------

        order.setSupplierNameSnapshot(
                supplier.getName()
        );

        order.setSupplierContactPersonSnapshot(
                supplier.getContactPerson()
        );

        order.setSupplierPhoneSnapshot(
                supplier.getPhone()
        );

        order.setSupplierEmailSnapshot(
                supplier.getEmail()
        );

        order.setSupplierAddressSnapshot(
                supplier.getAddress()
        );

        // -------------------------------------------------
        // 第四步：準備採購商品明細
        // -------------------------------------------------

        List<PurchaseOrderItem> items = new ArrayList<>();

        // 紀錄這張採購單已經加入的商品 ID
        // 用來防止同一張採購單重複加入相同商品
        Set<Long> productIds = new HashSet<>();

        // 採購單總金額，初始值為 0
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 至少必須有一筆明細
        // Request DTO 已有 @NotEmpty，
        // Service 再補上必要的保護。
        if (request.getItems() == null
                || request.getItems().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "採購單至少需要一筆商品明細"
            );
        }

        // -------------------------------------------------
        // 第五步：逐筆處理商品明細
        // -------------------------------------------------

        for (PurchaseOrderItemRequest itemRequest
                : request.getItems()) {

            if (itemRequest == null
                    || itemRequest.getProductId() == null
                    || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購明細資料不正確"
                );
            }

            Long productId = itemRequest.getProductId();

            // 檢查同一張採購單是否重複加入商品
            if (!productIds.add(productId)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "此採購單已包含相同商品"
                );
            }

            // 查詢未軟刪除的商品
            Product product = productRepository
                    .findByIdAndDeletedAtIsNull(productId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "商品不存在或已刪除"
                            )
                    );

            // 停用商品不可新增至採購單
            if (!ACTIVE.equals(product.getStatus())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品已停用，無法加入採購單"
                );
            }

            // 取得商品目前售價
            // 新版專案尚未設計進貨成本欄位，
            // 目前暫時使用 Product.price 作為訂單價。
            BigDecimal unitPrice = product.getPrice();

            // 確認商品價格有效
            if (unitPrice == null
                    || unitPrice.compareTo(BigDecimal.ZERO) < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品價格不正確，無法建立採購明細"
                );
            }

            // 計算商品小計
            // subtotal = quantity × unitPrice
            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            // 防止商品小計超過 DECIMAL(12,2)
            if (subtotal.compareTo(MAX_TOTAL_AMOUNT) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購明細小計超過允許金額"
                );
            }

            // 建立採購明細 Entity
            PurchaseOrderItem item = new PurchaseOrderItem();

            // 設定所屬採購單
            // 維護雙向關聯的 owning side
            item.setPurchaseOrder(order);

            // 設定商品
            item.setProduct(product);

            // 設定訂貨數量
            item.setQuantity(itemRequest.getQuantity());

            // 保存訂貨當下單價
            item.setUnitPrice(unitPrice);

            // 保存商品小計
            item.setSubtotal(subtotal);

            // 加入明細集合
            items.add(item);

            // 累加採購單總金額
            totalAmount = totalAmount.add(subtotal);

            // 防止採購單總金額超過 DECIMAL(12,2)
            if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購單總金額超過允許金額"
                );
            }
        }

        // -------------------------------------------------
        // 第六步：設定採購單明細與總金額
        // -------------------------------------------------

        // 設定所有商品明細
        order.setItems(items);

        // 設定採購單總金額
        order.setTotalAmount(totalAmount);

        // -------------------------------------------------
        // 第七步：儲存採購單
        // -------------------------------------------------
        // PurchaseOrder.items 已設定：
        // CascadeType.PERSIST
        //
        // 因此儲存主檔時，
        // JPA 會一併儲存所有商品明細。
        //
        // 使用 saveAndFlush 讓 INSERT 與
        // @PrePersist 在轉換 Response 前執行。
        //
        // 若交易最後提交失敗，
        // 仍會由 @Transactional 回滾。
        // -------------------------------------------------

        PurchaseOrder savedOrder =
                purchaseOrderRepository.saveAndFlush(order);

        // 回傳完整採購單 DTO
        return toResponse(savedOrder);
    }

    // =====================================================
    // 4. 送出採購單
    // =====================================================
    // 只有 DRAFT（草稿）狀態可以送出。
    //
    // 狀態變更：
    // DRAFT → SUBMITTED
    //
    // 送出時：
    // 1. 確認採購單存在且未軟刪除
    // 2. 確認目前狀態為 DRAFT
    // 3. 確認採購單至少包含一筆商品明細
    // 4. 將狀態修改為 SUBMITTED
    // 5. 儲存並回傳完整採購單
    //
    // 注意：
    // 送出採購單不會增加商品庫存。
    // SUBMITTED 狀態的明細會計入未到貨採購量。
    //
    // 使用類別層級的 @Transactional，
    // 確保狀態更新在同一交易內完成。
    // =====================================================
    public PurchaseOrderResponse submitOrder(Long id) {

        // -------------------------------------------------
        // 第一步：查詢採購單
        // -------------------------------------------------

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        // -------------------------------------------------
        // 第二步：檢查採購單狀態
        // -------------------------------------------------
        // 只有草稿才能送出。
        //
        // 已送出、已到貨或已取消的採購單，
        // 都不可再次執行送出操作。
        // -------------------------------------------------

        if (!DRAFT.equals(order.getOrderStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 DRAFT 狀態的採購單可以送出"
            );
        }

        // -------------------------------------------------
        // 第三步：確認草稿的建立日期仍是台灣今天
        // -------------------------------------------------
        // 以伺服器時鐘判斷，不信任瀏覽器停留時間或前端日期。
        // 若在前一天建立 DRAFT，禁止直接送出，要求使用者重新下訂。
        // 此處只回傳 409，不會自動取消或刪除原始採購單。
        // 不變更商品明細、供應商快照或庫存。
        if (!PurchaseOrderTimePolicy.isDraftCreatedToday(
                order.getOrderDate(), Clock.systemUTC())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此採購草稿建立日期已過期，請取消原草稿並重新建立"
            );
        }

        // -------------------------------------------------
        // 第四步：確認至少有一筆商品明細
        // -------------------------------------------------

        if (order.getItems() == null
                || order.getItems().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "採購單沒有商品明細，無法送出"
            );
        }

        // -------------------------------------------------
        // 第五步：更新採購單狀態
        // -------------------------------------------------

        order.setOrderStatus("SUBMITTED");

        // -------------------------------------------------
        // 第六步：儲存並回傳結果
        // -------------------------------------------------
        // saveAndFlush 讓 JPA 在回傳 DTO 前
        // 執行資料更新與 @PreUpdate。
        //
        // 但 flush 不代表交易已經提交，
        // 最終仍由 @Transactional 管理。
        // -------------------------------------------------

        PurchaseOrder savedOrder =
                purchaseOrderRepository.saveAndFlush(order);

        return toResponse(savedOrder);
    }

    // =====================================================
    // 5. 確認採購單到貨並增加商品庫存
    // =====================================================
    // 只有 SUBMITTED（已送出）狀態可以確認到貨。
    //
    // 狀態變更：
    // SUBMITTED → RECEIVED
    //
    // 執行流程：
    // 1. 查詢採購單，確認存在且未軟刪除
    // 2. 確認採購單狀態為 SUBMITTED
    // 3. 確認採購單具有商品明細
    // 4. 逐筆取得商品並增加庫存
    // 5. 將採購單狀態改為 RECEIVED
    // 6. 儲存採購單並回傳完整 DTO
    //
    // 注意：
    // 商品即使後來停用，仍允許既有採購單入庫。
    // 因為採購單是在商品有效時建立的，
    // 停用不代表已訂購的商品不能入庫。
    //
    // 本方法沿用類別層級的 @Transactional。
    // 庫存更新與採購單狀態變更在同一交易內完成。
    // =====================================================
    public PurchaseOrderResponse receiveOrder(Long id) {

        // -------------------------------------------------
        // 第一步：查詢採購單
        // -------------------------------------------------

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        // -------------------------------------------------
        // 第二步：檢查採購單狀態
        // -------------------------------------------------
        // 只有 SUBMITTED 狀態可以確認到貨。
        //
        // 如果已經 RECEIVED，
        // 再次執行會得到 409，
        // 避免正常序列操作下重複增加庫存。
        // -------------------------------------------------

        if (!"SUBMITTED".equals(order.getOrderStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 SUBMITTED 狀態的採購單可以確認到貨"
            );
        }

        // -------------------------------------------------
        // 第三步：檢查商品明細
        // -------------------------------------------------

        if (order.getItems() == null
                || order.getItems().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "採購單沒有商品明細，無法確認到貨"
            );
        }

        // -------------------------------------------------
        // 第四步：逐筆增加商品庫存
        // -------------------------------------------------
        // 使用採購單原始明細數量，
        // 不重新依據現在的商品價格計算。
        //
        // 商品停用或軟刪除不會使原採購關聯消失，
        // 因此仍可完成既有採購單的入庫。
        // -------------------------------------------------

        for (PurchaseOrderItem item : order.getItems()) {

            Product product = item.getProduct();

            // 檢查明細數量是否有效
            if (item.getQuantity() == null
                    || item.getQuantity() <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購明細數量不正確，無法確認到貨"
                );
            }

            // 取得商品目前庫存
            Integer currentStock = product.getCurrentStock();

            // 防止資料庫存在不合理的庫存資料
            if (currentStock == null || currentStock < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品目前庫存資料不正確"
                );
            }

            // 使用 long 進行加總，
            // 避免 int 相加時發生整數溢位。
            long newStock =
                    (long) currentStock + item.getQuantity();

            // products.current_stock 為 INT，
            // 不可超過 Integer.MAX_VALUE。
            if (newStock > Integer.MAX_VALUE) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品庫存超過允許的最大數量"
                );
            }

            // 設定入庫後的商品庫存
            product.setCurrentStock((int) newStock);

            // product 是 JPA 管理中的 Entity，
            // @Transactional 提交時會自動更新資料庫。
            //
            // 不需要在迴圈內反覆呼叫 save()。
        }

        // -------------------------------------------------
        // 第五步：將採購單狀態改為 RECEIVED
        // -------------------------------------------------
        // 只有所有商品庫存檢查通過，
        // 才會執行到貨狀態變更。
        // -------------------------------------------------

        order.setOrderStatus("RECEIVED");

        // -------------------------------------------------
        // 第六步：儲存並回傳結果
        // -------------------------------------------------
        // saveAndFlush 讓採購單狀態更新先執行。
        //
        // 商品庫存變動與採購單狀態更新
        // 仍由同一筆 @Transactional 交易管理。
        //
        // 注意：
        // flush 不代表交易已提交。
        // -------------------------------------------------

        PurchaseOrder savedOrder =
                purchaseOrderRepository.saveAndFlush(order);

        return toResponse(savedOrder);
    }


    // =====================================================
    // 6. 取消採購單
    // =====================================================
    // 允許取消的狀態：
    // DRAFT     → CANCELLED
    // SUBMITTED → CANCELLED
    //
    // 不允許取消的狀態：
    // RECEIVED  已到貨，商品已增加庫存
    // CANCELLED 已取消，不可重複取消
    //
    // 取消採購單只會修改 orderStatus，
    // 不會刪除採購單、採購明細或供應商歷史快照。
    //
    // 取消 DRAFT：
    // 尚未送出，不影響庫存及未到貨採購量。
    //
    // 取消 SUBMITTED：
    // 不影響目前庫存。
    // 但因為狀態不再是 SUBMITTED，
    // 後續 OrderRecommendation 不會再將其計入
    // 尚未到貨的採購數量 O。
    //
    // 使用類別層級 @Transactional，
    // 確保狀態更新在同一筆交易內完成。
    // =====================================================
    public PurchaseOrderResponse cancelOrder(Long id) {

        // -------------------------------------------------
        // 第一步：查詢尚未軟刪除的採購單
        // -------------------------------------------------

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        // -------------------------------------------------
        // 第二步：取得目前採購單狀態
        // -------------------------------------------------

        String currentStatus = order.getOrderStatus();

        // -------------------------------------------------
        // 第三步：檢查是否允許取消
        // -------------------------------------------------
        // 僅允許 DRAFT 或 SUBMITTED。
        //
        // 已到貨與已取消的採購單，
        // 一律拒絕再次取消，避免資料狀態不一致。
        // -------------------------------------------------

        if (!DRAFT.equals(currentStatus)
                && !"SUBMITTED".equals(currentStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 DRAFT 或 SUBMITTED 狀態的採購單可以取消"
            );
        }

        // -------------------------------------------------
        // 第四步：更新採購單狀態
        // -------------------------------------------------

        order.setOrderStatus("CANCELLED");

        // -------------------------------------------------
        // 第五步：儲存並回傳完整採購單
        // -------------------------------------------------
        // 保留：
        // 1. 原始供應商 ID
        // 2. 五個供應商歷史資訊快照
        // 3. 所有商品明細
        // 4. 原本的採購金額
        //
        // 不修改商品庫存，也不設定 deletedAt。
        // CANCELLED 代表業務取消，
        // 與資料軟刪除是不同概念。
        // -------------------------------------------------

        PurchaseOrder savedOrder =
                purchaseOrderRepository.saveAndFlush(order);

        return toResponse(savedOrder);
    }

    // =====================================================
    // 7. 修改草稿採購單
    // =====================================================
    // 只允許修改 DRAFT 狀態的採購單。
    //
    // 本方法採用 PUT 完整更新：
    // 1. 驗證採購單狀態
    // 2. 驗證供應商
    // 3. 判斷是否需要更新供應商快照
    // 4. 驗證新的商品明細
    // 5. 重新計算每筆明細小計及總金額
    // 6. 刪除舊明細
    // 7. 儲存新明細與採購單
    //
    // 使用類別層級的 @Transactional，
    // 確保修改失敗時整筆交易回滾。
    // =====================================================
    public PurchaseOrderResponse updateOrder(
            Long id,
            PurchaseOrderUpdateRequest request
    ) {

        // -------------------------------------------------
        // 第一步：查詢採購單
        // -------------------------------------------------

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        // -------------------------------------------------
        // 第二步：檢查採購單狀態
        // -------------------------------------------------
        // 只有 DRAFT 可以修改。
        // 已送出、已到貨、已取消都不能修改。
        // -------------------------------------------------

        if (!DRAFT.equals(order.getOrderStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 DRAFT 狀態的採購單可以修改"
            );
        }

        // -------------------------------------------------
        // 第三步：驗證供應商
        // -------------------------------------------------

        if (request.getSupplierId() == null
                || request.getSupplierId() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "供應商編號不正確"
            );
        }

        Supplier supplier = supplierRepository
                .findByIdAndDeletedAtIsNull(
                        request.getSupplierId()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "供應商不存在或已刪除"
                        )
                );

        // 停用供應商不可用於修改草稿
        if (!ACTIVE.equals(supplier.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "供應商已停用，無法修改採購單"
            );
        }

        // -------------------------------------------------
        // 第四步：判斷是否更換供應商
        // -------------------------------------------------
        // 如果供應商 ID 不同：
        // 1. 更新供應商關聯
        // 2. 重新保存五個供應商歷史快照
        //
        // 如果供應商 ID 相同：
        // 保留原本的快照，不重新複製目前資料。
        //
        // 因此即使供應商修改過聯絡資訊，
        // 也不會因為編輯草稿而覆蓋原快照。
        // -------------------------------------------------

        Long originalSupplierId =
                order.getSupplier().getId();

        if (!originalSupplierId.equals(supplier.getId())) {

            order.setSupplier(supplier);

            order.setSupplierNameSnapshot(
                    supplier.getName()
            );

            order.setSupplierContactPersonSnapshot(
                    supplier.getContactPerson()
            );

            order.setSupplierPhoneSnapshot(
                    supplier.getPhone()
            );

            order.setSupplierEmailSnapshot(
                    supplier.getEmail()
            );

            order.setSupplierAddressSnapshot(
                    supplier.getAddress()
            );
        }

        // -------------------------------------------------
        // 第五步：驗證新的採購明細
        // -------------------------------------------------
        // 先確認新資料全部有效，
        // 再執行舊明細刪除與新明細儲存。
        // -------------------------------------------------

        if (request.getItems() == null
                || request.getItems().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "採購單至少需要一筆商品明細"
            );
        }

        List<PurchaseOrderItem> newItems =
                new ArrayList<>();

        Set<Long> productIds = new HashSet<>();

        BigDecimal totalAmount = BigDecimal.ZERO;

        // -------------------------------------------------
        // 第六步：逐筆處理新的商品明細
        // -------------------------------------------------

        for (PurchaseOrderItemRequest itemRequest
                : request.getItems()) {

            // 檢查明細基本資料
            if (itemRequest == null
                    || itemRequest.getProductId() == null
                    || itemRequest.getProductId() <= 0
                    || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購明細資料不正確"
                );
            }

            Long productId = itemRequest.getProductId();

            // 檢查商品是否重複
            if (!productIds.add(productId)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "此採購單已包含相同商品"
                );
            }

            // 查詢未軟刪除商品
            Product product = productRepository
                    .findByIdAndDeletedAtIsNull(productId)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "商品不存在或已刪除"
                            )
                    );

            // 停用商品不可加入新的採購明細
            if (!ACTIVE.equals(product.getStatus())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品已停用，無法加入採購單"
                );
            }

            // 取得目前商品價格
            BigDecimal unitPrice = product.getPrice();

            if (unitPrice == null
                    || unitPrice.compareTo(BigDecimal.ZERO) < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "商品價格不正確，無法建立採購明細"
                );
            }

            // 計算新的明細小計
            BigDecimal subtotal = unitPrice.multiply(
                    BigDecimal.valueOf(itemRequest.getQuantity())
            );

            // 檢查 DECIMAL(12,2) 金額上限
            if (subtotal.compareTo(MAX_TOTAL_AMOUNT) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購明細小計超過允許金額"
                );
            }

            // 建立新的採購明細
            PurchaseOrderItem item = new PurchaseOrderItem();

            item.setPurchaseOrder(order);

            item.setProduct(product);

            item.setQuantity(itemRequest.getQuantity());

            item.setUnitPrice(unitPrice);

            item.setSubtotal(subtotal);

            newItems.add(item);

            // 累加採購單總金額
            totalAmount = totalAmount.add(subtotal);

            if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "採購單總金額超過允許金額"
                );
            }
        }

        // -------------------------------------------------
        // 第七步：準備刪除原本的草稿明細
        // -------------------------------------------------
        // 複製舊明細集合，避免直接修改原集合時，
        // 造成迭代過程的 ConcurrentModificationException。
        //
        // 因為 PurchaseOrder 沒有 orphanRemoval，
        // 所以不能只使用 order.setItems(newItems)。
        // -------------------------------------------------

        List<PurchaseOrderItem> oldItems =
                new ArrayList<>(order.getItems());

        // 先清空主檔記憶體中的明細集合
        order.getItems().clear();

        // -------------------------------------------------
        // 第八步：刪除資料庫中的舊明細
        // -------------------------------------------------
        // 只有 DRAFT 才會執行此操作。
        //
        // 使用 Repository 明確刪除舊明細。
        // flush 確保舊明細 DELETE 先執行，
        // 避免新增相同商品時觸發 UNIQUE 衝突。
        //
        // 注意：
        // flush 不代表交易已提交。
        // -------------------------------------------------

        purchaseOrderItemRepository.deleteAll(oldItems);

        purchaseOrderItemRepository.flush();

        // -------------------------------------------------
        // 第九步：儲存新的採購明細
        // -------------------------------------------------
        // 每筆新明細已經設定 purchaseOrder，
        // 因此外鍵會指向原本的採購單。
        // -------------------------------------------------

        List<PurchaseOrderItem> savedItems =
                purchaseOrderItemRepository
                        .saveAllAndFlush(newItems);

        // 將新明細加入主檔的集合
        order.getItems().addAll(savedItems);

        // -------------------------------------------------
        // 第十步：更新採購單基本資料
        // -------------------------------------------------

        order.setExpectedDeliveryDate(
                request.getExpectedDeliveryDate()
        );

        order.setRemark(
                request.getRemark()
        );

        order.setTotalAmount(totalAmount);

        // 不修改 orderDate
        // 不修改 createdAt
        // 不修改 orderStatus
        // 不設定 deletedAt

        // -------------------------------------------------
        // 第十一步：儲存採購單並回傳 Response
        // -------------------------------------------------
        // saveAndFlush 讓更新先送至資料庫。
        //
        // 所有操作仍由 @Transactional 管理。
        // 若後續發生例外，整筆交易應回滾。
        // -------------------------------------------------

        PurchaseOrder savedOrder =
                purchaseOrderRepository.saveAndFlush(order);

        return toResponse(savedOrder);
    }

    // =====================================================
    // 8. 軟刪除採購單
    // =====================================================
    // 只允許下列狀態執行軟刪除：
    // DRAFT     草稿
    // CANCELLED 已取消
    //
    // 不允許：
    // SUBMITTED 已送出但尚未到貨
    // RECEIVED  已到貨並完成庫存增加
    //
    // 軟刪除時：
    // 1. 不實體刪除採購單
    // 2. 不刪除採購商品明細
    // 3. 不修改供應商歷史資訊快照
    // 4. 不修改商品庫存
    // 5. 只設定 deletedAt
    //
    // 使用類別層級 @Transactional，
    // 確保資料更新在同一筆交易內完成。
    // =====================================================
    public void deleteOrder(Long id) {

        // -------------------------------------------------
        // 第一步：查詢尚未軟刪除的採購單
        // -------------------------------------------------

        PurchaseOrder order = purchaseOrderRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "採購單不存在或已刪除"
                        )
                );

        // -------------------------------------------------
        // 第二步：取得採購單目前狀態
        // -------------------------------------------------

        String currentStatus = order.getOrderStatus();

        // -------------------------------------------------
        // 第三步：檢查是否允許軟刪除
        // -------------------------------------------------
        // SUBMITTED 必須先取消，才能軟刪除。
        // RECEIVED 已完成入庫，不允許軟刪除。
        //
        // 只有 DRAFT 或 CANCELLED 可以進行軟刪除。
        // -------------------------------------------------

        if (!DRAFT.equals(currentStatus)
                && !"CANCELLED".equals(currentStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 DRAFT 或 CANCELLED 狀態的採購單可以刪除"
            );
        }

        // -------------------------------------------------
        // 第四步：設定軟刪除時間
        // -------------------------------------------------
        // 只設定 deletedAt，不修改 orderStatus。
        //
        // @PreUpdate 會在資料更新時，
        // 自動更新 updatedAt。
        // -------------------------------------------------

        order.setDeletedAt(LocalDateTime.now());

        // -------------------------------------------------
        // 第五步：儲存採購單
        // -------------------------------------------------
        // 不呼叫 Repository.delete()，
        // 避免真正刪除資料庫中的採購單。
        //
        // 不刪除明細，也不修改庫存。
        // -------------------------------------------------

        purchaseOrderRepository.saveAndFlush(order);
    }

    // =====================================================
    // 9. 將採購單 Entity 轉成 Response DTO
    // =====================================================
    // 特別注意：
    //
    // 供應商歷史資訊必須使用 Snapshot，
    // 不可改成 supplier.getName() 等目前資料。
    //
    // 因為 Service 有 @Transactional，
    // 在交易內可以讀取 LAZY 關聯的商品與明細。
    // =====================================================
    private PurchaseOrderResponse toResponse(
            PurchaseOrder order
    ) {

        PurchaseOrderResponse response =
                new PurchaseOrderResponse();

        // 採購單 ID
        response.setId(order.getId());

        // 原始供應商 ID
        response.setSupplierId(
                order.getSupplier().getId()
        );

        // -------------------------------------------------
        // 供應商歷史快照
        // -------------------------------------------------

        response.setSupplierNameSnapshot(
                order.getSupplierNameSnapshot()
        );

        response.setSupplierContactPersonSnapshot(
                order.getSupplierContactPersonSnapshot()
        );

        response.setSupplierPhoneSnapshot(
                order.getSupplierPhoneSnapshot()
        );

        response.setSupplierEmailSnapshot(
                order.getSupplierEmailSnapshot()
        );

        response.setSupplierAddressSnapshot(
                order.getSupplierAddressSnapshot()
        );

        // -------------------------------------------------
        // 採購單基本資訊
        // -------------------------------------------------

        response.setOrderDate(
                order.getOrderDate()
        );

        response.setExpectedDeliveryDate(
                order.getExpectedDeliveryDate()
        );

        response.setOrderStatus(
                order.getOrderStatus()
        );

        response.setTotalAmount(
                order.getTotalAmount()
        );

        response.setRemark(
                order.getRemark()
        );

        response.setCreatedAt(
                order.getCreatedAt()
        );

        response.setUpdatedAt(
                order.getUpdatedAt()
        );

        // -------------------------------------------------
        // 轉換所有採購商品明細
        // -------------------------------------------------

        List<PurchaseOrderItemResponse> itemResponses =
                order.getItems()
                        .stream()
                        .map(this::toItemResponse)
                        .toList();

        response.setItems(itemResponses);

        return response;
    }

    // =====================================================
    // 10. 將採購明細 Entity 轉成 Response DTO
    // =====================================================
    // 單價及小計使用當時儲存的資料。
    //
    // 商品名稱與 SKU 目前從 Product 取得。
    // 尚未建立商品名稱歷史快照功能。
    // =====================================================
    private PurchaseOrderItemResponse toItemResponse(
            PurchaseOrderItem item
    ) {

        PurchaseOrderItemResponse response =
                new PurchaseOrderItemResponse();

        response.setId(
                item.getId()
        );

        response.setProductId(
                item.getProduct().getId()
        );

        response.setSku(
                item.getProduct().getSku()
        );

        response.setProductName(
                item.getProduct().getName()
        );

        response.setQuantity(
                item.getQuantity()
        );

        response.setUnitPrice(
                item.getUnitPrice()
        );

        response.setSubtotal(
                item.getSubtotal()
        );

        return response;
    }
}
