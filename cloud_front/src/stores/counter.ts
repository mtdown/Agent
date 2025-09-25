import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

export const useCounterStore = defineStore('counter', () => {
  // 定义初始值
  const count = ref(0)
  // 定义计算逻辑
  const doubleCount = computed(() => count.value * 2)
  // 定义状态修改的方法
  function increment() {
    count.value++
  }

  return { count, doubleCount, increment }
})
//一个状态存储一类要共享的数据
