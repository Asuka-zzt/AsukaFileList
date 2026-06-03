# Pull Request

## 1. Summary

- Implement M3: storage mounting (admin CRUD for mounts), LocalDriver (list/get/link with safety), MountedStorageRegistry + StorageResolver + FsApplicationService integration for virtual mounts at `/` and real FS listing under mount paths.
- Includes M2 prerequisite: authentication (JWT), roles, permissions (PATH_LIMIT etc), admin user/role/storage controllers foundation.
- Addressed all findings from `docs/review/m3-storage-localdriver-review.md` in a follow-up fix commit.
- Added unit/integration tests; design doc + review notes.

Refs: `feat(storage)/m3-local-driver`

## 2. Why

Before this PR:
- No storage mount concept; `/api/fs/list` had no real backend (only scaffold).
- No admin APIs for managing storage/drivers.
- No Local driver; auth was incomplete (no roles/perms for path scoping).
- M2/M3 features developed on feature branches, not merged to main.

After this PR:
- Admin can register Local mounts (with rootPath whitelisted), enable/disable/delete.
- `GET /api/admin/driver/{list,names}` exposes driver metadata for UI.
- `POST /api/fs/list` at `/` returns virtual mount entries (1st level); under mount e.g. `/local` returns real directory listing from LocalDriver.
- Path safety: whitelist + normalize + no traversal; PATH_LIMIT users cannot bypass via direct mount access.
- Responses respect user's `basePath` (no internal prefix leak).
- Full M2 auth + role/permission system (used by admin guards and fs path limits).
- All per `docs/m3-design.md` and `docs/m2-design.md`.

This enables the foundation for "网盘挂载" in the AList-like filelist + RAG product.

## 3. Implementation Details

This PR mainly changes:

1. **Driver layer** (`infrastructure/driver/`): DriverInfo/DriverItem, extend Registry/Factory; full LocalDriver impl (with addition parse, root validate+whitelist, list/get/link, path safety using PathUtils + normalize).
2. **Storage app** (`application/storage/`): MountedStorageRuntime/Registry (concurrent map, longest prefix match, mount/unmount/init status), StorageResolver (request->actual), StorageApplicationService (CRUD + runtime lifecycle), StorageRuntimeInitializer (on boot), StorageModelMapper.
3. **API** (`api/controller/`, `api/request/`, `api/response/`): AdminDriverController, AdminStorageController + DTOs (create/update/id/enable etc); also full M2 admin/auth controllers (User/Role/Me/Auth) + security (JWT, interceptor, CurrentUser resolver).
4. **Fs integration**: FsApplicationService.list now joins basePath, resolves via StorageResolver, supports virtualChildren for mounts, delegates to driver, maps back paths, pagination+sort. Added PermissionApplicationService wiring + visible path stripping.
5. **Config/Security**: AsukaProperties local-root-whitelist; full auth app services (Password, Token, CurrentUserService); PermissionBits + scopes; DefaultAccountInitializer.
6. **Persistence**: Entities/mappers for User/Role/Storage/Share/Task etc (from M1/M2); Flyway V1 init (no M3 schema change).
7. **Fixes (last commit)**: See review P1/P2/P3 addressed in FsApplicationService + LocalDriver.

Key design decisions:
- Mounts use longest-prefix match on mountPath (in-memory for M3; single node).
- Local only for M3 (read-only list); writes, proxy download, multi-driver in later Ms.
- Admin APIs protected by role (M2); fs list uses CurrentUser + PermissionApplicationService for PATH_LIMIT.
- Response paths always user-visible (strip basePath) so clients see consistent `/local/..` regardless of internal base.
- rootPath must be whitelisted absolute existing dir; all access normalized + contained.
- No new DB columns (storages table from M1 sufficient); status work/disabled/init_error.
- Followed AGENTS.md: min change (no unrelated refactor), <=300/80 lines, BusinessException only, comments, tests.

See `docs/m3-design.md` §6-11 for flows, exceptions, tests; `docs/review/m3-storage-localdriver-review.md` for findings + resolutions.

## 4. Modified Files

| File | Description |
|---|---|
| `src/main/java/.../infrastructure/driver/local/LocalDriver.java` + Factory/Addition | Core Local FS driver impl + safety |
| `.../application/storage/*` (Registry, Resolver, Service, Runtime, Initializer, Mapper) | Mount runtime + app logic |
| `.../api/controller/AdminStorageController.java` + requests/responses | Storage admin APIs |
| `.../api/controller/AdminDriverController.java` + responses | Driver metadata APIs |
| `src/main/java/.../application/fs/FsApplicationService.java` | Integrate resolver + virtual/real list + fixes |
| `.../api/controller/{Auth,AdminUser,AdminRole,Me,Fs}*.java` + security/* | M2 auth + roles + perm (full stack) |
| `src/main/java/.../domain/user/{Permission*,Role,User}*` + app services | Permission model + role/user app |
| `src/main/resources/db/migration/V1__init_schema.sql` + entities/mappers | Schema + persistence for M1/M2 |
| `src/main/resources/application.yml` + `.env.example` + AsukaProperties | Config for whitelist, jwt etc |
| `src/test/.../LocalDriverTest.java`, `MountedStorageRegistryTest.java`, `AdminStorageControllerTest.java` + M2 tests | Unit + @SpringBootTest integration (fs list after create) |
| `docs/m3-design.md`, `docs/review/m3-storage-localdriver-review.md`, `AGENTS.md` | Design + review notes + guideline update |
| `pom.xml`, `docker-compose.yml`, README etc | Deps (H2 for test etc), compose updates |

(Full: 78+ files, ~4760+ lines net from origin/main; see `git diff --stat origin/main..HEAD`)

## 5. Commits

(Ordered oldest -> newest; followed per-module split, no giant commit)

- ae60c33 feat(auth): implement M2 authentication and roles
- fcacf9a docs(storage): add M3 storage mounting design doc and review notes; update AGENTS commit guideline
- cd7c2ef config(storage): add local-root-whitelist support in AsukaProperties and application configs; update affected test
- c2582bc feat(driver): add DriverInfo/DriverItem models; extend Factory and Registry for admin driver listing
- e6ffbc5 feat(driver): implement LocalDriver with list/get/link, path safety checks and whitelist validation
- bf94044 feat(storage): add MountedStorageRegistry/Runtime, StorageResolver, StorageApplicationService and runtime initializer
- 70541ca feat(api): add AdminDriver/AdminStorage controllers with request/response DTOs for storage management
- 0696c13 feat(fs): integrate StorageResolver into FsApplicationService.list for virtual mounts and LocalDriver-backed listing
- a9fc997 test(storage): add unit tests for LocalDriver, MountedStorageRegistry and AdminStorageController
- 095c22c fix(storage): address M3 code review findings for path permission, visible paths, pagination and Local root validation

## 6. Test & Verification

- Design: `docs/m3-design.md` (interfaces, flows, exceptions, test strategy) + review doc updated.
- `mvn compile -q` : success (multiple runs).
- `mvn test` : BUILD SUCCESS, 29/29 tests green (including LocalDriverTest, MountedStorageRegistryTest, AdminStorageControllerTest which covers create+fs/list+disable, Permission tests, full auth tests).
- Specific: admin login -> create Local storage under temp -> /api/fs/list returns files; disable -> 404 STORAGE_NOT_FOUND; non-admin -> 403 on admin APIs.
- Checklist (per AGENTS.md §4 + docs/rules/coding.md):
  - [x] docs/ design + review present
  - [x] feat/storage branch (no direct main)
  - [x] no new Flyway (M1 storages sufficient)
  - [x] user data: fs uses CurrentUser + perm; admin via roles (M2)
  - [x] all errors via BusinessException(ErrorCode.*)
  - [x] line limits: key files <300 lines (FsApp 238, LocalDriver 266, StorageApp 253)
  - [x] compile + tests green
  - [x] review issues fixed + documented
  - Path safety via PathUtils.sanitize/fix + whitelist + contains check.
- Manual: would start with spring-boot:run + admin create mount + curl fs/list (but CI/tests cover).

## 7. Notes / Risks

- This PR merges M2 auth + M3 storage (the branch was advanced on top of M2 commit).
- No upload/download yet (M4).
- Single-node in-mem registry (no redis sync yet).
- For users with basePath != "/", mounts should be created under the base (admin responsibility); responses now correct.
- gh pr will be created after user confirmation (no auto-push per AGENTS.md rule 7).

## 8. How to review / verify locally

```bash
git fetch origin
git checkout feat(storage)/m3-local-driver
mvn clean compile -q
mvn test -Dtest=*Storage*,*LocalDriver*,*Auth*,*Permission* -q
# or full: mvn test
```

Then (after merge) test endpoints with admin token etc.
