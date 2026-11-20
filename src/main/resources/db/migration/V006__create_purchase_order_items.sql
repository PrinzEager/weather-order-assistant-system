-- 訂貨單商品明細：每張訂貨單的同一商品僅允許一筆明細
CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '訂貨明細編號',
    purchase_order_id BIGINT NOT NULL COMMENT '訂貨單編號',
    product_id BIGINT NOT NULL COMMENT '商品編號',
    quantity INT NOT NULL COMMENT '訂貨數量',
    unit_price DECIMAL(10, 2) NOT NULL COMMENT '訂貨當下單價',
    subtotal DECIMAL(12, 2) NOT NULL COMMENT '商品小計',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建立時間',
    PRIMARY KEY (id),
    CONSTRAINT fk_poi_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_poi_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uq_poi_order_product UNIQUE (purchase_order_id, product_id),
    INDEX idx_poi_product (product_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='訂貨單商品明細表';
