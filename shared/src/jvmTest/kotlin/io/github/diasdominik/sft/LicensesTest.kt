package io.github.diasdominik.sft

import com.mikepenz.aboutlibraries.Libs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The licenses screen's data is generated at build time; check it covers real dependencies and the custom entries. */
class LicensesTest {
    private val libs = Libs.Builder()
        .withJson(File("build/generated/composeResourcesWithLicenses/files/aboutlibraries.json").readText())
        .build()

    @Test fun listsDependenciesAndReusedProjects() {
        val ids = libs.libraries.map { it.uniqueId }
        assertTrue("io.ktor:ktor-client-core" in ids, "ktor missing from $ids")
        assertTrue("greeny:SatisfactoryTools" in ids, "SatisfactoryTools missing from $ids")
        assertTrue("Kotlin:KMP-App-Template" in ids, "KMP App Template missing from $ids")
    }

    @Test fun everyLibraryHasALicenseText() {
        val missing = libs.libraries.filter { lib -> lib.licenses.none { !it.licenseContent.isNullOrBlank() } }
        assertTrue(missing.isEmpty(), "No license text for ${missing.map { it.uniqueId }}")
    }
}
