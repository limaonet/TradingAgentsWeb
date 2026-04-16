import axios from 'axios'
import { getApiBaseUrl } from '@/config/apiConfig'

const apiClient = axios.create({
  baseURL: getApiBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

export interface StartAnalysisRequest {
  ticker: string
  date: string
  researchDepth?: number
  selectedAnalysts?: string[]
  llmProvider?: string
  deepThinkModel?: string
  quickThinkModel?: string
}

export interface StartAnalysisResponse {
  analysisId: string
  status: string
  message: string
}

export interface SymbolSearchItem {
  code: string
  name: string
  marketType?: string
  quoteId?: string
}

export const startAnalysis = async (request: StartAnalysisRequest): Promise<StartAnalysisResponse> => {
  const response = await apiClient.post<StartAnalysisResponse>('/analysis/start', request)
  return response.data
}

export const getAnalysisState = async (analysisId: string): Promise<any> => {
  const response = await apiClient.get(`/analysis/${analysisId}`)
  return response.data
}

export const getAnalysisReports = async (analysisId: string): Promise<Record<string, string>> => {
  const response = await apiClient.get(`/analysis/${analysisId}/reports`)
  return response.data
}

export const searchSymbols = async (keyword: string, limit = 10): Promise<SymbolSearchItem[]> => {
  if (!keyword.trim()) return []
  const response = await apiClient.get<SymbolSearchItem[]>('/analysis/symbols/search', {
    params: { keyword, limit },
  })
  return response.data || []
}

export default apiClient
