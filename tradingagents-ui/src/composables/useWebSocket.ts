import { ref, onUnmounted } from 'vue'
import { Client, type IMessage, type IFrame } from '@stomp/stompjs'
import { useAnalysisStore } from '@/stores/analysisStore'

export function useWebSocket() {
  const client = ref<Client | null>(null)
  const isConnected = ref(false)

  const connect = (analysisId: string) => {
    disconnect()

    const store = useAnalysisStore()

    // 使用原生 WebSocket（不依赖 sockjs-client）
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsHost = window.location.host
    const wsUrl = `${wsProtocol}//${wsHost}/ws/websocket`

    const stompClient = new Client({
      brokerURL: wsUrl,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    })

    stompClient.onConnect = () => {
      isConnected.value = true
      console.log('WebSocket connected, subscribing to', analysisId)

      stompClient.subscribe(`/topic/analysis/${analysisId}`, (message: IMessage) => {
        try {
          const body = JSON.parse(message.body)
          store.handleMessage(body)
        } catch (e) {
          console.error('Failed to parse WS message:', e)
        }
      })
    }

    stompClient.onDisconnect = () => {
      isConnected.value = false
    }

    stompClient.onStompError = (frame: IFrame) => {
      console.error('STOMP Error:', frame)
      isConnected.value = false
    }

    stompClient.activate()
    client.value = stompClient
  }

  const disconnect = () => {
    if (client.value) {
      client.value.deactivate()
      client.value = null
      isConnected.value = false
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return { isConnected, connect, disconnect }
}
