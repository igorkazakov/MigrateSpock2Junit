// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package basetest.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil

interface TestLibrary {
    fun addTo(module: Module) {
        ModuleRootModificationUtil.updateModel(module) { model: ModifiableRootModel -> addTo(module, model) }
    }

    fun addTo(module: Module, model: ModifiableRootModel)

    operator fun plus(library: TestLibrary): TestLibrary {
        return CompoundTestLibrary(this, library)
    }
}