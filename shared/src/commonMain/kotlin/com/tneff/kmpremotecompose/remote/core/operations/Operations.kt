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
package com.tneff.kmpremotecompose.remote.core.operations

import kotlin.concurrent.Volatile

/**
 * The RemoteCompose opcode registry: the canonical 1-byte opcode → operation map.
 *
 * The opcode numbers below are the authoritative set agreed by the writer/reader reconcile
 * (172 constants, byte-for-byte the upstream `Operations` numbering). Nine of them are reserved
 * placeholders that no operation may ever be dispatched to — see [ORPHAN_OPCODES]; they are never
 * registered, so a document that emits one is rejected on read (fail closed).
 *
 * Decoders are looked up through a version- and profile-gated map ([readerMapFor]): API 6 documents
 * use the v6 set; API ≥ 7 documents use a v7 base plus the overlays selected by the document's
 * `DOC_PROFILES` bitmask. When multiple profiles are present the overlays are intersected, so the
 * document only resolves operations valid in *all* its declared profiles (mirrors upstream).
 *
 * REM-3 establishes this framework and its composition logic; the per-operation [OperationReader]s
 * are registered by the operation classes themselves as they land in REM-4 via [register].
 */
object Operations {

    // ---------------------------------------------------------------------------------------------
    // Profile bitmask (mirror of upstream RcProfiles).
    // ---------------------------------------------------------------------------------------------

    const val PROFILE_BASELINE: Int = 0x0
    const val PROFILE_EXPERIMENTAL: Int = 0x1
    const val PROFILE_DEPRECATED: Int = 0x2
    const val PROFILE_OEM: Int = 0x4
    const val PROFILE_LOW_POWER: Int = 0x8
    const val PROFILE_WIDGETS: Int = 0x100
    const val PROFILE_ANDROIDX: Int = 0x200
    const val PROFILE_ANDROID_NATIVE: Int = 0x400
    const val PROFILE_WEAR_WIDGETS: Int = 0x800

    // ---------------------------------------------------------------------------------------------
    // Opcodes (0..255). Numbering is fixed by the wire format and must not be "tidied".
    // ---------------------------------------------------------------------------------------------

    const val HEADER = 0
    const val COMPONENT_START = 2
    const val LOAD_BITMAP = 4
    const val ANIMATION_SPEC = 14
    const val MODIFIER_WIDTH = 16
    const val CLIP_PATH = 38
    const val CLIP_RECT = 39
    const val PAINT_VALUES = 40
    const val DRAW_RECT = 42
    const val DRAW_TEXT_RUN = 43
    const val DRAW_BITMAP = 44
    const val DATA_SHADER = 45
    const val DRAW_CIRCLE = 46
    const val DRAW_LINE = 47
    const val DRAW_BITMAP_FONT_TEXT_RUN = 48
    const val DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH = 49
    const val DRAW_ROUND_RECT = 51
    const val DRAW_SECTOR = 52
    const val DRAW_TEXT_ON_PATH = 53
    const val MODIFIER_ROUNDED_CLIP_RECT = 54
    const val MODIFIER_BACKGROUND = 55
    const val DRAW_OVAL = 56
    const val DRAW_TEXT_ON_CIRCLE = 57
    const val MODIFIER_PADDING = 58
    const val MODIFIER_CLICK = 59
    const val THEME = 63
    const val CLICK_AREA = 64
    const val ROOT_CONTENT_BEHAVIOR = 65
    const val DRAW_BITMAP_INT = 66
    const val MODIFIER_HEIGHT = 67
    const val DATA_FLOAT = 80
    const val ANIMATED_FLOAT = 81
    const val MODIFIER_MULTI_CLICK = 83
    const val LAYOUT_CUSTOM = 93
    const val DATA_BITMAP = 101
    const val DATA_TEXT = 102
    const val ROOT_CONTENT_DESCRIPTION = 103
    const val MODIFIER_BORDER = 107
    const val MODIFIER_CLIP_RECT = 108
    const val DATA_PATH = 123
    const val DRAW_PATH = 124
    const val DRAW_TWEEN_PATH = 125
    const val MATRIX_SCALE = 126
    const val MATRIX_TRANSLATE = 127
    const val MATRIX_SKEW = 128
    const val MATRIX_ROTATE = 129
    const val MATRIX_SAVE = 130
    const val MATRIX_RESTORE = 131
    const val MATRIX_SET = 132
    const val DRAW_TEXT_ANCHOR = 133
    const val COLOR_EXPRESSIONS = 134
    const val TEXT_FROM_FLOAT = 135
    const val TEXT_MERGE = 136
    const val NAMED_VARIABLE = 137
    const val COLOR_CONSTANT = 138
    const val DRAW_CONTENT = 139
    const val DATA_INT = 140
    const val PLAY_SOUND = 141
    const val REFERENCED_OPERATIONS = 142
    const val DATA_BOOLEAN = 143
    const val INTEGER_EXPRESSION = 144
    const val ID_MAP = 145
    const val ID_LIST = 146
    const val FLOAT_LIST = 147
    const val DATA_LONG = 148
    const val DRAW_BITMAP_SCALED = 149
    const val COMPONENT_VALUE = 150
    const val TEXT_LOOKUP = 151
    const val DRAW_ARC = 152
    const val TEXT_LOOKUP_INT = 153
    const val DATA_MAP_LOOKUP = 154
    const val TEXT_MEASURE = 155
    const val TEXT_LENGTH = 156
    const val TOUCH_EXPRESSION = 157
    const val PATH_TWEEN = 158
    const val PATH_CREATE = 159
    const val PATH_ADD = 160
    const val PARTICLE_DEFINE = 161
    const val PARTICLE_PROCESS = 162
    const val PARTICLE_LOOP = 163
    const val IMPULSE_START = 164
    const val IMPULSE_PROCESS = 165
    const val FUNCTION_CALL = 166
    const val DATA_BITMAP_FONT = 167
    const val FUNCTION_DEFINE = 168
    const val DATA_SOUND = 169
    const val ATTRIBUTE_TEXT = 170
    const val ATTRIBUTE_IMAGE = 171
    const val ATTRIBUTE_TIME = 172
    const val CANVAS_OPERATIONS = 173
    const val MODIFIER_DRAW_CONTENT = 174
    const val PATH_COMBINE = 175
    const val LAYOUT_FIT_BOX = 176
    const val HAPTIC_FEEDBACK = 177
    const val CONDITIONAL_OPERATIONS = 178
    const val DEBUG_MESSAGE = 179
    const val ATTRIBUTE_COLOR = 180
    const val MATRIX_FROM_PATH = 181
    const val TEXT_SUBTEXT = 182
    const val BITMAP_TEXT_MEASURE = 183
    const val DRAW_BITMAP_TEXT_ANCHORED = 184
    const val REM = 185
    const val MATRIX_CONSTANT = 186
    const val MATRIX_EXPRESSION = 187
    const val MATRIX_VECTOR_MATH = 188
    const val DATA_FONT = 189
    const val DRAW_TO_BITMAP = 190
    const val WAKE_IN = 191
    const val ID_LOOKUP = 192
    const val PATH_EXPRESSION = 193
    const val PARTICLE_COMPARE = 194
    const val UPDATE = 195
    const val COLOR_THEME = 196
    const val DYNAMIC_FLOAT_LIST = 197
    const val UPDATE_DYNAMIC_FLOAT_LIST = 198
    const val TEXT_TRANSFORM = 199
    const val LAYOUT_ROOT = 200
    const val LAYOUT_CONTENT = 201
    const val LAYOUT_BOX = 202
    const val LAYOUT_ROW = 203
    const val LAYOUT_COLUMN = 204
    const val LAYOUT_CANVAS = 205
    const val SOUND_EXPRESSION = 206
    const val LAYOUT_CANVAS_CONTENT = 207
    const val LAYOUT_TEXT = 208
    const val HOST_ACTION = 209
    const val HOST_NAMED_ACTION = 210
    const val MODIFIER_VISIBILITY = 211
    const val VALUE_INTEGER_CHANGE_ACTION = 212
    const val VALUE_STRING_CHANGE_ACTION = 213
    const val CONTAINER_END = 214
    const val LOOP_START = 215
    const val HOST_METADATA_ACTION = 216
    const val LAYOUT_STATE = 217
    const val VALUE_INTEGER_EXPRESSION_CHANGE_ACTION = 218
    const val MODIFIER_TOUCH_DOWN = 219
    const val MODIFIER_TOUCH_UP = 220
    const val MODIFIER_OFFSET = 221
    const val VALUE_FLOAT_CHANGE_ACTION = 222
    const val MODIFIER_ZINDEX = 223
    const val MODIFIER_GRAPHICS_LAYER = 224
    const val MODIFIER_TOUCH_CANCEL = 225
    const val MODIFIER_SCROLL = 226
    const val VALUE_FLOAT_EXPRESSION_CHANGE_ACTION = 227
    const val MODIFIER_MARQUEE = 228
    const val MODIFIER_RIPPLE = 229
    const val LAYOUT_COLLAPSIBLE_ROW = 230
    const val MODIFIER_WIDTH_IN = 231
    const val MODIFIER_HEIGHT_IN = 232
    const val LAYOUT_COLLAPSIBLE_COLUMN = 233
    const val LAYOUT_IMAGE = 234
    const val MODIFIER_COLLAPSIBLE_PRIORITY = 235
    const val RUN_ACTION = 236
    const val MODIFIER_ALIGN_BY = 237
    const val LAYOUT_COMPUTE = 238
    const val CORE_TEXT = 239
    const val LAYOUT_FLOW = 240
    const val SKIP = 241
    const val TEXT_STYLE = 242
    const val MODIFIER_DIMENSION_CONSTRAINTS = 243
    const val MACRO_FOR_EACH = 244
    const val INCLUDE_REFERENCED_OPERATIONS = 245
    const val MACRO_DEFINE = 246
    const val MACRO_CALL = 247
    const val MACRO_ARGUMENT = 248
    const val MACRO_BLOCK = 249
    const val ACCESSIBILITY_SEMANTICS = 250
    const val EXTENSION_RANGE_RESERVED_4 = 251
    const val EXTENSION_RANGE_RESERVED_3 = 252
    const val EXTENSION_RANGE_RESERVED_2 = 253
    const val EXTENSION_RANGE_RESERVED_1 = 254
    const val EXTENDED_OPCODE = 255

    /**
     * Opcodes that exist as reserved constants but are deliberately never registered to a reader.
     * The writer must never emit them and the reader rejects them (verified in the reconcile).
     */
    val ORPHAN_OPCODES: Set<Int> = setOf(
        LOAD_BITMAP, MATRIX_SET, PARTICLE_PROCESS, UPDATE,
        EXTENSION_RANGE_RESERVED_4, EXTENSION_RANGE_RESERVED_3,
        EXTENSION_RANGE_RESERVED_2, EXTENSION_RANGE_RESERVED_1, EXTENDED_OPCODE,
    )

    /** opcode → human-readable name, for the debug dump. */
    private val NAMES: Map<Int, String> = mapOf(
        HEADER to "HEADER", COMPONENT_START to "COMPONENT_START", LOAD_BITMAP to "LOAD_BITMAP",
        ANIMATION_SPEC to "ANIMATION_SPEC", MODIFIER_WIDTH to "MODIFIER_WIDTH", CLIP_PATH to "CLIP_PATH",
        CLIP_RECT to "CLIP_RECT", PAINT_VALUES to "PAINT_VALUES", DRAW_RECT to "DRAW_RECT",
        DRAW_TEXT_RUN to "DRAW_TEXT_RUN", DRAW_BITMAP to "DRAW_BITMAP", DATA_SHADER to "DATA_SHADER",
        DRAW_CIRCLE to "DRAW_CIRCLE", DRAW_LINE to "DRAW_LINE",
        DRAW_BITMAP_FONT_TEXT_RUN to "DRAW_BITMAP_FONT_TEXT_RUN",
        DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH to "DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH",
        DRAW_ROUND_RECT to "DRAW_ROUND_RECT", DRAW_SECTOR to "DRAW_SECTOR",
        DRAW_TEXT_ON_PATH to "DRAW_TEXT_ON_PATH", MODIFIER_ROUNDED_CLIP_RECT to "MODIFIER_ROUNDED_CLIP_RECT",
        MODIFIER_BACKGROUND to "MODIFIER_BACKGROUND", DRAW_OVAL to "DRAW_OVAL",
        DRAW_TEXT_ON_CIRCLE to "DRAW_TEXT_ON_CIRCLE", MODIFIER_PADDING to "MODIFIER_PADDING",
        MODIFIER_CLICK to "MODIFIER_CLICK", THEME to "THEME", CLICK_AREA to "CLICK_AREA",
        ROOT_CONTENT_BEHAVIOR to "ROOT_CONTENT_BEHAVIOR", DRAW_BITMAP_INT to "DRAW_BITMAP_INT",
        MODIFIER_HEIGHT to "MODIFIER_HEIGHT", DATA_FLOAT to "DATA_FLOAT", ANIMATED_FLOAT to "ANIMATED_FLOAT",
        MODIFIER_MULTI_CLICK to "MODIFIER_MULTI_CLICK", LAYOUT_CUSTOM to "LAYOUT_CUSTOM",
        DATA_BITMAP to "DATA_BITMAP", DATA_TEXT to "DATA_TEXT",
        ROOT_CONTENT_DESCRIPTION to "ROOT_CONTENT_DESCRIPTION", MODIFIER_BORDER to "MODIFIER_BORDER",
        MODIFIER_CLIP_RECT to "MODIFIER_CLIP_RECT", DATA_PATH to "DATA_PATH", DRAW_PATH to "DRAW_PATH",
        DRAW_TWEEN_PATH to "DRAW_TWEEN_PATH", MATRIX_SCALE to "MATRIX_SCALE",
        MATRIX_TRANSLATE to "MATRIX_TRANSLATE", MATRIX_SKEW to "MATRIX_SKEW", MATRIX_ROTATE to "MATRIX_ROTATE",
        MATRIX_SAVE to "MATRIX_SAVE", MATRIX_RESTORE to "MATRIX_RESTORE", MATRIX_SET to "MATRIX_SET",
        DRAW_TEXT_ANCHOR to "DRAW_TEXT_ANCHOR", COLOR_EXPRESSIONS to "COLOR_EXPRESSIONS",
        TEXT_FROM_FLOAT to "TEXT_FROM_FLOAT", TEXT_MERGE to "TEXT_MERGE", NAMED_VARIABLE to "NAMED_VARIABLE",
        COLOR_CONSTANT to "COLOR_CONSTANT", DRAW_CONTENT to "DRAW_CONTENT", DATA_INT to "DATA_INT",
        PLAY_SOUND to "PLAY_SOUND", REFERENCED_OPERATIONS to "REFERENCED_OPERATIONS",
        DATA_BOOLEAN to "DATA_BOOLEAN", INTEGER_EXPRESSION to "INTEGER_EXPRESSION", ID_MAP to "ID_MAP",
        ID_LIST to "ID_LIST", FLOAT_LIST to "FLOAT_LIST", DATA_LONG to "DATA_LONG",
        DRAW_BITMAP_SCALED to "DRAW_BITMAP_SCALED", COMPONENT_VALUE to "COMPONENT_VALUE",
        TEXT_LOOKUP to "TEXT_LOOKUP", DRAW_ARC to "DRAW_ARC", TEXT_LOOKUP_INT to "TEXT_LOOKUP_INT",
        DATA_MAP_LOOKUP to "DATA_MAP_LOOKUP", TEXT_MEASURE to "TEXT_MEASURE", TEXT_LENGTH to "TEXT_LENGTH",
        TOUCH_EXPRESSION to "TOUCH_EXPRESSION", PATH_TWEEN to "PATH_TWEEN", PATH_CREATE to "PATH_CREATE",
        PATH_ADD to "PATH_ADD", PARTICLE_DEFINE to "PARTICLE_DEFINE", PARTICLE_PROCESS to "PARTICLE_PROCESS",
        PARTICLE_LOOP to "PARTICLE_LOOP", IMPULSE_START to "IMPULSE_START", IMPULSE_PROCESS to "IMPULSE_PROCESS",
        FUNCTION_CALL to "FUNCTION_CALL", DATA_BITMAP_FONT to "DATA_BITMAP_FONT",
        FUNCTION_DEFINE to "FUNCTION_DEFINE", DATA_SOUND to "DATA_SOUND", ATTRIBUTE_TEXT to "ATTRIBUTE_TEXT",
        ATTRIBUTE_IMAGE to "ATTRIBUTE_IMAGE", ATTRIBUTE_TIME to "ATTRIBUTE_TIME",
        CANVAS_OPERATIONS to "CANVAS_OPERATIONS", MODIFIER_DRAW_CONTENT to "MODIFIER_DRAW_CONTENT",
        PATH_COMBINE to "PATH_COMBINE", LAYOUT_FIT_BOX to "LAYOUT_FIT_BOX", HAPTIC_FEEDBACK to "HAPTIC_FEEDBACK",
        CONDITIONAL_OPERATIONS to "CONDITIONAL_OPERATIONS", DEBUG_MESSAGE to "DEBUG_MESSAGE",
        ATTRIBUTE_COLOR to "ATTRIBUTE_COLOR", MATRIX_FROM_PATH to "MATRIX_FROM_PATH",
        TEXT_SUBTEXT to "TEXT_SUBTEXT", BITMAP_TEXT_MEASURE to "BITMAP_TEXT_MEASURE",
        DRAW_BITMAP_TEXT_ANCHORED to "DRAW_BITMAP_TEXT_ANCHORED", REM to "REM",
        MATRIX_CONSTANT to "MATRIX_CONSTANT", MATRIX_EXPRESSION to "MATRIX_EXPRESSION",
        MATRIX_VECTOR_MATH to "MATRIX_VECTOR_MATH", DATA_FONT to "DATA_FONT", DRAW_TO_BITMAP to "DRAW_TO_BITMAP",
        WAKE_IN to "WAKE_IN", ID_LOOKUP to "ID_LOOKUP", PATH_EXPRESSION to "PATH_EXPRESSION",
        PARTICLE_COMPARE to "PARTICLE_COMPARE", UPDATE to "UPDATE", COLOR_THEME to "COLOR_THEME",
        DYNAMIC_FLOAT_LIST to "DYNAMIC_FLOAT_LIST", UPDATE_DYNAMIC_FLOAT_LIST to "UPDATE_DYNAMIC_FLOAT_LIST",
        TEXT_TRANSFORM to "TEXT_TRANSFORM", LAYOUT_ROOT to "LAYOUT_ROOT", LAYOUT_CONTENT to "LAYOUT_CONTENT",
        LAYOUT_BOX to "LAYOUT_BOX", LAYOUT_ROW to "LAYOUT_ROW", LAYOUT_COLUMN to "LAYOUT_COLUMN",
        LAYOUT_CANVAS to "LAYOUT_CANVAS", SOUND_EXPRESSION to "SOUND_EXPRESSION",
        LAYOUT_CANVAS_CONTENT to "LAYOUT_CANVAS_CONTENT", LAYOUT_TEXT to "LAYOUT_TEXT",
        HOST_ACTION to "HOST_ACTION", HOST_NAMED_ACTION to "HOST_NAMED_ACTION",
        MODIFIER_VISIBILITY to "MODIFIER_VISIBILITY",
        VALUE_INTEGER_CHANGE_ACTION to "VALUE_INTEGER_CHANGE_ACTION",
        VALUE_STRING_CHANGE_ACTION to "VALUE_STRING_CHANGE_ACTION", CONTAINER_END to "CONTAINER_END",
        LOOP_START to "LOOP_START", HOST_METADATA_ACTION to "HOST_METADATA_ACTION",
        LAYOUT_STATE to "LAYOUT_STATE",
        VALUE_INTEGER_EXPRESSION_CHANGE_ACTION to "VALUE_INTEGER_EXPRESSION_CHANGE_ACTION",
        MODIFIER_TOUCH_DOWN to "MODIFIER_TOUCH_DOWN", MODIFIER_TOUCH_UP to "MODIFIER_TOUCH_UP",
        MODIFIER_OFFSET to "MODIFIER_OFFSET", VALUE_FLOAT_CHANGE_ACTION to "VALUE_FLOAT_CHANGE_ACTION",
        MODIFIER_ZINDEX to "MODIFIER_ZINDEX", MODIFIER_GRAPHICS_LAYER to "MODIFIER_GRAPHICS_LAYER",
        MODIFIER_TOUCH_CANCEL to "MODIFIER_TOUCH_CANCEL", MODIFIER_SCROLL to "MODIFIER_SCROLL",
        VALUE_FLOAT_EXPRESSION_CHANGE_ACTION to "VALUE_FLOAT_EXPRESSION_CHANGE_ACTION",
        MODIFIER_MARQUEE to "MODIFIER_MARQUEE", MODIFIER_RIPPLE to "MODIFIER_RIPPLE",
        LAYOUT_COLLAPSIBLE_ROW to "LAYOUT_COLLAPSIBLE_ROW", MODIFIER_WIDTH_IN to "MODIFIER_WIDTH_IN",
        MODIFIER_HEIGHT_IN to "MODIFIER_HEIGHT_IN", LAYOUT_COLLAPSIBLE_COLUMN to "LAYOUT_COLLAPSIBLE_COLUMN",
        LAYOUT_IMAGE to "LAYOUT_IMAGE", MODIFIER_COLLAPSIBLE_PRIORITY to "MODIFIER_COLLAPSIBLE_PRIORITY",
        RUN_ACTION to "RUN_ACTION", MODIFIER_ALIGN_BY to "MODIFIER_ALIGN_BY", LAYOUT_COMPUTE to "LAYOUT_COMPUTE",
        CORE_TEXT to "CORE_TEXT", LAYOUT_FLOW to "LAYOUT_FLOW", SKIP to "SKIP", TEXT_STYLE to "TEXT_STYLE",
        MODIFIER_DIMENSION_CONSTRAINTS to "MODIFIER_DIMENSION_CONSTRAINTS", MACRO_FOR_EACH to "MACRO_FOR_EACH",
        INCLUDE_REFERENCED_OPERATIONS to "INCLUDE_REFERENCED_OPERATIONS", MACRO_DEFINE to "MACRO_DEFINE",
        MACRO_CALL to "MACRO_CALL", MACRO_ARGUMENT to "MACRO_ARGUMENT", MACRO_BLOCK to "MACRO_BLOCK",
        ACCESSIBILITY_SEMANTICS to "ACCESSIBILITY_SEMANTICS",
        EXTENSION_RANGE_RESERVED_4 to "EXTENSION_RANGE_RESERVED_4",
        EXTENSION_RANGE_RESERVED_3 to "EXTENSION_RANGE_RESERVED_3",
        EXTENSION_RANGE_RESERVED_2 to "EXTENSION_RANGE_RESERVED_2",
        EXTENSION_RANGE_RESERVED_1 to "EXTENSION_RANGE_RESERVED_1", EXTENDED_OPCODE to "EXTENDED_OPCODE",
    )

    /** The human-readable name of an opcode, or `OP_<n>` if unknown. */
    fun name(opcode: Int): String = NAMES[opcode] ?: "OP_$opcode"

    // ---------------------------------------------------------------------------------------------
    // Reader registry — version/profile layers.
    //
    // Each layer is a sparse opcode → reader map. A document's effective decode map is composed from
    // the base layer for its api level plus the overlays its profiles select (REM-4 populates these
    // via register(); REM-3 ships the empty layers and the composition logic).
    // ---------------------------------------------------------------------------------------------

    enum class Layer {
        /** API 6 base set. */
        V6,

        /** API ≥ 7 profile-independent base set. */
        V7_BASE,

        /** PROFILE_ANDROIDX overlay and its experimental/deprecated sub-overlays. */
        V7_ANDROIDX, V7_ANDROIDX_EXPERIMENTAL, V7_ANDROIDX_DEPRECATED,

        /** PROFILE_WIDGETS overlay and its experimental/deprecated sub-overlays. */
        V7_WIDGETS, V7_WIDGETS_EXPERIMENTAL, V7_WIDGETS_DEPRECATED,
    }

    // Thread-safety: the reader registry is a write-once-at-init, read-many structure. Registration
    // publishes a fresh IMMUTABLE snapshot through a @Volatile copy-on-write reference, so concurrent
    // document decodes — which only READ — always observe a consistent map and never mutate shared
    // state. (The previous design cached the composed map on each read, which was not thread-safe.)
    // Contract: build the registry during single-threaded startup; concurrent *distinct*
    // registrations are not supported, only concurrent reads.
    private fun emptyLayers(): Map<Layer, Map<Int, OperationReader>> =
        Layer.entries.associateWith { emptyMap() }

    @Volatile
    private var layers: Map<Layer, Map<Int, OperationReader>> = emptyLayers()

    @Volatile
    private var defaultsRegistered = false

    // ---------------------------------------------------------------------------------------------
    // Authoritative per-layer opcode membership (mirrors upstream Operations.java exactly).
    //
    // This is the SPEC: which opcodes belong to which layer, independent of which op readers are
    // implemented yet. Getting it wrong silently breaks profile gating, so it is pinned by a
    // reconcile test (RegistryReconcileTest). Op classes register their reader (as they land) into
    // the layer they are members of here. Caveats baked in below:
    //  - DATA_SHADER and ROOT_CONTENT_BEHAVIOR are V6-base, but in V7 they live in the
    //    androidx/widgets overlays, NOT V7_BASE.
    //  - REM, MATRIX_CONSTANT/EXPRESSION/VECTOR_MATH are V7_BASE always-on, NOT in V6.
    // ---------------------------------------------------------------------------------------------

    /** The default operation set shared by V6 and V7 base (upstream `fillDefaultVersionMap`, 123). */
    private val DEFAULT_SET: Set<Int> = setOf(
        ACCESSIBILITY_SEMANTICS, ANIMATED_FLOAT, ANIMATION_SPEC, ATTRIBUTE_COLOR, ATTRIBUTE_IMAGE,
        ATTRIBUTE_TEXT, ATTRIBUTE_TIME, CANVAS_OPERATIONS, CLICK_AREA, CLIP_PATH, CLIP_RECT,
        COLOR_CONSTANT, COLOR_EXPRESSIONS, COMPONENT_START, COMPONENT_VALUE, CONDITIONAL_OPERATIONS,
        CONTAINER_END, DATA_BITMAP, DATA_BITMAP_FONT, DATA_BOOLEAN, DATA_FLOAT, DATA_INT, DATA_LONG,
        DATA_MAP_LOOKUP, DATA_PATH, DATA_TEXT, DEBUG_MESSAGE, DRAW_ARC, DRAW_BITMAP,
        DRAW_BITMAP_FONT_TEXT_RUN, DRAW_BITMAP_INT, DRAW_BITMAP_SCALED, DRAW_CIRCLE, DRAW_CONTENT,
        DRAW_LINE, DRAW_OVAL, DRAW_PATH, DRAW_RECT, DRAW_ROUND_RECT, DRAW_SECTOR, DRAW_TEXT_ANCHOR,
        DRAW_TEXT_ON_CIRCLE, DRAW_TEXT_ON_PATH, DRAW_TEXT_RUN, DRAW_TWEEN_PATH, FLOAT_LIST,
        FUNCTION_CALL, FUNCTION_DEFINE, HAPTIC_FEEDBACK, HEADER, HOST_ACTION, HOST_METADATA_ACTION,
        HOST_NAMED_ACTION, ID_LIST, ID_MAP, IMPULSE_PROCESS, IMPULSE_START, INTEGER_EXPRESSION,
        LAYOUT_BOX, LAYOUT_CANVAS, LAYOUT_CANVAS_CONTENT, LAYOUT_COLLAPSIBLE_COLUMN,
        LAYOUT_COLLAPSIBLE_ROW, LAYOUT_COLUMN, LAYOUT_CONTENT, LAYOUT_FIT_BOX, LAYOUT_IMAGE,
        LAYOUT_ROOT, LAYOUT_ROW, LAYOUT_STATE, LAYOUT_TEXT, LOOP_START, MATRIX_RESTORE, MATRIX_ROTATE,
        MATRIX_SAVE, MATRIX_SCALE, MATRIX_SKEW, MATRIX_TRANSLATE, MODIFIER_BACKGROUND, MODIFIER_BORDER,
        MODIFIER_CLICK, MODIFIER_CLIP_RECT, MODIFIER_COLLAPSIBLE_PRIORITY, MODIFIER_DRAW_CONTENT,
        MODIFIER_GRAPHICS_LAYER, MODIFIER_HEIGHT, MODIFIER_HEIGHT_IN, MODIFIER_MARQUEE,
        MODIFIER_OFFSET, MODIFIER_PADDING, MODIFIER_RIPPLE, MODIFIER_ROUNDED_CLIP_RECT,
        MODIFIER_SCROLL, MODIFIER_TOUCH_CANCEL, MODIFIER_TOUCH_DOWN, MODIFIER_TOUCH_UP,
        MODIFIER_VISIBILITY, MODIFIER_WIDTH, MODIFIER_WIDTH_IN, MODIFIER_ZINDEX, NAMED_VARIABLE,
        PAINT_VALUES, PARTICLE_DEFINE, PARTICLE_LOOP, PATH_ADD, PATH_COMBINE, PATH_CREATE, PATH_TWEEN,
        ROOT_CONTENT_DESCRIPTION, RUN_ACTION, TEXT_FROM_FLOAT, TEXT_LENGTH, TEXT_LOOKUP,
        TEXT_LOOKUP_INT, TEXT_MEASURE, TEXT_MERGE, THEME, TOUCH_EXPRESSION, VALUE_FLOAT_CHANGE_ACTION,
        VALUE_FLOAT_EXPRESSION_CHANGE_ACTION, VALUE_INTEGER_CHANGE_ACTION,
        VALUE_INTEGER_EXPRESSION_CHANGE_ACTION, VALUE_STRING_CHANGE_ACTION,
    )

    /** V6 base additions on top of the default set. */
    private val V6_EXTRA: Set<Int> = setOf(DATA_SHADER, ROOT_CONTENT_BEHAVIOR)

    /** V7 base always-on additions on top of the default set. */
    private val V7_BASE_EXTRA: Set<Int> = setOf(REM, MATRIX_CONSTANT, MATRIX_EXPRESSION, MATRIX_VECTOR_MATH)

    private val ANDROIDX_OVERLAY: Set<Int> = setOf(
        MATRIX_FROM_PATH, TEXT_SUBTEXT, BITMAP_TEXT_MEASURE, DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
        DRAW_BITMAP_TEXT_ANCHORED, DATA_SHADER, DATA_FONT, DRAW_TO_BITMAP, WAKE_IN, ID_LOOKUP,
        PATH_EXPRESSION, PARTICLE_COMPARE, DYNAMIC_FLOAT_LIST, UPDATE_DYNAMIC_FLOAT_LIST, SKIP,
        CORE_TEXT, TEXT_STYLE, TEXT_TRANSFORM, COLOR_THEME,
    )

    private val ANDROIDX_EXPERIMENTAL_OVERLAY: Set<Int> = setOf(
        MODIFIER_ALIGN_BY, LAYOUT_COMPUTE, LAYOUT_FLOW, MODIFIER_MULTI_CLICK,
        MODIFIER_DIMENSION_CONSTRAINTS, REFERENCED_OPERATIONS, INCLUDE_REFERENCED_OPERATIONS,
        MACRO_DEFINE, MACRO_CALL, MACRO_ARGUMENT, MACRO_BLOCK, MACRO_FOR_EACH, LAYOUT_CUSTOM,
        DATA_SOUND, SOUND_EXPRESSION, PLAY_SOUND,
    )

    private val WIDGETS_OVERLAY: Set<Int> = setOf(
        MATRIX_FROM_PATH, TEXT_SUBTEXT, BITMAP_TEXT_MEASURE, DRAW_BITMAP_FONT_TEXT_RUN_ON_PATH,
        DRAW_BITMAP_TEXT_ANCHORED, DRAW_TO_BITMAP, WAKE_IN, ID_LOOKUP, PATH_EXPRESSION,
        PARTICLE_COMPARE, DYNAMIC_FLOAT_LIST, UPDATE_DYNAMIC_FLOAT_LIST, SKIP, CORE_TEXT, TEXT_STYLE,
        TEXT_TRANSFORM, COLOR_THEME,
    )

    private val WIDGETS_EXPERIMENTAL_OVERLAY: Set<Int> = setOf(
        MODIFIER_ALIGN_BY, LAYOUT_COMPUTE, LAYOUT_FLOW, MODIFIER_MULTI_CLICK,
        MODIFIER_DIMENSION_CONSTRAINTS, REFERENCED_OPERATIONS, INCLUDE_REFERENCED_OPERATIONS,
        MACRO_DEFINE, MACRO_CALL, MACRO_ARGUMENT, MACRO_BLOCK, MACRO_FOR_EACH, DATA_SOUND,
        SOUND_EXPRESSION, PLAY_SOUND,
    )

    private val DEPRECATED_OVERLAY: Set<Int> = setOf(ROOT_CONTENT_BEHAVIOR)

    /** Authoritative per-layer opcode membership. Pinned by RegistryReconcileTest. */
    val MEMBERSHIP: Map<Layer, Set<Int>> = mapOf(
        Layer.V6 to (DEFAULT_SET + V6_EXTRA),
        Layer.V7_BASE to (DEFAULT_SET + V7_BASE_EXTRA),
        Layer.V7_ANDROIDX to ANDROIDX_OVERLAY,
        Layer.V7_ANDROIDX_EXPERIMENTAL to ANDROIDX_EXPERIMENTAL_OVERLAY,
        Layer.V7_ANDROIDX_DEPRECATED to DEPRECATED_OVERLAY,
        Layer.V7_WIDGETS to WIDGETS_OVERLAY,
        Layer.V7_WIDGETS_EXPERIMENTAL to WIDGETS_EXPERIMENTAL_OVERLAY,
        Layer.V7_WIDGETS_DEPRECATED to DEPRECATED_OVERLAY,
    )

    /**
     * Register the [reader] for [opcode] in [layer] (single-threaded init; see thread-safety note).
     * Registering one of the [ORPHAN_OPCODES] is a programming error.
     */
    fun register(layer: Layer, opcode: Int, reader: OperationReader) {
        require(opcode in 0..255) { "opcode out of range: $opcode" }
        require(opcode !in ORPHAN_OPCODES) { "opcode $opcode (${name(opcode)}) is reserved and must not be registered" }
        val current = layers
        val updatedLayer = current.getValue(layer) + (opcode to reader)
        layers = current + (layer to updatedLayer) // publish a new immutable snapshot atomically
    }

    /**
     * The effective opcode → reader map for a document at [apiLevel] with the given [profiles]
     * bitmask. API < 7 → V6 set. API ≥ 7 → V7 base plus selected profile overlays; multiple
     * profiles are intersected so only operations valid in all of them resolve.
     *
     * Pure: recomputed from a single consistent snapshot on every call, with no shared-state writes.
     */
    fun readerMapFor(apiLevel: Int, profiles: Int): Map<Int, OperationReader> {
        val snapshot = layers // one volatile read → consistent view for the whole composition
        if (apiLevel < 7) return snapshot.getValue(Layer.V6)
        val map = snapshot.getValue(Layer.V7_BASE).toMutableMap()
        if (profiles != 0) {
            if ((profiles and PROFILE_ANDROID_NATIVE) != 0) {
                throw UnsupportedOperationException("Android native profile is defined externally")
            }
            val overlays = mutableListOf<Map<Int, OperationReader>>()
            if ((profiles and PROFILE_ANDROIDX) != 0) {
                overlays += overlayFor(
                    snapshot, profiles, Layer.V7_ANDROIDX,
                    Layer.V7_ANDROIDX_EXPERIMENTAL, Layer.V7_ANDROIDX_DEPRECATED,
                )
            }
            if ((profiles and PROFILE_WIDGETS) != 0) {
                overlays += overlayFor(
                    snapshot, profiles, Layer.V7_WIDGETS,
                    Layer.V7_WIDGETS_EXPERIMENTAL, Layer.V7_WIDGETS_DEPRECATED,
                )
            }
            when (overlays.size) {
                0 -> {}
                1 -> map.putAll(overlays[0])
                else -> map.putAll(intersect(overlays)) // only ops valid in ALL profiles
            }
        }
        return map
    }

    /** True if [opcode] resolves to a registered reader for this api level + profile combination. */
    fun isValid(opcode: Int, apiLevel: Int, profiles: Int): Boolean =
        readerMapFor(apiLevel, profiles).containsKey(opcode)

    /**
     * The authoritative set of opcodes valid for a document at [apiLevel] with [profiles] — composed
     * from [MEMBERSHIP] with the SAME rules as [readerMapFor] (base + selected overlays, multiple
     * profiles intersected). This is the spec set (every opcode the format allows here), whereas
     * [readerMapFor] is the implemented subset; they converge as op readers land.
     */
    fun membershipFor(apiLevel: Int, profiles: Int): Set<Int> {
        if (apiLevel < 7) return MEMBERSHIP.getValue(Layer.V6)
        val out = MEMBERSHIP.getValue(Layer.V7_BASE).toMutableSet()
        if (profiles != 0) {
            if ((profiles and PROFILE_ANDROID_NATIVE) != 0) {
                throw UnsupportedOperationException("Android native profile is defined externally")
            }
            val overlays = mutableListOf<Set<Int>>()
            if ((profiles and PROFILE_ANDROIDX) != 0) {
                overlays += membershipOverlay(
                    profiles, Layer.V7_ANDROIDX,
                    Layer.V7_ANDROIDX_EXPERIMENTAL, Layer.V7_ANDROIDX_DEPRECATED,
                )
            }
            if ((profiles and PROFILE_WIDGETS) != 0) {
                overlays += membershipOverlay(
                    profiles, Layer.V7_WIDGETS,
                    Layer.V7_WIDGETS_EXPERIMENTAL, Layer.V7_WIDGETS_DEPRECATED,
                )
            }
            when (overlays.size) {
                0 -> {}
                1 -> out += overlays[0]
                else -> out += overlays[0].filter { op -> overlays.all { it.contains(op) } }
            }
        }
        return out
    }

    private fun membershipOverlay(
        profiles: Int,
        base: Layer,
        experimental: Layer,
        deprecated: Layer,
    ): Set<Int> {
        val out = MEMBERSHIP.getValue(base).toMutableSet()
        if ((profiles and PROFILE_EXPERIMENTAL) != 0) out += MEMBERSHIP.getValue(experimental)
        if ((profiles and PROFILE_DEPRECATED) != 0) out += MEMBERSHIP.getValue(deprecated)
        return out
    }

    /** The opcodes that currently have a registered reader in [layer] (for the reconcile test). */
    internal fun registeredOpcodes(layer: Layer): Set<Int> = layers.getValue(layer).keys.toSet()

    private fun overlayFor(
        snapshot: Map<Layer, Map<Int, OperationReader>>,
        profiles: Int,
        base: Layer,
        experimental: Layer,
        deprecated: Layer,
    ): Map<Int, OperationReader> {
        val out = snapshot.getValue(base).toMutableMap()
        if ((profiles and PROFILE_EXPERIMENTAL) != 0) out.putAll(snapshot.getValue(experimental))
        if ((profiles and PROFILE_DEPRECATED) != 0) out.putAll(snapshot.getValue(deprecated))
        return out
    }

    private fun intersect(overlays: List<Map<Int, OperationReader>>): Map<Int, OperationReader> {
        val first = overlays[0]
        return first.filter { (opcode, _) -> overlays.all { it.containsKey(opcode) } }
    }

    /**
     * Register the built-in operation readers (idempotent). The reader/writer facades call this
     * before they touch the registry, so real documents resolve without manual wiring. Each op is
     * registered in the profile-independent base layers (V6 + V7_BASE), matching the upstream
     * `fillDefaultVersionMap`. REM-4 adds the document/data ops; later stories extend this.
     */
    fun registerDefaults() {
        if (defaultsRegistered) return
        defaultsRegistered = true
        inBase(HEADER, Header)
        inBase(DATA_TEXT, TextData)
        inBase(DATA_FLOAT, FloatConstant)
        inBase(DATA_INT, IntegerConstant)
        inBase(COLOR_CONSTANT, ColorConstant)
        inBase(DATA_BITMAP, BitmapData)
    }

    /** Register [reader] for [opcode] in both profile-independent base layers (V6 and V7_BASE). */
    private fun inBase(opcode: Int, reader: OperationReader) {
        require(opcode in MEMBERSHIP.getValue(Layer.V6) && opcode in MEMBERSHIP.getValue(Layer.V7_BASE)) {
            "opcode $opcode (${name(opcode)}) is not a base-layer member — wrong layer"
        }
        register(Layer.V6, opcode, reader)
        register(Layer.V7_BASE, opcode, reader)
    }

    /** Test/maintenance hook: drop all registered readers (does not touch opcode constants). */
    internal fun resetReaders() {
        layers = emptyLayers()
        defaultsRegistered = false
    }
}
