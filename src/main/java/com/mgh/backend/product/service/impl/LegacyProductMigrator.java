package com.mgh.backend.product.service.impl;

import com.mgh.backend.product.entity.Product;
import com.mgh.backend.product.entity.ProductBarcode;
import com.mgh.backend.product.entity.ProductStatus;
import com.mgh.backend.product.entity.ProductType;
import com.mgh.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LegacyProductMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyProductMigrator.class);

    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!hasLegacyPriceColumn()) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.id, p.code, p.name, p.price, p.stock
                FROM cashier_products p
                WHERE NOT EXISTS (
                    SELECT 1 FROM product_barcodes b WHERE b.product_id = p.id
                )
                """);

        if (rows.isEmpty()) {
            return;
        }

        log.info("Migrating {} legacy product row(s) to barcode model", rows.size());

        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Product product = productRepository.findById(id).orElse(null);
            if (product == null) {
                continue;
            }

            if (product.getBaseName() == null) {
                product.setBaseName((String) row.get("name"));
            }
            if (product.getType() == null) {
                product.setType(ProductType.INVENTORY);
            }
            if (product.getStatus() == null) {
                product.setStatus(ProductStatus.ACTIVE);
            }

            BigDecimal price = row.get("price") instanceof BigDecimal decimal
                    ? decimal
                    : BigDecimal.ZERO;
            int stock = row.get("stock") instanceof Number number ? number.intValue() : 0;
            String code = (String) row.get("code");

            ProductBarcode barcode = ProductBarcode.builder()
                    .product(product)
                    .barcode(code)
                    .sellingPrice(price)
                    .buyingPrice(price)
                    .stock(stock)
                    .isDefault(true)
                    .build();
            product.getBarcodes().add(barcode);
            productRepository.save(product);
        }
    }

    private boolean hasLegacyPriceColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'cashier_products' AND column_name = 'price'
                """, Integer.class);
        return count != null && count > 0;
    }
}
