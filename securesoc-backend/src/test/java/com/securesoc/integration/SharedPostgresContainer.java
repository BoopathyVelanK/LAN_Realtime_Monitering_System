package com.securesoc.integration;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The single PostgreSQL container shared by every Spring Boot integration
 * test in this module.
 *
 * <h2>Why one shared container instead of a per-class {@code @Container}</h2>
 * Spring's test-context cache outlives a test class: every
 * {@code @SpringBootTest} context (with its Hikari pool and its
 * {@code @Scheduled} sweeper) stays cached until the JVM exits. The
 * Testcontainers JUnit extension, however, stops a static {@code @Container}
 * after its class. A cached context therefore keeps running against a dead
 * database - the connection-refused / Hikari timeout / scheduled-task errors
 * seen at the end of a full {@code mvn test}. Here the container is started
 * once, on first use, and is never stopped by JUnit: it outlives every cached
 * context, and Testcontainers' Ryuk reaps it when the JVM exits.
 *
 * <h2>Contract every test using this container must honour</h2>
 * <ul>
 *   <li>The suite runs sequentially (no surefire parallelism is configured).
 *       Do not enable parallel execution without giving each test its own
 *       schema or database.</li>
 *   <li>Every row a test commits must be removed by that test (usually in
 *       {@code @AfterEach}). Nothing is torn down between classes any more.
 *       In particular, enabled detection rules are evaluated for <em>every</em>
 *       later event with the same event source, so a leaked rule changes the
 *       outcome of unrelated tests.</li>
 *   <li>Use unique names/tokens/MACs (random UUIDs) for fixtures, and compare
 *       row counts as before/after deltas, never as absolute numbers.</li>
 * </ul>
 *
 * {@code EndpointAlertDeduplicationMigrationTest} deliberately does not use this
 * container: it migrates a database to a specific Flyway version and needs a
 * dedicated, disposable instance.
 */
final class SharedPostgresContainer {

    static final PostgreSQLContainer<?> INSTANCE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        INSTANCE.start();
    }

    private SharedPostgresContainer() {
    }
}
