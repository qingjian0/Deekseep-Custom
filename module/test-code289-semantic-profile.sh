#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
PROFILE="$ROOT/docs/evidence/deepseek-2.7.2-code289-semantic-profile.json"

test -s "$PROFILE"

python3 - "$PROFILE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    profile = json.load(handle)

assert profile["profile_id"] == "deepseek-2.7.2-code289-cn"
assert profile["host_package"] == "com.deepseek.chat"
assert profile["version"] == "2.7.2"
assert profile["build"] == "code289-cn"
assert profile["apk"]["sha256"] == (
    "3c2fe31c73575400b9d59e46dde05daa2ef06491498ebfef658801e99eca1aff"
)
assert profile["apk"]["dex_count"] == 3

runtime = profile["evidence"]["dynamic_runtime"]
assert runtime["request_constructions"] == 4
assert runtime["transport_dispatch_entries"] == 4
assert runtime["low_level_entries"] == 4
assert runtime["same_request_object_between_dispatch_and_low_level"] is True
assert runtime["credentials_or_message_bodies_collected"] is False

nodes = profile["semantic_nodes"]
assert nodes["chat_request_model"]["class"] == "Lly1;"
assert nodes["transport_dispatch"]["method"] == (
    "Lzn3;->o(Lwu1;Ljava/lang/Long;)Ly60;"
)
assert nodes["transport_low_level"]["method"] == (
    "Ljw3;->g(Lwu1;Ljava/lang/Long;)Ly60;"
)
assert nodes["async_continuation"]["class"] == "Ly60;"
assert "b(Lfl4;Lyb3;)Ljava/lang/Object;" in (
    nodes["async_continuation"]["methods"]
)

guidance = profile["hook_guidance"]
assert guidance["mode"] == "read-only observer first"
assert guidance["privacy"].startswith("Do not collect message bodies")
assert any("Do not infer field semantics" in item
           for item in guidance["prohibited_assumptions"])

print("code289 semantic profile: PASS")
PY
