package com.pr0gramm.app.ui

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.createViewModelLazy
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.DEFAULT_ARGS_KEY
import com.pr0gramm.app.util.di.Injector
import com.pr0gramm.app.util.di.injector
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <VM : ViewModel> singleViewModelFactory(fragment: Fragment, expectedModelClass: Class<VM>, create: Injector.(SavedStateHandle) -> VM): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(expectedModelClass)) {
                "Cannot create instance of $modelClass, this factory only creates $expectedModelClass"
            }

            // empty default args: the fragment arguments must not end up in the handle.
            val handle = MutableCreationExtras(extras)
                .apply { set(DEFAULT_ARGS_KEY, Bundle.EMPTY) }
                .createSavedStateHandle()

            val injector = fragment.requireContext().injector
            val model = injector.create(handle)
            return modelClass.cast(model) as T
        }
    }
}

@MainThread
inline fun <reified VM : ViewModel> Fragment.viewModels(
        noinline create: Injector.(SavedStateHandle) -> VM): Lazy<VM> {

    return createViewModelLazy(VM::class, { viewModelStore }) {
        singleViewModelFactory(this, VM::class.java, create)
    }
}


open class SavedStateAccessor(private val handle: SavedStateHandle) {
    protected fun <T : Any> savedStateValue(key: String): ReadWriteProperty<SavedStateAccessor, T?> {
        return object : ReadWriteProperty<SavedStateAccessor, T?> {
            override fun getValue(thisRef: SavedStateAccessor, property: KProperty<*>): T? {
                return handle.get<T>(key)
            }

            override fun setValue(thisRef: SavedStateAccessor, property: KProperty<*>, value: T?) {
                handle.set(key, value)
            }
        }
    }

    protected fun <T : Any> savedStateValue(key: String, defaultValue: T): ReadWriteProperty<SavedStateAccessor, T> {
        return object : ReadWriteProperty<SavedStateAccessor, T> {
            override fun getValue(thisRef: SavedStateAccessor, property: KProperty<*>): T {
                return handle.get<T>(key) ?: defaultValue
            }

            override fun setValue(thisRef: SavedStateAccessor, property: KProperty<*>, value: T) {
                handle.set(key, value)
            }
        }
    }
}
