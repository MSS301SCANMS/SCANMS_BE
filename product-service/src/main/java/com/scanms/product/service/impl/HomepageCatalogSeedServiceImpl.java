package com.scanms.product.service.impl;

import com.scanms.product.service.HomepageCatalogSeedService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomepageCatalogSeedServiceImpl implements HomepageCatalogSeedService {
    private final JdbcTemplate jdbc;

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private boolean empty() {
        return count("stores") == 0 && count("product_categories") == 0 && count("products") == 0
                && count("product_variants") == 0 && count("product_images") == 0;
    }

    @Override
    @Transactional
    public Result seedIfEmpty() {
        if (!empty()) return new Result(false, count("products"), count("product_variants"), count("product_images"));
        // Serialize initialisation when several instances connect to a fresh DB.
        jdbc.execute("SELECT pg_advisory_xact_lock(624302)");
        if (!empty()) return new Result(false, count("products"), count("product_variants"), count("product_images"));
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ClassPathResource("seed/homepage-catalog.sql"), StandardCharsets.UTF_8));
            return null;
        });
        var result = new Result(true, count("products"), count("product_variants"), count("product_images"));
        if (count("stores") != 1 || count("product_categories") != 6 || result.products() != 24 || result.variants() != 96 || result.images() != 69)
            throw new IllegalStateException("Homepage catalog seed is incomplete; transaction rolled back");
        return result;
    }
}
