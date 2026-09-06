<template>
  <div id="globalSider">
    <a-layout-sider v-if="loginUserStore.loginUser.id" width="200" breakpoint="lg">
      <a-menu mode="inline" :selected-keys="current" :items="menuItems" @click="doMenuClick" />
    </a-layout-sider>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, ref, watch } from 'vue'
import { type MenuProps, message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOutlined, FolderOutlined, PictureOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'

const loginUserStore = useLoginUserStore()
const router = useRouter()
const route = useRoute()
const teamSpaceList = ref<API.SpaceUserVis[]>([])
const current = computed(() => [route.path.startsWith('/gallery') ? route.path : '/documentWiki'])
const menuItems = computed<MenuProps['items']>(() => [
  { key: '/documentWiki', label: 'Wiki 文档', icon: () => h(BookOutlined) },
  {
    key: 'files',
    label: '文件与图库',
    icon: () => h(FolderOutlined),
    children: [
      { key: '/gallery', label: '公共图库', icon: () => h(PictureOutlined) },
      { key: '/gallery/my_space', label: '我的图片空间', icon: () => h(UserOutlined) },
      ...teamSpaceList.value.map((entry) => ({
        key: '/gallery/space/' + entry.spaceId,
        label: entry.space?.spaceName,
      })),
    ],
  },
])
const doMenuClick: MenuProps['onClick'] = ({ key }) => router.push(String(key))
watch(
  () => loginUserStore.loginUser.id,
  async (id) => {
    teamSpaceList.value = []
    if (!id) return
    const res = await listMyTeamSpaceUsingPost()
    if (res.data.code === 0 && res.data.data) teamSpaceList.value = res.data.data
    else message.error('加载我的团队空间失败，' + res.data.message)
  },
  { immediate: true },
)
</script>
