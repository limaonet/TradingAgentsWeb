<template>
  <div class="debate-arena">
    <div class="arena-header">
      <CommentOutlined class="header-icon" />
      <h3>策略辩论</h3>
      <div class="debate-status">
        <span class="status-dot" :class="debateStatus"></span>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </div>

    <div class="debate-content" ref="scrollContainer">
      <!-- 牛熊辩论 -->
      <div class="debate-section">
        <div class="section-title">
          <span class="section-icon">⚔️</span>
          <span>多空对决</span>
        </div>
        <div class="messages-list">
          <ChatBubble
            v-for="(msg, index) in bullBearMessages"
            :key="index"
            v-bind="msg"
            :show-typewriter="msg.showTypewriter"
            :delay="msg.delay"
          />
        </div>
      </div>

      <!-- 风控评估 -->
      <div class="debate-section">
        <div class="section-title">
          <span class="section-icon">🛡️</span>
          <span>风控辩论</span>
        </div>
        <RiskPanel :risks="riskAssessments" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { CommentOutlined } from '@ant-design/icons-vue'
import ChatBubble from './ChatBubble.vue'
import RiskPanel from './RiskPanel.vue'

interface Message {
  name: string
  avatar: string
  position: 'left' | 'right'
  type: 'bull' | 'bear' | 'risk' | 'neutral'
  content: string
  reasoning?: string
  time: string
  showTypewriter: boolean
  delay: number
}

interface Risk {
  name: string
  level: 'high' | 'medium' | 'low'
  description: string
  suggestion: string
}

const props = defineProps<{
  messages: Message[]
  risks: Risk[]
  status: 'idle' | 'running' | 'completed'
}>()

const scrollContainer = ref<HTMLElement>()

const bullBearMessages = computed(() => 
  props.messages.filter(m => m.type === 'bull' || m.type === 'bear')
)

const riskAssessments = computed(() => props.risks)

const debateStatus = computed(() => {
  if (props.status === 'completed') return 'completed'
  if (props.status === 'running') return 'active'
  return 'idle'
})

const statusText = computed(() => {
  switch (props.status) {
    case 'running': return '辩论进行中'
    case 'completed': return '辩论完成'
    default: return '等待开始'
  }
})
</script>

<style scoped>
.debate-arena {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.arena-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.header-icon {
  font-size: 20px;
  color: var(--color-accent);
}

.arena-header h3 {
  flex: 1;
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.debate-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}

.status-dot.active {
  background: var(--color-info);
  animation: pulse 2s infinite;
}

.status-dot.completed {
  background: var(--color-bull);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  font-size: 12px;
  color: var(--text-secondary);
}

.debate-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.debate-section {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-color);
}

.section-icon {
  font-size: 16px;
}

.messages-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
