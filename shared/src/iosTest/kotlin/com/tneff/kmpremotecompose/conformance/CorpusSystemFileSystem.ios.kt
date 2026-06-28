package com.tneff.kmpremotecompose.conformance

import okio.FileSystem

/** iOS (Native, incl. the simulator host) has a real filesystem → `FileSystem.SYSTEM` (REM-122). */
internal actual fun corpusSystemFileSystem(): FileSystem? = FileSystem.SYSTEM
