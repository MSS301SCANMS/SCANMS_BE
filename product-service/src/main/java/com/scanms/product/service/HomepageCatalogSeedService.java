package com.scanms.product.service;

public interface HomepageCatalogSeedService {
    record Result(boolean seeded, int products, int variants, int images) { }

    Result seedIfEmpty();
}
