package de.fosstenbuch.utils

import timber.log.Timber

class TimberDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String? {
        return "FOSStenbuch(${element.fileName}:${element.lineNumber})#${element.methodName}"
    }
}