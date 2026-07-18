package com.java.myapplication.discovery

import android.content.Context
import android.util.AtomicFile
import com.java.myapplication.config.InstanceKeyResolver
import com.java.myapplication.config.LocalCredentialCipher
import org.json.JSONObject
import java.io.File

data class SavedDashboardRecipe(
    val recipe: DashboardRequestRecipe,
    val cookiesByEndpoint: Map<String, String>
)

/**
 * Stores the one validated dashboard recipe in an atomic app-private file.
 *
 * DashboardDiscoveryActivity runs in a dedicated process while Widget runs in the main process.
 * SharedPreferences keeps a per-process cache and therefore cannot be the source of truth for P4.
 * The file contains only recipe metadata plus an Android-Keystore encrypted cookie payload.
 */
class DashboardRecipeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val recipeFile = AtomicFile(File(appContext.noBackupFilesDir, FILE_NAME))
    private val legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    fun loadRecipeMetadata(): DashboardRequestRecipe? =
        readContainer()?.recipe ?: migrateLegacyRecipe()?.recipe

    fun load(): SavedDashboardRecipe? {
        val container = readContainer() ?: return migrateLegacyRecipe()
        return decrypt(container)
    }

    fun save(recipe: DashboardRequestRecipe, cookiesByEndpoint: Map<String, String>): Boolean {
        if (DashboardRecipeRules.validate(recipe) != null) return false
        if (recipe.endpoints.any { cookiesByEndpoint[it].isNullOrBlank() }) return false
        val cookiePayload = JSONObject().apply {
            recipe.endpoints.forEach { endpoint -> put(endpoint, cookiesByEndpoint.getValue(endpoint)) }
        }.toString()
        val encryptedCookies = LocalCredentialCipher.encrypt(cookiePayload) ?: return false
        if (!LocalCredentialCipher.isEncrypted(encryptedCookies)) return false
        val saved = writeContainer(RecipeContainer(recipe, encryptedCookies))
        if (saved) clearLegacyPrefs()
        return saved
    }

    fun bindToInstance(instanceId: String): Boolean {
        val saved = load() ?: return false
        val canonical = InstanceKeyResolver.canonicalInstanceId(instanceId)
        if (canonical.isBlank()) return false
        return save(saved.recipe.copy(boundInstanceId = canonical), saved.cookiesByEndpoint)
    }

    fun unbind(): Boolean {
        val saved = load() ?: return false
        return save(saved.recipe.copy(boundInstanceId = null), saved.cookiesByEndpoint)
    }

    fun clear(): Boolean {
        recipeFile.delete()
        val legacyCleared = clearLegacyPrefs()
        return !recipeFile.baseFile.exists() && legacyCleared
    }

    private fun readContainer(): RecipeContainer? {
        if (!recipeFile.baseFile.exists()) return null
        return runCatching {
            val root = JSONObject(recipeFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            if (root.optInt("version") != CONTAINER_VERSION) return@runCatching null
            val recipe = DashboardRequestRecipe.fromJson(root.optJSONObject("recipe") ?: return@runCatching null)
                ?: return@runCatching null
            val encryptedCookies = root.optString("encryptedCookies")
            if (!LocalCredentialCipher.isEncrypted(encryptedCookies)) return@runCatching null
            RecipeContainer(recipe, encryptedCookies)
        }.getOrNull()
    }

    private fun writeContainer(container: RecipeContainer): Boolean {
        val payload = JSONObject()
            .put("version", CONTAINER_VERSION)
            .put("recipe", container.recipe.toJson())
            .put("encryptedCookies", container.encryptedCookies)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val output = runCatching { recipeFile.startWrite() }.getOrNull() ?: return false
        return try {
            output.write(payload)
            output.flush()
            recipeFile.finishWrite(output)
            true
        } catch (_: Exception) {
            recipeFile.failWrite(output)
            false
        }
    }

    private fun decrypt(container: RecipeContainer): SavedDashboardRecipe? {
        val cookiePayload = LocalCredentialCipher.decrypt(container.encryptedCookies) ?: return null
        val cookieRoot = runCatching { JSONObject(cookiePayload) }.getOrNull() ?: return null
        val cookies = container.recipe.endpoints.associateWith { endpoint -> cookieRoot.optString(endpoint) }
        if (cookies.values.any { it.isBlank() }) return null
        return SavedDashboardRecipe(container.recipe, cookies)
    }

    private fun migrateLegacyRecipe(): SavedDashboardRecipe? {
        val recipeText = legacyPrefs.getString(LEGACY_KEY_RECIPE, null).orEmpty()
        val encryptedCookies = legacyPrefs.getString(LEGACY_KEY_COOKIES, null).orEmpty()
        if (recipeText.isBlank() || !LocalCredentialCipher.isEncrypted(encryptedCookies)) return null
        val recipe = runCatching { DashboardRequestRecipe.fromJson(JSONObject(recipeText)) }.getOrNull() ?: return null
        val saved = decrypt(RecipeContainer(recipe, encryptedCookies)) ?: return null
        if (writeContainer(RecipeContainer(recipe, encryptedCookies))) clearLegacyPrefs()
        return saved
    }

    private fun clearLegacyPrefs(): Boolean = legacyPrefs.edit()
        .remove(LEGACY_KEY_RECIPE)
        .remove(LEGACY_KEY_COOKIES)
        .commit()

    private data class RecipeContainer(
        val recipe: DashboardRequestRecipe,
        val encryptedCookies: String
    )

    private companion object {
        const val CONTAINER_VERSION = 1
        const val FILE_NAME = "dashboard_recipe_v1.json"
        const val LEGACY_PREFS_NAME = "dashboard_recipe_v1"
        const val LEGACY_KEY_RECIPE = "latest_recipe"
        const val LEGACY_KEY_COOKIES = "latest_recipe_cookies"
    }
}
