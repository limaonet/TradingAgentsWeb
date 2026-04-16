function trimTrailingSlash(s: string): string {
  return s.replace(/\/+$/, '')
}

/**
 * REST API base URL (must include `/api` path prefix used by the backend).
 * In dev, defaults to Vite proxy path `/api` unless VITE_API_BASE_URL is set.
 */
export function getApiBaseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL
  if (raw != null && String(raw).trim() !== '') {
    return trimTrailingSlash(String(raw).trim())
  }
  return '/api'
}

/**
 * STOMP broker WebSocket URL (Spring registers the endpoint at `/ws/websocket`).
 * Set VITE_WS_URL for a fixed backend (e.g. ws://localhost:8080/ws/websocket).
 */
export function getWsBrokerUrl(): string {
  const raw = import.meta.env.VITE_WS_URL
  if (raw != null && String(raw).trim() !== '') {
    return String(raw).trim()
  }
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${window.location.host}/ws/websocket`
}
