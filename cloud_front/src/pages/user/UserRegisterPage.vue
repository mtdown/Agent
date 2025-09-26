<template>
  <div id="userRegisterPage">
    <h2 class="title">海东文库-用户注册</h2>
    <div class="description">企业级文库制作</div>
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <a-form-item
        label="用户账号"
        name="userAccount"
        :rules="[{ required: true, message: 'Please input your username!' }]"
      >
        <a-input v-model:value="formState.userAccount" placeholder="在这里输入账号" />
      </a-form-item>

      <a-form-item
        label="用户密码"
        name="userPassword"
        :rules="[{ required: true, message: '密码长度不小于6位' }]"
      >
        <a-input-password
          v-model:value="formState.userPassword"
          placeholder="在这里输入密码,密码长度不小于6位"
        />
      </a-form-item>

      <a-form-item
        label="二次确认密码"
        name="checkPassword"
        :rules="[{ required: true, message: '密码长度不小于6位' }]"
      >
        <a-input-password
          v-model:value="formState.checkPassword"
          placeholder="输入确认密码,密码长度不小于6位"
        />
      </a-form-item>

      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
import { reactive } from 'vue'
import { message } from 'ant-design-vue'
import router from '@/router'
import { userLoginUsingPost, userRegisterUsingPost } from '@/api/userController.ts'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

/**
 * 提交表单
 * @param values
 */
const handleSubmit = async (values: any) => {
  const res = await userRegisterUsingPost(values)
  console.log('后端返回的响应:', res)
  // 注册成功，跳转到登陆页面
  if (res.data.code === 0 && res.data.data) {
    message.success('注册成功')
    router.push({
      path: '/user/login',
      // Path这里我们不是使用的注册后返回原界面的逻辑，那种注册需要提取出当前界面的用户信息值
      replace: true,
      //   replace是用来替换当前网页，不然你点返回就又回到用户注册界面了
    })
  } else {
    message.error('注册失败，' + res.data.message)
  }
}
</script>

<style scoped>
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.description {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  color: #bbb;
  text-align: right;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>
