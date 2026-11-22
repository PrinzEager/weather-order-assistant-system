package com.example.weather_order_assistant_system.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.weather_order_assistant_system.response.ManagementProductResponse;
import com.example.weather_order_assistant_system.response.ManagementSupplierResponse;
import com.example.weather_order_assistant_system.service.ManagementStatusService;

import lombok.RequiredArgsConstructor;

/**
 * 後台專用管理清單 API。
 *
 * 這裡只新增讀取介面，啟用／停用操作繼續使用現有的
 * PATCH /api/products/{id}/status?status=0|1
 * PATCH /api/suppliers/{id}/status?status=0|1
 */
@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class ManagementStatusController {

    private final ManagementStatusService managementStatusService;

    // all：全部未刪除資料；active：啟用；inactive：停用。
    @GetMapping("/products")
    public List<ManagementProductResponse> findProducts(
            @RequestParam(defaultValue = "all") String status
    ) {
        return managementStatusService.findProducts(status);
    }

    @GetMapping("/suppliers")
    public List<ManagementSupplierResponse> findSuppliers(
            @RequestParam(defaultValue = "all") String status
    ) {
        return managementStatusService.findSuppliers(status);
    }
}
