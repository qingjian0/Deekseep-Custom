# DEX class-data and code-item reader

`RoiDexCodeReader` adds bounded, read-only parsing for `class_data_item`, delta-encoded fields/methods, `code_item` headers and raw 16-bit instruction units. It validates ULEB128/SLEB128 widths, field/method index bounds, duplicate delta indices, code offsets, register/ins word counts, debug-info offsets, instruction extents, try-item extents, handler offsets and catch type/address references. Try/catch handler lists are structurally walked rather than silently skipped.

The reader is a standalone parser and is not yet connected to `RoiDexFileReader` class definitions or DexKit DEX export. It does not perform control-flow analysis or register data-flow tracing. Raw code units are not Hook evidence. Unsupported or malformed data fails closed. No Hook policy or authorization was changed.

Next gate: connect class definitions to parsed class data and code items, then decode supported instructions and implement conservative register-flow analysis with branch-join ambiguity rejection.
