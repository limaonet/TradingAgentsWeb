<template>
  <div class="decision-panel">
    <div class="panel-header">
      <TrophyOutlined class="header-icon" />
      <h3>最终决策</h3>
    </div>

    <div class="decision-content">
      <!-- 投资结论 -->
      <div class="conclusion-section">
        <div class="conclusion-label">投资结论</div>
        <div class="conclusion-value" :class="decisionClass">
          {{ conclusion }}
        </div>
      </div>

      <!-- 置信度仪表盘 -->
      <GaugeChart :value="confidence" label="决策置信度" />

      <!-- 操作建议 -->
      <ActionPlan :actions="actions" />

      <!-- 风险提示 -->
      <div class="risk-section">
        <div class="section-title">
          <WarningOutlined />
          <span>风险提示</span>
        </div>
        <ul class="risk-list">
          <li v-for="(risk, index) in risks" :key="index" class="risk-item">
            <span class="risk-dot"></span>
            {{ risk }}
          </li>
        </ul>
        <a-button
          v-if="fullReport?.trim()"
          type="link"
          size="small"
          class="full-report-btn"
          @click="emit('view-full-report')"
        >
          查看完整决策全文
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { TrophyOutlined, WarningOutlined } from '@ant-design/icons-vue'
import GaugeChart from './GaugeChart.vue'
import ActionPlan from './ActionPlan.vue'

interface Action {
  label: string
  value: string
  type: 'primary' | 'warning' | 'danger'
}

const props = defineProps<{
  conclusion: string
  confidence: number
  actions: Action[]
  risks: string[]
  /** 组合经理完整输出，用于弹窗查看 */
  fullReport?: string
}>()

const emit = defineEmits<{
  'view-full-report': []
}>()

const decisionClass = computed(() => {
  if (props.conclusion.includes('买入') || props.conclusion.includes('看涨')) return 'bull'
  if (props.conclusion.includes('卖出') || props.conclusion.includes('看跌')) return 'bear'
  return 'neutral'
})
</script>

<style scoped>
.decision-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  overflow-x: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.header-icon {
  font-size: 20px;
  color: var(--color-bull);
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.decision-content {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 20px;
  min-width: 0;
  scrollbar-color: rgba(59, 130, 246, 0.45) var(--bg-input);
}

.decision-content::-webkit-scrollbar {
  width: 8px;
}

.decision-content::-webkit-scrollbar-track {
  background: var(--bg-input);
  border-radius: 4px;
}

.decision-content::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.45);
  border-radius: 4px;
}

.decision-content::-webkit-scrollbar-thumb:hover {
  background: rgba(59, 130, 246, 0.65);
}

.conclusion-section {
  text-align: center;
  margin-bottom: 24px;
  padding: 20px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.conclusion-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.conclusion-value {
  font-size: 28px;
  font-weight: 700;
  word-break: break-word;
  overflow-wrap: anywhere;
  max-width: 100%;
}

.conclusion-value.bull {
  color: var(--color-bull);
  text-shadow: 0 0 20px rgba(34, 197, 94, 0.3);
}

.conclusion-value.bear {
  color: var(--color-bear);
  text-shadow: 0 0 20px rgba(239, 68, 68, 0.3);
}

.conclusion-value.neutral {
  color: var(--color-neutral);
}

.risk-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--border-color);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.risk-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  font-size: 13px;
  color: var(--text-secondary);
  word-break: break-word;
  overflow-wrap: anywhere;
}

.full-report-btn {
  padding-left: 0;
  margin-top: 8px;
  height: auto;
}

.risk-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-neutral);
  flex-shrink: 0;
}

@media (max-width: 1200px) {
  .decision-content {
    padding: 16px;
  }

  .conclusion-value {
    font-size: 24px;
  }
}
</style>
