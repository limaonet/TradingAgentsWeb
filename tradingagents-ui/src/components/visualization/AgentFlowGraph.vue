<template>
  <div ref="graphContainer" class="graph-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { Graph } from '@antv/g6'
import { useAnalysisStore, type NodeStatus } from '@/stores/analysisStore'

const emit = defineEmits<{
  'node-click': [nodeId: string]
}>()

const store = useAnalysisStore()
const graphContainer = ref<HTMLElement | null>(null)
let graph: Graph | null = null

// 状态到颜色的映射
const STATUS_COLORS: Record<NodeStatus, string> = {
  idle: '#d9d9d9',
  pending: '#faad14',
  running: '#1890ff',
  completed: '#52c41a',
  error: '#f5222d',
}

const FLOW_EDGES = [
  { source: 'market_analyst', target: 'research_manager' },
  { source: 'sentiment_analyst', target: 'research_manager' },
  { source: 'fundamentals_analyst', target: 'research_manager' },
  { source: 'research_manager', target: 'trader' },
  { source: 'trader', target: 'aggressive_risk' },
  { source: 'trader', target: 'conservative_risk' },
  { source: 'trader', target: 'neutral_risk' },
  { source: 'aggressive_risk', target: 'portfolio_manager' },
  { source: 'conservative_risk', target: 'portfolio_manager' },
  { source: 'neutral_risk', target: 'portfolio_manager' },
]

const FLOW_NODE_ORDER = [
  'market_analyst',
  'sentiment_analyst',
  'fundamentals_analyst',
  'research_manager',
  'trader',
  'aggressive_risk',
  'conservative_risk',
  'neutral_risk',
  'portfolio_manager',
]

const flowNodes = computed(() =>
  FLOW_NODE_ORDER
    .filter((id) => Boolean(store.nodes[id]))
    .map((id) => ({
      id,
      data: {
        label: `${store.AGENT_META[id]?.icon || ''} ${store.nodes[id]?.name || id}`.trim(),
        phase: store.nodes[id]?.phase || 0,
      },
    }))
)

function getNodeColor(nodeId: string): string {
  const status = store.nodes[nodeId]?.status || 'idle'
  return STATUS_COLORS[status]
}

function getStrokeColor(nodeId: string): string {
  if (store.selectedNodeId === nodeId) return '#1890ff'
  const status = store.nodes[nodeId]?.status || 'idle'
  if (status === 'running') return '#1890ff'
  return 'transparent'
}

function initGraph() {
  if (!graphContainer.value) return

  const width = graphContainer.value.clientWidth
  const height = 320

  graph = new Graph({
    container: graphContainer.value,
    width,
    height,
    autoFit: 'view',
    padding: [20, 40, 20, 40],
    behaviors: ['zoom-canvas', 'drag-canvas'],
    layout: {
      type: 'dagre',
      rankdir: 'LR',
      nodesep: 30,
      ranksep: 60,
    },
    node: {
      type: 'rect',
      style: {
        size: [130, 44],
        radius: 8,
        fill: (d: any) => getNodeColor(d.id),
        stroke: (d: any) => getStrokeColor(d.id),
        lineWidth: (d: any) => {
          const status = store.nodes[d.id]?.status
          return status === 'running' || store.selectedNodeId === d.id ? 3 : 0
        },
        cursor: 'pointer',
      labelText: (d: any) => d.data?.label || d.id,
        labelFill: '#fff',
        labelFontSize: 13,
        labelFontWeight: 600,
        shadowColor: 'rgba(0,0,0,0.15)',
        shadowBlur: 8,
        shadowOffsetY: 2,
      },
    },
    edge: {
      type: 'cubic-horizontal',
      style: {
        stroke: '#c0c0c0',
        lineWidth: 1.5,
        endArrow: true,
        endArrowSize: 6,
      },
    },
    data: {
      nodes: flowNodes.value,
      edges: FLOW_EDGES,
    },
  })

  graph.on('node:click', (evt: any) => {
    const nodeId = evt.target?.id || evt.targetId
    if (nodeId && store.nodes[nodeId]) {
      store.selectNode(nodeId)
      emit('node-click', nodeId)
      refreshStyles()
    }
  })

  graph.render()
}

function refreshStyles() {
  if (!graph) return
  // 重新设置数据触发重渲染
  graph.setData({
    nodes: flowNodes.value.map(n => ({ ...n })),
    edges: FLOW_EDGES.map(e => ({ ...e })),
  })
  graph.render()
}

// 监听节点状态变化，刷新图表
watch(
  () => {
    // 监听所有节点的状态和选中节点
    const statuses = Object.values(store.nodes).map(n => n.status)
    return [statuses, store.selectedNodeId]
  },
  () => {
    nextTick(() => refreshStyles())
  },
  { deep: true }
)

onMounted(() => {
  nextTick(() => initGraph())
})

onUnmounted(() => {
  if (graph) {
    graph.destroy()
    graph = null
  }
})
</script>

<style scoped>
.graph-container {
  width: 100%;
  height: 320px;
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--border-color-split);
  overflow: hidden;
}
</style>
