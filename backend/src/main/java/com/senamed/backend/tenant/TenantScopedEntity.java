package com.senamed.backend.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Base class for every JPA entity that belongs to a single clinic (tenant).
 *
 * <h2>How to use this for a future entity (e.g. {@code Doctor} in Fase 2)</h2>
 * <pre>{@code
 * @Entity
 * @Table(name = "doctors")
 * public class Doctor extends TenantScopedEntity {
 *     @Id
 *     @GeneratedValue(strategy = GenerationType.IDENTITY)
 *     private Long id;
 *     // ... other fields
 * }
 * }</pre>
 *
 * <p>The subclass table only needs a {@code clinic_id BIGINT NOT NULL REFERENCES clinics(id)}
 * column (added via a Flyway migration) matching the column name declared below. Because the
 * Hibernate {@code @Filter} is defined here, every subclass automatically inherits it - no extra
 * annotations are required on the entity itself.</p>
 *
 * <p>The filter itself is *disabled* by default (it is opt-in per Hibernate session) and is
 * enabled by {@link TenantFilterInterceptor} for every authenticated HTTP request, using the
 * {@code clinicId} resolved from {@link TenantContext}. This means:</p>
 * <ul>
 *   <li>Any {@code CrudRepository}/{@code JpaRepository} query (derived, {@code @Query}, or
 *   Specification-based) executed while handling a request will silently be restricted to
 *   {@code clinic_id = currentClinicId}, even if the query itself "forgot" to add that
 *   condition.</li>
 *   <li>Background jobs / tests that run outside the web request lifecycle must enable the
 *   filter manually (or add an explicit {@code clinicId} condition) since there is no HTTP
 *   request to trigger {@link TenantFilterInterceptor}.</li>
 * </ul>
 *
 * <p>This is a defense-in-depth mechanism: repositories/services should still explicitly scope
 * queries by the tenant id resolved from {@link TenantContext} whenever practical (e.g.
 * {@code findByIdAndClinicId(id, clinicId)}); the Hibernate filter is the safety net that catches
 * the case where a developer forgets to do so.</p>
 */
@MappedSuperclass
@FilterDef(name = TenantScopedEntity.TENANT_FILTER, parameters = @ParamDef(name = "clinicId", type = Long.class))
@Filter(name = TenantScopedEntity.TENANT_FILTER, condition = "clinic_id = :clinicId")
public abstract class TenantScopedEntity {

    public static final String TENANT_FILTER = "clinicFilter";
    public static final String CLINIC_ID_COLUMN = "clinic_id";

    @Column(name = CLINIC_ID_COLUMN, nullable = false, insertable = false, updatable = false)
    private Long clinicId;

    public Long getClinicId() {
        return clinicId;
    }
}
