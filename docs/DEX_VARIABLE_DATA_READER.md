# DEX variable-data reader status (2026-11-05)

RoiDexDataReader adds bounded readers for variable-width DEX data items without runtime dependencies.

Implemented: five-byte unsigned LEB128 with uint32 overflow/truncation checks; strict DEX modified UTF-8 decoding including encoded NUL and UTF-16 surrogate pairs; UTF-16 code-unit count and in-data NUL validation; type_list parsing with alignment, bounds, and type-index validation. Synthetic regression tests are wired into ROI fast audit and source-only regression CI.

Not yet implemented: these readers are not yet invoked by RoiDexFileReader.read; the fixed ID reader still retains string-data offsets and proto parameter offsets. class_data_item, encoded methods, code_item, debug info, try/catch, instruction streams, DexKit export/integration, and Hook authorization remain out of scope. Next stage: connect validated strings and type lists to the ID model, then decode class data and code items under fail-closed bounds checks.
