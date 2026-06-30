<template>
  <div class="causal-chain-panel">
    <div class="panel-header">
      <div class="title">因果链分析</div>
      <div class="actions">
        <a-button size="small" :type="traceMode === 'up' ? 'primary' : 'default'" @click="setTraceMode('up')">
          向上追因
        </a-button>
        <a-button size="small" :type="traceMode === 'down' ? 'primary' : 'default'" @click="setTraceMode('down')">
          向下看果
        </a-button>
        <a-button size="small" @click="resetTrace">重置</a-button>
      </div>
    </div>
    <div v-if="summary" class="summary">{{ summary }}</div>
    <div v-if="!hasGraph" class="empty">等待因果分析完成...</div>
    <div v-show="hasGraph" ref="graphContainer" class="graph-container"></div>
    <div v-if="selectedNode" class="node-detail">
      <div class="detail-title">{{ selectedNode.label }}</div>
      <div class="detail-type">{{ typeLabel(selectedNode.type) }}</div>
      <div v-if="selectedNode.description" class="detail-desc">{{ selectedNode.description }}</div>
      <div v-if="selectedNode.sourceRef" class="detail-ref">来源: {{ selectedNode.sourceRef }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Graph } from '@antv/g6'
import { useAnalysisStore, type CausalGraphData } from '@/stores/analysisStore'

const store = useAnalysisStore()
const graphContainer = ref<HTMLElement | null>(null)
let graph: Graph | null = null
const selectedNodeId = ref<string | null>(null)
const traceMode = ref<'none' | 'up' | 'down'>('none')

const graphData = computed(() => store.causalGraph)
const summary = computed(() => graphData.value?.summary || '')
const hasGraph = computed(() => (graphData.value?.nodes?.length || 0) > 0)

const selectedNode = computed(() =>
  graphData.value?.nodes?.find((n) => n.id === selectedNodeId.value) || null
)

const TYPE_COLORS: Record<string, string> = {
  event: '#1677ff',
  factor: '#722ed1',
  indicator: '#fa8c16',
  outcome: '#52c41a',
}

function typeLabel(type: string) {
  const map: Record<string, string> = {
    event: '事件',
    factor: '因子',
    indicator: '指标',
    outcome: '结果',
  }
  return map[type] || type
}

function buildGraphElements(data: CausalGraphData, highlightIds: Set<string> | null) {
  const nodes = (data.nodes || []).map((n) => ({
    id: n.id,
    data: {
      label: n.label,
      type: n.type,
      dimmed: highlightIds ? !highlightIds.has(n.id) : false,
    },
  }))
  const edges = (data.edges || []).map((e) => ({
    id: e.id || `${e.source}-${e.target}`,
    source: e.source,
    target: e.target,
    data: {
      dimmed: highlightIds ? !(highlightIds.has(e.source) && highlightIds.has(e.target)) : false,
      edgeType: e.edgeType,
    },
  }))
  return { nodes, edges }
}

function collectUpstream(nodeId: string, edges: CausalGraphData['edges']): Set<string> {
  const result = new Set<string>([nodeId])
  let changed = true
  while (changed) {
    changed = false
    for (const e of edges || []) {
      if (result.has(e.target) && !result.has(e.source)) {
        result.add(e.source)
        changed = true
      }
    }
  }
  return result
}

function collectDownstream(nodeId: string, edges: CausalGraphData['edges']): Set<string> {
  const result = new Set<string>([nodeId])
  let changed = true
  while (changed) {
    changed = false
    for (const e of edges || []) {
      if (result.has(e.source) && !result.has(e.target)) {
        result.add(e.target)
        changed = true
      }
    }
  }
  return result
}

function getHighlightIds(): Set<string> | null {
  if (!selectedNodeId.value || traceMode.value === 'none') return null
  const edges = graphData.value?.edges || []
  if (traceMode.value === 'up') return collectUpstream(selectedNodeId.value, edges)
  return collectDownstream(selectedNodeId.value, edges)
}

function renderGraph() {
  if (!graphContainer.value || !hasGraph.value || !graphData.value) return

  const highlightIds = getHighlightIds()
  const { nodes, edges } = buildGraphElements(graphData.value, highlightIds)

  if (!graph) {
    const width = graphContainer.value.clientWidth || 600
    graph = new Graph({
      container: graphContainer.value,
      width,
      height: 280,
      autoFit: 'view',
      padding: [24, 24, 24, 24],
      behaviors: ['zoom-canvas', 'drag-canvas'],
      layout: { type: 'dagre', rankdir: 'TB', nodesep: 40, ranksep: 50 },
      node: {
        type: 'rect',
        style: {
          size: [140, 40],
          radius: 8,
          fill: (d: any) => TYPE_COLORS[d.data?.type] || '#595959',
          opacity: (d: any) => (d.data?.dimmed ? 0.25 : 1),
          stroke: (d: any) => (d.id === selectedNodeId.value ? '#fff' : 'transparent'),
          lineWidth: (d: any) => (d.id === selectedNodeId.value ? 2 : 0),
          labelText: (d: any) => d.data?.label || d.id,
          labelFill: '#fff',
          labelFontSize: 12,
          cursor: 'pointer',
        },
      },
      edge: {
        type: 'polyline',
        style: {
          stroke: (d: any) => (d.data?.edgeType === 'fact' ? '#52c41a' : '#bfbfbf'),
          lineDash: (d: any) => (d.data?.edgeType === 'fact' ? undefined : [4, 4]),
          lineWidth: 1.5,
          opacity: (d: any) => (d.data?.dimmed ? 0.2 : 1),
          endArrow: true,
        },
      },
      data: { nodes, edges },
    })

    graph.on('node:click', (evt: any) => {
      const nodeId = evt.target?.id || evt.targetId
      if (nodeId) {
        selectedNodeId.value = nodeId
        renderGraph()
      }
    })
    graph.render()
  } else {
    graph.setData({ nodes, edges })
    graph.render()
  }
}

function setTraceMode(mode: 'up' | 'down') {
  traceMode.value = mode
  if (!selectedNodeId.value && graphData.value?.nodes?.length) {
    selectedNodeId.value = graphData.value.nodes[0]?.id ?? null
  }
  nextTick(() => renderGraph())
}

function resetTrace() {
  traceMode.value = 'none'
  selectedNodeId.value = null
  nextTick(() => renderGraph())
}

watch(
  () => graphData.value,
  () => {
    if (graph) {
      graph.destroy()
      graph = null
    }
    nextTick(() => renderGraph())
  },
  { deep: true }
)

onMounted(() => nextTick(() => renderGraph()))
onUnmounted(() => {
  if (graph) {
    graph.destroy()
    graph = null
  }
})
</script>

<style scoped>
.causal-chain-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--border-color-split);
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color-split);
}

.title {
  font-weight: 600;
  color: var(--text-primary);
}

.actions {
  display: flex;
  gap: 8px;
}

.summary {
  padding: 8px 16px;
  font-size: 13px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color-split);
}

.empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 14px;
}

.graph-container {
  flex: 1;
  min-height: 200px;
}

.node-detail {
  padding: 10px 16px;
  border-top: 1px solid var(--border-color-split);
  font-size: 12px;
  color: var(--text-secondary);
}

.detail-title {
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.detail-type {
  color: var(--color-primary);
  margin-bottom: 4px;
}
</style>
