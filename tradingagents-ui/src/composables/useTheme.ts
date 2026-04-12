import { ref, onMounted, watch } from 'vue'
import { theme as antTheme } from 'ant-design-vue'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'tradingagents-theme'

export function useTheme() {
  const theme = ref<Theme>('light')
  const isDark = ref(false)

  const setTheme = (newTheme: Theme) => {
    theme.value = newTheme
    isDark.value = newTheme === 'dark'
    document.documentElement.setAttribute('data-theme', newTheme)
    localStorage.setItem(STORAGE_KEY, newTheme)
    
    // Update Ant Design Vue theme
    updateAntDesignTheme(newTheme)
  }

  const toggleTheme = () => {
    const newTheme = theme.value === 'light' ? 'dark' : 'light'
    setTheme(newTheme)
  }

  const updateAntDesignTheme = (currentTheme: Theme) => {
    // Configure Ant Design Vue theme
    const config = {
      theme: currentTheme === 'dark' ? 'dark' : 'light',
      token: {
        colorPrimary: '#1890ff',
        colorSuccess: '#52c41a',
        colorWarning: '#faad14',
        colorError: '#f5222d',
        colorInfo: '#1890ff',
        colorBgBase: currentTheme === 'dark' ? '#141414' : '#ffffff',
        colorTextBase: currentTheme === 'dark' ? 'rgba(255, 255, 255, 0.85)' : 'rgba(0, 0, 0, 0.85)',
        borderRadius: 4,
        wireframe: false,
      },
      algorithm: currentTheme === 'dark' ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
    }
    
    // Emit event for App.vue to handle
    window.dispatchEvent(new CustomEvent('theme-change', { detail: config }))
  }

  // Initialize theme on mount
  onMounted(() => {
    const savedTheme = localStorage.getItem(STORAGE_KEY) as Theme | null
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    
    const initialTheme = savedTheme || (prefersDark ? 'dark' : 'light')
    setTheme(initialTheme)
  })

  // Watch for system theme changes
  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      if (!localStorage.getItem(STORAGE_KEY)) {
        setTheme(e.matches ? 'dark' : 'light')
      }
    })
  }

  return {
    theme,
    isDark,
    setTheme,
    toggleTheme,
  }
}
