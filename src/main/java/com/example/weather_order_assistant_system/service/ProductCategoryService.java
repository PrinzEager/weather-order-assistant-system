package com.example.weather_order_assistant_system.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.ProductCategory;
import com.example.weather_order_assistant_system.repository.ProductCategoryRepository;
import com.example.weather_order_assistant_system.request.ProductCategoryRequest;
import com.example.weather_order_assistant_system.response.ProductCategoryResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    // 商品分類啟用狀態
    private static final Integer ACTIVE = 1;

    // 商品分類停用狀態
    private static final Integer INACTIVE = 0;

    // 商品分類 Repository
    private final ProductCategoryRepository categoryRepository;

    /**
     * 查詢所有啟用且尚未軟刪除的商品分類
     */
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> findAllCategories() {

        return categoryRepository
                .findByStatusAndDeletedAtIsNullOrderByIdAsc(ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 根據分類 ID 查詢單一商品分類
     */
    @Transactional(readOnly = true)
    public ProductCategoryResponse findCategoryById(Long id) {

        // 確認商品分類存在且尚未軟刪除
        ProductCategory category = findCategory(id);

        return toResponse(category);
    }

    /**
     * 新增商品分類
     */
    @Transactional
    public ProductCategoryResponse createCategory(
            ProductCategoryRequest request) {

        // 檢查同一層級是否已有相同名稱的分類
        checkDuplicateName(
                request.getName(),
                request.getParentId(),
                null
        );

        // 建立商品分類 Entity
        ProductCategory category = new ProductCategory();

        // 設定分類名稱
        category.setName(request.getName());

        // 有傳入 parentId，代表建立第二層分類
        if (request.getParentId() != null) {

            // 驗證上層分類是否有效
            ProductCategory parent =
                    getValidParent(request.getParentId());

            category.setParent(parent);
        }

        // 新增商品分類時，預設為啟用
        category.setStatus(ACTIVE);

        // 儲存商品分類
        ProductCategory savedCategory =
                categoryRepository.save(category);

        return toResponse(savedCategory);
    }

    /**
     * 修改商品分類
     */
    @Transactional
    public ProductCategoryResponse updateCategory(
            Long id,
            ProductCategoryRequest request) {

        // 查詢目前要修改的商品分類
        ProductCategory category = findCategory(id);

        // 預設為第一層分類
        ProductCategory newParent = null;

        // 有傳入 parentId，代表修改為第二層分類
        if (request.getParentId() != null) {

            // 不允許將自己設定為自己的上層分類
            if (request.getParentId().equals(id)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "不可將自己或自己的下層分類設為上層分類"
                );
            }

            // 驗證新的上層分類存在、啟用且為第一層分類
            newParent = getValidParent(request.getParentId());
        }

        // 如果目前分類仍有子分類
        // 就不能將它移動到第二層，避免產生第三層分類
        if (newParent != null
                && categoryRepository
                        .existsByParent_IdAndDeletedAtIsNull(id)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "此分類仍有下層分類，無法移至第二層"
            );
        }

        // 檢查修改後的分類名稱是否重複
        // 排除目前正在修改的分類 ID
        checkDuplicateName(
                request.getName(),
                request.getParentId(),
                id
        );

        // 更新分類名稱及上層分類
        category.setName(request.getName());
        category.setParent(newParent);

        // 儲存修改結果
        ProductCategory updatedCategory =
                categoryRepository.save(category);

        return toResponse(updatedCategory);
    }

    /**
     * 軟刪除商品分類
     */
    @Transactional
    public void deleteCategory(Long id) {

        // 查詢要刪除的商品分類
        ProductCategory category = findCategory(id);

        // 如果分類仍有尚未軟刪除的子分類
        // 則不允許刪除
        if (categoryRepository
                .existsByParent_IdAndDeletedAtIsNull(id)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類仍有下層分類，無法刪除"
            );
        }

        // 如果分類仍有尚未軟刪除的商品使用
        // 則不允許刪除
        if (categoryRepository
                .countActiveProductsByCategoryId(id) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類仍有商品使用，無法刪除"
            );
        }

        // 設定軟刪除時間
        // 不實際刪除資料庫紀錄
        category.setDeletedAt(LocalDateTime.now());

        // 儲存軟刪除結果
        categoryRepository.save(category);
    }

    /**
     * 根據 ID 查詢尚未軟刪除的商品分類
     */
    private ProductCategory findCategory(Long id) {

        return categoryRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品分類不存在或已刪除"
                ));
    }

    /**
     * 驗證上層分類是否有效
     */
    private ProductCategory getValidParent(Long parentId) {

        // 確認上層分類存在且尚未軟刪除
        ProductCategory parent = findCategory(parentId);

        // 上層分類必須處於啟用狀態
        if (!ACTIVE.equals(parent.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "上層分類已停用，無法使用"
            );
        }

        // 上層分類本身不可再有上層分類
        // 避免建立第三層商品分類
        if (parent.getParent() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只能選擇第一層分類作為上層分類，不允許建立第三層分類"
            );
        }

        return parent;
    }

    /**
     * 檢查同一層級是否有重複的商品分類名稱
     *
     * @param name      商品分類名稱
     * @param parentId  上層分類 ID
     * @param excludeId 修改時需要排除的分類 ID
     */
    private void checkDuplicateName(
            String name,
            Long parentId,
            Long excludeId) {

        boolean duplicated;

        // 第一層分類名稱檢查
        if (parentId == null) {

            if (excludeId == null) {

                // 新增第一層分類
                duplicated = categoryRepository
                        .existsByNameAndParentIsNullAndDeletedAtIsNull(
                                name
                        );

            } else {

                // 修改第一層分類，排除目前分類
                duplicated = categoryRepository
                        .existsByNameAndParentIsNullAndDeletedAtIsNullAndIdNot(
                                name,
                                excludeId
                        );
            }

        } else {

            // 第二層分類名稱檢查
            if (excludeId == null) {

                // 新增第二層分類
                duplicated = categoryRepository
                        .existsByNameAndParent_IdAndDeletedAtIsNull(
                                name,
                                parentId
                        );

            } else {

                // 修改第二層分類，排除目前分類
                duplicated = categoryRepository
                        .existsByNameAndParent_IdAndDeletedAtIsNullAndIdNot(
                                name,
                                parentId,
                                excludeId
                        );
            }
        }

        // 發現重複名稱時，回傳 HTTP 409
        if (duplicated) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類名稱已存在"
            );
        }
    }

    /**
     * 將 ProductCategory Entity 轉換成 Response DTO
     */
    private ProductCategoryResponse toResponse(
            ProductCategory category) {

        // 第一層分類的上層資訊預設為 null
        Long parentId = null;
        String parentName = null;

        // 第二層分類需要回傳上層分類資訊
        if (category.getParent() != null) {

            parentId = category.getParent().getId();
            parentName = category.getParent().getName();
        }

        return new ProductCategoryResponse(
                category.getId(),
                category.getName(),
                parentId,
                parentName,
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
