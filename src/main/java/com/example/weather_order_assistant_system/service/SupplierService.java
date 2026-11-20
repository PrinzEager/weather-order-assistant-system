package com.example.weather_order_assistant_system.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.weather_order_assistant_system.entity.Supplier;
import com.example.weather_order_assistant_system.repository.SupplierRepository;
import com.example.weather_order_assistant_system.request.SupplierRequest;
import com.example.weather_order_assistant_system.response.SupplierResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierService {

    // 供應商 Repository，負責資料庫操作
    private final SupplierRepository supplierRepository;

    // 供應商啟用與停用狀態
    private static final Integer ACTIVE = 1;
    private static final Integer INACTIVE = 0;

    // 查詢所有啟用且未軟刪除的供應商
    public List<SupplierResponse> findAllSuppliers() {

        return supplierRepository
                .findByStatusAndDeletedAtIsNullOrderByIdAsc(ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 依照 ID 查詢單筆未軟刪除的供應商
    // 停用但未刪除的供應商仍允許查詢
    public SupplierResponse findSupplierById(Long id) {

        Supplier supplier = getExistingSupplier(id);

        return toResponse(supplier);
    }

    // 新增供應商
    public SupplierResponse createSupplier(SupplierRequest request) {

        // 整理 Email，空白字串轉為 null
        String email = normalizeEmail(request.getEmail());

        // 確認 Email 未被其他供應商使用
        if (email != null && supplierRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此電子郵件已被其他供應商使用"
            );
        }

        Supplier supplier = new Supplier();

        // 設定供應商基本資料
        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(email);
        supplier.setAddress(request.getAddress());

        // 新增供應商預設啟用
        supplier.setStatus(ACTIVE);

        // 儲存供應商
        Supplier savedSupplier = supplierRepository.save(supplier);

        return toResponse(savedSupplier);
    }

    // 修改供應商基本資料
    // 不透過此方法修改啟用或停用狀態
    public SupplierResponse updateSupplier(
            Long id,
            SupplierRequest request
    ) {

        Supplier supplier = getExistingSupplier(id);

        // 整理 Email
        String email = normalizeEmail(request.getEmail());

        // 排除目前供應商，檢查 Email 是否與其他資料重複
        if (email != null
                && supplierRepository.existsByEmailAndIdNot(email, id)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "此電子郵件已被其他供應商使用"
            );
        }

        // 更新供應商基本資料
        supplier.setName(request.getName().trim());
        supplier.setContactPerson(request.getContactPerson());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(email);
        supplier.setAddress(request.getAddress());

        // 不變更 status 與 deletedAt
        Supplier updatedSupplier = supplierRepository.save(supplier);

        return toResponse(updatedSupplier);
    }

    // 修改供應商啟用或停用狀態
    public SupplierResponse updateSupplierStatus(
            Long id,
            Integer status
    ) {

        // 只允許狀態 1 或 0
        if (!ACTIVE.equals(status) && !INACTIVE.equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "供應商狀態只能是 1（啟用）或 0（停用）"
            );
        }

        Supplier supplier = getExistingSupplier(id);

        // 若狀態相同，不需要重複更新
        if (status.equals(supplier.getStatus())) {
            return toResponse(supplier);
        }

        supplier.setStatus(status);

        Supplier updatedSupplier = supplierRepository.save(supplier);

        return toResponse(updatedSupplier);
    }

    // 軟刪除供應商
    // 保留資料庫紀錄，不執行實體刪除
    public void deleteSupplier(Long id) {

        Supplier supplier = getExistingSupplier(id);

        // 設定軟刪除時間
        supplier.setDeletedAt(LocalDateTime.now());

        supplierRepository.save(supplier);
    }

    // 取得存在且尚未軟刪除的供應商 Entity
    // 供查詢、修改、狀態更新及刪除共用
    private Supplier getExistingSupplier(Long id) {

        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "供應商不存在或已刪除"
                ));
    }

    // 整理電子郵件
    // null 或空白字串轉為 null
    // 其他內容去除前後空白
    private String normalizeEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return email.trim();
    }

    // 將 Supplier Entity 轉換成 SupplierResponse DTO
    private SupplierResponse toResponse(Supplier supplier) {

        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getStatus(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
