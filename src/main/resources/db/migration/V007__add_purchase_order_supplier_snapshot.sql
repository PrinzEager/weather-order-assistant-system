-- =====================================================
-- 採購單：新增供應商歷史資訊快照
--
-- 目的：
-- 1. 保留下單當時的供應商資料
-- 2. 避免供應商日後修改資料影響歷史採購單
-- 3. 供應商停用或軟刪除後，歷史訂單仍可查詢
--
-- 注意：
-- supplier_id 外鍵繼續保留
-- 不修改原有的 Flyway Migration
-- =====================================================

ALTER TABLE purchase_orders

    ADD COLUMN supplier_name_snapshot VARCHAR(100) NULL
        COMMENT '下單當時的供應商名稱',

    ADD COLUMN supplier_contact_person_snapshot VARCHAR(50) NULL
        COMMENT '下單當時的供應商聯絡人',

    ADD COLUMN supplier_phone_snapshot VARCHAR(30) NULL
        COMMENT '下單當時的供應商電話',

    ADD COLUMN supplier_email_snapshot VARCHAR(100) NULL
        COMMENT '下單當時的供應商電子郵件',

    ADD COLUMN supplier_address_snapshot VARCHAR(255) NULL
        COMMENT '下單當時的供應商地址';
