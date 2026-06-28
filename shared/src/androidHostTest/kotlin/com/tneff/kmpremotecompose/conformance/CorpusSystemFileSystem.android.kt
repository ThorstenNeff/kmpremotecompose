package com.tneff.kmpremotecompose.conformance

import okio.FileSystem

/** Android host (JVM) unit tests have a real filesystem → `FileSystem.SYSTEM` (REM-122). */
internal actual fun corpusSystemFileSystem(): FileSystem? = FileSystem.SYSTEM
