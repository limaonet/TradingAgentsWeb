<template>
  <div class="action-plan">
    <div class="plan-title">
      <BulbOutlined />
      <span>操作建议</span>
    </div>
    
    <div class="actions-grid">
      <div
        v-for="(action, index) in actions"
        :key="index"
        class="action-item"
        :class="action.type"
        :style="{ animationDelay: `${index * 100}ms` }"
      >
        <div class="action-icon">
          <WalletOutlined v-if="action.label.includes('仓位') || action.label.includes('建仓')" />
          <RiseOutlined v-else-if="action.label.includes('目标') || action.label.includes('止盈') || action.label.includes('止损')" />
          <CheckCircleOutlined v-else />
        </div>
        <div class="action-info">
          <span class="action-label">{{ action.label }}</span>
          <span class="action-value">{{ action.value }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  BulbOutlined,
  WalletOutlined,
  RiseOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons-vue'

interface Action {
  label: string
  value: string
  type: 'primary' | 'warning' | 'danger'
}

const props = defineProps<{
  actions: Action[]
}>()
</script>

<style scoped>
.action-plan {
  margin-top: 20px;
}

.plan-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 14px;
}

.actions-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  animation: slideUp 0.4s ease-out;
  animation-fill-mode: both;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.action-item.primary {
  border-left: 3px solid var(--color-bull);
}

.action-item.warning {
  border-left: 3px solid var(--color-neutral);
}

.action-item.danger {
  border-left: 3px solid var(--color-bear);
}

.action-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-input);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 16px;
}

.action-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.action-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.action-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: var(--font-mono);
  word-break: break-word;
  overflow-wrap: anywhere;
}

.action-item.primary .action-value {
  color: var(--color-bull);
}

.action-item.warning .action-value {
  color: var(--color-neutral);
}

.action-item.danger .action-value {
  color: var(--color-bear);
}
</style>
