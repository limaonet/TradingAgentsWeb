<template>
  <div class="trading-dashboard dark-theme">
    <!-- 顶部搜索栏 -->
    <SearchHeader @search="handleSearch" />

    <!-- 主内容区 -->
    <main class="dashboard-main">
      <!-- 三栏布局 -->
      <div class="dashboard-grid">
        <!-- 左侧：分析师团队 -->
        <div class="grid-left">
          <AnalystTeam :analysts="analysts" @select="handleAnalystSelect" />
        </div>

        <!-- 中间：辩论区 -->
        <div class="grid-center">
          <DebateArena
            :messages="debateMessages"
            :risks="riskAssessments"
            :status="debateStatus"
            @open-risk-detail="openRiskDetail"
          />
        </div>

        <!-- 右侧：最终决策 -->
        <div class="grid-right">
          <DecisionPanel
            :conclusion="finalDecision.conclusion"
            :confidence="finalDecision.confidence"
            :actions="finalDecision.actions"
            :risks="finalDecision.risks"
            :full-report="finalDecision.fullDecisionText"
            @view-full-report="onViewFullDecision"
          />
        </div>
      </div>

      <!-- 底部：分析流程 -->
      <div class="dashboard-bottom">
        <AnalysisPipeline :stages="pipelineStages" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import SearchHeader from '@/components/topbar/SearchHeader.vue'
import AnalystTeam from '@/components/sidebar/AnalystTeam.vue'
import DebateArena from '@/components/debate/DebateArena.vue'
import DecisionPanel from '@/components/decision/DecisionPanel.vue'
import AnalysisPipeline from '@/components/timeline/AnalysisPipeline.vue'
import { useAnalysisStore } from '@/stores/analysisStore'
import { startAnalysis } from '@/api/analysisApi'
import { useWebSocket } from '@/composables/useWebSocket'
import { message } from 'ant-design-vue'

const store = useAnalysisStore()
const { connect, disconnect } = useWebSocket()
let hydrateTimer: ReturnType<typeof setInterval> | null = null

const openRiskDetail = (payload: { name: string; content: string }) => {
  if (payload.content?.trim()) {
    store.openReportViewer(`${payload.name} — 完整风控报告`, payload.content)
  }
}

const onViewFullDecision = () => {
  const text = finalDecision.value.fullDecisionText
  if (text?.trim()) {
    store.openReportViewer('组合经理 — 最终决策全文', text)
  }
}

const statusToCard = (status: string): 'idle' | 'running' | 'completed' | 'error' =>
  status === 'running' || status === 'completed' || status === 'error' ? status : 'idle'

const confidenceByStatus = (status: string) => {
  if (status === 'completed') return 85
  if (status === 'running') return 45
  return 0
}

const analysts = computed(() => {
  const ids = ['market_analyst', 'sentiment_analyst', 'fundamentals_analyst'] as const
  return ids.map((id) => {
    const node = store.nodes[id]
    const status = statusToCard(node?.status || 'idle')
    const hasContent = Boolean(node?.content)
    return {
      id,
      name: node?.name || id,
      role: node?.role || '',
      avatar: id.replace('_analyst', ''),
      status,
      confidence: hasContent ? confidenceByStatus(status) : 0,
      insights: hasContent ? [{ label: '报告已生成', type: 'bull' as const }] : [],
      metrics: [
        { label: '状态', value: status === 'completed' ? '完成' : status === 'running' ? '进行中' : '待执行', trend: status === 'completed' ? 'up' as const : status === 'error' ? 'down' as const : 'neutral' as const },
        { label: '字数', value: String(node?.content?.length || 0), trend: (node?.content?.length || 0) > 0 ? 'up' as const : 'neutral' as const },
      ],
      progress: status === 'completed' ? 100 : status === 'running' ? 60 : 0,
    }
  })
})

const debateMessages = computed(() => {
  const raw = store.timelineMessages.filter((m) => m.type === 'debate')
  return raw.map((m, idx) => ({
    name: m.agentName || m.agent || '辩论角色',
    avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(m.agent || String(idx))}&gender=male`,
    position: (m.agent?.includes('aggressive') || m.agent?.includes('bull')) ? 'left' as const : 'right' as const,
    type: (m.agent?.includes('aggressive') || m.agent?.includes('bull')) ? 'bull' as const :
      (m.agent?.includes('conservative') || m.agent?.includes('bear')) ? 'bear' as const : 'neutral' as const,
    content: m.content || '',
    reasoning: undefined,
    time: formatTimelineTime(m.timestamp),
    showTypewriter: false,
    delay: 0,
  }))
})

const riskAssessments = computed(() => {
  const riskNodes = ['aggressive_risk', 'conservative_risk', 'neutral_risk'] as const
  return riskNodes.map((id) => {
    const node = store.nodes[id]
    const text = node?.content || ''
    const lines = text.split('\n').filter(Boolean)
    return {
      nodeId: id,
      name: node?.name || id,
      level: id === 'aggressive_risk' ? 'high' as const : id === 'conservative_risk' ? 'low' as const : 'medium' as const,
      description: lines[0] || (node?.status === 'running' ? '风控分析进行中' : '等待风控阶段执行'),
      suggestion: lines[1] || '等待进一步建议',
      fullReport: text,
    }
  })
})

const debateStatus = computed<'idle' | 'running' | 'completed'>(() => {
  const riskNodes = ['aggressive_risk', 'conservative_risk', 'neutral_risk'] as const
  const statuses = riskNodes.map((id) => store.nodes[id]?.status)
  if (statuses.some((s) => s === 'running')) return 'running'
  if (statuses.every((s) => s === 'completed')) return 'completed'
  return 'idle'
})

const finalDecision = computed(() => {
  const decision = store.nodes.portfolio_manager?.content || store.reportsCache.finalTradeDecision || ''
  const hasDecision = Boolean(decision)
  return {
    conclusion: hasDecision ? '最终决策已生成' : (store.status === 'running' ? '决策生成中' : '待生成'),
    confidence: hasDecision ? 80 : 0,
    actions: [
      { label: '分析状态', value: store.status === 'completed' ? '完成' : store.status === 'error' ? '异常' : '进行中', type: store.status === 'error' ? 'danger' as const : 'primary' as const },
      { label: '报告数', value: String(Object.values(store.nodes).filter((n) => Boolean(n.content)).length), type: 'primary' as const },
      { label: '流程进度', value: `${store.progress}%`, type: 'primary' as const },
    ],
    risks: hasDecision ? decision.split('\n').filter((l) => l.trim().length > 0).slice(0, 4) : ['等待组合经理输出最终决策'],
    fullDecisionText: decision,
  }
})

const pipelineStages = computed(() => {
  const mapStatus = (ids: string[]) => {
    const statuses = ids.map((id) => store.nodes[id]?.status || 'idle')
    if (statuses.some((s) => s === 'running')) return 'running' as const
    if (statuses.every((s) => s === 'completed')) return 'completed' as const
    return 'pending' as const
  }
  return [
    { name: '市场分析', status: mapStatus(['market_analyst']) },
    { name: '情绪分析', status: mapStatus(['sentiment_analyst']) },
    { name: '基本面分析', status: mapStatus(['fundamentals_analyst']) },
    { name: '研究整合', status: mapStatus(['research_manager']) },
    { name: '风控评估', status: mapStatus(['aggressive_risk', 'conservative_risk', 'neutral_risk']) },
    { name: '最终决策', status: mapStatus(['portfolio_manager']) },
  ]
})

// 处理搜索
const handleSearch = async (payload: string | { ticker: string; displayName?: string }) => {
  const ticker = typeof payload === 'string' ? payload : payload.ticker
  const displayName = typeof payload === 'string' ? payload : payload.displayName
  if (!ticker?.trim() || store.analysisBusy) return
  store.setAnalysisStarting(true)
  try {
    const end = new Date()
    
    const resp = await startAnalysis({
      ticker: ticker.trim(),
      date: end.toISOString().split('T')[0] || '',
    })
    
    const analysisId = resp.analysisId || ''
    const dateStr = end.toISOString().split('T')[0] || ''
    
    store.startAnalysis(analysisId, displayName || ticker.trim(), dateStr)
    localStorage.setItem('tradingagents_last_analysis_id', analysisId)
    connect(analysisId)
    await store.hydrateFromServer(analysisId)
    startHydrationPolling(analysisId)
    message.success(`开始分析 ${displayName || ticker.trim()}`)
  } catch (e: any) {
    message.error('启动分析失败：' + (e.message || '网络错误'))
  } finally {
    store.setAnalysisStarting(false)
  }
}

const startHydrationPolling = (analysisId: string) => {
  if (hydrateTimer) clearInterval(hydrateTimer)
  hydrateTimer = setInterval(async () => {
    await store.hydrateFromServer(analysisId)
    if (store.status === 'completed' || store.status === 'error') {
      if (hydrateTimer) {
        clearInterval(hydrateTimer)
        hydrateTimer = null
      }
    }
  }, 2500)
}

// 处理分析师选择
const handleAnalystSelect = (id: string) => {
  store.selectNode(id)
  const node = store.nodes[id]
  if (node?.content?.trim()) {
    store.openReportViewer(`${node.name} — 报告详情`, node.content)
  }
}

onMounted(() => {
  // 加载深色主题
  document.documentElement.classList.add('dark-theme')
  const cachedAnalysisId = localStorage.getItem('tradingagents_last_analysis_id')
  if (cachedAnalysisId) {
    store.hydrateFromServer(cachedAnalysisId).then(() => {
      if (store.status === 'running') {
        connect(cachedAnalysisId)
        startHydrationPolling(cachedAnalysisId)
      }
    })
  }
})

onUnmounted(() => {
  disconnect()
  if (hydrateTimer) {
    clearInterval(hydrateTimer)
    hydrateTimer = null
  }
})

const formatTimelineTime = (ts: string) => {
  try {
    const d = new Date(ts)
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return ts
  }
}
</script>

<style scoped>
.trading-dashboard {
  width: 100%;
  height: 100vh;
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dashboard-main {
  flex: 1;
  width: 100%;
  max-width: none;
  margin: 0;
  box-sizing: border-box;
  padding: 16px 20px 20px;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 16px;
  overflow: hidden;
  min-height: 0;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(250px, 22vw) minmax(0, 1fr) minmax(250px, 20vw);
  grid-template-rows: minmax(350px, 1fr);
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.grid-left,
.grid-center,
.grid-right {
  min-height: 0;
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

.grid-left > *,
.grid-center > *,
.grid-right > * {
  height: 100%;
  width: 100%;
}

.dashboard-bottom {
  flex-shrink: 0;
}

@media (max-width: 1200px) {
  .dashboard-grid {
    grid-template-columns: minmax(240px, 28%) minmax(0, 1fr) minmax(220px, 24%);
    grid-template-rows: minmax(320px, 1fr);
  }
}

/* 小屏：单列布局 */
@media (max-width: 768px) {
  .dashboard-main {
    padding: 12px;
    display: flex;
    flex-direction: column;
    overflow: auto;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    min-height: auto;
    overflow: visible;
  }

  .grid-left,
  .grid-center,
  .grid-right {
    max-height: 300px;
    overflow: visible;
    height: auto;
  }

  .grid-left > *,
  .grid-center > *,
  .grid-right > * {
    height: auto;
  }
}
</style>
