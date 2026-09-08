package com.pranav.drsti.data.repository

import android.content.Context
import android.location.Geocoder
import com.pranav.drsti.database.dao.PlaceDao
import com.pranav.drsti.database.entity.PlaceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

/** Fully local, offline place database (spec §15). Ships with a handful of major Indian cities. */
class PlaceRepository(private val context: Context, private val dao: PlaceDao) {

    fun observeAll(): Flow<List<PlaceEntity>> = dao.observeAll()
    suspend fun search(query: String): List<PlaceEntity> = dao.search(query)
    suspend fun getById(id: Long): PlaceEntity? = dao.getById(id)
    suspend fun save(entity: PlaceEntity): Long = dao.upsert(entity)
    suspend fun delete(entity: PlaceEntity) = dao.delete(entity)

    /** Uses Android Geocoder to fetch coordinates for a given query (spec §15). */
    suspend fun searchOnline(query: String): List<PlaceEntity> = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 5)
            addresses?.map { addr ->
                PlaceEntity(
                    name = addr.featureName ?: addr.locality ?: query,
                    city = addr.locality ?: addr.subAdminArea,
                    state = addr.adminArea,
                    country = addr.countryName ?: "Unknown",
                    latitude = addr.latitude,
                    longitude = addr.longitude,
                    timezone = "Asia/Kolkata", // Default, could be refined with another API if needed
                    isUserCreated = true
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun seedDefaultPlacesIfEmpty() {
        if (dao.count() > 0) return
        dao.insertAll(DEFAULT_PLACES)
    }

    companion object {
        private val DEFAULT_PLACES = listOf(
            PlaceEntity(name = "New Delhi", city = "New Delhi", state = "Delhi", latitude = 28.6139, longitude = 77.2090, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Mumbai", city = "Mumbai", state = "Maharashtra", latitude = 19.0760, longitude = 72.8777, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Nagpur", city = "Nagpur", state = "Maharashtra", latitude = 21.1458, longitude = 79.0882, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Bengaluru", city = "Bengaluru", state = "Karnataka", latitude = 12.9716, longitude = 77.5946, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Chennai", city = "Chennai", state = "Tamil Nadu", latitude = 13.0827, longitude = 80.2707, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Kolkata", city = "Kolkata", state = "West Bengal", latitude = 22.5726, longitude = 88.3639, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Hyderabad", city = "Hyderabad", state = "Telangana", latitude = 17.3850, longitude = 78.4867, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Ahmedabad", city = "Ahmedabad", state = "Gujarat", latitude = 23.0225, longitude = 72.5714, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Dhoraji", city = "Dhoraji", state = "Gujarat", latitude = 21.7361, longitude = 70.4497, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Pune", city = "Pune", state = "Maharashtra", latitude = 18.5204, longitude = 73.8567, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Jaipur", city = "Jaipur", state = "Rajasthan", latitude = 26.9124, longitude = 75.7873, timezone = "Asia/Kolkata"),
            PlaceEntity(name = "Lucknow", city = "Lucknow", state = "Uttar Pradesh", latitude = 26.8467, longitude = 80.9462, timezone = "Asia/Kolkata")
        )
    }
}
