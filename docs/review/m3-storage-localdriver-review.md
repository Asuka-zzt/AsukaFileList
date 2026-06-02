# M3 Storage / LocalDriver Code Review

Review date: 2026-05-31  
Branch: `feat(storage)/m3-local-driver`  
Scope: M3 storage mounting, LocalDriver, and `/api/fs/list` integration.

## Summary

The patch adds functional storage listing, but it currently misses path-limit authorization and mishandles response paths for users with non-root `basePath`. These are correctness and security issues for supported user configurations. There are also smaller correctness issues in Local root validation and pagination.

## Findings

### P1: Enforce path limits before resolving storage

File: `src/main/java/com/asuka/filelist/application/fs/FsApplicationService.java`  
Location: lines 51-53

Issue:

When a non-admin user has `PATH_LIMIT` with role scopes limited to a path such as `/allowed`, `FsApplicationService.list` joins `basePath` and resolves any requested mounted path, for example `/private`, without consulting `PermissionApplicationService`.

Impact:

The new real directory listing can bypass the path-scope restriction implemented in M2.

Suggested fix:

Check the resolved permission and path-limit result before calling `StorageResolver`. If the user has `PATH_LIMIT` and the requested path does not match any allowed role scope, return `PERMISSION_DENIED` or the project-standard permission error.

Status: resolved (P1: added pre-check using PermissionApplicationService.resolvePermission in FsApplicationService.list; also filter virtual mounts by perm; deny when eff perm==0)

### P2: Return paths relative to the user's base path

File: `src/main/java/com/asuka/filelist/application/fs/FsApplicationService.java`  
Location: line 141

Issue:

For users whose `basePath` is not `/`, rebuilding response paths from the storage's absolute `mountPath` leaks the hidden base and breaks navigation. Example: with `basePath=/home/alice` and a mount at `/home/alice/local`, listing `/local` returns `/home/alice/local/file`; following that path gets the base prefixed again.

Impact:

The API exposes internal path prefixes and produces paths that clients cannot safely navigate.

Suggested fix:

Build response paths relative to the user's visible request path, or strip `basePath` before returning file and virtual entry paths.

Status: resolved (P2: introduced toVisiblePath + use visibleMount in toResponse/virtualChildren/immediateChild; paths now relative to basePath)

### P2: Reject relative Local root paths before absolutizing

File: `src/main/java/com/asuka/filelist/infrastructure/driver/local/LocalDriver.java`  
Location: lines 154-155

Issue:

`toAbsolutePath()` runs before the `isAbsolute()` check. A relative `rootPath` such as `data` becomes `${cwd}/data` and can be accepted if that directory exists and is whitelisted, despite the Local driver contract requiring an existing absolute directory.

Impact:

Relative root configuration can accidentally resolve against the process working directory.

Suggested fix:

Check `Path.of(rawRootPath).isAbsolute()` before converting it to an absolute normalized path.

Status: resolved (P2 relative root: re-ordered isAbsolute check before toAbsolutePath in LocalDriver.validateRootPath; added distinct error for non-abs)

### P3: Avoid overflowing the pagination offset

File: `src/main/java/com/asuka/filelist/application/fs/FsApplicationService.java`  
Location: lines 187-190

Issue:

For very large `page` values, `(page - 1) * perPage` overflows `int` before `Math.min`. Example: `page=5000000` and `perPage=500` can make `from` negative and cause `subList` to throw.

Impact:

The API can return a 500 instead of an empty page for out-of-range pagination.

Suggested fix:

Use long arithmetic for offset calculation or cap the validated page before slicing.

Status: resolved (P3 pagination overflow fixed in FsApplicationService.responsePage using long offset)
