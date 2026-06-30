<template>
  <div class="causal-chain-panel">
    <div class="panel-header">
      <div class="title-row">
        <div class="title">因果链分析</div>
        <div v-if="hasGraph" class="live-controls">
          <a-switch
            :checked="store.causalLiveEnabled"
            :loading="liveToggling"
            :disabled="!store.analysisId"
            checked-children="实时"
            un-checked-children="快照"
            @change="onLiveToggle"
          />
        </div>
      </div>
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

    <div v-if="liveStatusText" class="live-status">{{ liveStatusText }}</div>

    <div class="legend">
      <span class="legend-item"><i class="dot event" />事件</span>
      <span class="legend-item"><i class="dot factor" />因子</span>
      <span class="legend-item"><i class="dot indicator" />指标</span>
      <span class="legend-item"><i class="dot outcome" />结果</span>
      <span class="legend-item"><i class="line fact" />事实边</span>
      <span class="legend-item"><i class="line inference" />推断边</span>
      <span class="legend-item"><i class="line contradicts" />矛盾边</span>
    </div>

    <div v-if="summary" class="summary">{{ summary }}</div>
    <div v-if="!hasGraph" class="empty">等待因果分析完成...</div>
    <div v-show="hasGraph" ref="graphContainer" class="graph-container"></div>

    <div v-if="selectedNode" class="detail-panel">
      <div class="detail-title">{{ selectedNode.label }}</div>
      <div class="detail-type">{{ typeLabel(selectedNode.type) }}</div>
      <div v-if="selectedNode.description" class="detail-desc">{{ selectedNode.description }}</div>
      <div v-if="selectedNode.confidence != null" class="detail-meta">置信度: {{ formatConfidence(selectedNode.confidence) }}</div>
      <div v-if="selectedNode.sourceRef" class="detail-ref">来源: {{ selectedNode.sourceRef }}</div>
    </div>

    <div v-if="selectedEdge" class="detail-panel edge-detail">
      <div class="detail-title">因果边: {{ relationLabel(selectedEdge.relation) }}</div>
      <div class="detail-meta">
        {{ selectedEdge.source }} → {{ selectedEdge.target }}
        · 类型: {{ selectedEdge.edgeType === 'fact' ? '事实' : '推断' }}
        <span v-if="selectedEdge.confidence != null"> · 置信度 {{ formatConfidence(selectedEdge.confidence) }}</span>
      </div>
      <div v-if="selectedEdge.evidence" class="detail-desc">证据: {{ selectedEdge.evidence }}</div>
      <div v-else class="detail-desc muted">无文字证据（推断边）</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { Graph } from '@antv/g6'
import { message } from 'ant-design-vue'
import { useAnalysisStore, type CausalGraphData, type CausalEdgeData } from '@/stores/analysisStore'
import { setCausalLive, getCausalLive } from '@/api/analysisApi'
import { useWebSocket } from '@/composables/useWebSocket'

type GraphNodeDatum = { id?: string; data?: { label?: string; type?: string; dimmed?: boolean } }
type GraphEdgeDatum = { id?: string; source?: string; target?: string; data?: { dimmed?: boolean; edgeType?: string; relation?: string } }

const store = useAnalysisStore()
const { connect, isConnected } = useWebSocket()
const graphContainer = ref<HTMLElement | null>(null)
let graph: Graph | null = null
const selectedNodeId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const traceMode = ref<'none' | 'up' | 'down'>('none')
const liveToggling = ref(false)

const graphData = computed(() => store.causalGraph)
const summary = computed(() => graphData.value?.summary || '')
const hasGraph = computed(() => (graphData.value?.nodes?.length || 0) > 0)

const liveStatusText = computed(() => {
  if (!hasGraph.value) return ''
  const parts: string[] = []
  if (store.causalLiveEnabled) {
    parts.push('实时追踪中（约每 1 分钟轮询新闻/公告）')
  }
  if (store.causalLastRefreshedAt) {
    parts.push(`上次刷新: ${formatTime(store.causalLastRefreshedAt)}`)
  }
  if (store.causalLiveMessage) {
    parts.push(store.causalLiveMessage)
  }
  return parts.join(' · ')
})

function formatTime(ts: string | null) {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return ts
  }
}

async function onLiveToggle(checked: boolean) {
  if (!store.analysisId) {
    message.warning('请先启动分析')
    return
  }
  liveToggling.value = true
  try {
    if (checked && !isConnected.value) {
      connect(store.analysisId)
    }
    const resp = await setCausalLive(store.analysisId, checked)
    store.setCausalLiveState(resp.causalLiveEnabled, store.causalLastRefreshedAt, resp.message)
    message.success(resp.message)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '操作失败'
    message.error(msg)
  } finally {
    liveToggling.value = false
  }
}

async function syncLiveState() {
  if (!store.analysisId) return
  try {
    const resp = await getCausalLive(store.analysisId)
    store.setCausalLiveState(resp.causalLiveEnabled, resp.causalLastRefreshedAt ?? null)
  } catch {
    // ignore
  }
}

const selectedNode = computed(() =>
  graphData.value?.nodes?.find((n) => n.id === selectedNodeId.value) || null
)

const selectedEdge = computed(() =>
  graphData.value?.edges?.find((e) => (e.id || `${e.source}-${e.target}`) === selectedEdgeId.value) || null
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

function relationLabel(relation?: string) {
  const map: Record<string, string> = {
    causes: '导致',
    amplifies: '放大',
    contradicts: '矛盾',
    dampens: '抑制',
    leads_to: '导向',
  }
  return relation ? (map[relation] || relation) : '关联'
}

function formatConfidence(value: number) {
  return value <= 1 ? `${Math.round(value * 100)}%` : `${Math.round(value)}%`
}

function edgeStroke(edge: CausalEdgeData) {
  if (edge.relation === 'contradicts' || edge.relation === 'dampens') return '#ff4d4f'
  return edge.edgeType === 'fact' ? '#52c41a' : '#bfbfbf'
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
      relation: e.relation,
      raw: e,
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
  const edgeLookup = new Map((graphData.value.edges || []).map((e) => [e.id || `${e.source}-${e.target}`, e]))

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
          fill: (d) => TYPE_COLORS[(d as GraphNodeDatum).data?.type || ''] || '#595959',
          opacity: (d) => ((d as GraphNodeDatum).data?.dimmed ? 0.25 : 1),
          stroke: (d) => ((d as GraphNodeDatum).id === selectedNodeId.value ? '#fff' : 'transparent'),
          lineWidth: (d) => ((d as GraphNodeDatum).id === selectedNodeId.value ? 2 : 0),
          labelText: (d) => (d as GraphNodeDatum).data?.label || (d as GraphNodeDatum).id || '',
          labelFill: '#fff',
          labelFontSize: 12,
          cursor: 'pointer',
        },
      },
      edge: {
        type: 'polyline',
        style: {
          stroke: (d) => {
            const edge = d as GraphEdgeDatum
            const raw = edge.id ? edgeLookup.get(edge.id) : undefined
            return raw ? edgeStroke(raw) : '#bfbfbf'
          },
          lineDash: (d) => {
            const edge = d as GraphEdgeDatum
            const raw = edge.id ? edgeLookup.get(edge.id) : undefined
            if (raw?.relation === 'contradicts' || raw?.relation === 'dampens') return [6, 3]
            return raw?.edgeType === 'fact' ? undefined : [4, 4]
          },
          lineWidth: (d) => ((d as GraphEdgeDatum).id === selectedEdgeId.value ? 2.5 : 1.5),
          opacity: (d) => ((d as GraphEdgeDatum).data?.dimmed ? 0.2 : 1),
          endArrow: true,
          cursor: 'pointer',
        },
      },
      data: { nodes, edges },
    })

    graph.on('node:click', (evt) => {
      const payload = evt as { target?: { id?: string }; targetId?: string }
      const nodeId = payload.target?.id || payload.targetId
      if (nodeId) {
        selectedNodeId.value = nodeId
        selectedEdgeId.value = null
        renderGraph()
      }
    })

    graph.on('edge:click', (evt) => {
      const payload = evt as { target?: { id?: string }; targetId?: string }
      const edgeId = payload.target?.id || payload.targetId
      if (edgeId) {
        selectedEdgeId.value = edgeId
        selectedNodeId.value = null
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
  selectedEdgeId.value = null
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

onMounted(() => {
  nextTick(() => renderGraph())
  syncLiveState()
})
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
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color-split);
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.live-controls {
  display: flex;
  align-items: center;
}

.live-status {
  padding: 6px 16px;
  font-size: 12px;
  color: var(--color-primary);
  background: rgba(22, 119, 255, 0.06);
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

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 16px;
  font-size: 11px;
  color: var(--text-tertiary);
  border-bottom: 1px solid var(--border-color-split);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.dot.event { background: #1677ff; }
.dot.factor { background: #722ed1; }
.dot.indicator { background: #fa8c16; }
.dot.outcome { background: #52c41a; }

.line {
  width: 18px;
  height: 0;
  border-top: 2px solid #bfbfbf;
  display: inline-block;
}

.line.fact { border-color: #52c41a; }
.line.inference { border-top-style: dashed; }
.line.contradicts { border-color: #ff4d4f; border-top-style: dashed; }

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

.detail-panel {
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

.detail-meta {
  margin-bottom: 4px;
}

.detail-desc.muted {
  color: var(--text-tertiary);
  font-style: italic;
}

.edge-detail {
  background: rgba(255, 77, 79, 0.06);
}
</style>
