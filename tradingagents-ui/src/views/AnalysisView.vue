<template>
  <div class="analysis-view">
    <!-- 顶部输入区 -->
    <AnalysisInput />

    <!-- 中部流程图 -->
    <div class="flow-section">
      <AgentFlowGraph v-if="graphReady" @node-click="onNodeClick" />
    </div>

    <!-- 底部内容区 -->
    <div class="content-section">
      <!-- 视图切换 Tabs -->
      <a-tabs v-model:activeKey="activeTab" size="small" class="content-tabs">
        <a-tab-pane key="detail" tab="节点详情">
          <NodeDetail />
        </a-tab-pane>
        <a-tab-pane key="timeline" tab="实时时间线">
          <AnalysisTimeline />
        </a-tab-pane>
        <a-tab-pane key="cards" tab="Agent 卡片">
          <AgentCards />
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AnalysisInput from '@/components/AnalysisInput.vue'
import AgentFlowGraph from '@/components/visualization/AgentFlowGraph.vue'
import NodeDetail from '@/components/NodeDetail.vue'
import AnalysisTimeline from '@/components/AnalysisTimeline.vue'
import AgentCards from '@/components/AgentCards.vue'
import { useAnalysisStore } from '@/stores/analysisStore'

const store = useAnalysisStore()
const activeTab = ref('cards')
const graphReady = ref(false)

// 延迟加载G6避免阻塞路由
import { onMounted } from 'vue'
onMounted(() => { graphReady.value = true })

const onNodeClick = (nodeId: string) => {
  activeTab.value = 'detail'
}
</script>

<style scoped>
.analysis-view {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  max-width: 1200px;
  margin: 0 auto;
}

.flow-section {
  /* 流程图区域不需要额外样式，AgentFlowGraph 自带背景和边框 */
}

.content-section {
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--border-color-split);
  padding: var(--spacing-sm) var(--spacing-md);
  min-height: 300px;
}

.content-tabs :deep(.ant-tabs-nav) {
  margin-bottom: var(--spacing-xs);
}
</style>
