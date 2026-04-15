<template>
  <div class="node-detail">
    <template v-if="node">
      <!-- 头部信息 -->
      <div class="detail-header">
        <span class="detail-icon">{{ meta?.icon }}</span>
        <div class="detail-info">
          <h3 class="detail-name">{{ node.name }}</h3>
          <p class="detail-role">{{ node.role }}</p>
        </div>
        <a-tag :color="statusTagColor(node.status)" size="small">
          {{ statusLabel(node.status) }}
        </a-tag>
        <a-button type="text" size="small" @click="store.selectNode(null)">
          <CloseOutlined />
        </a-button>
      </div>

      <!-- 内容区 -->
      <div class="detail-body">
        <!-- 加载中 -->
        <div v-if="node.status === 'running'" class="loading-state">
          <a-skeleton active :paragraph="{ rows: 6 }" />
        </div>
        <!-- 等待 -->
        <div v-else-if="!node.content && node.status !== 'completed'" class="idle-state">
          <p>{{ node.status === 'idle' ? '等待执行' : '准备中...' }}</p>
        </div>
        <!-- 报告内容 -->
        <div v-else class="report-content" v-html="simpleMarkdownToHtml(node.content)"></div>
      </div>
    </template>

    <!-- 未选中 -->
    <div v-else class="no-selection">
      <p>点击流程图节点或卡片查看详细内容</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import { useAnalysisStore } from '@/stores/analysisStore'
import { simpleMarkdownToHtml } from '@/utils/simpleMarkdown'

const store = useAnalysisStore()
const node = computed(() => store.selectedNode)
const meta = computed(() => store.selectedNodeId ? store.AGENT_META[store.selectedNodeId] : null)

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

</script>

<style scoped>
.node-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color-split);
  margin-bottom: var(--spacing-sm);
}

.detail-icon {
  font-size: 28px;
}

.detail-info {
  flex: 1;
}

.detail-name {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.detail-role {
  font-size: 12px;
  color: var(--text-tertiary);
  margin: 2px 0 0;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  max-height: 350px;
}

.loading-state, .idle-state {
  padding: var(--spacing-lg);
}

.idle-state {
  text-align: center;
  color: var(--text-tertiary);
}

.report-content {
  font-size: var(--font-size-sm);
  line-height: 1.8;
  color: var(--text-primary);
  padding: var(--spacing-xs) 0;
}

.report-content :deep(h2) {
  font-size: 18px;
  margin: 16px 0 8px;
  color: var(--text-primary);
}

.report-content :deep(h3) {
  font-size: 15px;
  margin: 12px 0 6px;
  color: var(--text-primary);
}

.report-content :deep(h4) {
  font-size: 14px;
  margin: 8px 0 4px;
  color: var(--text-primary);
}

.report-content :deep(ul) {
  padding-left: 20px;
  margin: 8px 0;
}

.report-content :deep(strong) {
  color: var(--primary-color);
}

.no-selection {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
