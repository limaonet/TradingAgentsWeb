import axios, { isAxiosError } from 'axios'
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

export interface AnalysisStateResponse {
  analysisId: string
  ticker?: string
  date?: string
  status?: string
  progress?: number
  causalGraph?: {
    nodes?: unknown[]
    edges?: unknown[]
    summary?: string
  }
  causalLiveEnabled?: boolean
  causalLastRefreshedAt?: string
  marketReport?: string
  sentimentReport?: string
  fundamentalsReport?: string
  causalReport?: string
  finalTradeDecision?: string
  aggressiveAnalysis?: string
  conservativeAnalysis?: string
  neutralAnalysis?: string
  agentStatuses?: Record<string, string>
  errorMessage?: string
}

export class AnalysisNotFoundError extends Error {
  constructor(public readonly analysisId: string) {
    super(`分析任务不存在或已过期: ${analysisId}`)
    this.name = 'AnalysisNotFoundError'
  }
}

export const startAnalysis = async (request: StartAnalysisRequest): Promise<StartAnalysisResponse> => {
  const response = await apiClient.post<StartAnalysisResponse>('/analysis/start', request)
  return response.data
}

export const getAnalysisState = async (analysisId: string): Promise<AnalysisStateResponse> => {
  try {
    const response = await apiClient.get<AnalysisStateResponse>(`/analysis/${analysisId}`)
    return response.data
  } catch (e) {
    if (isAxiosError(e) && e.response?.status === 404) {
      throw new AnalysisNotFoundError(analysisId)
    }
    throw e
  }
}

export const getAnalysisReports = async (analysisId: string): Promise<Record<string, string>> => {
  try {
    const response = await apiClient.get<Record<string, string>>(`/analysis/${analysisId}/reports`)
    return response.data
  } catch (e) {
    if (isAxiosError(e) && e.response?.status === 404) {
      throw new AnalysisNotFoundError(analysisId)
    }
    throw e
  }
}

export const searchSymbols = async (keyword: string, limit = 10): Promise<SymbolSearchItem[]> => {
  if (!keyword.trim()) return []
  const response = await apiClient.get<SymbolSearchItem[]>('/analysis/symbols/search', {
    params: { keyword, limit },
  })
  return response.data || []
}

export const setCausalLive = async (analysisId: string, enabled: boolean): Promise<{
  analysisId: string
  causalLiveEnabled: boolean
  message: string
}> => {
  const response = await apiClient.post(`/analysis/${analysisId}/causal/live`, { enabled })
  return response.data
}

export const getCausalLive = async (analysisId: string): Promise<{
  analysisId: string
  causalLiveEnabled: boolean
  causalLastRefreshedAt?: string
}> => {
  const response = await apiClient.get(`/analysis/${analysisId}/causal/live`)
  return response.data
}

export default apiClient
