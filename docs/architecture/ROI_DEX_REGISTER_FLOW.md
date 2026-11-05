# ROI DEX register-flow analysis

## Phase 1: intra-basic-block def/use

`RoiDexRegisterFlowAnalyzer` consumes the existing structural DEX CFG and produces:
- per-instruction register uses and definitions;
- per-basic-block registers read before a local definition;
- per-basic-block registers defined at least once;
- the CFG edges for later phases.

This phase does **not** propagate definitions across blocks. Wide values occupy two adjacent register words. Invoke operands are represented as register words, not inferred Java parameter values.

Unknown or not-yet-modelled opcodes fail closed with an explicit analysis error. This is intentional: the analyzer must not turn incomplete opcode semantics into a false data-flow fact.

## Phase 2: cross-block reaching definitions (not implemented)

The next independent phase may add a forward fixed-point analysis over the CFG:
- `IN[B] = union(OUT[P])` for predecessors `P`;
- `OUT[B] = GEN[B] union (IN[B] - KILL[B])`;
- bounded iteration until stable, with explicit handling of exception edges before claiming complete method coverage.

Do not use phase-1 block-local facts as cross-block reaching-definition results. No automatic Hook authorization may depend on this analyzer alone.
