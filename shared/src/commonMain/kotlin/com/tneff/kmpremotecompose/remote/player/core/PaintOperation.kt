/*
 * Copyright 2026 The KmpRemoteCompose Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tneff.kmpremotecompose.remote.player.core

import com.tneff.kmpremotecompose.remote.core.operations.Operation

/**
 * An [Operation] that draws (upstream `PaintOperation`). The [RemoteComposePlayer] op-walk dispatches
 * to [paint] for every op that implements this; non-paint ops (data, headers) are skipped.
 *
 * This is the **render seam** between Layer 1 and Layer 2: a draw op resolves its referenced
 * ids/variables against [context] and then calls the matching primitive on [paint]. Geometry draw
 * ops gain this in L2-S2 (dev-2); text draw ops in L2-S3 (dev-1). Marking it on the op (rather than a
 * giant `when` in the walk) keeps the walk stable as op coverage grows — same pattern as upstream.
 */
interface PaintOperation : Operation {

    /** Render this op into [paint], resolving any id/variable references via [context]. */
    fun paint(context: RemoteContext, paint: PaintContext)
}
