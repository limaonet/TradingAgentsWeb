<template>
  <div class="analysis-pipeline">
    <div class="pipeline-header">
      <NodeIndexOutlined class="header-icon" />
      <span>分析流程</span>
    </div>

    <div class="pipeline-track">
      <div
        v-for="(stage, index) in stages"
        :key="index"
        class="pipeline-node"
        :class="{
          completed: stage.status === 'completed',
          active: stage.status === 'running',
          pending: stage.status === 'pending'
        }"
        :style="{ animationDelay: `${index * 100}ms` }"
      >
        <div class="node-icon">
          <CheckOutlined v-if="stage.status === 'completed'" />
          <LoadingOutlined v-else-if="stage.status === 'running'" spin />
          <span v-else class="node-number">{{ index + 1 }}</span>
        </div>
        <div class="node-label">{{ stage.name }}</div>
        
        <!-- 连接线 -->
        <div
          v-if="index < stages.length - 1"
          class="node-connector"
          :class="{ active: stage.status === 'completed' }"
        >
          <div class="connector-line"></div>
          <div class="connector-flow" v-if="stage.status === 'completed'"></div>
        </div>
      </div>
    </div>

    <div class="pipeline-progress">
      <div class="progress-bar">
        <div
          class="progress-fill"
          :style="{ width: `${progressPercent}%` }"
        ></div>
      </div>
      <span class="progress-text">{{ completedCount }}/{{ stages.length }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NodeIndexOutlined, CheckOutlined, LoadingOutlined } from '@ant-design/icons-vue'

interface Stage {
  name: string
  status: 'completed' | 'running' | 'pending'
}

const props = defineProps<{
  stages: Stage[]
}>()

const completedCount = computed(() =>
  props.stages.filter(s => s.status === 'completed').length
)

const progressPercent = computed(() =>
  (completedCount.value / props.stages.length) * 100
)
</script>

<style scoped>
.analysis-pipeline {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 16px 24px;
}

.pipeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

.header-icon {
  color: var(--color-accent);
}

.pipeline-track {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  margin-bottom: 16px;
}

.pipeline-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  position: relative;
  z-index: 2;
  animation: fadeIn 0.4s ease-out;
  animation-fill-mode: both;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.node-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.3s ease;
}

.node-number {
  font-weight: 600;
  font-size: 13px;
}

.pipeline-node.pending .node-icon {
  background: var(--bg-input);
  border: 2px solid var(--border-color);
  color: var(--text-muted);
}

.pipeline-node.active .node-icon {
  background: rgba(59, 130, 246, 0.2);
  border: 2px solid var(--color-info);
  color: var(--color-info);
  animation: pulse 2s infinite;
}

.pipeline-node.completed .node-icon {
  background: rgba(34, 197, 94, 0.2);
  border: 2px solid var(--color-bull);
  color: var(--color-bull);
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(59, 130, 246, 0);
  }
}

.node-label {
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.node-connector {
  position: absolute;
  top: 18px;
  left: 50%;
  width: calc(100% - 36px);
  height: 2px;
  transform: translateX(18px);
}

.connector-line {
  position: absolute;
  inset: 0;
  background: var(--border-color);
}

.connector-flow {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, var(--color-bull), var(--color-accent));
  animation: flow 1.5s linear infinite;
}

@keyframes flow {
  0% {
    transform: scaleX(0);
    transform-origin: left;
  }
  50% {
    transform: scaleX(1);
    transform-origin: left;
  }
  50.1% {
    transform-origin: right;
  }
  100% {
    transform: scaleX(0);
    transform-origin: right;
  }
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
