package com.scanms.ops;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.core.KafkaAdmin;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"})
@ConditionalOnProperty(prefix = "scanms.ops", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OpsProperties.class)
public class OpsAutoConfiguration {

    @Bean
    LogCapture scanmsLogCapture(OpsProperties props) {
        return new LogCapture(props);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
    static class ServletAccessConfiguration {
        @Bean
        @ConditionalOnMissingBean(OpsAccess.class)
        OpsAccess scanmsOpsAccess(OpsProperties props) {
            return new ServletOpsAccess(props);
        }
    }

    /**
     * Ứng dụng không phải Servlet (Gateway dùng WebFlux) không đọc được bối cảnh bảo mật theo cách này, nên
     * việc kiểm tra role phải đặt ở chuỗi bảo mật của chính ứng dụng đó.
     */
    @Bean
    @ConditionalOnMissingBean(OpsAccess.class)
    OpsAccess scanmsOpsPermitAll() {
        return () -> OpsAccess.Decision.ALLOWED;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DataSource.class)
    static class DatabaseConfiguration {
        @Bean
        @ConditionalOnBean(DataSource.class)
        DependencyCheck scanmsDatabaseCheck(DataSource dataSource) {
            return new DatabaseCheck(dataSource);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisConnectionFactory.class)
    static class RedisConfiguration {
        @Bean
        @ConditionalOnBean(RedisConnectionFactory.class)
        DependencyCheck scanmsRedisCheck(RedisConnectionFactory factory) {
            return new RedisCheck(factory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaAdmin.class)
    static class KafkaConfiguration {
        @Bean
        @ConditionalOnBean(KafkaAdmin.class)
        DependencyCheck scanmsKafkaCheck(KafkaAdmin admin) {
            return new KafkaCheck(admin);
        }
    }

    @Bean
    ScanmsInfoEndpoint scanmsInfoEndpoint(OpsProperties props, Environment env, OpsAccess access, List<DependencyCheck> checks) {
        return new ScanmsInfoEndpoint(props, env, access, checks);
    }

    @Bean
    ScanmsLogsEndpoint scanmsLogsEndpoint(OpsProperties props, Environment env, OpsAccess access, LogCapture capture) {
        return new ScanmsLogsEndpoint(props, env, access, capture);
    }
}
