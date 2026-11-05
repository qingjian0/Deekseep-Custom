# ROI DEX register-flow analysis

## Phase 1: intra-basic-block def/use

`RoiDexRegisterFlowAnalyzer` consumes the structural DEX CFG and produces per-instruction register uses/definitions, per-block reads-before-local-definition, per-block definitions, and CFG edges.

The decoder covers only explicitly modelled instruction formats. Unknown semantics fail closed. Wide values occupy two adjacent register words. Invoke operands are represented as register words, not inferred Java parameter values.

## Phase 2: normal-CFG reaching definitions

`RoiDexReachingDefinitions` tracks each definition site per register word and computes a forward fixed point:

- `IN[B] = union(OUT[P])` for normal-CFG predecessors `P`;
- `OUT[B] = GEN[B] union (IN[B] - KILL[B])`;
- `GEN[B]` retains the final local definition for each register word;
- `KILL[B]` removes other definition sites for every register word written in the block.
- A CFG reachability pass starts at the method entry block; unreachable blocks do not contribute definitions to reachable code and are explicitly marked `reachableFromEntry=false`.

The worklist, definition count, and block-by-definition state space have explicit budgets. Exceeding a budget returns an analysis error rather than a partial result.

**Scope limitation:** the current CFG has normal control-flow edges only. It does not parse DEX try/catch handler tables or add exceptional edges. Results therefore explicitly report `exceptionEdgesIncluded=false` and `completeForExceptionFlow=false`. They must not be represented as complete method-wide reaching definitions when exception handlers matter.

## Phase 3: instruction-level use-def chains

`RoiDexUseDefChains` walks each basic block from its reaching-IN state, emits one use site per register-word operand, links it to every definition that can reach that exact instruction, and then applies the instruction's definitions before processing the next instruction.

- Branch joins retain all reaching definition sites until a later write kills them.
- Loop-carried definitions are included after the reaching-definitions fixed point.
- Wide values are represented as two register-word use sites.
- Repeated operands remain distinct through their operand index.
- A use with no modeled reaching definition is marked `mayBeEntryOrUnknownValue`; it is not declared uninitialized.
- Use-site and chain-link budgets fail closed instead of returning partial chains.

These chains inherit the normal-CFG-only limitation from phase 2. Exception-handler edges, method parameter register mapping, and receiver/parameter provenance are not yet modeled.

## Hook-safety boundary

These analyses are static evidence only. They do not authorize Hook installation, infer semantic roles by themselves, or prove a runtime Flow/decoder path. Unknown versions remain read-only and feature Hooks remain separately gated.
