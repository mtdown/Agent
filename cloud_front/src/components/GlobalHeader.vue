<template>
  <div id="globalHeader">
    <router-link to="/" class="title-bar">
      <img class="logo" src="../assets/logo.jpg" alt="logo" />
      <div class="title">憨带 Wiki</div>
    </router-link>

    <a-menu
      v-model:selectedKeys="current"
      mode="horizontal"
      class="top-menu"
      @click="doMenuClick"
      :items="items"
    />

    <div class="user-login-status">
      <div v-if="loginUserStore.loginUser.id">
        <a-dropdown>
          <a-space class="user-entry"
            ><a-avatar :src="loginUserStore.loginUser.userAvatar" />
            {{ loginUserStore.loginUser.userName ?? '无名' }}</a-space
          >
            <template #overlay>
              <a-menu>
                <a-menu-item>
                  <router-link to="/gallery/my_space">
                    <UserOutlined />
                    我的图片空间
                  </router-link>
                </a-menu-item>
                <a-menu-item @click="doLogout">
                  <LogoutOutlined />
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      <div v-else>
        <a-button type="primary" href="/user/login">登录</a-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, type VNode } from 'vue'
import {
  BookOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  FolderOutlined,
  LogoutOutlined,
  PictureOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { type MenuProps, message } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
const loginUserStore = useLoginUserStore()

type RawNavItem = {
  key: string
  label: string
  icon?: () => VNode
  adminOnly?: boolean
}

const originItems: RawNavItem[] = [
  { key: '/documentWiki', icon: () => h(BookOutlined), label: 'WIKI文档' },
  {
    key: '/documentWiki?region=manage',
    icon: () => h(FolderOpenOutlined),
    label: '文档空间管理',
    adminOnly: true,
  },
  {
    key: '/documentWiki?region=recycle',
    icon: () => h(DeleteOutlined),
    label: '回收站',
  },
  { key: '/gallery', icon: () => h(PictureOutlined), label: '图库功能' },
  {
    key: '/admin/pictureManage',
    icon: () => h(SettingOutlined),
    label: '图片管理',
    adminOnly: true,
  },
  {
    key: '/admin/spaceManage',
    icon: () => h(FolderOutlined),
    label: '图片空间管理',
    adminOnly: true,
  },
  { key: '/admin/userManage', icon: () => h(UserOutlined), label: '用户管理', adminOnly: true },
]

const filterMenus = (menus: RawNavItem[]) => {
  return menus?.filter((menu) => {
    if (menu.adminOnly) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

const items = computed<MenuProps['items']>(() =>
  filterMenus(originItems).map(({ adminOnly: _adminOnly, ...menu }) => menu),
)

const route = useRoute()
const doMenuClick: MenuProps['onClick'] = ({ key }) => router.push(String(key))
const current = computed(() => {
  if (route.path === '/documentWiki' && route.query.region === 'manage') {
    return ['/documentWiki?region=manage']
  }
  if (route.path === '/documentWiki' && route.query.region === 'recycle') {
    return ['/documentWiki?region=recycle']
  }
  if (
    route.path.startsWith('/documentWiki') ||
    route.path.startsWith('/add_documentWiki') ||
    route.path.startsWith('/edit_documentWiki')
  ) {
    return ['/documentWiki']
  }
  return [route.path]
})

const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#globalHeader {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 22px;
  background: #111111;
  border-bottom: 3px solid #e07a2d;
}

.title-bar {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 10px;
  min-width: 150px;
  text-decoration: none;
}

.title {
  color: #fffaf1;
  font-size: 18px;
  font-weight: 500;
  white-space: nowrap;
}

.logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: cover;
}

.top-menu {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  background: transparent;
  border-bottom: 0;
  scrollbar-width: thin;
  scrollbar-color: #e07a2d transparent;
}

.top-menu::-webkit-scrollbar {
  height: 8px;
}

.top-menu::-webkit-scrollbar-thumb {
  background: #e07a2d;
  border-radius: 999px;
}

:deep(.top-menu.ant-menu-horizontal) {
  line-height: 55px;
}

:deep(.top-menu.ant-menu-horizontal > .ant-menu-item),
:deep(.top-menu.ant-menu-horizontal > .ant-menu-submenu) {
  color: #e8ded1;
}

:deep(.top-menu.ant-menu-horizontal > .ant-menu-item:hover),
:deep(.top-menu.ant-menu-horizontal > .ant-menu-item-selected) {
  color: #ffffff;
  background: #2b2520;
}

:deep(.top-menu.ant-menu-horizontal > .ant-menu-item-selected::after),
:deep(.top-menu.ant-menu-horizontal > .ant-menu-item:hover::after) {
  border-bottom-color: #e07a2d;
}

.user-login-status {
  flex: 0 0 auto;
  white-space: nowrap;
}

.user-entry {
  color: #e8ded1;
  cursor: pointer;
}

@media (max-width: 760px) {
  #globalHeader {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
    padding: 12px;
  }

  .top-menu {
    width: 100%;
  }

  .user-login-status {
    display: none;
  }
}
</style>
