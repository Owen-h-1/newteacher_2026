# 智课云师 · 最小迁移包

从当前仓库抽取的**仅全局主题**：设计变量（CSS 自定义属性）+ 与本项目 class 绑定的覆盖样式 + 路由过渡片段。不包含各页面私有样式（如 `TeacherLayout.vue` 内 scoped 等）。

## 文件说明

| 文件 | 作用 |
|------|------|
| `edu-theme-minimal.css` | `:root` 令牌 + `body`/`#app`/卡片/按钮/表单/登录页等全局覆盖 |
| `app-router-transition.css` | 全站路由 `page-fade-slide` 过渡动画 |

## 在「原版」中接入（任选一种）

### Vue / Vite

1. 将 `migration` 文件夹复制到原版项目（或只复制两个 `.css`）。
2. 在入口 `main.ts` / `main.js` 中：

```ts
import './migration/edu-theme-minimal.css'
import './migration/app-router-transition.css'
```

3. 根组件（如 `App.vue`）使用与当前项目相同的 `router-view` 包裹：

```vue
<template>
  <router-view v-slot="{ Component }">
    <transition name="page-fade-slide" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
```

### 非 Vue / 纯 HTML

在 `</head>` 前引入：

```html
<link rel="stylesheet" href="./migration/edu-theme-minimal.css" />
<link rel="stylesheet" href="./migration/app-router-transition.css" />
```

（路径按实际放置调整。）

## 原版必须对齐的约定（否则效果打折）

本 CSS 通过 **class 名** 生效，原版需使用相同命名，或自行改选择器：

- 页面容器：`.page-container`
- 卡片：`.card-custom`、次级 `.card-secondary`
- 按钮：`.btn-primary`、`.btn-secondary`、`.btn-text`
- 题目发布等：`.type-btn`、`.btn-publish`、`.btn-preview`、`.btn-ai-generate`、`.btn-clear`、`.btn-add-question`、`.btn-login`
- 表单：`.input-custom`、`.select-custom`（或依赖裸 `input`/`select`/`textarea` 的全局规则）
- 布局块：`.layout-sidebar`、`.layout-content`、`.form-card`、`.question-list-card`、`.search-filter-bar`、`.filter-area` 等

登录页需有外层 `.login-page`，内部 `.login-container`、`.brand-side`、`.btn-login` 与当前项目结构一致，登录区样式才会命中。

## 与当前仓库的关系

- 内容等价于 `src/assets/main.scss` 中 **「5. 统一视觉规范覆盖」** 整段（已转为纯 `.css`，无 Sass 依赖）。
- **不包含**：`main.scss` 第 1～4 节（旧版 `$color-*` 变量、`.btn-primary` 第一套定义等）。若原版未引入任何基础样式，可只依赖本包；若原版已有冲突样式，请**后加载**本文件或提高优先级。

## 可选：只迁变量

若只需调色，可只复制 `edu-theme-minimal.css` 顶部的 `:root { ... }`，再自行写组件样式引用 `var(--edu-primary)` 等。
