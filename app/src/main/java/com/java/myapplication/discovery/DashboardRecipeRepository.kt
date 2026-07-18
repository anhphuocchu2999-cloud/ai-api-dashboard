package com.java.myapplication.discovery

import android.content.Context
import com.java.myapplication.config.LocalCredentialCipher
import org.json.JSONObject

data class SavedDashboardRecipe(
    val recipe: DashboardRequestRecipe,
    val cookiesByEndpoint: Map<String, String>
)

class DashboardRecipeRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SavedDashboardRecipe? {
        val recipeText = prefs.getString(KEY_RECIPE, null).orEmpty()
        val encryptedCookies = prefs.getString(KEY_COOKIES, null).orEmpty()
        if (recipeText.isBlank() || !LocalCredentialCipher.isEncrypted(encryptedCookies)) return null
        val recipe = runCatching { DashboardRequestRecipe.fromJson(JSONObject(recipeText)) }.getOrNull() ?: return null
        val cookiePayload = LocalCredentialCipher.decrypt(encryptedCookies) ?: return null
        val cookieRoot = runCatching { JSONObject(cookiePayload) }.getOrNull() ?: return null
        val cookies = recipe.endpoints.associateWith { endpoint -> cookieRoot.optString(endpoint) }
        if (cookies.values.any { it.isBlank() }) return null
        return SavedDashboardRecipe(recipe, cookies)
    }

    fun save(recipe: DashboardRequestRecipe, cookiesByEndpoint: Map<String, String>): Boolean {
        if (DashboardRecipeRules.validate(recipe) != null) return false
        if (recipe.endpoints.any { cookiesByEndpoint[it].isNullOrBlank() }) return false
        val cookiePayload = JSONObject().apply {
            recipe.endpoints.forEach { endpoint -> put(endpoint, cookiesByEndpoint.getValue(endpoint)) }
        }.toString()
        val encryptedCookies = LocalCredentialCipher.encrypt(cookiePayload) ?: return false
        if (!LocalCredentialCipher.isEncrypted(encryptedCookies)) return false
        return prefs.edit()
            .putString(KEY_RECIPE, recipe.toJson().toString())
            .putString(KEY_COOKIES, encryptedCookies)
            .commit()
    }

    fun clear(): Boolean = prefs.edit().remove(KEY_RECIPE).remove(KEY_COOKIES).commit()

    private companion object {
        const val PREFS_NAME = "dashboard_recipe_v1"
        const val KEY_RECIPE = "latest_recipe"
        const val KEY_COOKIES = "latest_recipe_cookies"
    }
}
