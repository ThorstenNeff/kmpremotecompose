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

/**
 * REM-108 (Epic-F, REM-154) **S0** — the public, capability-staffed **interaction callback surface** that a
 * consuming app (Taxi=Mobile, Agent-Tool=Desktop/Wasm) implements to observe in-doc interactions. It is the
 * analog of [SensorSource] / [HapticActuator] for the *output* direction: where those feed host capability
 * **into** the player, this surfaces interaction events **out** to the app.
 *
 * **S0 is a NoOp/zero-risk foundation (TechSpec §7-S0):** the contract types exist and are threaded as an
 * optional, default-[NoOp] parameter onto the render entry points ([RemoteComposePlayer.paint],
 * `RemoteComposeApp`), but nothing emits yet — the producing paths land in later slices (S1 wires [onScroll]
 * over the already-computed `ScrollModifier.scrollOffset`; S2 wires [onClick] from the click-action
 * dispatch-walk, Option B). The default [NoOp] sink is the §0 capability-floor: **no callback ⇒ behaviour-,
 * byte- and render-identical to today**, so every existing test stays green by construction.
 *
 * **§2 (binary `.rc`) invariant:** this surface is **render-only**. It introduces no wire op and changes no
 * existing operation's `write`/`read`/`equals`/`hashCode` — the same discipline as [TouchState],
 * `scrollOffset`, the touch/sensor ids and [HapticActuator]. Interaction state is interpreted at runtime; no
 * serialized bytes change.
 */
interface RcInteractionCallbacks {
    /**
     * A tap hit an identified, rendered element. Wired in S2: by then the in-doc-bound action
     * (`ValueChange`/`Scroll` → float-store mutation, upstream-faithful) has **already executed**; this
     * callback is the additional app-facing signal — and the cross-platform carrier for `HostAction` on
     * non-Mobile targets (Desktop/Web have no `PendingIntent`; §3 capability-staffing). Default: no-op.
     */
    fun onClick(event: RcClickEvent) {}

    /**
     * A scrollable component's offset changed after a drag frame. Wired in S1 over the already-computed
     * `ScrollModifier.scrollOffset` (observation, not control). Default: no-op.
     */
    fun onScroll(event: RcScrollEvent) {}

    companion object {
        /**
         * The explicit capability-floor sink: observes nothing. The default on every render entry point, so
         * an app that supplies no callbacks gets exactly today's behaviour (no emission, no render change).
         */
        val NoOp: RcInteractionCallbacks = object : RcInteractionCallbacks {}
    }
}

/**
 * A click on an identified, rendered element (S2 payload; carried by [RcInteractionCallbacks.onClick]).
 *
 * @property elementId the `ClickArea.id` where present; else the component id from the measure pass; `-1`
 *   when unknown (TechSpec §9-Q2: `ClickArea.id → component-id → -1`).
 * @property metadata the app-defined `ClickArea.metadata` (e.g. a `HostAction` action key on non-Mobile).
 * @property docX tap position in doc-space px.
 * @property docY tap position in doc-space px.
 */
data class RcClickEvent(
    val elementId: Int,
    val metadata: Int,
    val docX: Float,
    val docY: Float,
)

/**
 * The observed scroll state of a scrollable component after a drag frame (S1 payload; carried by
 * [RcInteractionCallbacks.onScroll]).
 *
 * @property componentId the scrollable component's id.
 * @property offset the clamped, signed `ScrollModifier.scrollOffset`.
 * @property axis `ScrollModifier.VERTICAL`=0 / `HORIZONTAL`=1.
 */
data class RcScrollEvent(
    val componentId: Int,
    val offset: Float,
    val axis: Int,
)
