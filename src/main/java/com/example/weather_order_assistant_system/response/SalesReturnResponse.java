package com.example.weather_order_assistant_system.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SalesReturnResponse {

    // 退貨明細資訊與退貨後累計狀態。
    private Long id;
    private Long salesRecordId;
    private Integer quantity;
    private BigDecimal returnAmount;
    private String reason;
    private LocalDateTime returnedAt;
    private Integer returnedQuantity;
    private Integer remainingReturnableQuantity;
}
