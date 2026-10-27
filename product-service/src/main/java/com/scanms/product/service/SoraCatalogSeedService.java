package com.scanms.product.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SoraCatalogSeedService {
    private final JdbcTemplate jdbc;
    @Value("${scanms.catalog.sora-owner-user-id:3ecf64b8-5c15-4f74-9690-57707ada1104}")
    private String ownerId;

    @Transactional
    public void seed() throws java.io.IOException {
        UUID.fromString(ownerId); // Owner override is an ID, never executable SQL.
        jdbc.execute("SELECT pg_advisory_xact_lock(624302)");
        Integer matches = jdbc.queryForObject("SELECT count(*) FROM stores WHERE lower(trim(name)) = 'sora skin'", Integer.class);
        if (matches != null && matches > 1) throw new IllegalStateException("Multiple Sora Skin stores; select the intended shop before seeding");
        String sql = new ClassPathResource("seed/sora-clothing.sql").getContentAsString(StandardCharsets.UTF_8)
                .replace("${soraOwnerId}", ownerId);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            return null;
        });
    }
}
