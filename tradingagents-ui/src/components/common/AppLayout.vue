<template>
  <a-config-provider :theme="antDesignTheme">
    <div class="app-layout">
      <AppHeader />
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </a-config-provider>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { theme } from 'ant-design-vue'
import AppHeader from './AppHeader.vue'

const antDesignTheme = ref({
  token: {
    colorPrimary: '#1890ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#f5222d',
    colorInfo: '#1890ff',
    borderRadius: 4,
    wireframe: false,
  },
  algorithm: theme.defaultAlgorithm,
})

onMounted(() => {
  window.addEventListener('theme-change', ((event: CustomEvent) => {
    antDesignTheme.value = event.detail
  }) as EventListener)
})
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-secondary);
}

.main-content {
  flex: 1;
  padding: var(--spacing-md);
  overflow-y: auto;
}
</style>
