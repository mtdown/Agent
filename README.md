# AI 文库项目学习笔记

# 6.Ai成图功能导入

这个应该比较简单

# 5.用户私有空间模块

问题开始出现了，心累。记录一下，以后修改：

1.管理员添加的图片同时在它的私有空间和公共空间

增加默认查询参数nullSpaceId: true,解决问题

2.用户添加的图片无法在自己的私有空间中看到

这个没找到解决办法

## 4. 核心功能 - 用户模块

在完成项目初始化和前后端联动配置后，我们开始开发第一个核心功能模块——用户模块。该模块是系统的基础，涵盖了用户的整个生命周期，包括注册、登录、信息管理和权限控制。

### 4.1. 用户模块 (后端)

**目标:** 实现用户注册、登录、权限控制和后台管理等一套完整的服务端功能。

#### 功能实现 breakdown

1. **库表设计**:
   - 设计了 `user` 表，包含 `userAccount` (唯一)、`userPassword`、`userName`、`userRole` (默认为 `user`) 等核心字段。
   - 使用了逻辑删除字段 `isDelete`，并通过 `@TableLogic` 注解实现了无感知的逻辑删除功能。
2. **用户注册与登录**:
   - **注册**: 实现了注册接口，包含对账号、密码长度、两次密码一致性等参数的后端校验。
   - **密码加密**: 为了数据安全，密码在存入数据库前，通过 `DigestUtils.md5DigestAsHex` 结合固定盐值 (`SALT`) 进行了 MD5 加密。
   - **登录**: 实现了登录接口，通过查询加密后的密码来验证用户身份。登录成功后，将脱敏后的用户信息存入 `Session`，作为后续请求的用户凭证。
3. **信息脱敏与封装**:
   - 遵循“最小暴露”原则，创建了 `LoginUserVis` 和 `UserVis` 类。
   - 在返回给前端数据时，通过 `BeanUtils.copyProperties` 将 `User` 实体对象转换为 Vis 对象，去除了 `userPassword` 等敏感字段，保证了接口的安全性。
4. **权限控制 (AOP)**:
   - 这是后端的一个设计亮点。通过 **Spring AOP** 和 **自定义注解** 实现了声明式的权限控制。
   - **`@AuthCheck` 注解**: 一个自定义注解，可以标记在任何 Controller 方法上，通过 `mustRole` 参数指定访问该接口所必需的角色（如 `admin`）。
   - **`AuthInterceptor` 切面**: 使用 `@Aspect` 定义了一个切面，通过 `@Around` 环绕通知拦截所有标记了 `@AuthCheck` 的方法。在方法执行前，切面会获取当前登录用户并校验其角色，如果权限不足则直接抛出异常，实现了与业务逻辑的完全解耦。
5. **用户管理 (CRUD)**:
   - 为管理员提供了完整的用户增、删、改、查功能。
   - **查询**: 实现了分页、模糊查询和排序功能。通过 `getQueryWrapper` 方法动态构建 MyBatis-Plus 的查询条件，代码逻辑清晰且扩展性强。
   - **接口保护**: 所有管理接口均通过 `@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)` 注解进行了保护，确保只有管理员才能调用。
6. **通用问题修复**:
   - **分页插件配置**: 通过添加 `MyBatisPlusConfig` 配置类并注册 `PaginationInnerInterceptor`，解决了 MyBatis-Plus 分页插件不生效的问题。
   - **JSON精度修复**: 由于 JavaScript 的 `Number` 类型无法精确表示 Java 的 `Long` 类型最大值，通过 Spring MVC 的 `JsonConfig` 将所有返回的 `Long` 类型序列化为 `String`，解决了前端ID精度丢失的问题。



### 4.2. 用户模块 (前端)

**目标:** 构建用户登录、注册、管理等页面，并实现全局的用户状态管理和前端路由权限控制。

#### 核心实现分析

1. **页面与路由**:
   - 在 `pages` 目录下创建了 `UserLoginPage.vue`、`UserRegisterPage.vue` 和 `UserManagePage.vue` 三个核心页面。
   - 在 `router/index.ts` 中配置了这些页面的路由规则，将 URL 路径与对应的 Vue 组件关联起来。
2. **全局状态管理 (Pinia)**:
   - 这是前端状态管理的核心。`stores/useLoginUserStore.ts` 负责维护全局的用户登录信息。
   - **`fetchLoginUser`**: 应用初始化或用户登录成功后，会调用此方法，通过 `getLoginUserUsingGet` 接口从后端获取当前用户信息，并将其保存在 `store` 中。
   - **响应式UI**: `GlobalHeader.vue` 等组件会监听 `store` 中的 `loginUser` 状态。当用户登录或注销时，`store` 状态改变，页面右上角会自动切换显示“用户名”或“登录按钮”，实现了UI的响应式更新。
3. **用户操作界面 (Ant Design Vue)**:
   - **登录/注册**: 使用 `<a-form>` 组件构建了标准的登录和注册表单，包含输入校验规则。表单提交时，调用 `openapi` 生成的 `userLoginUsingPost` 等请求函数与后端交互，并使用 `<a-message>` 组件向用户反馈操作结果（如“登录成功”）。
   - **用户管理**: `UserManagePage.vue` 页面使用 `<a-table>` 组件展示用户列表。
     - **数据获取**: 在组件挂载时 (`onMounted`) 和分页/搜索条件变化时，调用 `fetchData` 方法，通过 `listUserVoByPageUsingPost` 接口获取数据并更新表格。
     - **交互功能**: 实现了搜索表单和表格分页功能，通过监听 `@change` 事件和点击搜索按钮来更新查询参数并重新请求数据。
4. **前端权限控制 (路由守卫)**:
   - 在 `src/access.ts` 文件中，利用 `vue-router` 的 **全局前置守卫 (`router.beforeEach`)** 实现了前端的权限控制。
   - **工作流程**: 在每次路由跳转前，守卫会触发。它会检查目标路径是否为 `/admin` 开头。如果是，则会从 `useLoginUserStore` 中获取当前用户的角色，判断是否为 `admin`。
   - **拦截与重定向**: 如果用户不是管理员却试图访问管理员页面，守卫会中断当前的导航，并通过 `message.error` 给出提示，然后将用户重定向到登录页。这有效地保护了前端的敏感页面。

------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

## 1.项目初始化 (后端)

**目标:** 搭建整个 Spring Boot 项目的基础架构，为后续所有功能模块提供通用的响应、异常处理和基础配置。

### 项目UML类图

![UML后端结构图](https://github.com/mtdown/Agent/blob/main/UML%E5%90%8E%E7%AB%AF.svg?raw=true)

### 核心组件分析

#### 1. 统一响应体 (`commen` 包)

-   **`BaseResponse<T>`**: 定义了所有后端接口返回给前端的数据结构。包含 `code` (状态码), `data` (泛型数据), `message` (信息) 三个字段，是后端 API 设计规范化的基础。
-   **`ResultUtils`**: 封装了创建 `BaseResponse` 对象的静态方法（如 `success` 和 `error`），简化了代码，避免在业务代码中频繁 `new BaseResponse`。

#### 2. 统一异常处理 (`exception` 包)

-   **`ErrorCode` (枚举)**: 统一定义了项目中所有业务相关的错误码和错误信息，便于管理和维护。
-   **`BusinessException`**: 自定义业务异常类。在业务逻辑中，当遇到可预见的错误（如“用户名不存在”）时，应抛出此异常。
-   **`GlobalExceptionHandler`**: 全局异常处理器。通过 `@RestControllerAdvice` 注解，可以捕获项目中抛出的所有异常（如 `BusinessException`, `RuntimeException` 等），并将其转换为统一格式的 `BaseResponse` 返回给前端，避免了将堆栈信息直接暴露给用户。
-   **`ThrowUtils`**: 异常抛出工具类。通过 `throwIf` 方法，可以用一行代码实现“如果条件成立，则抛出异常”，让业务代码更简洁。

#### 3. 通用配置 (`config` 包)

-   **`CorsConfig`**: 全局跨域配置。解决了前端应用在不同域名下调用后端 API 时产生的跨域问题。

#### 4. 应用入口及健康检查

-   **`CloudApplication`**: Spring Boot 主启动类。
-   **`MainController`**: 提供了一个 `/health` 健康检查接口，用于确认后端服务是否正常运行。

---

## 2.项目初始化 (前端)

**目标:** 使用 Vue3 和 Ant Design Vue 搭建项目的基础布局和导航，为后续页面提供统一的视觉框架。

### 核心组件分析

#### 1. 应用入口 (`App.vue`)

-   [cite_start]**`App.vue`**: 作为 Vue 应用的根组件 [cite: 1][cite_start]，它的核心作用是加载全局布局组件 `BasicLayout` [cite: 1]。它是整个前端页面的起点。

#### 2. 全局布局 (`BasicLayout.vue`)

-   **`BasicLayout.vue`**: 定义了应用的整体页面结构，包括顶部导航栏、中间内容区和底部信息栏。
    -   **`<a-layout-header>`**: 承载全局导航栏 `GlobalHeader`。
    -   **`<a-layout-content>`**: 内部使用 `<router-view />`，这是 Vue Router 的核心，用于根据当前 URL 动态渲染不同的页面组件。
    -   **`<a-layout-footer>`**: 固定的底部栏，包含项目链接等信息。

#### 3. 全局顶栏 (`GlobalHeader.vue`)

-   **`GlobalHeader.vue`**: 实现了网站的顶部导航功能，主要分为三个部分：
    -   **左侧Logo区**: 展示项目 Logo 和名称“海东文库”，点击可返回主页。
    -   **中间菜单区**: 使用 Ant Design 的 `<a-menu>` 组件，动态生成导航菜单，并通过 `vue-router` 实现页面跳转。
    -   **右侧登录区**: 放置一个“登录”按钮，引导用户进行登录操作。

### 学习要点

-   **组件化开发**: 前端界面被拆分为 `App` -> `BasicLayout` -> `GlobalHeader` 等多个层级分明、职责清晰的组件。
-   **路由驱动视图**: 通过 `vue-router` 管理 URL 和视图的对应关系，使用 `<router-view />` 作为页面容器。
-   **UI框架集成**: 借助 Ant Design Vue 快速构建美观且统一的页面布局和组件。

---

## 3. 前后端高效联动 - API自动化

**目标:** 实现后端API变更后，前端能够自动生成对应的请求代码，从而提高开发效率、降低沟通成本并确保类型安全。

### 工作流总览

整个流程分为两步：
1.  **后端自动生成API文档**: 后端项目通过集成 **Knife4j** 等插件，自动扫描所有 Controller 接口，并生成一个遵循 [OpenAPI Specification](https://swagger.io/specification/) (OAS) 标准的 JSON 文件。
2.  **前端基于API文档生成代码**: 前端项目执行一个脚本，通过 **`@umijs/openapi`** 工具读取后端生成的 JSON 文件，并自动生成所有接口的请求函数和 TypeScript 类型定义。

### 3.1. 后端实现：API文档生成

后端通过在 Maven/Gradle 中引入 `knife4j-openapi2-spring-boot-starter` (或其他类似) 依赖，实现了API文档的自动化。

-   **工作原理**:
    -   项目启动后，Knife4j 会自动扫描所有被 `@RestController` 注解标记的类。
    -   它会解析类中的 `@GetMapping`, `@PostMapping` 等注解，以及方法的参数和返回值。
    -   根据这些信息，它在内存中构建了一个描述所有API接口的结构化数据。
-   **最终产物**:
    -   一个可视化的API文档页面，我这里设置访问地址为 `http://localhost:8123/api/doc.html`，方便后端人员自测和前端人员查阅。
    -   一个原始的 OpenAPI 规范JSON文件，访问地址为 `http://localhost:8123/api/v2/api-docs`。**这个地址是前后端联动的关键**。

### 3.2. 前端实现：API代码生成

前端通过 `openapi.config.js` 配置文件和 `@umijs/openapi` 库来消费后端产出的API文档。

-   **核心配置 (`openapi.config.js`)**:

    ```javascript
    import { generateService } from '@umijs/openapi'

    generateService({
      requestLibPath: "import request from '@/request'",
      // 指向后端生成的 OpenAPI JSON 文件的地址
      schemaPath: 'http://localhost:8123/api/v2/api-docs',
      // 生成的前端代码存放目录
      serversPath: './src',
    })
    ```

-   **执行脚本**: 在 `package.json` 中配置了如下命令：

    ```json
    "scripts": {
      "openapi": "node openapi.config.js"
    }
    ```

-   **工作流程**:
    1.  在本地同时启动前端和后端服务。
    2.  在前端项目根目录下，执行 `npm run openapi` 命令。
    3.  `@umijs/openapi` 工具会请求 `schemaPath` 指定的URL，获取到API的JSON描述文件。
    4.  工具会解析这个JSON文件，并自动在 `./src/services` 或类似目录下生成所有接口的TypeScript请求函数，包括完整的参数和返回值类型定义。

### 总结

这种开发模式极大地提升了协同效率。每当后端修改、增加或删除了接口，只需要：
1.  后端重启服务，`api-docs` 就会自动更新。
2.  前端执行一次 `npm run openapi`。

这样，前端就能获得与后端完全同步的、带有类型提示的API请求代码，无需手动编写和调试接口。
