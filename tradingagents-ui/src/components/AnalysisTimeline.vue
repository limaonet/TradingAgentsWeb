<template>
  <div class="timeline-container" ref="containerRef">
    <div v-if="store.timelineMessages.length === 0" class="empty-state">
      <p>等待分析开始...</p>
    </div>
    <a-timeline v-else>
      <a-timeline-item
        v-for="msg in store.timelineMessages"
        :key="msg.id"
        :color="getTimelineColor(msg)"
      >
        <div class="timeline-item" @click="handleClick(msg)">
          <div class="timeline-header">
            <a-tag :color="getTagColor(msg.type)" size="small">
              {{ getTypeLabel(msg.type) }}
            </a-tag>
            <span class="agent-name">{{ msg.agentName }}</span>
            <span v-if="msg.round" class="round-badge">第{{ msg.round }}轮</span>
            <span class="timestamp">{{ formatTime(msg.timestamp) }}</span>
          </div>
          <div class="timeline-content" :class="{ expanded: expandedId === msg.id }">
            {{ expandedId === msg.id ? msg.content : truncate(msg.content, 150) }}
          </div>
          <a
            v-if="msg.content.length > 150"
            class="expand-link"
            @click.stop="toggleExpand(msg.id)"
          >
            {{ expandedId === msg.id ? '收起' : '展开全文' }}
          </a>
        </div>
      </a-timeline-item>
    </a-timeline>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useAnalysisStore, type TimelineMessage } from '@/stores/analysisStore'

const store = useAnalysisStore()
const containerRef = ref<HTMLElement | null>(null)
const expandedId = ref<string | null>(null)

const getTimelineColor = (msg: TimelineMessage) => {
  switch (msg.type) {
    case 'complete': return 'green'
    case 'error': return 'red'
    case 'report': return 'green'
    case 'debate': return 'orange'
    default: return 'blue'
  }
}

const getTagColor = (type: string) => {
  switch (type) {
    case 'progress': return 'processing'
    case 'agent_status': return 'blue'
    case 'report': return 'success'
    case 'debate': return 'warning'
    case 'complete': return 'success'
    case 'error': return 'error'
    default: return 'default'
  }
}

const getTypeLabel = (type: string) => {
  switch (type) {
    case 'progress': return '进度'
    case 'agent_status': return '状态'
    case 'report': return '报告'
    case 'debate': return '辩论'
    case 'complete': return '完成'
    case 'error': return '错误'
    default: return type
  }
}

const formatTime = (ts: string) => {
  try {
    const d = new Date(ts)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return ts
  }
}

const truncate = (text: string, len: number) => {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

const toggleExpand = (id: string) => {
  expandedId.value = expandedId.value === id ? null : id
}

const handleClick = (msg: TimelineMessage) => {
  const nodeId = store.resolveNodeId(msg.agent)
  if (nodeId) {
    store.selectNode(nodeId)
  }
}

// 自动滚动到底部
watch(
  () => store.timelineMessages.length,
  () => {
    nextTick(() => {
      if (containerRef.value) {
        containerRef.value.scrollTop = containerRef.value.scrollHeight
      }
    })
  }
)
</script>

<style scoped>
.timeline-container {
  max-height: 400px;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.empty-state {
  text-align: center;
  color: var(--text-tertiary);
  padding: var(--spacing-xl) 0;
}

.timeline-item {
  cursor: pointer;
}

.timeline-item:hover {
  background: var(--bg-tertiary);
  border-radius: var(--border-radius-md);
  margin: -4px;
  padding: 4px;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: 4px;
}

.agent-name {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}

.round-badge {
  font-size: 11px;
  background: var(--warning-color);
  color: #fff;
  padding: 0 6px;
  border-radius: 10px;
}

.timestamp {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: auto;
}

.timeline-content {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.timeline-content.expanded {
  max-height: none;
}

.expand-link {
  font-size: 12px;
  color: var(--primary-color);
  cursor: pointer;
}
</style>
