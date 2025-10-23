package com.google.mediapipe.tasks.core

class BaseOptions private constructor() {
    var delegate: Delegate = Delegate.CPU
    var modelAssetPath: String = ""

    companion object {
        fun builder(): Builder {
            return BuilderImp()
        }
    }

    abstract class Builder {
        abstract fun setDelegate(delegate: Delegate): Builder
        abstract fun setModelAssetPath(modelAssetPath: String): Builder
        abstract fun build(): BaseOptions
    }

    private class BuilderImp: Builder() {
        private var delegate: Delegate = Delegate.CPU
        private var modelAssetPath: String = ""

        override fun setDelegate(delegate: Delegate): Builder {
            this.delegate = delegate
            return this
        }

        override fun setModelAssetPath(modelAssetPath: String): Builder {
            this.modelAssetPath = modelAssetPath
            return this
        }

        override fun build(): BaseOptions {
            return BaseOptions().also {
                it.delegate = delegate
                it.modelAssetPath = modelAssetPath
            }
        }
    }

}
