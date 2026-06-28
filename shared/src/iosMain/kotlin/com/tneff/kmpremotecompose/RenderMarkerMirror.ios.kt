/*
 * Copyright 2026 The KmpRemoteCompose Authors
 * Licensed under the Apache License, Version 2.0.
 */
package com.tneff.kmpremotecompose

/** REM-83 (W2): no DOM on this target — the DOM marker mirror is web-only (see commonMain expect). */
actual fun mirrorRenderMarkersToDom(rendered: Boolean, error: String?, docName: String, drawCount: Int) {}
