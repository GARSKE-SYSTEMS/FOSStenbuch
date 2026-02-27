package de.fosstenbuch.data.repository

import de.fosstenbuch.data.model.TripTemplate
import kotlinx.coroutines.flow.Flow

interface TripTemplateRepository {
    fun getAllTemplates(): Flow<List<TripTemplate>>
    fun getTemplateById(id: Long): Flow<TripTemplate?>
    suspend fun insertTemplate(template: TripTemplate): Long
    suspend fun deleteTemplate(template: TripTemplate)
}
