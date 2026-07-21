package com.senamed.backend.user;

/**
 * A single granular permission that can be granted to a {@link UserRole#STAFF} (or
 * {@link UserRole#DOCTOR}) user. {@link UserRole#ADMIN} users implicitly hold every permission -
 * see {@link User#effectivePermissions()} - and never have rows in the {@code user_permissions}
 * table.
 */
public enum Permission {
    MANAGE_PATIENTS,
    MANAGE_APPOINTMENTS,
    MANAGE_FINANCE,
    MANAGE_SERVICES,
    MANAGE_USERS,
    VIEW_REPORTS
}
