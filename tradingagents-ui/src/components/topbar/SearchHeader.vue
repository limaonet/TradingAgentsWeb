<template>
  <header class="search-header">
    <div class="logo-section">
      <div class="logo-icon">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M3 3V21H21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M7 16L11 11L15 14L21 7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <span class="logo-text">TradingAgents</span>
    </div>

    <div class="search-section">
      <div class="search-input-wrapper">
        <SearchOutlined class="search-icon" />
        <input
          v-model="ticker"
          type="text"
          class="search-input"
          placeholder="输入股票代码或名称..."
          :disabled="store.analysisBusy"
          @keyup.enter="handleSearch"
          @input="handleInput"
          @focus="showHistory = true"
          @blur="handleBlur"
        />
        <div v-if="ticker" class="clear-btn" @click="ticker = ''">
          <CloseOutlined />
        </div>
      </div>

      <!-- 历史记录下拉 -->
      <Transition name="dropdown">
        <div v-if="showHistory && dropdownItems.length > 0" class="history-dropdown">
          <div class="history-header">
            <span>{{ ticker.trim() ? '搜索结果' : '最近分析' }}</span>
            <button v-if="!ticker.trim()" class="clear-history" @click="clearHistory">清空</button>
          </div>
          <div
            v-for="item in dropdownItems"
            :key="`${item.ticker}-${item.name}`"
            class="history-item"
            @click="selectHistory(item)"
          >
            <span class="ticker-code">{{ item.ticker }}</span>
            <span class="ticker-name">{{ item.name }}</span>
            <span class="ticker-date">{{ formatDate(item.date) }}</span>
          </div>
        </div>
      </Transition>

      <button
        class="analyze-btn"
        type="button"
        :disabled="!ticker.trim() || store.analysisBusy"
        @click="handleSearch"
      >
        <LoadingOutlined v-if="store.analysisBusy" spin />
        <ThunderboltOutlined v-else />
        <span>{{ store.analysisBusy ? '分析中...' : '开始分析' }}</span>
      </button>
    </div>

  </header>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import {
  SearchOutlined,
  CloseOutlined,
  ThunderboltOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { searchSymbols } from '@/api/analysisApi'
import { useAnalysisStore } from '@/stores/analysisStore'

interface HistoryItem {
  ticker: string
  name: string
  date: number
}

const emit = defineEmits<{
  search: [payload: { ticker: string; displayName?: string }]
}>()

const store = useAnalysisStore()

const ticker = ref('')
const showHistory = ref(false)
const searchHistory = ref<HistoryItem[]>([])
const suggestions = ref<HistoryItem[]>([])
let queryTimer: ReturnType<typeof setTimeout> | null = null

const dropdownItems = computed(() => ticker.value.trim() ? suggestions.value : searchHistory.value)

// 加载历史记录
onMounted(() => {
  const saved = localStorage.getItem('tradingagents_history')
  if (saved) {
    searchHistory.value = JSON.parse(saved)
  }
})

const handleSearch = () => {
  if (!ticker.value.trim() || store.analysisBusy) return

  store.setAnalysisStarting(true)
  const selected = suggestions.value.find((s) => s.ticker === ticker.value.trim())
  emit('search', {
    ticker: ticker.value.trim(),
    displayName: selected ? `${selected.name} (${selected.ticker})` : ticker.value.trim(),
  })

  addToHistory(ticker.value.trim(), selected?.name || ticker.value.trim())
}

const addToHistory = (code: string, name: string) => {
  const existingIndex = searchHistory.value.findIndex(h => h.ticker === code)
  if (existingIndex > -1) {
    searchHistory.value.splice(existingIndex, 1)
  }
  
  searchHistory.value.unshift({
    ticker: code,
    name,
    date: Date.now(),
  })
  
  // 只保留最近10条
  if (searchHistory.value.length > 10) {
    searchHistory.value = searchHistory.value.slice(0, 10)
  }
  
  localStorage.setItem('tradingagents_history', JSON.stringify(searchHistory.value))
}

const selectHistory = (item: HistoryItem) => {
  ticker.value = item.ticker
  suggestions.value = []
  showHistory.value = false
  handleSearch()
}

const clearHistory = () => {
  searchHistory.value = []
  localStorage.removeItem('tradingagents_history')
}

const handleBlur = () => {
  setTimeout(() => {
    showHistory.value = false
  }, 200)
}

const handleInput = () => {
  if (store.analysisBusy) return
  const keyword = ticker.value.trim()
  if (!keyword) {
    suggestions.value = []
    return
  }
  if (queryTimer) clearTimeout(queryTimer)
  queryTimer = setTimeout(async () => {
    try {
      const items = await searchSymbols(keyword, 10)
      suggestions.value = items.map((item) => ({
        ticker: item.code,
        name: item.name,
        date: Date.now(),
      }))
    } catch {
      suggestions.value = []
    }
  }, 250)
}

const formatDate = (timestamp: number): string => {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${date.getMonth() + 1}/${date.getDate()}`
}

onBeforeUnmount(() => {
  if (queryTimer) clearTimeout(queryTimer)
})

</script>

<style scoped>
.search-header {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: nowrap;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  position: relative;
  z-index: 100;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.logo-icon svg {
  width: 20px;
  height: 20px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.search-section {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  flex: 1;
  min-width: 320px;
  max-width: none;
}

.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 220px;
}

.search-icon {
  position: absolute;
  left: 14px;
  color: var(--text-muted);
  font-size: 16px;
}

.search-input {
  width: 100%;
  height: 44px;
  padding: 0 40px;
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 14px;
  transition: all 0.3s ease;
}

.search-input:focus {
  border-color: var(--color-info);
  box-shadow: var(--glow-blue);
  outline: none;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.clear-btn {
  position: absolute;
  right: 12px;
  color: var(--text-muted);
  cursor: pointer;
  transition: color 0.2s;
}

.clear-btn:hover {
  color: var(--text-primary);
}

.history-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  width: 100%;
  min-width: 240px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-secondary);
}

.clear-history {
  background: none;
  border: none;
  color: var(--color-info);
  cursor: pointer;
  font-size: 12px;
}

.clear-history:hover {
  text-decoration: underline;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
}

.history-item:hover {
  background: var(--bg-card-hover);
}

.ticker-code {
  font-weight: 600;
  color: var(--text-primary);
  min-width: 60px;
}

.ticker-name {
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
}

.ticker-date {
  font-size: 11px;
  color: var(--text-muted);
}

.analyze-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  min-width: 112px;
  padding: 0 24px;
  background: linear-gradient(135deg, #3b82f6, #06b6d4);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.analyze-btn:hover:not(:disabled) {
  box-shadow: var(--glow-cyan);
  transform: translateY(-1px);
}

.analyze-btn:disabled {
  background: var(--bg-input);
  color: var(--text-muted);
  cursor: not-allowed;
}

@media (max-width: 920px) {
  .search-header {
    padding: 12px;
    flex-wrap: wrap;
    align-items: stretch;
  }

  .logo-section {
    order: 1;
  }

  .search-section {
    order: 2;
    width: 100%;
    min-width: 0;
    max-width: none;
  }

  .search-input-wrapper {
    min-width: 0;
  }

  .analyze-btn {
    padding: 0 16px;
    flex-shrink: 0;
  }
}

@media (max-width: 640px) {
  .search-section {
    flex-wrap: wrap;
    gap: 10px;
  }

  .analyze-btn {
    width: 100%;
    justify-content: center;
  }
}

/* 下拉动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.2s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
