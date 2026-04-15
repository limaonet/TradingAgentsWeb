<template>
  <div class="analysis-pipeline">
    <div class="pipeline-header">
      <NodeIndexOutlined class="header-icon" />
      <span>分析流程</span>
    </div>

    <!-- Ant Design Vue Steps（开源组件，横向可滚动、状态清晰） -->
    <div class="steps-wrap">
      <a-steps :current="currentIndex" size="small" class="pipeline-steps">
        <a-step
          v-for="(stage, i) in stages"
          :key="i"
          :title="stage.name"
          :status="mapStepStatus(stage)"
        />
      </a-steps>
    </div>

    <div class="pipeline-progress">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: `${progressPercent}%` }"></div>
      </div>
      <span class="progress-text">{{ completedCount }}/{{ stages.length }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NodeIndexOutlined } from '@ant-design/icons-vue'

interface Stage {
  name: string
  status: 'completed' | 'running' | 'pending'
}

const props = defineProps<{
  stages: Stage[]
}>()

const completedCount = computed(() => props.stages.filter((s) => s.status === 'completed').length)

const progressPercent = computed(() =>
  props.stages.length ? (completedCount.value / props.stages.length) * 100 : 0
)

/** 当前步骤：进行中下标；全部完成则为 length */
const currentIndex = computed(() => {
  const runningAt = props.stages.findIndex((s) => s.status === 'running')
  if (runningAt >= 0) return runningAt
  if (props.stages.length && props.stages.every((s) => s.status === 'completed')) {
    return props.stages.length
  }
  const firstPending = props.stages.findIndex((s) => s.status === 'pending')
  return firstPending >= 0 ? firstPending : 0
})

function mapStepStatus(stage: Stage): 'wait' | 'process' | 'finish' | 'error' {
  if (stage.status === 'completed') return 'finish'
  if (stage.status === 'running') return 'process'
  return 'wait'
}
</script>

<style scoped>
.analysis-pipeline {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 16px 20px;
}

.pipeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

.header-icon {
  color: var(--color-accent);
}

.steps-wrap {
  overflow-x: auto;
  overflow-y: hidden;
  margin-bottom: 14px;
  padding-bottom: 4px;
  scrollbar-color: rgba(59, 130, 246, 0.45) var(--bg-input);
}

.steps-wrap::-webkit-scrollbar {
  height: 6px;
}

.steps-wrap::-webkit-scrollbar-track {
  background: var(--bg-input);
  border-radius: 3px;
}

.steps-wrap::-webkit-scrollbar-thumb {
  background: rgba(59, 130, 246, 0.45);
  border-radius: 3px;
}

.pipeline-steps {
  min-width: 720px;
}

.pipeline-steps :deep(.ant-steps-item-title) {
  font-size: 12px !important;
  color: var(--text-secondary) !important;
  white-space: nowrap;
}

.pipeline-steps :deep(.ant-steps-item-finish .ant-steps-item-title) {
  color: var(--color-bull) !important;
}

.pipeline-steps :deep(.ant-steps-item-process .ant-steps-item-title) {
  color: var(--color-info) !important;
}

.pipeline-progress {
  display: flex;
  align-items: center;
  gap: 12px;
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
  background: linear-gradient(90deg, var(--color-bull), var(--color-accent));
  border-radius: 2px;
  transition: width 0.5s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}
</style>
