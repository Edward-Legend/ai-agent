import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
  
  const fullUrl = `${API_BASE_URL}${url}?${queryString}`
  
  // 创建EventSource
  const eventSource = new EventSource(fullUrl)
  
  eventSource.onmessage = event => {
    let data = event.data
    
    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }
  
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  
  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI恋爱大师聊天
export const chatWithLoveApp = (message, chatId) => {
  return connectSSE('/ai/love_app/chat/sse', { message, chatId })
}

// AI超级智能体聊天
export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// 游戏排行智能分析聊天
export const chatWithGameRanking = (message, chatId) => {
  return connectSSE('/ai/game-ranking/game/chat/sse', { message, chatId })
}

// 获取最新的游戏榜单报表
export const getLatestGameReports = () => {
  return request.get('/ai/game-ranking/report/latest')
}

// 获取Steam榜单报表
export const getSteamReport = () => {
  return request.get('/ai/game-ranking/report/steam')
}

// 获取TapTap榜单报表
export const getTapTapReport = () => {
  return request.get('/ai/game-ranking/report/taptap')
}

// 获取3DM榜单报表
export const get3DMReport = () => {
  return request.get('/ai/game-ranking/report/3dm')
}

// 获取分析结果（异步）
export const getAnalysisResult = (reportId) => {
  return request.get(`/ai/game-ranking/report/analysis/${reportId}`)
}

export default {
  chatWithLoveApp,
  chatWithManus,
  chatWithGameRanking,
  getLatestGameReports,
  getSteamReport,
  getTapTapReport,
  get3DMReport,
  getAnalysisResult
} 