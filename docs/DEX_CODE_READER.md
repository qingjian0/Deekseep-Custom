# DEX class-data, code-item, instruction scanner and CFG

`RoiDexCodeReader` parses bounded `class_data_item` and `code_item` structures, including encoded fields/methods, instruction code units, try items and encoded catch-handler lists. `RoiDexFileReader` associates class definitions with parsed class data and code items.

`RoiDexInstructionDecoder` is a semantic decoder for a deliberately small instruction subset. It must not be used as a whole-method boundary scanner.

`RoiDexInstructionScanner` is a separate structural pass. It recognizes standard DEX 035 opcodes and the DEX 038 additions (invoke-polymorphic, invoke-custom, method-handle and method-type constants), assigns instruction widths, parses packed-switch/sparse-switch/fill-array-data payload extents, and rejects reserved opcodes, truncation, malformed payloads, payload-reference mismatches, unaligned payloads and branch targets that do not land on instruction boundaries. Width recognition does not mean the semantic decoder understands an opcode.

The CFG builder creates basic blocks and explicit goto, conditional, switch, default and fallthrough edges. Return and throw terminate a block. Switch payload case offsets are interpreted relative to the switch instruction. Payload data is never treated as executable code; CFG construction rejects reachable sequential fallthrough into payload data and skips payload extents only when they follow an unconditional transfer or terminal instruction.

This remains read-only analysis. It does not perform register def-use tracing, does not connect to DexKit export, and does not create Hook evidence or authorization. Unsupported semantics and ambiguity must fail closed. No unknown-version Hook policy is changed. A real-device validation is still required before claiming cross-version Hook support.
