<template>
  <div class="analyst-team">
    <div class="panel-header">
      <TeamOutlined class="header-icon" />
      <h3>分析师团队</h3>
      <span class="agent-count">{{ activeCount }}/{{ analysts.length }}</span>
    </div>

    <div class="analysts-list">
      <AnalystCard
        v-for="analyst in analysts"
        :key="analyst.id"
        v-bind="analyst"
        @click="selectAnalyst(analyst.id)"
      />
    </div>

    <div class="team-summary">
      <div class="summary-item">
        <span class="summary-label">整体趋势</span>
        <span class="summary-value" :class="overallTrend">
          {{ overallTrend === 'bull' ? '看涨' : overallTrend === 'bear' ? '看跌' : '震荡' }}
        </span>
      </div>
      <div class="summary-item">
        <span class="summary-label">平均置信度</span>
        <span class="summary-value">{{ avgConfidence }}%</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TeamOutlined } from '@ant-design/icons-vue'
import AnalystCard from './AnalystCard.vue'

const props = defineProps<{
  analysts: Array<{
    id: string
    name: string
    role: string
    avatar: string
    status: 'idle' | 'running' | 'completed' | 'error'
    confidence: number
    insights: Array<{ label: string; type: 'bull' | 'bear' | 'neutral' }>
    metrics: Array<{ label: string; value: string; trend: 'up' | 'down' | 'neutral' }>
    progress: number
  }>
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const activeCount = computed(() => 
  props.analysts.filter(a => a.status === 'completed').length
)

const avgConfidence = computed(() => {
  if (props.analysts.length === 0) return 0
  const completed = props.analysts.filter(a => a.status === 'completed')
  if (completed.length === 0) return 0
  return Math.round(completed.reduce((sum, a) => sum + a.confidence, 0) / completed.length)
})

const overallTrend = computed(() => {
  const completed = props.analysts.filter(a => a.status === 'completed')
  if (completed.length === 0) return 'neutral'
  
  const bullCount = completed.filter(a => 
    a.insights.some(i => i.type === 'bull')
  ).length
  const bearCount = completed.filter(a => 
    a.insights.some(i => i.type === 'bear')
  ).length
  
  if (bullCount > bearCount) return 'bull'
  if (bearCount > bullCount) return 'bear'
  return 'neutral'
})

const selectAnalyst = (id: string) => {
  emit('select', id)
}
</script>

<style scoped>
.analyst-team {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.header-icon {
  font-size: 20px;
  color: var(--color-info);
}

.panel-header h3 {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.agent-count {
  padding: 4px 10px;
  background: var(--bg-input);
  border-radius: 20px;
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.analysts-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.team-summary {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.summary-label {
  color: var(--text-secondary);
}

.summary-value {
  font-weight: 600;
  font-family: var(--font-mono);
}

.summary-value.bull {
  color: var(--color-bull);
}

.summary-value.bear {
  color: var(--color-bear);
}

.summary-value.neutral {
  color: var(--color-neutral);
}
</style>
