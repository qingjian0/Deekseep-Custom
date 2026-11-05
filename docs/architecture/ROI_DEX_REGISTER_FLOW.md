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

The worklist, definition count, and block-by-definition state space have explicit budgets. Exceeding a budget returns an analysis error rather than a partial result.

**Scope limitation:** the current CFG has normal control-flow edges only. It does not parse DEX try/catch handler tables or add exceptional edges. Results therefore explicitly report `exceptionEdgesIncluded=false` and `completeForExceptionFlow=false`. They must not be represented as complete method-wide reaching definitions when exception handlers matter.

## Hook-safety boundary

These analyses are static evidence only. They do not authorize Hook installation, infer semantic roles by themselves, or prove a runtime Flow/decoder path. Unknown versions remain read-only and feature Hooks remain separately gated.
