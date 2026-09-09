<template>
  <section class="manage-section">
    <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
      <h3>文档空间管理</h3>
      <a-space>
        <a-input v-model:value="newTeamName" placeholder="团队空间名称" />
        <a-button type="primary" @click="createTeamSpace">创建团队空间</a-button>
      </a-space>
    </a-flex>
    <a-table
      :data-source="manageSpaces"
      :pagination="false"
      row-key="id"
      :loading="loading"
      class="manage-table"
    >
      <a-table-column title="名称" data-index="name" />
      <a-table-column title="状态">
        <template #default="{ record }">{{ record.isDelete === 1 ? '已删除' : '正常' }}</template>
      </a-table-column>
      <a-table-column title="操作">
        <template #default="{ record }">
          <a-space wrap>
            <a-button v-if="record.isDelete !== 1" @click="openRename(record)">重命名</a-button>
            <a-button @click="selectManageSpace(record)">成员</a-button>
            <a-button v-if="record.isDelete === 1" @click="restoreTeamSpace(record)">恢复</a-button>
            <a-button v-if="record.isDelete === 1" danger @click="permanentDeleteTeamSpace(record)">
              永久删除
            </a-button>
            <a-button v-else danger @click="deleteTeamSpace(record)">删除</a-button>
          </a-space>
        </template>
      </a-table-column>
    </a-table>

    <div v-if="selectedManageSpaceId" class="member-panel">
      <a-flex justify="space-between" align="center" wrap="wrap" gap="middle">
        <h3>成员管理</h3>
        <a-space>
          <a-select
            v-model:value="memberUserId"
            show-search
            allow-clear
            placeholder="搜索并选择用户"
            style="min-width: 240px"
            :filter-option="false"
            :options="memberOptions"
            :disabled="!selectedManageSpaceId"
            @search="searchUsers"
          />
          <a-select v-model:value="memberRole" style="width: 120px">
            <a-select-option value="viewer">viewer</a-select-option>
            <a-select-option value="editor">editor</a-select-option>
            <a-select-option value="admin">admin</a-select-option>
          </a-select>
          <a-button @click="addMember">添加成员</a-button>
        </a-space>
      </a-flex>
      <a-list :data-source="members">
        <template #renderItem="{ item }">
          <a-list-item>
            <template #actions>
              <a-button type="link" danger @click="removeMember(item)">移除</a-button>
            </template>
            {{ item.user?.userName ?? item.userId }} / {{ item.spaceRole }}
          </a-list-item>
        </template>
      </a-list>
    </div>

    <a-modal
      v-model:open="renameVisible"
      title="重命名空间"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="renaming"
      @ok="submitRename"
    >
      <a-input
        v-model:value="renameName"
        placeholder="请输入空间名称"
        @press-enter="submitRename"
      />
    </a-modal>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { IdValue } from './wikiShared'
import { listUserVisByPageUsingPost } from '@/api/userController.ts'
import {
  addTeamMemberUsingPost,
  addTeamSpaceUsingPost,
  deleteTeamSpaceUsingPost,
  listManageTeamSpacesUsingGet,
  listTeamMembersUsingGet,
  permanentDeleteTeamSpaceUsingPost,
  removeTeamMemberUsingPost,
  renameSpaceUsingPost,
  restoreTeamSpaceUsingPost,
} from '@/api/wikiSpaceController.ts'
const props = defineProps<{ loading: boolean; active: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const manageSpaces = ref<API.WikiSpaceVis[]>([])
const selectedManageSpaceId = ref<IdValue>()
const members = ref<API.WikiSpaceUserVis[]>([])
const newTeamName = ref('')
const memberUserId = ref<IdValue>()
const memberRole = ref('editor')
const userOptions = ref<API.UserVis[]>([])
const userSearchTimer = ref<number>()
const renameVisible = ref(false)
const renameName = ref('')
const renameTargetId = ref<IdValue>()
const renaming = ref(false)
const fetchManageSpaces = async () => {
  const res = await listManageTeamSpacesUsingGet()
  if (res.data.code === 0) {
    manageSpaces.value = res.data.data ?? []
  } else {
    message.error('获取团队空间失败，' + res.data.message)
  }
}

const createTeamSpace = async () => {
  if (!newTeamName.value) return
  const res = await addTeamSpaceUsingPost({ name: newTeamName.value })
  if (res.data.code === 0) {
    message.success('团队空间已创建')
    newTeamName.value = ''
    await fetchManageSpaces()
    emit('changed')
  } else {
    message.error('创建团队空间失败，' + res.data.message)
  }
}

// Members are picked from the existing users instead of typing a raw user id. Users that are
// already in the space stay visible but disabled, so the admin can tell "already added" apart
// from "not found".
const memberOptions = computed(() =>
  userOptions.value.map((user) => {
    const name = user.userName || user.userAccount || String(user.id ?? '')
    return {
      label: `${name}（${user.userAccount ?? user.id}）`,
      value: user.id,
      disabled: members.value.some((member) => String(member.userId) === String(user.id)),
    }
  }),
)

const fetchUsers = async (keyword?: string) => {
  const res = await listUserVisByPageUsingPost({
    current: 1,
    pageSize: 20,
    userName: keyword?.trim() ? keyword.trim() : undefined,
  })
  if (res.data.code === 0) {
    userOptions.value = res.data.data?.records ?? []
  } else {
    message.error('获取用户列表失败，' + res.data.message)
  }
}

const searchUsers = (keyword: string) => {
  window.clearTimeout(userSearchTimer.value)
  userSearchTimer.value = window.setTimeout(() => fetchUsers(keyword), 300)
}

const selectManageSpace = async (space: API.WikiSpaceVis) => {
  selectedManageSpaceId.value = space.id
  memberUserId.value = undefined
  const res = await listTeamMembersUsingGet({ spaceId: space.id })
  if (res.data.code === 0) {
    members.value = res.data.data ?? []
  }
  await fetchUsers()
}

const addMember = async () => {
  if (!selectedManageSpaceId.value || !memberUserId.value) return
  const res = await addTeamMemberUsingPost({
    spaceId: selectedManageSpaceId.value,
    userId: memberUserId.value,
    spaceRole: memberRole.value,
  })
  if (res.data.code === 0) {
    message.success('成员已添加')
    memberUserId.value = undefined
    await selectManageSpace({ id: selectedManageSpaceId.value })
    emit('changed')
  } else {
    message.error('添加成员失败，' + res.data.message)
  }
}

const openRename = (space: API.WikiSpaceVis) => {
  renameTargetId.value = space.id
  renameName.value = String(space.name ?? '')
  renameVisible.value = true
}

// Renaming emits `changed` so the parent reloads the space list; the navigation tree renders
// space names from the same list and therefore picks the new name up too.
const submitRename = async () => {
  const name = renameName.value?.trim()
  if (!renameTargetId.value || !name) {
    message.warning('请输入空间名称')
    return
  }
  renaming.value = true
  try {
    const res = await renameSpaceUsingPost({ id: renameTargetId.value, name })
    if (res.data.code === 0) {
      message.success('空间已重命名')
      renameVisible.value = false
      await fetchManageSpaces()
      emit('changed')
    } else {
      message.error('重命名失败，' + res.data.message)
    }
  } finally {
    renaming.value = false
  }
}

const removeMember = async (member: API.WikiSpaceUserVis) => {
  if (!selectedManageSpaceId.value || !member.userId) return
  const res = await removeTeamMemberUsingPost({
    spaceId: selectedManageSpaceId.value,
    userId: member.userId,
  })
  if (res.data.code === 0) {
    message.success('成员已移除')
    await selectManageSpace({ id: selectedManageSpaceId.value })
  } else {
    message.error('移除成员失败，' + res.data.message)
  }
}

const deleteTeamSpace = async (space: API.WikiSpaceVis) => {
  Modal.confirm({
    title: '确认删除团队空间？非空空间会进入管理区待恢复或永久删除。',
    async onOk() {
      const res = await deleteTeamSpaceUsingPost({ id: space.id, confirm: true })
      if (res.data.code === 0) {
        message.success('团队空间已删除')
        await fetchManageSpaces()
        emit('changed')
      } else {
        message.error('删除团队空间失败，' + res.data.message)
      }
    },
  })
}

const restoreTeamSpace = async (space: API.WikiSpaceVis) => {
  const res = await restoreTeamSpaceUsingPost({ id: space.id })
  if (res.data.code === 0) {
    message.success('团队空间已恢复')
    await fetchManageSpaces()
    emit('changed')
  } else {
    message.error('恢复团队空间失败，' + res.data.message)
  }
}

const permanentDeleteTeamSpace = async (space: API.WikiSpaceVis) => {
  Modal.confirm({
    title: '永久删除团队空间后不可恢复，确认继续？',
    async onOk() {
      const res = await permanentDeleteTeamSpaceUsingPost({ id: space.id, confirm: true })
      if (res.data.code === 0) {
        message.success('团队空间已永久删除')
        await fetchManageSpaces()
        emit('changed')
      } else {
        message.error('永久删除团队空间失败，' + res.data.message)
      }
    },
  })
}

watch(
  () => props.active,
  (active) => {
    if (active) fetchManageSpaces()
  },
  { immediate: true },
)
defineExpose({ refresh: fetchManageSpaces })
</script>
<style scoped>
.manage-table,
.member-panel {
  margin-top: 16px;
}
</style>
