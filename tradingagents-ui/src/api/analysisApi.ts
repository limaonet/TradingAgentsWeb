import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
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

export default apiClient
