import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '@/components/common/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          name: 'analysis',
          component: () => import('@/views/AnalysisView.vue'),
        },
      ],
    },
  ],
})

export default router
