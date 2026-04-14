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
          <AnalystTeam
            :analysts="analysts"
            @select="handleAnalystSelect"
          />
        </div>

        <!-- 中间：辩论区 -->
        <div class="grid-center">
          <DebateArena
            :messages="debateMessages"
            :risks="riskAssessments"
            :status="debateStatus"
          />
        </div>

        <!-- 右侧：最终决策 -->
        <div class="grid-right">
          <DecisionPanel
            :conclusion="finalDecision.conclusion"
            :confidence="finalDecision.confidence"
            :actions="finalDecision.actions"
            :risks="finalDecision.risks"
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
import { ref, computed, onMounted } from 'vue'
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

// 模拟数据 - 分析师团队
const analysts = ref([
  {
    id: 'market',
    name: '市场分析师',
    role: '技术面分析',
    avatar: 'market',
    status: 'completed' as const,
    confidence: 75,
    insights: [
      { label: '趋势上涨', type: 'bull' as const },
      { label: '突破阻力', type: 'bull' as const },
    ],
    metrics: [
      { label: 'MA5/MA20', value: '金叉', trend: 'up' as const },
      { label: 'RSI', value: '62.5', trend: 'neutral' as const },
    ],
    progress: 100,
  },
  {
    id: 'sentiment',
    name: '情绪分析师',
    role: '市场情绪',
    avatar: 'sentiment',
    status: 'completed' as const,
    confidence: 68,
    insights: [
      { label: '情绪贪婪', type: 'bull' as const },
      { label: '资金流入', type: 'bull' as const },
    ],
    metrics: [
      { label: '恐惧指数', value: '72', trend: 'up' as const },
      { label: '资金流向', value: '+2.3亿', trend: 'up' as const },
    ],
    progress: 100,
  },
  {
    id: 'news',
    name: '新闻分析师',
    role: '资讯解析',
    avatar: 'news',
    status: 'running' as const,
    confidence: 0,
    insights: [
      { label: '业绩预增', type: 'bull' as const },
      { label: '行业政策', type: 'neutral' as const },
    ],
    metrics: [
      { label: '利好', value: '3条', trend: 'up' as const },
      { label: '利空', value: '1条', trend: 'down' as const },
    ],
    progress: 65,
  },
  {
    id: 'fundamentals',
    name: '基本面分析师',
    role: '财务分析',
    avatar: 'fundamentals',
    status: 'idle' as const,
    confidence: 0,
    insights: [],
    metrics: [
      { label: 'PE', value: '28.5', trend: 'neutral' as const },
      { label: 'ROE', value: '15.2%', trend: 'up' as const },
    ],
    progress: 0,
  },
])

// 模拟数据 - 辩论消息
const debateMessages = ref([
  {
    name: '牛市研究员',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Bull&gender=male',
    position: 'left' as const,
    type: 'bull' as const,
    content: '这只股票基本面扎实，近期突破关键阻力位，建议积极布局。从技术指标看，MACD金叉确认，成交量温和放大，上涨动能充足。',
    reasoning: '1. 营收连续3季度增长\n2. 行业龙头地位稳固\n3. 估值处于历史低位',
    time: '10:23',
    showTypewriter: false,
    delay: 0,
  },
  {
    name: '熊市研究员',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Bear&gender=male',
    position: 'right' as const,
    type: 'bear' as const,
    content: '虽然短期技术面偏强，但宏观环境不确定性增加，建议谨慎。当前估值已反映乐观预期，上行空间有限。',
    reasoning: '1. 宏观经济下行压力\n2. 行业竞争加剧\n3. 估值修复空间有限',
    time: '10:25',
    showTypewriter: false,
    delay: 0,
  },
])

// 模拟数据 - 风险评估
const riskAssessments = ref([
  {
    name: '风控A（激进）',
    level: 'high' as const,
    description: '波动率过高，短期回调风险较大',
    suggestion: '建议控制仓位，设置严格止损',
  },
  {
    name: '风控B（稳健）',
    level: 'medium' as const,
    description: '回撤风险可控，但需关注宏观变化',
    suggestion: '建议分批建仓，降低平均成本',
  },
  {
    name: '风控C（保守）',
    level: 'low' as const,
    description: '长期趋势向好，短期波动属正常',
    suggestion: '建议逢低吸纳，长期持有',
  },
])

// 辩论状态
const debateStatus = ref<'idle' | 'running' | 'completed'>('running')

// 最终决策
const finalDecision = ref({
  conclusion: '谨慎买入',
  confidence: 72,
  actions: [
    { label: '建议仓位', value: '30%', type: 'primary' as const },
    { label: '止损位', value: '-8%', type: 'danger' as const },
    { label: '目标位', value: '+15%', type: 'primary' as const },
  ],
  risks: ['宏观波动风险', '行业政策变化', '市场情绪转向'],
})

// 分析流程阶段
const pipelineStages = ref([
  { name: '市场分析', status: 'completed' as const },
  { name: '情绪分析', status: 'completed' as const },
  { name: '新闻解析', status: 'running' as const },
  { name: '牛熊辩论', status: 'pending' as const },
  { name: '风控评估', status: 'pending' as const },
  { name: '最终决策', status: 'pending' as const },
])

// 处理搜索
const handleSearch = async (ticker: string) => {
  try {
    const end = new Date()
    
    const resp = await startAnalysis({
      ticker: ticker.trim(),
      date: end.toISOString().split('T')[0] || '',
    })
    
    const analysisId = resp.analysisId || ''
    const dateStr = end.toISOString().split('T')[0] || ''
    
    store.startAnalysis(analysisId, ticker.trim(), dateStr)
    connect(analysisId)
    message.success(`开始分析 ${ticker.trim()}`)
    
    // 重置所有状态
    resetAnalysisState()
  } catch (e: any) {
    message.error('启动分析失败：' + (e.message || '网络错误'))
  }
}

// 重置分析状态
const resetAnalysisState = () => {
  analysts.value.forEach(a => {
    a.status = 'idle'
    a.progress = 0
  })
  debateStatus.value = 'idle'
  pipelineStages.value.forEach(s => s.status = 'pending')
}

// 处理分析师选择
const handleAnalystSelect = (id: string) => {
  console.log('Selected analyst:', id)
}

onMounted(() => {
  // 加载深色主题
  document.documentElement.classList.add('dark-theme')
})
</script>

<style scoped>
.trading-dashboard {
  min-height: 100vh;
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
}

.dashboard-main {
  flex: 1;
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

.dashboard-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 200px 1fr 240px;
  grid-template-rows: minmax(350px, 1fr);
  gap: 12px;
  min-height: 0;
}

.grid-left,
.grid-center,
.grid-right {
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.grid-left > *,
.grid-center > *,
.grid-right > * {
  height: 100%;
}

.dashboard-bottom {
  flex-shrink: 0;
}

/* 响应式 - 小屏幕时改为单列 */
@media (max-width: 768px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
    min-height: auto;
  }
  
  .grid-left,
  .grid-center,
  .grid-right {
    max-height: 300px;
  }
}
</style>
