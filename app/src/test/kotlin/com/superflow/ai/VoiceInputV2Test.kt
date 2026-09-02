package com.superflow.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputV2Test {

    @Test
    fun `provider enum contains expected providers`() {
        val providers = VoiceInputV2.Provider.values()
        assertEquals(4, providers.size)
        assertTrue(providers.contains(VoiceInputV2.Provider.PLATFORM))
        assertTrue(providers.contains(VoiceInputV2.Provider.WHISPER_API))
        assertTrue(providers.contains(VoiceInputV2.Provider.WHISPER_LOCAL))
        assertTrue(providers.contains(VoiceInputV2.Provider.VOSK))
    }

    @Test
    fun `provider valueOf resolves valid string`() {
        assertEquals(VoiceInputV2.Provider.PLATFORM, VoiceInputV2.Provider.valueOf("PLATFORM"))
        assertEquals(VoiceInputV2.Provider.WHISPER_API, VoiceInputV2.Provider.valueOf("WHISPER_API"))
        assertEquals(VoiceInputV2.Provider.WHISPER_LOCAL, VoiceInputV2.Provider.valueOf("WHISPER_LOCAL"))
        assertEquals(VoiceInputV2.Provider.VOSK, VoiceInputV2.Provider.valueOf("VOSK"))
    }
}
