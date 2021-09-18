package ru.alfabank.converters

import basetest.test.BaseTest

class SpockToJunitConverterTest : BaseTest() {

    override fun actualFileName() = "TestSpock.groovy"

    override fun expectedFileName() = "TestJunit.groovy"

    override fun getTestFolder() = "allconverterstest"

    fun testTransformToJunit() = runSpockToJunitConverter()
}