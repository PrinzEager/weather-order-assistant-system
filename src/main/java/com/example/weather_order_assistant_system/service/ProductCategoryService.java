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

    // 分類狀態：1 代表啟用
    private static final Integer ACTIVE = 1;

    // 分類狀態：0 代表停用
    private static final Integer INACTIVE = 0;

    private final ProductCategoryRepository categoryRepository;

    /**
     * 查詢所有啟用且尚未刪除的分類
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
     * 根據 ID 查詢單一分類
     */
    @Transactional(readOnly = true)
    public ProductCategoryResponse findCategoryById(Long id) {

        ProductCategory category = findCategory(id);

        return toResponse(category);
    }

    /**
     * 新增商品分類
     */
    @Transactional
    public ProductCategoryResponse createCategory(ProductCategoryRequest request) {

        // 檢查分類名稱是否重複
        checkDuplicateName(request.getName(), request.getParentId(), null);

        ProductCategory category = new ProductCategory();

        category.setName(request.getName());

        // 有傳入 parentId 代表建立第二層分類
        if (request.getParentId() != null) {

            // 確認上層分類有效，且只能選擇第一層分類
            ProductCategory parent = getValidParent(request.getParentId());

            category.setParent(parent);
        }

        category.setStatus(ACTIVE);

        ProductCategory savedCategory = categoryRepository.save(category);

        return toResponse(savedCategory);
    }

    /**
     * 修改商品分類
     */
    @Transactional
    public ProductCategoryResponse updateCategory(
            Long id,
            ProductCategoryRequest request) {

        // 查詢目前要修改的分類
        ProductCategory category = findCategory(id);

        // 取得新的上層分類
        ProductCategory newParent = null;

        if (request.getParentId() != null) {

            // 不能將自己設為自己的上層分類
            if (request.getParentId().equals(id)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "不可將自己或自己的下層分類設為上層分類");
            }

            // 確認新的上層分類存在、啟用且為第一層
            newParent = getValidParent(request.getParentId());
        }

        // 如果目前分類底下還有子分類，就不能將它移到第二層
        if (newParent != null
                && categoryRepository.existsByParent_IdAndDeletedAtIsNull(id)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "此分類仍有下層分類，無法移至第二層");
        }

        // 檢查修改後的分類名稱是否重複
        checkDuplicateName(request.getName(), request.getParentId(), id);

        category.setName(request.getName());
        category.setParent(newParent);

        ProductCategory updatedCategory = categoryRepository.save(category);

        return toResponse(updatedCategory);
    }

    /**
     * 軟刪除商品分類
     */
    @Transactional
    public void deleteCategory(Long id) {

        // 查詢要刪除的分類
        ProductCategory category = findCategory(id);

        // 有尚未刪除的子分類時，不允許刪除
        if (categoryRepository.existsByParent_IdAndDeletedAtIsNull(id)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類仍有下層分類，無法刪除");
        }

        // 確認是否仍有商品使用此分類
        if (categoryRepository.countActiveProductsByCategoryId(id) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類仍有商品使用，無法刪除");
        }

        // 設定軟刪除時間，不直接刪除資料庫紀錄
        category.setDeletedAt(LocalDateTime.now());

        categoryRepository.save(category);
    }

    /**
     * 根據 ID 查詢尚未刪除的分類
     */
    private ProductCategory findCategory(Long id) {

        return categoryRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "商品分類不存在或已刪除"));
    }

    /**
     * 驗證上層分類是否有效
     */
    private ProductCategory getValidParent(Long parentId) {

        ProductCategory parent = findCategory(parentId);

        // 上層分類必須是啟用狀態
        if (!ACTIVE.equals(parent.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "上層分類已停用，無法使用");
        }

        // 上層分類本身不能再有上層分類，避免建立第三層
        if (parent.getParent() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "只能選擇第一層分類作為上層分類，不允許建立第三層分類");
        }

        return parent;
    }

    /**
     * 檢查同一層級是否有重複的分類名稱
     */
    private void checkDuplicateName(
            String name,
            Long parentId,
            Long excludeId) {

        boolean duplicated;

        if (parentId == null) {

            // 第一層分類：檢查其他第一層分類是否有相同名稱
            if (excludeId == null) {

                duplicated = categoryRepository
                        .existsByNameAndParentIsNullAndDeletedAtIsNull(name);

            } else {

                duplicated = categoryRepository
                        .existsByNameAndParentIsNullAndDeletedAtIsNullAndIdNot(
                                name, excludeId);
            }

        } else {

            // 第二層分類：檢查相同上層分類底下是否有同名分類
            if (excludeId == null) {

                duplicated = categoryRepository
                        .existsByNameAndParent_IdAndDeletedAtIsNull(name, parentId);

            } else {

                duplicated = categoryRepository
                        .existsByNameAndParent_IdAndDeletedAtIsNullAndIdNot(
                                name, parentId, excludeId);
            }
        }

        if (duplicated) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此分類名稱已存在");
        }
    }

    /**
     * 將 Entity 轉換成 Response DTO
     */
    private ProductCategoryResponse toResponse(ProductCategory category) {

        Long parentId = null;
        String parentName = null;

        // 如果是第二層分類，取得上層分類資訊
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
                category.getUpdatedAt());
    }
}