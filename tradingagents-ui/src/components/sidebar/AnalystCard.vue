<template>
  <div
    class="analyst-card"
    :class="{ active: status === 'running', completed: status === 'completed' }"
    @click="emit('click')"
  >
    <div class="card-header">
      <div class="avatar-section">
        <div class="avatar" :class="status">
          <img :src="avatarUrl" :alt="name" />
          <div v-if="status === 'running'" class="status-indicator pulse"></div>
        </div>
        <div class="agent-info">
          <h4 class="agent-name">{{ name }}</h4>
          <span class="agent-role">{{ role }}</span>
        </div>
      </div>
      <div class="confidence-badge" :class="confidenceClass">
        {{ confidence }}%
      </div>
    </div>

    <div class="card-content">
      <div class="insight-tags">
        <span
          v-for="(tag, index) in insights"
          :key="index"
          class="insight-tag"
          :class="tag.type"
        >
          {{ tag.label }}
        </span>
      </div>

      <div class="key-metrics">
        <div v-for="(metric, index) in metrics" :key="index" class="metric-item">
          <span class="metric-label">{{ metric.label }}</span>
          <span class="metric-value" :class="metric.trend">
            {{ metric.value }}
            <ArrowUpOutlined v-if="metric.trend === 'up'" />
            <ArrowDownOutlined v-if="metric.trend === 'down'" />
          </span>
        </div>
      </div>
    </div>

    <div class="card-footer">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: `${progress}%` }"></div>
      </div>
      <span class="status-text">{{ statusText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons-vue'

interface InsightTag {
  label: string
  type: 'bull' | 'bear' | 'neutral'
}

interface Metric {
  label: string
  value: string
  trend: 'up' | 'down' | 'neutral'
}

const props = defineProps<{
  name: string
  role: string
  avatar: string
  status: 'idle' | 'running' | 'completed' | 'error'
  confidence: number
  insights: InsightTag[]
  metrics: Metric[]
  progress: number
}>()

const emit = defineEmits<{
  click: []
}>()

const avatarUrl = computed(() => {
  // 使用占位头像服务，实际项目中替换为真实头像
  const avatars: Record<string, string> = {
    'market': 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix&gender=male',
    'sentiment': 'https://api.dicebear.com/7.x/avataaars/svg?seed=Aneka&gender=female',
    'news': 'https://api.dicebear.com/7.x/avataaars/svg?seed=Mark&gender=male',
    'fundamentals': 'https://api.dicebear.com/7.x/avataaars/svg?seed=Sara&gender=female',
  }
  return avatars[props.avatar] || avatars['market']
})

const confidenceClass = computed(() => {
  if (props.confidence >= 70) return 'high'
  if (props.confidence >= 40) return 'medium'
  return 'low'
})

const statusText = computed(() => {
  switch (props.status) {
    case 'running':
      return '分析中...'
    case 'completed':
      return '已完成'
    case 'error':
      return '出错'
    default:
      return '等待中'
  }
})
</script>

<style scoped>
.analyst-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.analyst-card:hover {
  border-color: var(--border-glow-blue);
  box-shadow: var(--glow-blue);
  transform: translateY(-2px);
}

.analyst-card.active {
  border-color: var(--color-info);
  box-shadow: var(--glow-blue);
}

.analyst-card.completed {
  border-color: var(--color-bull);
  box-shadow: var(--glow-green);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2px solid var(--border-color);
  overflow: hidden;
  transition: all 0.3s ease;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar.running {
  border-color: var(--color-info);
  box-shadow: var(--glow-blue);
}

.avatar.completed {
  border-color: var(--color-bull);
  box-shadow: var(--glow-green);
}

.status-indicator {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  background: var(--color-info);
  border-radius: 50%;
  border: 2px solid var(--bg-card);
}

.status-indicator.pulse {
  animation: pulse-ring 2s ease-out infinite;
}

@keyframes pulse-ring {
  0% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
  }
  70% {
    box-shadow: 0 0 0 10px rgba(59, 130, 246, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0);
  }
}

.agent-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.agent-role {
  font-size: 12px;
  color: var(--text-secondary);
}

.confidence-badge {
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--font-mono);
}

.confidence-badge.high {
  background: rgba(34, 197, 94, 0.15);
  color: var(--color-bull);
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.confidence-badge.medium {
  background: rgba(234, 179, 8, 0.15);
  color: var(--color-neutral);
  border: 1px solid rgba(234, 179, 8, 0.3);
}

.confidence-badge.low {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-bear);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.card-content {
  margin-bottom: 12px;
}

.insight-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.insight-tag {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.insight-tag.bull {
  background: rgba(34, 197, 94, 0.15);
  color: var(--color-bull);
}

.insight-tag.bear {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-bear);
}

.insight-tag.neutral {
  background: rgba(234, 179, 8, 0.15);
  color: var(--color-neutral);
}

.key-metrics {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.metric-label {
  color: var(--text-secondary);
}

.metric-value {
  font-family: var(--font-mono);
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
}

.metric-value.up {
  color: var(--color-bull);
}

.metric-value.down {
  color: var(--color-bear);
}

.metric-value.neutral {
  color: var(--text-secondary);
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: var(--bg-input);
  border-radius: 2px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-info), var(--color-accent));
  border-radius: 2px;
  transition: width 0.3s ease;
}

.status-text {
  font-size: 11px;
  color: var(--text-muted);
  min-width: 50px;
  text-align: right;
}
</style>
