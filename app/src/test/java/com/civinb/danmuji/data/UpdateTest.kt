package com.civinb.danmuji.data

import com.civinb.danmuji.update.AppVersion
import com.civinb.danmuji.update.ReleaseParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {

    @Test
    fun versionCompare() {
        assertEquals(true, AppVersion.isNewer("v1.0.1", "1.0.0"))
        assertEquals(true, AppVersion.isNewer("v1.10.0", "1.9.9"))
        assertEquals(true, AppVersion.isNewer("2", "1.9.9"))
        assertEquals(false, AppVersion.isNewer("v1.0.0", "1.0.0"))
        assertEquals(false, AppVersion.isNewer("1.0", "1.0.0"))
        assertEquals(false, AppVersion.isNewer("V0.9.9", "1.0.0"))
        assertEquals(false, AppVersion.isNewer("v1.0.0-beta", "1.0.0"))
        assertNull(AppVersion.isNewer("latest", "1.0.0"))
        assertNull(AppVersion.isNewer("", "1.0.0"))
    }

    /** 字段取自 GitHub 文档示例结构；body 为 null、第一个附件不是 APK */
    @Test
    fun parseRelease() {
        val json = JSONObject(
            """
            {"tag_name":"v1.1.0","name":null,"body":null,"html_url":"https://github.com/Civinb/DanmakuAndroid/releases/tag/v1.1.0",
             "published_at":"2026-10-01T12:00:00Z","draft":false,"prerelease":false,
             "assets":[
               {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
               {"name":"DanmakuAndroid-1.1.0.APK","browser_download_url":"https://x/a.apk","size":12345,
                "digest":"sha256:2151B604e3429bff440b9fbc03eb3617bc2603cda96c95b9bb05277f9ddba255"}
             ]}
            """.trimIndent(),
        )
        val r = ReleaseParser.parse(json)
        assertEquals("v1.1.0", r.tag)
        assertEquals("v1.1.0", r.title)
        assertEquals("", r.notes)
        assertEquals("DanmakuAndroid-1.1.0.APK", r.apk?.name)
        assertEquals("https://x/a.apk", r.apk?.url)
        assertEquals(12345L, r.apk?.size)
        assertEquals("2151b604e3429bff440b9fbc03eb3617bc2603cda96c95b9bb05277f9ddba255", r.apk?.sha256)
    }

    @Test
    fun parseReleaseWithoutApk() {
        val r = ReleaseParser.parse(JSONObject("""{"tag_name":"v2.0.0","name":"大版本","body":"说明","assets":[]}"""))
        assertEquals("大版本", r.title)
        assertEquals("说明", r.notes)
        assertNull(r.apk)
        val noDigest = ReleaseParser.parse(
            JSONObject("""{"tag_name":"v2","assets":[{"name":"a.apk","browser_download_url":"u","size":1,"digest":null}]}"""),
        )
        assertTrue(noDigest.apk != null)
        assertNull(noDigest.apk?.sha256)
        assertFalse(noDigest.apk?.url.isNullOrEmpty())
    }
}
