package com.ecom.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills @CreatedDate / @LastModifiedDate automatically on save. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
