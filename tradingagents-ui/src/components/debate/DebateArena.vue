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
        <div class="messages-list duel-list">
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
        <RiskPanel :risks="riskAssessments" @open-detail="(p) => emit('open-risk-detail', p)" />
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
  fullReport?: string
}

const props = defineProps<{
  messages: Message[]
  risks: Risk[]
  status: 'idle' | 'running' | 'completed'
}>()

const emit = defineEmits<{
  'open-risk-detail': [payload: { name: string; content: string }]
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
  min-width: 0;
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
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  min-width: 0;
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

/* 多空对决：桌面端左右铺满并增加 VS 对抗感 */
.duel-list {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-items: start;
  padding: 10px;
  border: 1px solid rgba(59, 130, 246, 0.25);
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(26, 43, 74, 0.25), rgba(30, 53, 90, 0.18));
}

.duel-list::after {
  content: 'VS';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #9ec8ff;
  background: rgba(16, 30, 58, 0.92);
  border: 1px solid rgba(96, 165, 250, 0.45);
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.25);
  pointer-events: none;
}

.duel-list :deep(.chat-bubble) {
  max-width: none;
  width: 100%;
  margin: 0;
}

.duel-list :deep(.chat-bubble.left) {
  grid-column: 1;
}

.duel-list :deep(.chat-bubble.right) {
  grid-column: 2;
}

@media (max-width: 900px) {
  .duel-list {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 0;
    border: 0;
    background: transparent;
  }

  .duel-list::after {
    display: none;
  }

  .duel-list :deep(.chat-bubble.left),
  .duel-list :deep(.chat-bubble.right) {
    grid-column: auto;
  }
}

@media (max-width: 1200px) {
  .debate-content {
    padding: 16px;
  }
}
</style>
