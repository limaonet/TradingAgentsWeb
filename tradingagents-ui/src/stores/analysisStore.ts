import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getAnalysisReports, getAnalysisState } from '@/api/analysisApi'

export type NodeStatus = 'idle' | 'pending' | 'running' | 'completed' | 'error'
export type AnalysisStatus = 'idle' | 'running' | 'completed' | 'error'

export interface AgentNode {
  id: string
  name: string
  role: string
  phase: number
  status: NodeStatus
  content: string
  startTime?: string
  endTime?: string
}

export interface TimelineMessage {
  id: string
  timestamp: string
  agent: string
  agentName: string
  type: 'progress' | 'report' | 'agent_status' | 'debate' | 'complete' | 'error'
  status: string
  content: string
  debateType?: string
  round?: number
}

// Agent 元数据定义
const AGENT_META: Record<string, { name: string; role: string; phase: number; icon: string }> = {
  market_analyst: { name: '市场分析师', role: '技术面分析：K线、MACD、RSI、KDJ、均线、支撑阻力位', phase: 1, icon: '📊' },
  sentiment_analyst: { name: '情绪分析师', role: '舆情分析：市场情绪、新闻倾向、社交热度、公告解读', phase: 1, icon: '💭' },
  fundamentals_analyst: { name: '基本面分析师', role: '财务分析：盈利能力、偿债能力、估值水平、现金流', phase: 1, icon: '📈' },
  research_manager: { name: '研究经理', role: '综合三维分析，识别一致性与分歧，制定投资策略', phase: 2, icon: '👨‍💼' },
  trader: { name: '交易员', role: '将投资计划具体化：入场价位、止盈止损、仓位管理', phase: 3, icon: '💼' },
  aggressive_risk: { name: '激进派风控', role: '强调收益机会，承担合理风险，优化收益方案', phase: 4, icon: '⚡' },
  conservative_risk: { name: '保守派风控', role: '强调风险控制，优先安全，强化风控措施', phase: 4, icon: '🛡️' },
  neutral_risk: { name: '中立派风控', role: '平衡双方观点，综合评估，给出折中建议', phase: 4, icon: '⚖️' },
  portfolio_manager: { name: '组合经理', role: '综合所有信息做出最终交易决策，对决策负责', phase: 5, icon: '🎯' },
}

// 后端 agent 名称到前端 nodeId 的映射
const AGENT_NAME_MAP: Record<string, string> = {
  'MarketAnalyst': 'market_analyst',
  'market_analyst': 'market_analyst',
  'SentimentAnalyst': 'sentiment_analyst',
  'sentiment_analyst': 'sentiment_analyst',
  'FundamentalsAnalyst': 'fundamentals_analyst',
  'fundamentals_analyst': 'fundamentals_analyst',
  'ResearchManager': 'research_manager',
  'research_manager': 'research_manager',
  'Trader': 'trader',
  'trader': 'trader',
  'AggressiveRisk': 'aggressive_risk',
  'aggressive_risk': 'aggressive_risk',
  'ConservativeRisk': 'conservative_risk',
  'conservative_risk': 'conservative_risk',
  'NeutralRisk': 'neutral_risk',
  'neutral_risk': 'neutral_risk',
  // WebSocket 辩论消息里的 speaker 简称
  'aggressive': 'aggressive_risk',
  'conservative': 'conservative_risk',
  'neutral': 'neutral_risk',
  'PortfolioManager': 'portfolio_manager',
  'portfolio_manager': 'portfolio_manager',
}

// 后端 reportType 到 nodeId 的映射
const REPORT_TYPE_MAP: Record<string, string> = {
  'market_report': 'market_analyst',
  'sentiment_report': 'sentiment_analyst',
  'fundamentals_report': 'fundamentals_analyst',
  'investment_plan': 'research_manager',
  'trader_plan': 'trader',
  'marketReport': 'market_analyst',
  'sentimentReport': 'sentiment_analyst',
  'fundamentalsReport': 'fundamentals_analyst',
  'newsReport': 'sentiment_analyst',
  'investmentPlan': 'research_manager',
  'researchManagerDecision': 'research_manager',
  'traderInvestmentPlan': 'trader',
  'tradePlan': 'trader',
  'aggressiveAnalysis': 'aggressive_risk',
  'conservativeAnalysis': 'conservative_risk',
  'neutralAnalysis': 'neutral_risk',
  'finalTradeDecision': 'portfolio_manager',
  'bullResearch': 'research_manager',
  'bearResearch': 'research_manager',
}

const REPORT_TO_STATE_KEY: Record<string, string> = {
  market_report: 'marketReport',
  sentiment_report: 'sentimentReport',
  fundamentals_report: 'fundamentalsReport',
  investment_plan: 'investmentPlan',
  trader_plan: 'traderInvestmentPlan',
}

export const useAnalysisStore = defineStore('analysis', () => {
  // 核心状态
  const analysisId = ref('')
  const status = ref<AnalysisStatus>('idle')
  const progress = ref(0)
  const currentAgent = ref('')
  const selectedNodeId = ref<string | null>(null)
  const ticker = ref('')
  const date = ref('')
  const startTime = ref<number | null>(null)
  const elapsedSeconds = ref(0)

  // 节点状态
  const nodes = ref<Record<string, AgentNode>>(createInitialNodes())
  const timelineMessages = ref<TimelineMessage[]>([])
  const syncedState = ref<any>(null)
  const reportsCache = ref<Record<string, string>>({})

  // 计时器
  let timerHandle: ReturnType<typeof setInterval> | null = null

  function createInitialNodes(): Record<string, AgentNode> {
    const result: Record<string, AgentNode> = {}
    for (const [id, meta] of Object.entries(AGENT_META)) {
      result[id] = { id, name: meta.name, role: meta.role, phase: meta.phase, status: 'idle', content: '' }
    }
    return result
  }

  // 计算属性
  const selectedNode = computed(() => selectedNodeId.value ? nodes.value[selectedNodeId.value] : null)
  const nodeList = computed(() => Object.values(nodes.value))
  const completedCount = computed(() => nodeList.value.filter(n => n.status === 'completed').length)
  const totalNodes = computed(() => nodeList.value.length)
  const isStarting = ref(false)
  const isRunning = computed(() => status.value === 'running')
  /** 分析请求进行中（含接口往返），用于防止重复点击 */
  const analysisBusy = computed(() => status.value === 'running' || isStarting.value)

  const reportViewerOpen = ref(false)
  const reportViewerTitle = ref('')
  const reportViewerBody = ref('')

  function setAnalysisStarting(v: boolean) {
    isStarting.value = v
  }

  function openReportViewer(title: string, body: string) {
    reportViewerTitle.value = title
    reportViewerBody.value = body
    reportViewerOpen.value = true
  }

  function closeReportViewer() {
    reportViewerOpen.value = false
  }

  // 获取 agent 元数据
  function getAgentMeta(id: string) {
    return AGENT_META[id]
  }

  // 解析后端 agent 名称
  function resolveNodeId(agentName: string): string | null {
    return AGENT_NAME_MAP[agentName] || null
  }

  // 开始分析
  function startAnalysis(id: string, tickerVal: string, dateVal: string) {
    analysisId.value = id
    ticker.value = tickerVal
    date.value = dateVal
    status.value = 'running'
    progress.value = 0
    currentAgent.value = ''
    selectedNodeId.value = null
    nodes.value = createInitialNodes()
    timelineMessages.value = []
    syncedState.value = null
    reportsCache.value = {}
    startTime.value = Date.now()
    elapsedSeconds.value = 0

    // 启动计时器
    if (timerHandle) clearInterval(timerHandle)
    timerHandle = setInterval(() => {
      if (startTime.value) {
        elapsedSeconds.value = Math.floor((Date.now() - startTime.value) / 1000)
      }
    }, 1000)
  }

  // 处理 WebSocket 消息
  function handleMessage(msg: any) {
    const type = msg.type
    const agentRaw = msg.agent || ''
    const nodeId = resolveNodeId(agentRaw)
    const timestamp = msg.timestamp || new Date().toISOString()

    // 追加时间线
    const tlMsg: TimelineMessage = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      timestamp,
      agent: agentRaw,
      agentName: nodeId ? AGENT_META[nodeId]?.name || agentRaw : agentRaw,
      type,
      status: msg.status || '',
      content: msg.content || '',
      debateType: msg.debateType,
      round: msg.round,
    }
    timelineMessages.value.push(tlMsg)

    switch (type) {
      case 'progress':
      case 'agent_status':
        if (nodeId && nodes.value[nodeId]) {
          const s = msg.status as NodeStatus
          if (s === 'running' || s === 'completed' || s === 'error' || s === 'pending') {
            nodes.value[nodeId].status = s
            if (s === 'running') {
              nodes.value[nodeId].startTime = timestamp
              currentAgent.value = nodeId
            }
            if (s === 'completed') {
              nodes.value[nodeId].endTime = timestamp
            }
          }
        }
        // 更新进度
        progress.value = Math.round((completedCount.value / totalNodes.value) * 100)
        break

      case 'report': {
        const reportType = msg.reportType || ''
        const normalizedReportType = REPORT_TO_STATE_KEY[reportType] || reportType
        const targetNodeId = REPORT_TYPE_MAP[reportType] || nodeId
        if (targetNodeId && nodes.value[targetNodeId]) {
          // 追加内容（研究经理可能收到多个report）
          if (nodes.value[targetNodeId].content) {
            nodes.value[targetNodeId].content += '\n\n---\n\n' + (msg.content || '')
          } else {
            nodes.value[targetNodeId].content = msg.content || ''
          }
          nodes.value[targetNodeId].status = 'completed'
          nodes.value[targetNodeId].endTime = timestamp
        }
        if (normalizedReportType) {
          reportsCache.value[normalizedReportType] = msg.content || ''
        }
        progress.value = Math.round((completedCount.value / totalNodes.value) * 100)
        break
      }

      case 'debate': {
        const content = msg.content || ''
        const isRiskPlaceholder = /正在分析|正在整合/.test(content)
        if (nodeId && nodes.value[nodeId]) {
          const prefix = msg.round ? `**[第${msg.round}轮]**\n\n` : ''
          if (nodes.value[nodeId].content) {
            nodes.value[nodeId].content += '\n\n---\n\n' + prefix + content
          } else {
            nodes.value[nodeId].content = prefix + content
          }
          nodes.value[nodeId].status =
            !isRiskPlaceholder && content.length > 15 ? 'completed' : 'running'
        }
        break
      }

      case 'complete':
        status.value = 'completed'
        progress.value = 100
        currentAgent.value = ''
        // 存储最终决策到组合经理
        if (msg.finalDecision && nodes.value.portfolio_manager) {
          nodes.value.portfolio_manager.content = msg.finalDecision
          nodes.value.portfolio_manager.status = 'completed'
          nodes.value.portfolio_manager.endTime = timestamp
          reportsCache.value.finalTradeDecision = msg.finalDecision
        }
        // 停止计时
        if (timerHandle) { clearInterval(timerHandle); timerHandle = null }
        break

      case 'error':
        if (nodeId && nodes.value[nodeId]) {
          nodes.value[nodeId].status = 'error'
          nodes.value[nodeId].content = msg.content || '分析出错'
        }
        status.value = 'error'
        if (timerHandle) { clearInterval(timerHandle); timerHandle = null }
        break
    }
  }

  function mergeReportsToNodes(reports: Record<string, string>) {
    const setNodeReport = (nodeId: string, content: string) => {
      const node = nodes.value[nodeId]
      if (!node) return
      node.content = content
      node.status = 'completed'
    }

    if (reports.marketReport) {
      setNodeReport('market_analyst', reports.marketReport)
    }
    if (reports.sentimentReport) {
      setNodeReport('sentiment_analyst', reports.sentimentReport)
    }
    if (reports.fundamentalsReport) {
      setNodeReport('fundamentals_analyst', reports.fundamentalsReport)
    }
    if (reports.investmentPlan) {
      setNodeReport('research_manager', reports.investmentPlan)
    }
    if (reports.traderInvestmentPlan) {
      setNodeReport('trader', reports.traderInvestmentPlan)
    }
    if (reports.aggressiveAnalysis) {
      setNodeReport('aggressive_risk', reports.aggressiveAnalysis)
    }
    if (reports.conservativeAnalysis) {
      setNodeReport('conservative_risk', reports.conservativeAnalysis)
    }
    if (reports.neutralAnalysis) {
      setNodeReport('neutral_risk', reports.neutralAnalysis)
    }
    if (reports.finalTradeDecision) {
      setNodeReport('portfolio_manager', reports.finalTradeDecision)
    }
  }

  function syncNodeStatusesFromAgentStatuses(agentStatuses: Record<string, string>) {
    for (const [agentName, agentStatus] of Object.entries(agentStatuses || {})) {
      const mapped = resolveNodeId(agentName)
      if (mapped && nodes.value[mapped]) {
        const normalized = (agentStatus || '').toLowerCase()
        if (normalized === 'running' || normalized === 'completed' || normalized === 'error' || normalized === 'pending') {
          nodes.value[mapped].status = normalized as NodeStatus
        }
      }
    }
  }

  async function hydrateFromServer(id: string) {
    if (!id) return
    try {
      const [stateResp, reportsResp] = await Promise.all([
        getAnalysisState(id),
        getAnalysisReports(id),
      ])
      syncedState.value = stateResp
      reportsCache.value = reportsResp || {}
      const mergedReports = {
        marketReport: stateResp?.marketReport || reportsResp?.marketReport || '',
        sentimentReport: stateResp?.sentimentReport || reportsResp?.sentimentReport || '',
        fundamentalsReport: stateResp?.fundamentalsReport || reportsResp?.fundamentalsReport || '',
        investmentPlan: reportsResp?.investmentPlan || '',
        traderInvestmentPlan: reportsResp?.traderInvestmentPlan || '',
        aggressiveAnalysis: stateResp?.aggressiveAnalysis || reportsResp?.aggressiveAnalysis || '',
        conservativeAnalysis: stateResp?.conservativeAnalysis || reportsResp?.conservativeAnalysis || '',
        neutralAnalysis: stateResp?.neutralAnalysis || reportsResp?.neutralAnalysis || '',
        finalTradeDecision: stateResp?.finalTradeDecision || reportsResp?.finalTradeDecision || '',
      }

      if (stateResp?.ticker) ticker.value = stateResp.ticker
      if (stateResp?.date) date.value = stateResp.date
      if (stateResp?.status === 'completed' || stateResp?.status === 'error' || stateResp?.status === 'running') {
        status.value = stateResp.status
      }
      if (stateResp?.status === 'completed') progress.value = 100

      syncNodeStatusesFromAgentStatuses(stateResp?.agentStatuses || {})
      mergeReportsToNodes(mergedReports)
    } catch (err) {
      console.warn('hydrateFromServer failed:', err)
    }
  }

  function selectNode(nodeId: string | null) {
    selectedNodeId.value = nodeId
  }

  function reset() {
    analysisId.value = ''
    status.value = 'idle'
    progress.value = 0
    currentAgent.value = ''
    selectedNodeId.value = null
    ticker.value = ''
    date.value = ''
    startTime.value = null
    elapsedSeconds.value = 0
    nodes.value = createInitialNodes()
    timelineMessages.value = []
    if (timerHandle) { clearInterval(timerHandle); timerHandle = null }
  }

  return {
    // state
    analysisId, status, progress, currentAgent, selectedNodeId,
    ticker, date, startTime, elapsedSeconds,
    nodes, timelineMessages, syncedState, reportsCache,
    // computed
    selectedNode, nodeList, completedCount, totalNodes, isRunning, isStarting, analysisBusy,
    reportViewerOpen, reportViewerTitle, reportViewerBody,
    // methods
    getAgentMeta, resolveNodeId, startAnalysis, handleMessage, selectNode, reset, hydrateFromServer,
    setAnalysisStarting, openReportViewer, closeReportViewer,
    // constants
    AGENT_META,
  }
})
