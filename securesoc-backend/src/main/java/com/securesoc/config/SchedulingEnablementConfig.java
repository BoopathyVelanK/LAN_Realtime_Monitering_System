package com.securesoc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} processing (currently only
 * {@code EndpointOfflineSweeper}).
 *
 * Scheduling is ON unless {@code securesoc.scheduling.enabled=false} is set
 * explicitly - the property is absent in application.yml, so production
 * behaviour is unchanged. The test suite sets it to false (see
 * src/test/resources/application.properties) so that the many cached
 * integration-test contexts do not each run a background sweeper against the
 * shared test database; the sweeper itself is tested by calling it directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "securesoc.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingEnablementConfig {
}
