<template>
  <div id="globalHeader">
    <!--    自动换行取消-->
    <a-row :wrap="false">
      <a-col flex="200px">
        <router-link to="/">
          <div class="title-bar">
            <!--alt的作用是当别人看到你图片未加载时还有一个文字-->
            <img class="logo" src="../assets/logo.jpg" alt="logo" />
            <div class="title">海东文库</div>
          </div>
        </router-link>
      </a-col>

      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          @click="doMenuClick"
          :items="items"
        /> </a-col
      ><!-- @click="onMenuClick"这行是点击函数的意思，这个内容可以在文档查询到-->

      <!--      这一行表示登录菜单-->
      <a-col flex="120px">
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-avatar src="loginUserStore.loginUser.avatar" size="small" />
            {{ loginUserStore.loginUser.userName ?? '无名' }}
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script lang="ts" setup>
import { h, ref } from 'vue'
import { HomeOutlined } from '@ant-design/icons-vue'
import type { MenuProps } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
const loginUserStore = useLoginUserStore()

const items = ref<MenuProps['items']>([
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/about',
    label: '关于',
    title: '关于',
  },
  {
    key: 'others',
    label: h('a', { href: 'https://www.codefather.cn', target: '_blank' }, '编程导航'),
    title: '编程导航',
  },
  {
    key: '个人博客',
    label: h('a', { href: 'https://mtdown.top', target: '_blank' }, '个人博客'),
    title: '个人博客',
  },
])

const route = useRouter()
const doMenuClick = ({ key }) => {
  router.push({
    path: key,
  })
}

const current = ref<string[]>([''])
router.afterEach((to) => {
  current.value = [to.path]
})
</script>

<style scoped>
.title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: blue;
  font-size: 28px;
  margin-left: 16px;
}

.logo {
  height: 48px;
}
</style>
