package basetest.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil

interface Library {
    fun addTo(module: Module) {
        ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel -> addTo(module, model) }
    }

    fun addTo(module: Module, model: ModifiableRootModel)

    operator fun plus(library: Library): Library {
        return CompoundLibrary(this, library)
    }
}