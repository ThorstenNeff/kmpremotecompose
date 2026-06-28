package com.tneff.kmpremotecompose.conformance

import okio.FileSystem

/** JVM has a real filesystem → the on-disk corpus reads via `FileSystem.SYSTEM` (REM-122). */
internal actual fun corpusSystemFileSystem(): FileSystem? = FileSystem.SYSTEM
