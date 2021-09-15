package basetest.test;

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel

internal class CompoundLibrary(vararg libraries: Library) : Library {
    private val myLibraries: Array<out Library>

    init {
        assert(libraries.isNotEmpty())
        myLibraries = libraries
    }

    override fun addTo(module: Module, model: ModifiableRootModel) {
        for (library in myLibraries) {
            library.addTo(module, model)
        }
    }
}