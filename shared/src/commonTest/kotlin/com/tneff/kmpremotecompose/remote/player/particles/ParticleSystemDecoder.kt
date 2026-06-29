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
package com.tneff.kmpremotecompose.remote.player.particles

import com.tneff.kmpremotecompose.remote.core.document.RemoteComposeDocument
import com.tneff.kmpremotecompose.remote.core.operations.Operation
import com.tneff.kmpremotecompose.remote.core.operations.Operations
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCompare
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesCreate
import com.tneff.kmpremotecompose.remote.core.operations.draw.ParticlesLoop
import com.tneff.kmpremotecompose.remote.player.core.RemoteComposePlayer

/**
 * REM-143 §5b — pure-data decode of a doc's particle systems into independent-reconstruction inputs. This
 * reads the BYTE-decoded ops (ParticlesCreate's init equations, ParticlesLoop's update/restart equations,
 * and the loop body ops), NOT the sim's evaluation/orchestration — so it does not make the gate circular.
 */
object ParticleSystemDecoder {

    /**
     * One decoded particle system: the [create]/[loop] op pair (matched by shared id), the [body] ops the
     * loop draws per particle, and a [ParticleReconstruction] driving the independent orchestration.
     */
    data class System(
        val create: ParticlesCreate,
        val loop: ParticlesLoop,
        val body: List<Operation>,
        val reconstruction: ParticleReconstruction,
    )

    /** Decode every particle system in [doc] (each PARTICLE_DEFINE + its matching PARTICLE_LOOP). */
    fun decode(doc: RemoteComposeDocument): List<System> {
        val ops = doc.operations
        val creates = ops.filterIsInstance<ParticlesCreate>()
        val out = mutableListOf<System>()
        for (create in creates) {
            val loopIndex = ops.indexOfFirst { it is ParticlesLoop && it.id == create.id }
            if (loopIndex < 0) continue
            val loop = ops[loopIndex] as ParticlesLoop
            // condition1Body compares (eq2 empty) for this system, in doc order — the maze wall collisions.
            val compares = ops.filterIsInstance<ParticlesCompare>()
                .filter { it.id == create.id && it.equations2.isEmpty() }
                .map { ParticleReconstruction.Compare(min = it.min, max = it.max, expr = it.compare, eq1 = it.equations1) }
            out += System(
                create = create,
                loop = loop,
                body = bodyOps(ops, loopIndex),
                reconstruction = ParticleReconstruction(
                    particleCount = create.particleCount,
                    varIds = create.varIds,
                    initEqs = create.equations,
                    restart = if (loop.restart.isEmpty()) null else loop.restart,
                    updateEqs = loop.equations,
                    compares = compares,
                ),
            )
        }
        return out
    }

    /** The ops of the PARTICLE_LOOP body: from just after [loopIndex] to its matching CONTAINER_END. */
    private fun bodyOps(ops: List<Operation>, loopIndex: Int): List<Operation> {
        val body = mutableListOf<Operation>()
        var depth = 0
        var i = loopIndex + 1
        while (i < ops.size) {
            val op = ops[i]
            if (op.opcode == Operations.CONTAINER_END) {
                if (depth == 0) break
                depth--
            } else if (RemoteComposePlayer.opensContainer(op)) {
                depth++
            }
            body += op
            i++
        }
        return body
    }
}
