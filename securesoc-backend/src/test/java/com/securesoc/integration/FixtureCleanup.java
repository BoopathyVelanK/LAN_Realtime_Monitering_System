package com.securesoc.integration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Fixture-scoped cleanup helpers for the integration tests that share
 * {@link SharedPostgresContainer}.
 *
 * Every method deletes by id (or by a foreign-key id) with a bulk query, so no
 * entity is loaded and no lazy association is ever navigated - nothing here
 * depends on a detached proxy being initialised. All methods must run inside
 * an active transaction (bulk queries require one) and return the number of
 * rows deleted. An empty id collection is a no-op, never a "match everything".
 *
 * Bulk deletes bypass JPA cascades; that is safe for the entities handled
 * here because the database cascades (running_apps) or nothing references the
 * rows (alerts, events, risk scores).
 */
final class FixtureCleanup {

    private FixtureCleanup() {
    }

    /**
     * Deletes every alert that references any of the given endpoints, rules or
     * users. Alerts must go before those rows: alerts.endpoint_id and
     * alerts.rule_id are ON DELETE SET NULL, so deleting the endpoint or rule
     * first would orphan the alert instead of removing it.
     */
    static int deleteAlertsReferencing(EntityManager em, Collection<UUID> endpointIds,
                                       Collection<UUID> ruleIds, Collection<UUID> userIds) {
        List<String> clauses = new ArrayList<>();
        if (!endpointIds.isEmpty()) {
            clauses.add("a.endpoint.id in :endpointIds");
        }
        if (!ruleIds.isEmpty()) {
            clauses.add("a.rule.id in :ruleIds");
        }
        if (!userIds.isEmpty()) {
            clauses.add("a.user.id in :userIds");
        }
        if (clauses.isEmpty()) {
            return 0;
        }
        Query query = em.createQuery("delete from Alert a where " + String.join(" or ", clauses));
        if (!endpointIds.isEmpty()) {
            query.setParameter("endpointIds", endpointIds);
        }
        if (!ruleIds.isEmpty()) {
            query.setParameter("ruleIds", ruleIds);
        }
        if (!userIds.isEmpty()) {
            query.setParameter("userIds", userIds);
        }
        return query.executeUpdate();
    }

    /**
     * Deletes the authentication-failure events of the given users, by id.
     *
     * The ids are collected first, while the events still point at their user,
     * and only then deleted. Order matters: auth_failure_events.user_id is
     * ON DELETE SET NULL (V6 keeps failure history when a user is deleted), so
     * deleting the user first would leave the events behind with a null user,
     * after which nothing could attribute them to a fixture. Call this before
     * the users are deleted.
     */
    static int deleteAuthFailureEventsOf(EntityManager em, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return 0;
        }
        List<UUID> eventIds = em
            .createQuery("select e.id from AuthFailureEvent e where e.user.id in :userIds", UUID.class)
            .setParameter("userIds", userIds)
            .getResultList();
        return deleteAuthFailureEventsById(em, eventIds);
    }

    /** Deletes exactly the given auth-failure events, whether or not they still have a user. */
    static int deleteAuthFailureEventsById(EntityManager em, Collection<UUID> eventIds) {
        if (eventIds.isEmpty()) {
            return 0;
        }
        return em.createQuery("delete from AuthFailureEvent e where e.id in :eventIds")
            .setParameter("eventIds", eventIds)
            .executeUpdate();
    }

    /**
     * Deletes the rows of an endpoint-owned table (login/logout/usb/vpn/idle/
     * network/internet events, running-app snapshots, risk scores) for the
     * given endpoints. The entity type must have an {@code endpoint}
     * association and the default entity name (its simple class name).
     */
    static int deleteOwnedByEndpoints(EntityManager em, Class<?> entityType, Collection<UUID> endpointIds) {
        if (endpointIds.isEmpty()) {
            return 0;
        }
        return em.createQuery("delete from " + entityType.getSimpleName() + " e where e.endpoint.id in :endpointIds")
            .setParameter("endpointIds", endpointIds)
            .executeUpdate();
    }
}
