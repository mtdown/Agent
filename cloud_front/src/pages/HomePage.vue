<template>
  <div id="homePage">
    <div class="search-bar">
      <div class="filter-section">
        <a-tabs v-model:activeKey="selectedCategory" @change="doSearch">
          <a-tab-pane key="all" tab="全部" />
          <a-tab-pane v-for="category in categoryList" :key="category" :tab="category" />
        </a-tabs>
        <div class="tag-bar">
          <span>标签：</span>
          <a-space :size="[0, 8]" wrap>
            <a-checkable-tag
              v-for="(tag, index) in tagList"
              :key="tag"
              v-model:checked="selectedTagList[index]"
              @change="doSearch"
            >
              {{ tag }}
            </a-checkable-tag>
          </a-space>
        </div>
      </div>
      <div class="search-input-section">
        <a-input-search
          placeholder="从海量图片中搜索"
          v-model:value="searchParams.searchText"
          enter-button="搜索"
          size="large"
          @search="doSearch"
        />
      </div>
    </div>

    <a-list
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :pagination="pagination"
      :loading="loading"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item>
          <a-card hoverable @click="doClickPicture(picture)">
            <template #cover>
              <img
                :alt="picture.name"
                :src="picture.thumbnailUrl ?? picture.url"
                loading="lazy"
                style="height: 200px; object-fit: cover"
              />
            </template>
            <a-card-meta :title="picture.name">
              <template #description>
                <a-flex>
                  <a-tag color="green">
                    {{ picture.category ?? '默认' }}
                  </a-tag>
                  <a-tag v-for="tag in picture.tags" :key="tag">
                    {{ tag }}
                  </a-tag>
                </a-flex>
              </template>
            </a-card-meta>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVisByPageUsingPost,
  listPictureVisByPageWithCacheUsingPost,
} from '@/api/pictureController'
import { useRouter } from 'vue-router'

const dataList = ref<API.PictureVis[]>([])
const total = ref(0)
const loading = ref(true)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
  searchText: '',
  nullSpaceId: true,
})
// nullSpaceId用来防止用户看到管理员上传的私有图片

// 使用 computed 来生成分页配置对象，并传递给 a-list
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 12,
    total: total.value,
    showTotal: (total: number) => `共 ${total} 条`,
    onChange: (page: number, pageSize: number) => {
      searchParams.current = page
      searchParams.pageSize = pageSize
      fetchData()
    },
  }
})

const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<boolean[]>([]) // 使用布尔数组来跟踪选中状态

const fetchData = async () => {
  loading.value = true
  // 准备要发送的请求参数
  const activeTags = tagList.value.filter((tag, index) => selectedTagList.value[index])
  const params = {
    ...searchParams,
    category: selectedCategory.value === 'all' ? undefined : selectedCategory.value,
    tags: activeTags,
  }

  try {
    // const res = await listPictureVisByPageUsingPost(params)
    const res = await listPictureVisByPageWithCacheUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0) // 确保 total 是数字
    } else {
      message.error('获取数据失败: ' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取数据失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      categoryList.value = res.data.data.categoryList ?? []
      tagList.value = res.data.data.tagList ?? []
      // 初始化 selectedTagList 为一个全为 false 的布尔数组
      selectedTagList.value = new Array(tagList.value.length).fill(false)
    } else {
      message.error('加载分类标签失败: ' + res.data.message)
    }
  } catch (e: any) {
    message.error('加载分类标签失败: ' + e.message)
  }
}

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const router = useRouter()
const doClickPicture = (picture: API.PictureVis) => {
  router.push(`/picture/${picture.id}`)
}

// 将所有初始化操作合并到一个 onMounted 中
onMounted(() => {
  getTagCategoryOptions()
  fetchData()
})
</script>

<style scoped>
#homePage {
  padding: 0 24px;
}

.search-bar {
  display: flex;
  align-items: flex-start; /* 顶部对齐 */
  margin-bottom: 16px;
}

.filter-section {
  flex: 1; /* 占据大部分空间 */
}

.search-input-section {
  width: 400px; /* 给搜索框一个固定宽度 */
  padding-top: 8px; /* 微调，使其与 Tabs 大致对齐 */
}

.tag-bar {
  margin-top: 8px;
  margin-bottom: 8px;
}
</style>
