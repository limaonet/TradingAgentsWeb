<template>
  <div class="analysis-input">
    <div class="input-row">
      <a-input
        v-model:value="ticker"
        placeholder="股票代码 如 600519 或名称 贵州茅台"
        class="ticker-input"
        :disabled="store.isRunning"
        @pressEnter="handleStart"
      >
        <template #prefix>
          <SearchOutlined />
        </template>
      </a-input>

      <a-range-picker
        v-model:value="dateRange"
        :disabled="store.isRunning"
        class="date-picker"
        format="YYYY-MM-DD"
        :placeholder="['开始日期', '结束日期']"
      />

      <a-button
        type="primary"
        size="large"
        :loading="store.isRunning"
        :disabled="!ticker || !ticker.trim()"
        @click="handleStart"
        class="start-btn"
      >
        <template #icon><ThunderboltOutlined /></template>
        {{ store.isRunning ? '分析中...' : '开始分析' }}
      </a-button>

      <a-button
        v-if="store.status !== 'idle'"
        @click="handleReset"
        class="reset-btn"
      >
        重置
      </a-button>
    </div>

    <!-- 进度条 -->
    <div v-if="store.isRunning || store.status === 'completed'" class="progress-bar">
      <a-progress
        :percent="store.progress"
        :status="store.status === 'error' ? 'exception' : store.status === 'completed' ? 'success' : 'active'"
        :stroke-color="{ '0%': '#108ee9', '100%': '#87d068' }"
        size="small"
      />
      <div class="progress-info">
        <span v-if="store.isRunning && store.currentAgent" class="current-agent">
          {{ store.AGENT_META[store.currentAgent]?.icon }}
          {{ store.AGENT_META[store.currentAgent]?.name || store.currentAgent }} 执行中...
        </span>
        <span v-else-if="store.status === 'completed'" class="completed-text">分析完成</span>
        <span v-else-if="store.status === 'error'" class="error-text">分析出错</span>
        <span class="elapsed">{{ formatTime(store.elapsedSeconds) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SearchOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { useAnalysisStore } from '@/stores/analysisStore'
import { useWebSocket } from '@/composables/useWebSocket'
import { startAnalysis } from '@/api/analysisApi'
import { message } from 'ant-design-vue'

const store = useAnalysisStore()
const { connect, disconnect } = useWebSocket()

const ticker = ref('')
const dateRange = ref<[Dayjs, Dayjs]>([
  dayjs().subtract(60, 'day'),
  dayjs(),
])

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m > 0 ? `${m}分${s}秒` : `${s}秒`
}

const handleStart = async () => {
  if (!ticker.value.trim() || store.isRunning) return

  const [start, end] = dateRange.value
  const dateStr = end.format('YYYY-MM-DD')

  try {
    const resp = await startAnalysis({
      ticker: ticker.value.trim(),
      date: dateStr,
    })

    store.startAnalysis(resp.analysisId, ticker.value.trim(), dateStr)
    connect(resp.analysisId)
    message.success(`开始分析 ${ticker.value.trim()}`)
  } catch (e: any) {
    message.error('启动分析失败：' + (e.message || '网络错误'))
  }
}

const handleReset = () => {
  disconnect()
  store.reset()
}
</script>

<style scoped>
.analysis-input {
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);
  border: 1px solid var(--border-color-split);
}

.input-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.ticker-input {
  width: 280px;
  height: 40px;
}

.date-picker {
  height: 40px;
}

.start-btn {
  height: 40px;
  font-weight: 600;
}

.reset-btn {
  height: 40px;
}

.progress-bar {
  margin-top: var(--spacing-sm);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
  margin-top: 2px;
}

.current-agent {
  color: var(--primary-color);
  font-weight: 500;
}

.completed-text {
  color: var(--success-color);
  font-weight: 500;
}

.error-text {
  color: var(--error-color);
  font-weight: 500;
}

.elapsed {
  color: var(--text-tertiary);
}
</style>
