<template>
  <div
    class="chat-bubble"
    :class="[position, type]"
    :style="{ animationDelay: `${delay}ms` }"
  >
    <div class="bubble-header">
      <img :src="avatar" :alt="name" class="bubble-avatar" />
      <div class="bubble-meta">
        <span class="bubble-name">{{ name }}</span>
        <span class="bubble-time">{{ time }}</span>
      </div>
      <div class="bubble-badge" :class="type">{{ badgeText }}</div>
    </div>

    <div class="bubble-content">
      <p class="bubble-text" :class="{ 'typewriter': showTypewriter }">
        {{ displayText }}
        <span v-if="isTyping" class="cursor">|</span>
      </p>

      <Transition name="expand">
        <div v-if="expanded && reasoning" class="bubble-reasoning">
          <div class="reasoning-header" @click="expanded = !expanded">
            <span>推理过程</span>
            <UpOutlined />
          </div>
          <div class="reasoning-content">{{ reasoning }}</div>
        </div>
      </Transition>

      <button
        v-if="reasoning && !isTyping"
        class="expand-btn"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '查看推理' }}
        <DownOutlined v-if="!expanded" />
        <UpOutlined v-else />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { DownOutlined, UpOutlined } from '@ant-design/icons-vue'
import { useTypewriter } from '@/composables/useTypewriter'

const props = defineProps<{
  name: string
  avatar: string
  position: 'left' | 'right'
  type: 'bull' | 'bear' | 'risk' | 'neutral'
  content: string
  reasoning?: string
  time: string
  showTypewriter?: boolean
  delay?: number
}>()

const expanded = ref(false)

const badgeText = computed(() => {
  switch (props.type) {
    case 'bull': return '看多'
    case 'bear': return '看空'
    case 'risk': return '风控'
    default: return '中性'
  }
})

// 打字机效果
const { displayText, isTyping, startTyping } = useTypewriter(props.content, {
  speed: 30,
  delay: props.delay || 0,
})

watch(() => props.content, () => {
  if (props.showTypewriter) {
    startTyping()
  }
}, { immediate: true })
</script>

<style scoped>
.chat-bubble {
  max-width: 85%;
  animation: slideIn 0.4s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.bubble-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.bubble-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 2px solid var(--border-color);
}

.bubble-meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bubble-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.bubble-time {
  font-size: 11px;
  color: var(--text-muted);
}

.bubble-badge {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.bubble-badge.bull {
  background: rgba(34, 197, 94, 0.15);
  color: var(--color-bull);
}

.bubble-badge.bear {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-bear);
}

.bubble-badge.risk {
  background: rgba(234, 179, 8, 0.15);
  color: var(--color-neutral);
}

.bubble-badge.neutral {
  background: var(--bg-input);
  color: var(--text-secondary);
}

.bubble-content {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 14px 16px;
  position: relative;
}

.chat-bubble.left .bubble-content {
  border-bottom-left-radius: 4px;
}

.chat-bubble.right .bubble-content {
  border-bottom-right-radius: 4px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1), rgba(6, 182, 212, 0.1));
  border-color: var(--border-glow-blue);
}

.chat-bubble.bull .bubble-content {
  border-left: 3px solid var(--color-bull);
}

.chat-bubble.bear .bubble-content {
  border-left: 3px solid var(--color-bear);
}

.chat-bubble.risk .bubble-content {
  border-left: 3px solid var(--color-neutral);
}

.bubble-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary);
}

.bubble-text.typewriter {
  font-family: var(--font-mono);
}

.cursor {
  animation: blink 1s step-end infinite;
  color: var(--color-accent);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.expand-btn {
  margin-top: 10px;
  padding: 6px 12px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
}

.expand-btn:hover {
  border-color: var(--color-info);
  color: var(--text-primary);
}

.bubble-reasoning {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.reasoning-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  cursor: pointer;
}

.reasoning-content {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  background: var(--bg-input);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
}

/* 展开动画 */
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  max-height: 500px;
  opacity: 1;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
