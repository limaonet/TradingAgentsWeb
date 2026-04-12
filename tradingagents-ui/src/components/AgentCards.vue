<template>
  <div class="agent-cards">
    <!-- 按阶段分组 -->
    <div v-for="phase in phases" :key="phase.id" class="phase-group">
      <div class="phase-label">{{ phase.label }}</div>
      <div class="phase-cards">
        <div
          v-for="node in phase.nodes"
          :key="node.id"
          class="agent-card"
          :class="[`status-${node.status}`, { selected: store.selectedNodeId === node.id }]"
          @click="handleClick(node.id)"
        >
          <div class="card-header">
            <span class="card-icon">{{ store.AGENT_META[node.id]?.icon }}</span>
            <span class="card-name">{{ node.name }}</span>
            <a-tag :color="statusTagColor(node.status)" size="small" class="status-tag">
              {{ statusLabel(node.status) }}
            </a-tag>
          </div>
          <div class="card-role">{{ node.role }}</div>
          <div v-if="node.content" class="card-content">
            {{ truncate(node.content, 120) }}
          </div>
          <div v-else-if="node.status === 'running'" class="card-loading">
            <a-spin size="small" />
            <span>正在分析...</span>
          </div>
          <div v-else class="card-placeholder">等待执行</div>
        </div>
      </div>
    </div>

    <!-- 展开详情弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalNode?.name"
      width="720px"
      :footer="null"
    >
      <div v-if="modalNode" class="modal-content">
        <a-tag :color="statusTagColor(modalNode.status)" class="modal-status">
          {{ statusLabel(modalNode.status) }}
        </a-tag>
        <p class="modal-role">{{ modalNode.role }}</p>
        <a-divider />
        <div class="modal-report" v-html="renderMarkdown(modalNode.content)"></div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useAnalysisStore, type AgentNode } from '@/stores/analysisStore'

const store = useAnalysisStore()
const modalVisible = ref(false)
const modalNode = ref<AgentNode | null>(null)

const phases = computed(() => {
  const nodeList = Object.values(store.nodes)
  return [
    { id: 1, label: 'Phase 1 · 并行分析', nodes: nodeList.filter(n => n.phase === 1) },
    { id: 2, label: 'Phase 2 · 研究整合', nodes: nodeList.filter(n => n.phase === 2) },
    { id: 3, label: 'Phase 3 · 交易策略', nodes: nodeList.filter(n => n.phase === 3) },
    { id: 4, label: 'Phase 4 · 风控辩论', nodes: nodeList.filter(n => n.phase === 4) },
    { id: 5, label: 'Phase 5 · 最终决策', nodes: nodeList.filter(n => n.phase === 5) },
  ]
})

const statusTagColor = (status: string) => {
  switch (status) {
    case 'running': return 'processing'
    case 'completed': return 'success'
    case 'error': return 'error'
    case 'pending': return 'warning'
    default: return 'default'
  }
}

const statusLabel = (status: string) => {
  switch (status) {
    case 'idle': return '待执行'
    case 'pending': return '等待中'
    case 'running': return '执行中'
    case 'completed': return '已完成'
    case 'error': return '出错'
    default: return status
  }
}

const truncate = (text: string, len: number) => {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

// 简单的 Markdown 渲染（标题、粗体、列表、换行）
const renderMarkdown = (text: string) => {
  if (!text) return '<p style="color:var(--text-tertiary)">暂无内容</p>'
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^# (.+)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>').replace(/$/, '</p>')
}

const handleClick = (nodeId: string) => {
  store.selectNode(nodeId)
  const node = store.nodes[nodeId]
  if (node && node.content) {
    modalNode.value = node
    modalVisible.value = true
  }
}
</script>

<style scoped>
.agent-cards {
  padding: var(--spacing-sm) 0;
}

.phase-group {
  margin-bottom: var(--spacing-md);
}

.phase-label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-xs);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.phase-cards {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.agent-card {
  flex: 1;
  min-width: 200px;
  max-width: 320px;
  background: var(--bg-card);
  border: 1px solid var(--border-color-split);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-sm) var(--spacing-md);
  cursor: pointer;
  transition: all 0.2s;
}

.agent-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.15);
}

.agent-card.selected {
  border-color: var(--primary-color);
  background: rgba(24, 144, 255, 0.04);
}

.agent-card.status-idle {
  opacity: 0.5;
}

.agent-card.status-running {
  border-color: var(--primary-color);
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.2); }
  50% { box-shadow: 0 0 0 6px rgba(24, 144, 255, 0); }
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: 4px;
}

.card-icon {
  font-size: 16px;
}

.card-name {
  font-weight: 600;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  flex: 1;
}

.card-role {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 6px;
  line-height: 1.4;
}

.card-content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  border-top: 1px solid var(--border-color-split);
  padding-top: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-loading {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: 12px;
  color: var(--primary-color);
  padding-top: 4px;
}

.card-placeholder {
  font-size: 12px;
  color: var(--text-disabled);
  padding-top: 4px;
}

.modal-content {
  max-height: 60vh;
  overflow-y: auto;
}

.modal-status {
  margin-bottom: var(--spacing-sm);
}

.modal-role {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.modal-report {
  font-size: var(--font-size-sm);
  line-height: 1.8;
  color: var(--text-primary);
}

.modal-report :deep(h2) {
  font-size: 18px;
  margin: 16px 0 8px;
}

.modal-report :deep(h3) {
  font-size: 16px;
  margin: 12px 0 6px;
}

.modal-report :deep(h4) {
  font-size: 14px;
  margin: 8px 0 4px;
}

.modal-report :deep(ul) {
  padding-left: 20px;
  margin: 8px 0;
}

.modal-report :deep(strong) {
  color: var(--text-primary);
}
</style>
