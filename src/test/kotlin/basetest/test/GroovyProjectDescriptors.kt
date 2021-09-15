package basetest.test

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor

interface GroovyProjectDescriptors {
    companion object {
        val LIB_GROOVY_2_5: TestLibrary = RepositoryTestLibrary("org.codehaus.groovy:groovy:2.5.11")
        val LIB_JUNIT4: TestLibrary = RepositoryTestLibrary("junit:junit:4.12")
        val LIB_HAMCREST: TestLibrary = RepositoryTestLibrary("org.hamcrest:hamcrest-library:1.3")
        val LIB_SPOCK_1: TestLibrary = RepositoryTestLibrary("org.spockframework:spock-core:1.3-groovy-2.5")
       // val LIB_KOTLIN7: TestLibrary = RepositoryTestLibrary("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.32")
        val LIB_KOTLIN8: TestLibrary = RepositoryTestLibrary("org.jetbrains.kotlin:kotlin-stdlib:1.4.32")


        val GROOVY_2_5: LightProjectDescriptor = LibraryLightProjectDescriptor(LIB_GROOVY_2_5)

        val GROOVY_2_5_JUNIT_SPOCK_1_HAMCREST =
            LibraryLightProjectDescriptor(
               // LIB_GROOVY_2_5,
               // LIB_SPOCK_1,
                LIB_JUNIT4,
                LIB_HAMCREST,
                //LIB_KOTLIN7,
                LIB_KOTLIN8
            )
    }
}