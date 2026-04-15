<template>
  <div class="risk-panel">
    <div
      v-for="(risk, index) in risks"
      :key="index"
      class="risk-item"
      :class="[risk.level, { clickable: Boolean(risk.fullReport?.trim()) }]"
      :style="{ animationDelay: `${index * 150}ms` }"
      role="button"
      tabindex="0"
      @click="onOpen(risk)"
      @keydown.enter.prevent="onOpen(risk)"
    >
      <div class="risk-header">
        <div class="risk-avatar">
          <img :src="getAvatar(index)" :alt="risk.name" />
        </div>
        <div class="risk-info">
          <span class="risk-name">{{ risk.name }}</span>
          <span class="risk-level" :class="risk.level">
            {{ levelText(risk.level) }}
          </span>
        </div>
      </div>
      <div class="risk-content">
        <p class="risk-desc">{{ risk.description }}</p>
        <div class="risk-suggestion">
          <SafetyOutlined />
          <span>{{ risk.suggestion }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { SafetyOutlined } from '@ant-design/icons-vue'
import { RISK_AVATARS } from '@/constants/agentVisuals'

interface Risk {
  name: string
  level: 'high' | 'medium' | 'low'
  description: string
  suggestion: string
  fullReport?: string
}

const props = defineProps<{
  risks: Risk[]
}>()

const emit = defineEmits<{
  'open-detail': [payload: { name: string; content: string }]
}>()

const onOpen = (risk: Risk) => {
  const content = risk.fullReport?.trim()
  if (!content) return
  emit('open-detail', { name: risk.name, content })
}

const getAvatar = (index: number) => {
  return RISK_AVATARS[index % RISK_AVATARS.length]
}

const levelText = (level: string) => {
  switch (level) {
    case 'high': return '高风险'
    case 'medium': return '中风险'
    case 'low': return '低风险'
    default: return level
  }
}
</script>

<style scoped>
.risk-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-item {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 14px;
  animation: slideIn 0.4s ease-out;
  animation-fill-mode: both;
}

.risk-item.clickable {
  cursor: pointer;
}

.risk-item.clickable:hover {
  border-color: var(--border-glow-blue);
  box-shadow: var(--glow-blue);
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.risk-item.high {
  border-left: 3px solid var(--color-bear);
}

.risk-item.medium {
  border-left: 3px solid var(--color-neutral);
}

.risk-item.low {
  border-left: 3px solid var(--color-bull);
}

.risk-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.risk-avatar {
  width: 32px;
  height: 32px;
  min-width: 32px;
  min-height: 32px;
  flex-shrink: 0;
  aspect-ratio: 1;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--border-color);
}

.risk-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.risk-info {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  min-width: 0;
}

.risk-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
  word-break: break-word;
  flex: 1;
}

.risk-level {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.risk-level.high {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-bear);
}

.risk-level.medium {
  background: rgba(234, 179, 8, 0.15);
  color: var(--color-neutral);
}

.risk-level.low {
  background: rgba(34, 197, 94, 0.15);
  color: var(--color-bull);
}

.risk-content {
  padding-left: 42px;
}

.risk-desc {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.risk-suggestion {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-info);
  background: rgba(59, 130, 246, 0.1);
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  word-break: break-word;
}

@media (max-width: 1200px) {
  .risk-content {
    padding-left: 0;
  }
}

@media (max-width: 920px) {
  .risk-item {
    padding: 12px;
  }

  .risk-avatar {
    width: 28px;
    height: 28px;
  }
}
</style>
