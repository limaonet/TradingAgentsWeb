<template>
  <header class="app-header">
    <div class="header-left">
      <div class="logo">
        <LineChartOutlined class="logo-icon" />
        <span class="logo-text">TradingAgents</span>
      </div>
      <span class="subtitle">多智能体交易分析系统</span>
    </div>
    <div class="header-right">
      <ThemeToggle />
    </div>
  </header>
</template>

<script setup lang="ts">
import { LineChartOutlined } from '@ant-design/icons-vue'
import ThemeToggle from './ThemeToggle.vue'
</script>

<style scoped>
.app-header {
  height: 56px;
  background-color: var(--bg-card);
  border-bottom: 1px solid var(--border-color-split);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-lg);
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--box-shadow-card);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.logo-icon {
  font-size: 24px;
  color: var(--primary-color);
}

.logo-text {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  border-left: 1px solid var(--border-color);
  padding-left: var(--spacing-md);
}

.header-right {
  display: flex;
  align-items: center;
}
</style>
