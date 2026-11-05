# ROI DEX register-flow analysis

## Phase 1: intra-basic-block def/use

`RoiDexRegisterFlowAnalyzer` consumes the structural DEX CFG and produces per-instruction register uses/definitions, per-block reads-before-local-definition, per-block definitions, and CFG edges.

The decoder covers only explicitly modelled instruction formats. Unknown semantics fail closed. Wide values occupy two adjacent register words. Invoke operands are represented as register words, not inferred Java parameter values.

## Phase 2: normal and conservative exception-CFG reaching definitions

`RoiDexReachingDefinitions` tracks each definition site per register word and computes a forward fixed point:

- `IN[B] = union(OUT[P])` for all modeled predecessors `P`, including conservative exception edges when analysis starts from a parsed `CodeItem`;
- `OUT[B] = GEN[B] union (IN[B] - KILL[B])`;
- `GEN[B]` retains the final local definition for each register word;
- `KILL[B]` removes other definition sites for every register word written in the block.
- A CFG reachability pass starts at the method entry block; unreachable blocks do not contribute definitions to reachable code and are explicitly marked `reachableFromEntry=false`.

The worklist, definition count, and block-by-definition state space have explicit budgets. Exceeding a budget returns an analysis error rather than a partial result.

**Exception-flow integration:** `RoiDexCodeReader` now exposes each parsed typed catch and catch-all clause with its handler-list-relative offset and target address. `RoiDexExceptionControlFlow` validates handler targets, promotes try starts/ends and handler entries to CFG leaders, and adds edges from every block intersecting a protected try range to each declared handler. This is deliberately conservative: it does not claim to know which individual opcode throws.

The `RoiDexRegisterFlowAnalyzer.analyze(CodeItem)` path carries those edges into reaching-definitions and use-def analysis. The raw `analyze(short[], registerCount)` overload remains normal-CFG-only. For exception-aware use-def, handler-entry registers are not marked definitely defined solely from block-level exceptional predecessors, because a throw may occur before an instruction commits its destination.

**Scope limitation:** exception-aware results report `exceptionEdgesIncluded=true`, but `completeForExceptionFlow=false`. The edges over-approximate protected-region transfers; uncaught-exception exits and exact per-opcode throw points are not modeled. Do not treat this as precise exception semantics or as Hook authorization.

## Phase 3: instruction-level use-def chains

`RoiDexUseDefChains` walks each basic block from its reaching-IN state, emits one use site per register-word operand, links it to every definition that can reach that exact instruction, and then applies the instruction's definitions before processing the next instruction.

- Branch joins retain all reaching definition sites until a later write kills them.
- Loop-carried definitions are included after the reaching-definitions fixed point.
- Wide values are represented as two register-word use sites.
- Repeated operands remain distinct through their operand index.
- A separate must-defined dataflow pass marks `mayBeEntryOrUnknownValue` whenever at least one reachable path can arrive without a modeled definition; this includes a method's initial entry path even when a loop back-edge contributes definitions.
- Parameter and receiver register words remain entry/unknown until method prototype and `ins_size` metadata are supplied; the analysis does not guess which incoming words are `this` or arguments.
- Unreachable use sites are explicitly marked `reachableFromEntry=false`.
- Use-site and chain-link budgets fail closed instead of returning partial chains.

These chains can consume the conservative exception-aware flow from phase 2. Exact throw-point semantics remain incomplete. Method parameter register mapping is available through the entry-layout integration below; receiver/parameter provenance still describes register positions, not caller-side runtime values.

## Phase 4: method-entry register layout

`RoiDexMethodEntryLayout` maps incoming register words from the already-decoded CodeItem header (`registers_size`, `ins_size`), access flags, and method prototype descriptor. It validates that the receiver (for non-static methods) plus prototype parameter widths exactly match `ins_size`, and maps both words of `J`/`D` parameters to the same parameter entry.

`RoiDexFileReader` now parses the raw DEX ID tables and class definitions, joins `class_data_item` method definitions to `method_id` and `proto_id`, and exposes `ResolvedMethod` values containing the declaring class descriptor, method name, prototype descriptor, access flags, and associated `CodeItem`. `RoiDexMethodEntryLayout` consumes that decoded metadata. Use-def sites can optionally consume the resulting layout to distinguish receiver and parameter entry positions from other undefined/unknown values. This is static metadata linkage, not caller-side argument provenance; no prototype or access-flag inference is attempted.

## Hook-safety boundary

These analyses are static evidence only. They do not authorize Hook installation, infer semantic roles by themselves, or prove a runtime Flow/decoder path. Runtime activation is controlled by three independent, explicit user switches defined in `ADR-ROI-HOOK-ACTIVATION-GATES.md`:

- Unknown-version Hook attempts are disabled by default and may be attempted only when the dedicated unknown-version switch is enabled.
- Diagnostic probes are disabled by default and may be installed or run only when the diagnostic-probe switch is enabled.
- Unknown-host feature Hooks require both the unknown-version-attempt switch and the dedicated feature-Hook authorization switch; both are disabled by default. Static-analysis confidence, candidate ranking, or a successful scan must never turn either switch on or bypass either gate.

Enabling one switch must not enable either of the others. Unknown-version attempts remain bounded by runtime compatibility checks and must fail closed on missing or contradictory evidence. These switches are authorization gates, not proof that a target Hook is correct or safe.
