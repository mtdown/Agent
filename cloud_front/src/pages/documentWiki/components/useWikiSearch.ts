import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { listDocumentWikiVisByPageWithCacheUsingPost } from '@/api/documentWikiController.ts'

export function useWikiSearch() {
  const searchResults = ref<API.DocumentWikiVis[]>([])
  const total = ref(0)
  const searchParams = ref<API.DocumentWikiQueryRequest>({
    current: 1,
    pageSize: 10,
    sortField: 'editTime',
    sortOrder: 'descend',
    matchMode: 'titleOrContent',
    searchText: '',
  })

  const isSearchMode = computed(() => Boolean(searchParams.value.searchText))

  const pagination = computed(() => ({
    current: searchParams.value.current ?? 1,
    pageSize: searchParams.value.pageSize ?? 10,
    total: total.value,
    showTotal: (value: number) => `共 ${value} 条`,
    onChange: (page: number, pageSize: number) => {
      searchParams.value.current = page
      searchParams.value.pageSize = pageSize
      fetchSearchResults()
    },
  }))

  const fetchSearchResults = async () => {
    const res = await listDocumentWikiVisByPageWithCacheUsingPost({ ...searchParams.value })
    if (res.data.code === 0 && res.data.data) {
      searchResults.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total ?? 0)
    } else {
      message.error('搜索文档失败，' + res.data.message)
    }
  }

  return { searchParams, searchResults, isSearchMode, pagination, fetchSearchResults }
}
