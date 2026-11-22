package com.example.weather_order_assistant_system.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Product;
import com.example.weather_order_assistant_system.entity.ProductCategory;
import com.example.weather_order_assistant_system.repository.ProductCategoryRepository;
import com.example.weather_order_assistant_system.repository.ProductRepository;
import com.example.weather_order_assistant_system.request.ProductRequest;
import com.example.weather_order_assistant_system.response.ProductResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    // 商品 Repository：負責存取商品資料
    private final ProductRepository productRepository;

    // 商品分類 Repository：用於檢查商品所屬分類是否有效
    private final ProductCategoryRepository productCategoryRepository;

    // 商品啟用狀態
    private static final Integer ACTIVE = 1;

    // 商品停用狀態
    private static final Integer INACTIVE = 0;

    /*
     * 查詢所有啟用且尚未軟刪除的商品
     *
     * 查詢條件：
     * 1. status = 1
     * 2. deleted_at IS NULL
     * 3. 按商品 ID 由小到大排序
     */
    public List<ProductResponse> findAllProducts() {

        List<Product> products =
                productRepository
                        .findByStatusAndDeletedAtIsNullOrderByIdAsc(ACTIVE);

        return products.stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * 根據商品 ID 查詢單筆商品
     *
     * 如果商品不存在或已軟刪除，
     * 回傳 HTTP 404。
     *
     * 此方法目前允許查詢停用商品，
     * 方便後續商品管理功能使用。
     */
    public ProductResponse findProductById(Long id) {

        Product product = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        return toResponse(product);
    }

    /*
     * 新增商品
     *
     * 商業規則：
     * 1. SKU 不可重複
     * 2. 商品分類必須存在、啟用且未刪除
     * 3. 商品只能選擇第二層分類
     * 4. 商品價格不可超過資料庫欄位範圍
     * 5. 自動設定預設單位、庫存與啟用狀態
     */
    public ProductResponse createProduct(ProductRequest request) {

        // 1. 檢查 SKU 是否已被使用
        // 包含停用及已軟刪除的商品
        if (productRepository.existsBySku(request.getSku())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此商品代碼已存在"
            );
        }

        // 2. 查詢商品分類
        // 如果分類不存在或已軟刪除，回傳 404
        ProductCategory category = productCategoryRepository
                .findByIdAndDeletedAtIsNull(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品分類不存在或已刪除"
                ));

        // 3. 檢查商品分類是否啟用
        if (!ACTIVE.equals(category.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品分類已停用，無法使用"
            );
        }

        // 4. 檢查是否為第二層分類
        // 第一層分類的 parent 為 null
        if (category.getParent() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品只能選擇第二層分類"
            );
        }

        // 5. 檢查商品價格是否符合 DECIMAL(10,2)
        // 最高允許 99999999.99
        BigDecimal price = request.getPrice();

        if (price.scale() > 2 ||
                price.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品售價最多為 8 位整數及 2 位小數"
            );
        }

        // 6. 建立商品 Entity
        Product product = new Product();

        product.setCategory(category);
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setPrice(price);

        // 7. 設定商品單位
        // 未提供或輸入空白時，預設為「個」
        String unit = request.getUnit();

        if (unit == null || unit.isBlank()) {
            product.setUnit("個");
        } else {
            product.setUnit(unit);
        }

        // 8. 設定目前庫存
        // 未提供時，預設為 0
        product.setCurrentStock(
                request.getCurrentStock() == null
                        ? 0
                        : request.getCurrentStock()
        );

        // 9. 設定安全庫存
        // 未提供時，預設為 0
        product.setSafetyStock(
                request.getSafetyStock() == null
                        ? 0
                        : request.getSafetyStock()
        );

        // 10. 新增商品預設為啟用
        product.setStatus(ACTIVE);

        // 11. 將商品儲存至資料庫
        Product savedProduct = productRepository.save(product);

        // 12. 將儲存後的 Entity 轉換為 Response DTO
        return toResponse(savedProduct);
    }

    /*
     * 修改商品資料
     *
     * 商業規則：
     * 1. 商品必須存在且尚未軟刪除
     * 2. SKU 不可與其他商品重複
     * 3. 商品分類必須存在、啟用且未刪除
     * 4. 商品只能選擇第二層分類
     * 5. 商品價格必須符合資料庫欄位限制
     */
    public ProductResponse updateProduct(
            Long id,
            ProductRequest request
    ) {

        // 1. 查詢要修改的商品
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        // 2. 檢查 SKU 是否被其他商品使用
        // 排除目前正在修改的商品 ID
        if (productRepository.existsBySkuAndIdNot(
                request.getSku(), id)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此商品代碼已存在"
            );
        }

        // 3. 查詢新的商品分類
        ProductCategory category = productCategoryRepository
                .findByIdAndDeletedAtIsNull(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品分類不存在或已刪除"
                ));

        // 4. 檢查商品分類是否啟用
        if (!ACTIVE.equals(category.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品分類已停用，無法使用"
            );
        }

        // 5. 檢查是否為第二層分類
        if (category.getParent() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品只能選擇第二層分類"
            );
        }

        // 6. 檢查商品價格是否符合 DECIMAL(10,2)
        BigDecimal price = request.getPrice();

        if (price.scale() > 2 ||
                price.compareTo(new BigDecimal("99999999.99")) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品售價最多為 8 位整數及 2 位小數"
            );
        }

        // 7. 更新商品基本資料
        product.setCategory(category);
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setPrice(price);

        // 8. 更新商品單位
        // 未提供或輸入空白時，使用預設值「個」
        String unit = request.getUnit();

        if (unit == null || unit.isBlank()) {
            product.setUnit("個");
        } else {
            product.setUnit(unit);
        }

        // 9. 更新目前庫存
        // 未提供時，預設為 0
        product.setCurrentStock(
                request.getCurrentStock() == null
                        ? 0
                        : request.getCurrentStock()
        );

        // 10. 更新安全庫存
        // 未提供時，預設為 0
        product.setSafetyStock(
                request.getSafetyStock() == null
                        ? 0
                        : request.getSafetyStock()
        );

        // 11. 儲存修改後的商品
        // updatedAt 會由 Product.java 的 @PreUpdate 更新
        Product savedProduct = productRepository.save(product);

        // 12. 回傳修改後的商品資料
        return toResponse(savedProduct);
    }

    /*
     * 修改商品啟用／停用狀態
     *
     * 商業規則：
     * 1. 商品必須存在且尚未軟刪除
     * 2. status 只能是 1（啟用）或 0（停用）
     * 3. 不允許修改已軟刪除商品的狀態
     * 4. 更新成功後回傳商品資料
     */
    public ProductResponse updateProductStatus(
            Long id,
            Integer status
    ) {

        // 1. 檢查傳入的商品狀態是否合法
        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "商品狀態只能是 1（啟用）或 0（停用）"
            );
        }

        // 2. 查詢要修改狀態的商品
        // 已軟刪除商品不允許修改狀態
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        // 3. 如果商品目前狀態與要求相同
        // 不需要重複更新資料庫
        if (status.equals(product.getStatus())) {
            return toResponse(product);
        }

        // 4. 更新商品狀態
        product.setStatus(status);

        // 5. 儲存商品
        // updatedAt 會由 Product.java 的 @PreUpdate 更新
        Product savedProduct = productRepository.save(product);

        // 6. 回傳更新後的商品資料
        return toResponse(savedProduct);
    }

    /*
     * 軟刪除商品
     *
     * 商業規則：
     * 1. 商品必須存在且尚未軟刪除
     * 2. 停用中的商品不可刪除
     * 3. 不使用真正的 DELETE SQL 刪除資料
     * 4. 設定 deletedAt，保留歷史商品資料
     */
    public void deleteProduct(Long id) {

        // 1. 查詢要刪除的商品
        // 商品不存在或已軟刪除時，回傳 HTTP 404
        Product product = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品不存在或已刪除"
                ));

        // 2. 檢查商品是否已停用
        // 依照專案規則，停用商品不可刪除
        if (INACTIVE.equals(product.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "停用商品不可刪除，請確認商品狀態"
            );
        }

        // 3. 設定軟刪除時間
        // 不會真正移除 MySQL 中的商品資料
        product.setDeletedAt(LocalDateTime.now());

        // 4. 儲存修改後的商品
        // updatedAt 也會由 @PreUpdate 自動更新
        productRepository.save(product);
    }

    /*
     * 將 Product Entity 轉換成 ProductResponse DTO
     *
     * 不直接回傳 Entity，
     * 避免暴露不必要的資料庫欄位及 JPA 關聯。
     */
    private ProductResponse toResponse(Product product) {

        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getSku(),
                product.getName(),
                product.getPrice(),
                product.getUnit(),
                product.getCurrentStock(),
                product.getSafetyStock(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }



}
