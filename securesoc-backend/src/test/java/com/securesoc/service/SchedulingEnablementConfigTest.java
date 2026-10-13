package com.securesoc.service;

import com.securesoc.config.SchedulingEnablementConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the scheduling switch: ON by default (production), OFF only when
 * securesoc.scheduling.enabled=false is set explicitly (the test suite).
 */
class SchedulingEnablementConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(SchedulingEnablementConfig.class);

    @Test
    void schedulingIsEnabledByDefault() {
        runner.run(context -> assertThat(context)
            .hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void schedulingIsEnabledWhenPropertyIsTrue() {
        runner.withPropertyValues("securesoc.scheduling.enabled=true")
            .run(context -> assertThat(context)
                .hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void schedulingIsDisabledOnlyWhenPropertyIsFalse() {
        runner.withPropertyValues("securesoc.scheduling.enabled=false")
            .run(context -> assertThat(context)
                .doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }
}
