package com.java.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogClientTest {
    @Test
    fun modelListUrl_appendsOpenAiCompatiblePath() {
        assertEquals(
            "https://api.example.com/v1/models",
            ModelCatalogClient.modelListUrl(" https://api.example.com/ ")
        )
        assertEquals(
            "https://api.example.com/openai/v1/models",
            ModelCatalogClient.modelListUrl("https://api.example.com/openai/v1/")
        )
    }

    @Test
    fun modelListUrl_rejectsUnsafeOrAmbiguousBase() {
        assertNull(ModelCatalogClient.modelListUrl("http://api.example.com/v1"))
        assertNull(ModelCatalogClient.modelListUrl("https://user@api.example.com/v1"))
        assertNull(ModelCatalogClient.modelListUrl("https://api.example.com/v1?route=models"))
        assertNull(ModelCatalogClient.modelListUrl("not a url"))
    }
}
