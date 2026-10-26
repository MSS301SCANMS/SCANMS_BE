package com.scanms.product.config;

import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import com.scanms.product.entity.ProductImage;
import com.scanms.product.entity.ProductVariant;

/** Gắn vào {@link Store} và {@link Product}: mọi thay đổi đều xoá cache catalog liên quan (sau commit). */
public class CatalogEntityListener {

    @PostPersist
    @PostUpdate
    @PostRemove
    public void onChange(Object entity) {
        if (entity instanceof Store) {
            CatalogCacheInvalidator.storeChanged();
        } else if (entity instanceof Product || entity instanceof ProductVariant
                || entity instanceof ProductImage) {
            CatalogCacheInvalidator.productChanged();
        }
    }
}
