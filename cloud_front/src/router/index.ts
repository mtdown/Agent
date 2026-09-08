import { createRouter, createWebHistory } from 'vue-router'
import UserManagePage from '@/pages/admin/UserManagePage.vue'
import UserRegisterPage from '@/pages/user/UserRegisterPage.vue'
import UserLoginPage from '@/pages/user/UserLoginPage.vue'
import AddPicturePage from '@/pages/AddPicturePage.vue'
import PictureManagePage from '@/pages/admin/PictureManagePage.vue'
import HomePage from '@/pages/HomePage.vue'
import PictureDetailPage from '@/pages/PictureDetailPage.vue'
import AddPictureBatchPage from '@/components/AddPictureBatchPage.vue'
import SpaceManagePage from '@/pages/admin/SpaceManagePage.vue'
import AddSpacePage from '@/pages/AddSpacePage.vue'
import MySpacePage from '@/pages/MySpacePage.vue'
import SpaceDetailPage from '@/pages/SpaceDetailPage.vue'
import SpaceUserManagePage from '@/pages/admin/SpaceUserManagePage.vue'
import DocumentWikiListPage from '@/pages/documentWiki/DocumentWikiListPage.vue'
// 扮演路由组件的位置
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/documentWiki',
    },
    {
      path: '/gallery',
      name: 'gallery',
      component: HomePage,
    },
    {
      path: '/user/login',
      name: '用户登录',
      component: UserLoginPage,
    },
    {
      path: '/user/register',
      name: '用户注册',
      component: UserRegisterPage,
    },
    {
      path: '/admin/userManage',
      name: '管理员界面',
      component: UserManagePage,
    },
    {
      path: '/admin/pictureManage',
      name: '图片管理',
      component: PictureManagePage,
    },
    {
      path: '/gallery/add_picture/batch',
      name: '批量创建图片',
      component: AddPictureBatchPage,
    },
    {
      path: '/gallery/picture/:id',
      name: '图片详情',
      component: PictureDetailPage,
      props: true,
    },
    {
      path: '/documentWiki',
      name: 'Wiki 文档',
      component: DocumentWikiListPage,
    },
    {
      path: '/documentWiki/:id',
      name: '文档详情',
      component: () => import('@/pages/documentWiki/DocumentWikiDetailPage.vue'),
      props: true,
    },
    {
      path: '/documentWiki/batch',
      name: '批量文档',
      component: () => import('@/pages/documentWiki/DocumentWikiBatchImportPage.vue'),
    },
    {
      path: '/add_documentWiki',
      name: '创建文档',
      component: () => import('@/pages/documentWiki/AddDocumentWikiPage.vue'),
    },
    {
      path: '/edit_documentWiki/:id',
      name: '编辑文档',
      component: () => import('@/pages/documentWiki/EditDocumentWikiPage.vue'),
    },
    {
      path: '/gallery/add_space',
      name: '创建空间',
      component: AddSpacePage,
    },
    {
      path: '/gallery/add_picture',
      name: '创建图片',
      component: AddPicturePage,
    },
    {
      path: '/admin/spaceManage',
      name: '空间管理',
      component: SpaceManagePage,
    },
    {
      path: '/gallery/spaceUserManage/:id',
      name: '空间成员管理',
      component: SpaceUserManagePage,
      props: true,
    },
    {
      path: '/gallery/my_space',
      name: '我的空间',
      component: MySpacePage,
    },
    {
      path: '/gallery/space/:id',
      name: '空间详情',
      component: SpaceDetailPage,
      props: true,
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
    },
  ],
})

export default router
