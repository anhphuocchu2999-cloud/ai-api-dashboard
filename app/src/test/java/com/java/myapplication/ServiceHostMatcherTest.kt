package com.java.myapplication

import com.java.myapplication.config.ServiceHostMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceHostMatcherTest {
    @Test
    fun acceptsExactHostAndSubdomain() {
        assertTrue(ServiceHostMatcher.matches("https://api.deepseek.com/v1", "deepseek.com"))
        assertTrue(ServiceHostMatcher.matches("sub2.aihuangniu.com/v1", "aihuangniu.com"))
    }

    @Test
    fun rejectsLookalikeAndPathInjection() {
        assertFalse(ServiceHostMatcher.matches("https://deepseek.com.attacker.example/v1", "deepseek.com"))
        assertFalse(ServiceHostMatcher.matches("https://attacker.example/deepseek.com/v1", "deepseek.com"))
    }

    @Test
    fun rejectsMalformedOrNonMatchingUrls() {
        assertFalse(ServiceHostMatcher.matches("not a url", "deepseek.com"))
        assertFalse(ServiceHostMatcher.matches("https://openai.com", "deepseek.com"))
    }
}
