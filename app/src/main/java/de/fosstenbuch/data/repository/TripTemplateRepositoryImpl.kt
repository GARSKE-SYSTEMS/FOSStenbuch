package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripTemplateDao
import de.fosstenbuch.data.model.TripTemplate
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TripTemplateRepositoryImpl @Inject constructor(
    private val tripTemplateDao: TripTemplateDao
) : TripTemplateRepository {
    override fun getAllTemplates(): Flow<List<TripTemplate>> = tripTemplateDao.getAllTemplates()
    override fun getTemplateById(id: Long): Flow<TripTemplate?> = tripTemplateDao.getTemplateById(id)
    override suspend fun insertTemplate(template: TripTemplate): Long = tripTemplateDao.insertTemplate(template)
    override suspend fun deleteTemplate(template: TripTemplate) = tripTemplateDao.deleteTemplate(template)
}
