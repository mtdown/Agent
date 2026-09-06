// vue-cropper 官方 types 入口链条会 reach 到无类型声明的 lib/index.ts 源码。
// 通过 tsconfig.app.json 的 paths 将该模块固定解析到本文件，绕开包内解析。
import type { DefineComponent, Plugin } from 'vue'

type CropperComponent = DefineComponent<Record<string, unknown>, Record<string, unknown>, any> & Plugin

export const VueCropper: CropperComponent

declare const vueCropper: CropperComponent
export default vueCropper
