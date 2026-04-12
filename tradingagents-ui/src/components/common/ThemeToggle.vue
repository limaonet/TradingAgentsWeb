<template>
  <a-button
    type="text"
    class="theme-toggle"
    @click="toggleTheme"
    :title="isDark ? '切换到亮色模式' : '切换到暗色模式'"
  >
    <BulbFilled v-if="isDark" class="theme-icon" />
    <BulbOutlined v-else class="theme-icon" />
  </a-button>
</template>

<script setup lang="ts">
import { BulbFilled, BulbOutlined } from '@ant-design/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { isDark, toggleTheme } = useTheme()
</script>

<style scoped>
.theme-toggle {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--border-radius-md);
  transition: all var(--transition-duration) var(--transition-timing);
}

.theme-toggle:hover {
  background-color: var(--bg-tertiary);
}

.theme-icon {
  font-size: 18px;
  color: var(--text-secondary);
  transition: all var(--transition-duration) var(--transition-timing);
}

.theme-toggle:hover .theme-icon {
  color: var(--primary-color);
}
</style>
