# AsukaFileList Web Frontend Design (M4+ parallel)

## 1. 目标与范围

为 AsukaFileList 提供 AList 风格的现代化 Web UI，实现“网盘挂载浏览 + 语义搜索 + 知识库问答”的一体化用户体验。

**本阶段（初始交付）重点**：
- 登录 / 登出 + 当前用户信息
- 文件列表浏览器（基于 M3 的虚拟挂载 + LocalDriver 只读 list）
- 管理员存储挂载管理（完整 CRUD + 启用/禁用，使用动态表单）
- 基础布局、权限感知（admin 入口、basePath 支持）
- 与后端现有 API 完全对接（M2 auth + M3 storage/fs）

**不包含（后续）**：
- 完整文件读写（mkdir/rename/upload/download）—— 等 M4 后端完成
- AI 语义搜索 / Chat 界面（等 M7 联调）
- 分享、WebDAV 客户端、任务中心等高级页面

参考：`ref/alist` 的用户体验 + 管理后台（存储添加流程、driver 配置项动态表单、左侧挂载列表、文件表格、面包屑、管理入口）。

## 2. 技术栈选择

| 层 | 技术 | 理由 |
|----|------|------|
| 框架 | React 18 + TypeScript + Vite | 现代、类型安全、开发体验好；与 ref/PandaWiki/web 等 TSX 参考一致 |
| UI | Tailwind CSS + lucide-react + 少量自定义组件（或按需引入 Ant Design 5） | 快速实现 AList 干净风格（侧边栏 + 表格 + 表单）；Tailwind 轻量，先不用重 Antd |
| 路由 | react-router-dom v6 | SPA，/login、/、/@admin 或 /admin |
| 数据 | @tanstack/react-query | API 缓存、loading、error、mutation 极佳 |
| HTTP | axios | 拦截器加 token、统一错误处理 |
| 状态 | Zustand (轻量) 或 Context + Query | token / currentUser 全局 |
| 构建集成 | 暂独立 `web/` 目录，dev 用 proxy；后续加 frontend-maven-plugin + 复制 dist 到 resources/static | 符合 AList "public/dist + 静态托管 index.html fallback" 模式 |
| 图标/样式 | Tailwind + lucide-react | 轻量，AList 风格可通过 class 模拟（蓝色主色、卡片、表格） |

目录结构（参考 PandaWiki/web + 常见实践）：
```
web/
  package.json
  vite.config.ts          # proxy: /api -> http://localhost:8080
  tsconfig.json
  index.html
  src/
    main.tsx
    App.tsx
    api/                  # axios instance + typed calls (fs, admin, auth)
    components/
      Layout.tsx          # Header + Sidebar + Content
      FileTable.tsx
      StorageForm.tsx     # 动态表单核心
      ...
    pages/
      Login.tsx
      FileBrowser.tsx
      Admin/
        Storages.tsx
        ...
    stores/
      auth.ts
    types/                # 复制或手动对齐后端 DTO（或用 openapi 后续）
    utils/
```

## 3. 核心页面与交互（AList 风格对齐）

### 3.1 登录页 (/)
- 居中卡片表单：username + password
- 调用 `POST /api/auth/login`
- 成功：存 accessToken + user 到 store/localStorage，重定向到 `/`
- 失败：展示 message

### 3.2 主布局 (所有已登录页面)
- **Header**：Logo "AsukaFileList" | 全局搜索框（占位，后续 AI） | 刷新按钮 | 用户头像 + 下拉（用户名、basePath 提示、切换 Admin、Logout）
- **Sidebar (左)**：可折叠
  - 我的文件（根）
  - 按挂载点虚拟列表：从 `/api/fs/list {path: "/"}` 得到的虚拟目录，或从 storage list 派生（更可靠）
  - 点击挂载点进入对应 path
- **主内容区**：
  - Breadcrumb（/local / sub）
  - Toolbar：Refresh | New Folder(占位) | Upload(占位) | View toggle (list/grid 未来)
  - 文件/目录表格：
    - 列：名称（带文件夹/文件图标）、大小、修改时间、操作（复制路径、详情等 M3 可用）
    - 支持分页（page/perPage），或简单 perPage=-1 先拿全量
    - 点击目录：push 路由或更新 path，重新 list
    - 虚拟挂载项特殊显示（storageClass: "virtual"）
  - 空状态、loading、error 处理

数据来源：
- 当前 path list：`POST /api/fs/list {path, page, perPage, refresh}`
- 响应 `content[]` 中的 `path/name/isDir/size/modified` 直接用（已处理 basePath 相对路径）

### 3.3 管理后台 ( /admin 或点击 "管理" 进入，admin 权限可见 )
参考 AList 管理存储流程：
- 顶部 Tab 或独立侧边：存储管理 / 用户管理(未来) / 角色(未来) / 设置
- **存储列表页**：
  - 表格：mountPath | driver | status (work/disabled/init_error 带颜色) | remark | orderNo | 操作列（编辑、启用/禁用、删除）
  - "添加存储" 按钮 → 抽屉/Drawer
    - 第一步：选择 Driver（调用 `GET /api/admin/driver/list` 或 /names + 详情）
    - 第二步：动态表单：
      - mountPath (必填)
      - addition 字段：根据选中的 DriverInfoResponse.items 渲染 input/checkbox
        - type=string → <input>
        - type=bool → <checkbox>
        - 带 label、required、defaultValue、description (tooltip)
      - 其他：orderNo, remark, disabled
    - 提交 `POST /api/admin/storage/create`
  - 编辑类似，预填当前 addition (Map)
  - 启用/禁用/删除直接调用对应 POST + 刷新列表

权限：非 admin 用户隐藏"管理"入口，或进入后 403。

### 3.4 其他
- 路由保护：未登录跳 /login；admin 页面检查 `currentUser.admin`
- 全局错误处理：token 过期自动跳登录（axios interceptor）
- 响应式：移动端先简单，sidebar 可抽屉

## 4. API 对接清单（当前可用）

Auth:
- POST /api/auth/login {username, password} → {accessToken, user}
- GET /api/me → CurrentUserResponse (含 basePath, admin, roles)

Fs:
- POST /api/fs/list {path, page?, perPage?, refresh?} → FsListResponse

Admin Driver:
- GET /api/admin/driver/list → DriverInfoResponse[] （用于动态表单）
- GET /api/admin/driver/names

Admin Storage:
- GET /api/admin/storage/list → StorageResponse[]
- POST /api/admin/storage/create
- POST /api/admin/storage/update
- POST /api/admin/storage/enable {id}
- POST /api/admin/storage/disable {id}
- POST /api/admin/storage/delete {id}

统一响应：`{success, code, message, data, timestamp}` — 前端优先用 `data`，code != "OK" 时 error。

Token：请求头 `Authorization: Bearer ${accessToken}`

## 5. 认证与状态流

1. 登录成功后把 token 存 localStorage + zustand。
2. 每次请求 axios interceptor 自动带上（无则不带）。
3. App 初始化时如果有 token，调用 /api/me 恢复 currentUser。
4. 401/403 统一跳转登录并清 token。
5. Logout：清 store + local + 调用 /api/auth/logout（可选） + 跳登录。

## 6. 开发与构建

**本地开发**：
```bash
# 1. 启动后端 (需要 DB 等)
mvn spring-boot:run
# 或 docker compose --profile app up

# 2. 前端
cd web
npm install
npm run dev   # http://localhost:5174 ，vite proxy /api 到 8080
```

vite.config.ts 示例：
```ts
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

**生产构建**（后续）：
- `npm run build` 产出 `dist/`
- 复制到 Java `src/main/resources/static` 或使用 Maven plugin 在 package 阶段自动构建并嵌入
- Java Spring Boot 直接 serve 静态 + SPA fallback（需配置 WebMvcConfigurer 处理非 api 路由返回 index.html）
- 或独立部署前端 + CORS

环境变量：
- VITE_API_BASE（可选，dev 用 proxy 即可）

## 7. 样式与 AList 对齐细节

- 主色：#1677ff 或 AList 蓝
- 字体：系统 sans
- 挂载点在侧边以文件夹图标 + mountPath 显示
- 文件列表使用 `<table>` 或 div grid，hover 效果
- 状态 badge：work=green, disabled=gray, init_error=red
- 表单使用 label + input + 说明文字
- 空目录/无挂载显示友好文案

## 8. 风险与后续演进

- 后端 API 尚不完整（无写操作、无下载签名），前端操作按钮先 disabled + tooltip "即将支持"
- 动态表单需健壮处理 addition 各种类型
- 大列表性能：先用简单分页，后续虚拟滚动或 driver localSort
- 主题/自定义：预留 site config 注入点（参考 AList index.html replace）
- 国际化：先中文，后续 i18n
- 构建集成：M4 后或单独 PR 加入 pom + Dockerfile 多阶段构建

## 9. 验收标准

- `cd web && npm run build` 无错
- 登录 → 看到虚拟挂载 → 点击进入能列出真实文件（Local）
- Admin 创建 Local storage（使用 driver list 的 items 渲染表单）成功，列表立即出现，可启用/禁用/删
- 刷新、面包屑、登出正常
- 非 admin 看不到管理入口
- 控制台无 401/跨域，token 正确传递
- README 更新启动说明

## 10. 实现顺序（迭代交付）

1. Scaffold + auth + layout + /me
2. FileBrowser + fs/list + 虚拟挂载 sidebar
3. Admin Storages 列表 + 启用禁用删除
4. 动态 Driver 表单 + create/update
5. 样式打磨 + 错误处理 + 文档

写完设计后等待确认，再切入编码。

Refs: docs/m3-design.md (接口), ref/alist/server/handles/storage.go & static/static.go (AList 管理与静态托管模式)
