package io.github.arashiyama11.a_larm.infra.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.arashiyama11.a_larm.domain.UserProfileRepository
import io.github.arashiyama11.a_larm.domain.models.Gender
import io.github.arashiyama11.a_larm.domain.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile_store")

class UserProfileRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : UserProfileRepository {

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_GENDER = stringPreferencesKey("user_gender")
    }

    override fun getProfile(): Flow<UserProfile?> {
        return context.dataStore.data.map { preferences ->
            val name = preferences[Keys.USER_NAME]
            val genderString = preferences[Keys.USER_GENDER]

            if (name != null && genderString != null) {
                val gender = try {
                    Gender.valueOf(genderString)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (gender != null) {
                    UserProfile(name, gender)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USER_NAME] = profile.name
            preferences[Keys.USER_GENDER] = profile.gender.name
        }
    }
}
