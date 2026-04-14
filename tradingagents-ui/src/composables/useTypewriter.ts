import { ref, computed } from 'vue'

export function useTypewriter(text: string, options: {
  speed?: number
  delay?: number
  onComplete?: () => void
} = {}) {
  const { speed = 30, delay = 0, onComplete } = options
  
  const displayText = ref('')
  const isTyping = ref(false)
  const isComplete = ref(false)
  let timeoutId: ReturnType<typeof setTimeout> | null = null
  
  const startTyping = () => {
    if (isTyping.value) return
    
    isTyping.value = true
    displayText.value = ''
    isComplete.value = false
    
    const typeNext = (index: number) => {
      if (index >= text.length) {
        isTyping.value = false
        isComplete.value = true
        onComplete?.()
        return
      }
      
      displayText.value = text.slice(0, index + 1)
      timeoutId = setTimeout(() => typeNext(index + 1), speed)
    }
    
    if (delay > 0) {
      timeoutId = setTimeout(() => typeNext(0), delay)
    } else {
      typeNext(0)
    }
  }
  
  const stopTyping = () => {
    if (timeoutId) {
      clearTimeout(timeoutId)
      timeoutId = null
    }
    isTyping.value = false
    displayText.value = text
    isComplete.value = true
  }
  
  const reset = () => {
    stopTyping()
    displayText.value = ''
    isComplete.value = false
  }
  
  return {
    displayText: computed(() => displayText.value),
    isTyping: computed(() => isTyping.value),
    isComplete: computed(() => isComplete.value),
    startTyping,
    stopTyping,
    reset,
  }
}

// 批量打字机（用于多条消息依次显示）
export function useBatchTypewriter(messages: string[], options: {
  speed?: number
  delayBetween?: number
  onMessageComplete?: (index: number) => void
  onAllComplete?: () => void
} = {}) {
  const { speed = 30, delayBetween = 500, onMessageComplete, onAllComplete } = options
  
  const currentIndex = ref(0)
  const displayTexts = ref<string[]>(new Array(messages.length).fill(''))
  const isTyping = ref(false)
  
  const startBatch = () => {
    if (isTyping.value) return
    isTyping.value = true
    currentIndex.value = 0
    displayTexts.value = new Array(messages.length).fill('')
    
    const typeMessage = (msgIndex: number, charIndex: number) => {
      if (msgIndex >= messages.length) {
        isTyping.value = false
        onAllComplete?.()
        return
      }
      
      const message = messages[msgIndex]
      if (!message) {
        typeMessage(msgIndex + 1, 0)
        return
      }
      
      if (charIndex >= message.length) {
        onMessageComplete?.(msgIndex)
        setTimeout(() => typeMessage(msgIndex + 1, 0), delayBetween)
        return
      }
      
      displayTexts.value[msgIndex] = message.slice(0, charIndex + 1)
      setTimeout(() => typeMessage(msgIndex, charIndex + 1), speed)
    }
    
    typeMessage(0, 0)
  }
  
  return {
    currentIndex: computed(() => currentIndex.value),
    displayTexts: computed(() => displayTexts.value),
    isTyping: computed(() => isTyping.value),
    startBatch,
  }
}
