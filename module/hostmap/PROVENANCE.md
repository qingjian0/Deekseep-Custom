# Host mapping provenance

## Scope

This note records what can currently be reproduced from this repository. It is not a claim that any mapping table has been independently validated against a particular published APK.

## Checked-in mapping tables

The repository contains these compressed runtime tables:

| Table | Git blob SHA | Blob size |
|---|---|---:|
| `hostmap287.txt.gz` | `583a1bf7fb18714cb5b3eba66b990340f4305ecc` | 204,075 bytes |
| `hostmap289.txt.gz` | `d2ed9bf18d456bac89d9ca003c7b22adf0e4c5ec` | 195,448 bytes |
| `hostmap290.txt.gz` | `9304eae4c5abdaf90f0a31529b57f74520fe6699` | 194,907 bytes |

These are Git object identifiers and blob sizes, not APK signing fingerprints or proof of source APK identity.

## Reproduction evidence currently present

The checked-in `pipeline.sh` explicitly generates the 287 table. It invokes:

1. `members.py /tmp/cmap2.json`
2. `genmap.py`
3. Copies `/tmp/hostmap287.txt.gz` into the working tree.

The member-map generator documents a 257 -> 287 comparison and reads these external intermediate files:

- `/tmp/c257.pkl`
- `/tmp/c287.pkl`
- `/tmp/cmap2.json`

It writes `/tmp/mmap.json`; `genmap.py` consumes that file and writes `/tmp/hostmap287.txt` plus its gzip form.

The checked-in pipeline also refers to external workspace paths, a separate verifier checkout, and `/tmp/ds287.jar`. Those inputs and the APK/DEX extraction steps that produced the pickle and class-map files are not captured as a clean-checkout, self-contained reproduction workflow in this repository.

## Explicit gaps

- The current checked-in pipeline does not independently regenerate 289 or 290.
- The repository does not currently contain sufficient provenance to establish which exact APK artifact produced each of the 287, 289, and 290 tables.
- Public third-party version listings are not a substitute for the APK itself, its package name, version name, versionCode, and signing-certificate digest.
- A passing structural/table-format regression test validates parsing and expected table invariants; it does not prove semantic correctness of every class/member mapping against a host APK.

## Evidence required to close the gaps

For each supported host build, retain an auditable record containing:

1. Original APK artifact and retrieval source.
2. Package name, version name, versionCode, and signing-certificate SHA-256.
3. APK SHA-256 and DEX inventory.
4. Exact extraction tool versions and commands.
5. Class-map and member-map intermediate artifacts, with hashes.
6. Generator revision and output table hash.
7. Runtime smoke-test evidence showing the mapped symbols resolve in that exact host build.

Until those records exist, treat the mapping tables as repository inputs with incomplete provenance. Do not infer domestic/international distribution or APK identity from the table filename alone.
