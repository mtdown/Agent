<template>
  <!-- 搜索框 -->
  <div class="search-bar">
    <a-input-search
      placeholder="从海量图片中搜索"
      v-model:value="searchParams.searchText"
      enter-button="搜索"
      size="large"
      @search="doSearch"
    />
  </div>
  <!-- 分类 + 标签 -->
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
  <!-- 图片列表 -->
  <a-list
    :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
    :data-source="dataList"
    :pagination="pagination"
    :loading="loading"
  >
    <template #renderItem="{ item: picture }">
      <a-list-item>
        <!-- 单张图片 -->
        <a-card hoverable @click="doClickPicture(picture)">
          <template #cover>
            <img :alt="picture.name" :src="picture.url" style="height: 200px; object-fit: cover" />
          </template>
          <a-card-meta :title="picture.name">
            <template #description>
              <!--              展示多个元素用-->
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
</template>

<script setup lang="ts">
import TheWelcome from '../components/TheWelcome.vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVisByPageUsingPost,
} from '@/api/pictureController.ts'
import { useRouter } from 'vue-router'
const dataList = ref([])
const total = ref(0)
const loading = ref(true)

const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,

    onChange: (page, pageSize) => {
      searchParams.current = page
      searchParams.pageSize = pageSize
      fetchData()
    },
  }
})

const fetchData = async () => {
  loading.value = true

  const params = {
    ...searchParams,
    tags: [],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(tagList.value[index])
    }
  })
  const res = await listPictureVisByPageUsingPost(params)
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

onMounted(() => {
  fetchData()
})

const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<string[]>([])

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
    tagList.value = res.data.data.tagList ?? []
  } else {
    message.error('加载分类标签失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})
const router = useRouter()
const doClickPicture = (picture) => {
  router.push({
    path: `/picture/${picture.id}`,
  })
}
</script>

<style scoped>
.search-bar {
  margin-top: 20px; /* 在搜索框顶部增加 20px 的外边距 */
  margin-bottom: 20px; /* 在搜索框底部增加 20px 的外边距 */
  max-width: 480px; /* 教程中的优化：限制最大宽度，避免在大屏幕上拉得太长 */
  margin-left: auto; /* 配合 max-width 实现水平居中 */
  margin-right: auto; /* 配合 max-width 实现水平居中 */
}
.tag-bar {
  margin-top:; /* 在搜索框顶部增加 20px 的外边距 */
  margin-bottom: 20px; /* 在搜索框底部增加 20px 的外边距 */
  max-width: 480px; /* 教程中的优化：限制最大宽度，避免在大屏幕上拉得太长 */
  margin-left:; /* 配合 max-width 实现水平居中 */
  margin-right: auto; /* 配合 max-width 实现水平居中 */
}
</style>
