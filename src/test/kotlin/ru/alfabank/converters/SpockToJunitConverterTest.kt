package ru.alfabank.converters

import basetest.test.BaseTest

class SpockToJunitConverterTest : BaseTest() {

    fun testTransformToJunit() = spockToJunit()

    override fun getTestPath() = "allconverterstest"
}