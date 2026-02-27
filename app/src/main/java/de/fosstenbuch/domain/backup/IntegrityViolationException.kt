package de.fosstenbuch.domain.backup

/**
 * Thrown when the integrity verification of audit-protected vehicle data
 * fails during a backup import. This indicates that trip or audit log data
 * in the JSON file has been modified since export.
 */
class IntegrityViolationException(message: String) : Exception(message)
