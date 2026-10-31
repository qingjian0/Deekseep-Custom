# Branch Strategy

This repository uses a deliberately small branch model. Do not introduce extra permanent branch categories unless the maintainer explicitly approves them.

## Long-lived branches

| Branch | Purpose | Policy |
|---|---|---|
| `main` | Stable, release-ready code | Keep this branch suitable for releases. Merge tested changes from `dev`. |
| `dev` | Daily development and integration | Default target for normal feature and bug-fix pull requests. Integrate and verify work here before releasing. |

## Temporary work branches

Create these only when work needs isolation:

- `feature/<short-description>` — a new feature or enhancement.
- `fix/<short-description>` — a bug fix.

Branch from `dev`, then open a pull request back into `dev`. Delete the temporary branch after the pull request is merged. For a small change, a direct commit to `dev` may be used when appropriate.

## Release flow

1. Develop and integrate changes on `dev`.
2. Run the relevant build and tests on `dev`.
3. When the integrated state is ready to release, open a pull request from `dev` to `main`.
4. Merge only after the release checks pass.

## Existing and legacy branches

Older branches may remain while their work or pull requests are still being reviewed. Do not delete a branch that is the head of an open pull request until that work has been merged, closed, or deliberately abandoned.

The former `develop` branch is superseded by `dev`. The `dev` branch was created from `develop` to carry forward its starting point. Once the maintainer confirms that `dev` is present and no longer needs the old branch, delete `develop` from GitHub:

1. Open the repository's [Branches page](https://github.com/qingjian0/Deekseep-Custom/branches).
2. Find the branch named `develop`.
3. Use its delete (trash) control and confirm.
4. Do **not** delete `dev` or `main`.

Branch deletion does not delete commits that are still reachable from another branch. If GitHub refuses deletion because of branch protection or an open pull request, review that protection or pull request before proceeding.

## Naming and scope

Keep names short and descriptive, using lowercase kebab-case after the prefix. Avoid adding permanent branches such as `release/*`, `hotfix/*`, `sync/*`, `refactor/*`, `docs/*`, or `test/*` by default. Use `feature/*` or `fix/*` for temporary work regardless of whether the change touches code, documentation, tests, or build configuration.
