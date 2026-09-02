package com.superflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class DataRulesTest {

    @Test
    fun testDataExtractionRules_excludesSecretsInCloudAndDeviceTransfer() {
        val xmlFile = File("src/main/res/xml/data_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_rules.xml")
        }
        assertTrue("data_rules.xml should exist", xmlFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(xmlFile)
        doc.documentElement.normalize()

        assertEquals("data-extraction-rules", doc.documentElement.nodeName)

        val sections = listOf("cloud-backup", "device-transfer")
        for (section in sections) {
            val sectionList = doc.getElementsByTagName(section)
            assertTrue("Section $section should exist", sectionList.length > 0)
            val sectionNode = sectionList.item(0)

            val childNodes = sectionNode.childNodes
            val includes = mutableListOf<String>()
            val excludes = mutableListOf<String>()

            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeName == "include") {
                    val path = node.attributes.getNamedItem("path")?.nodeValue
                    val domain = node.attributes.getNamedItem("domain")?.nodeValue
                    if (path != null) includes.add("$domain:$path")
                } else if (node.nodeName == "exclude") {
                    val path = node.attributes.getNamedItem("path")?.nodeValue
                    val domain = node.attributes.getNamedItem("domain")?.nodeValue
                    if (path != null) excludes.add("$domain:$path")
                }
            }

            assertTrue(
                "In section $section, superflow_prefs.xml must be explicitly included if any sharedpref is included or if rules are specified",
                includes.contains("sharedpref:superflow_prefs.xml")
            )
            assertTrue(
                "In section $section, superflow_secrets.xml must be excluded",
                excludes.contains("sharedpref:superflow_secrets.xml")
            )
        }
    }
}
