<template>
  <div id="addPicturePage">
    <h2>
      {{ route.query?.id ? '修改图片' : '创建图片' }}
    </h2>
    <!--    图片上传组件-->
    <PictureUpload :picture="picture" :onSuccess="onSuccess" />
    <!--    图片信息表单-->
    <a-form
      v-if="picture"
      name="picture"
      layout="vertical"
      :model="pictureForm"
      @finish="handleSubmit"
    >
      <a-form-item label="名称" name="name">
        <a-input v-model:value="pictureForm.name" placeholder="请输入名称" allowClear />
      </a-form-item>
      <a-form-item label="简介" name="introduction">
        <a-textarea
          v-model:value="pictureForm.introduction"
          placeholder="请输入简介"
          :rows="2"
          autoSize
          allowClear
        />
      </a-form-item>

      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="pictureForm.category"
          placeholder="请输入分类"
          :options="categoryOptions"
          allowClear
        />
      </a-form-item>

      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="pictureForm.tags"
          mode="tags"
          placeholder="请输入标签"
          :options="tagOptions"
          allowClear
        />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit">创建</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
// 脚本部分，应该包含所有的 JS/TS 逻辑
// import { ref } from 'vue' // 需要引入 ref
import PictureUpload from '@/components/PictureUpload.vue'
import { useRoute, useRouter } from 'vue-router'
import { reactive, ref, onMounted } from 'vue'
import {
  editPictureUsingPost,
  getPictureVisByIdUsingGet,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

// 把这里的逻辑从 template 移到 script 中
const picture = ref<API.PictureVis>()
const pictureForm = reactive<API.PictureEditRequest>({})

const onSuccess = (newPicture: API.PictureVis) => {
  picture.value = newPicture
  // 自动填充表单名称
  pictureForm.name = newPicture.name
}

const router = useRouter()

// 修正 handleSubmit 逻辑
const handleSubmit = async (values: any) => {
  // a. 获取图片ID
  const pictureId = picture.value?.id
  if (!pictureId) {
    message.error('请先上传图片')
    return
  }
  // c. 调用正确的接口
  const res = await editPictureUsingPost({
    id: pictureId,
    ...values,
  })

  if (res.data.code === 0 && res.data.data) {
    // d. 成功后提示并跳转
    message.success('创建成功')
    router.push({
      path: `/picture/${pictureId}`,
    })
  } else {
    message.error('创建失败，' + res.data.message)
  }
}

const categoryOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
    categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
  } else {
    message.error('加载选项失败，' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

const route = useRoute()

const getOldPicture = async () => {
  const id = route.query?.id
  if (id) {
    const res = await getPictureVisByIdUsingGet({
      id: id,
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data
      picture.value = data
      pictureForm.name = data.name
      pictureForm.introduction = data.introduction
      pictureForm.category = data.category
      pictureForm.tags = data.tags
    }
  }
}

onMounted(() => {
  getOldPicture()
})
</script>

<style scoped>
#addPicturePage {
  max-width: 600px;
  margin: 0 auto;
}
</style>
