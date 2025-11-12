<template>
  <div class="game-ranking-container">
    <div class="header">
      <div class="back-button" @click="goBack">返回</div>
      <h1 class="title">游戏排行智能分析</h1>
      <div class="placeholder"></div>
    </div>

    <div class="content-wrapper">
      <section class="intro-banner">
        <div class="intro-text">
          <h2>实时榜单 + 智能对话，全面洞察游戏行业趋势</h2>
          <p>
            系统自动爬取Steam、TapTap、3DM等平台榜单数据，结合AI可视化分析能力，
            帮你发现玩家偏好与市场机会。右侧图表可即时刷新，左侧可随时向AI提问，辅助决策。
          </p>
        </div>
        <div class="banner-actions">
          <button class="refresh-button" @click="loadReports" :disabled="reportsLoading">
            {{ reportsLoading ? '正在更新数据...' : '刷新榜单数据' }}
          </button>
          <span class="last-updated" v-if="lastUpdated">
            最近更新：{{ lastUpdated }}
          </span>
        </div>
      </section>

      <div class="main-layout">
        <div class="chat-area">
          <ChatRoom
            :messages="messages"
            :connection-status="connectionStatus"
            ai-type="game-ranking"
            @send-message="sendMessage"
          />
        </div>

        <div class="report-area">
          <div class="report-header">
            <div>
              <h2>游戏榜单可视化看板</h2>
              <p class="report-subtitle">
                数据驱动的分析结果，帮助你更快识别游戏行业的热门赛道与潜力产品。
              </p>
            </div>
          </div>

          <div v-if="reportsError" class="report-error">
            <p>{{ reportsError }}</p>
            <button @click="loadReports">重新获取数据</button>
          </div>

          <div v-else class="reports-grid">
            <GameReportCard
              v-for="report in reports"
              :key="report.id"
              :report="report"
            />

            <div v-if="reportsLoading && !reports.length" class="report-loading">
              <div class="spinner"></div>
              <p>正在抓取最新榜单数据...</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="footer-container">
      <AppFooter />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import GameReportCard from '../components/GameReportCard.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithGameRanking, getLatestGameReports } from '../api'

useHead({
  title: '游戏排行智能分析 - AI智能分析平台',
  meta: [
    {
      name: 'description',
      content: '游戏排行智能分析平台，自动检索游戏行业榜单、流量数据，提供专业的比较和分析功能'
    },
    {
      name: 'keywords',
      content: '游戏排行,游戏分析,游戏榜单,游戏数据,AI分析,游戏行业'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
const reports = ref([])
const reportsLoading = ref(false)
const reportsError = ref('')
const lastUpdated = ref('')
let eventSource = null

const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

const sendMessage = (message) => {
  addMessage(message, true, 'user-question')

  if (eventSource) {
    eventSource.close()
  }

  connectionStatus.value = 'connecting'

  const aiMessageIndex = messages.value.length
  addMessage('', false, 'ai-answer')

  eventSource = chatWithGameRanking(message)

  eventSource.onmessage = (event) => {
    const data = event.data

    if (data && data !== '[DONE]') {
      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].content += data
      }
    }

    if (data === '[DONE]') {
      connectionStatus.value = 'disconnected'
      eventSource.close()

      if (aiMessageIndex < messages.value.length) {
        messages.value[aiMessageIndex].type = 'ai-final'
      }
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()

    if (aiMessageIndex < messages.value.length) {
      messages.value[aiMessageIndex].type = 'ai-error'
    }
  }
}

const goBack = () => {
  router.push('/')
}

const formatDateTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

const loadReports = async () => {
  if (reportsLoading.value) return
  reportsLoading.value = true
  reportsError.value = ''

  try {
    const { data } = await getLatestGameReports()
    if (Array.isArray(data) && data.length > 0) {
      reports.value = data
      const latestTime = data
        .map(item => item.generatedAt)
        .filter(Boolean)
        .sort()
        .pop()
      lastUpdated.value = formatDateTime(latestTime)
    } else {
      reports.value = []
      reportsError.value = '暂未获取到榜单数据，请稍后重试。'
      lastUpdated.value = ''
    }
  } catch (error) {
    console.error('获取榜单数据失败', error)
    reportsError.value = '获取榜单数据失败，请检查网络或稍后再试。'
  } finally {
    reportsLoading.value = false
  }
}

onMounted(() => {
  addMessage('你好！我是游戏排行智能分析助手。我可以帮你：\n1. 自动检索游戏行业的各种榜单\n2. 分析游戏的流量、热度、玩家数据\n3. 比较不同游戏的表现和趋势\n4. 提供游戏行业洞察和建议\n\n请告诉我你想了解什么游戏数据？', false)
  loadReports()
})

onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.game-ranking-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f9fbff;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #ff6b6b, #ffa500);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s;
  justify-self: start;
}

.back-button:hover {
  opacity: 0.8;
}

.back-button:before {
  content: '←';
  margin-right: 8px;
}

.title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  text-align: center;
  justify-self: center;
}

.placeholder {
  width: 1px;
  justify-self: end;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: 0 24px 24px;
  gap: 24px;
}

.footer-container {
  margin-top: auto;
}

.intro-banner {
  background: linear-gradient(135deg, rgba(79, 117, 255, 0.12), rgba(130, 89, 245, 0.12));
  border-radius: 20px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  border: 1px solid rgba(79, 117, 255, 0.08);
}

.intro-text h2 {
  font-size: 22px;
  color: #1d2547;
  margin-bottom: 12px;
}

.intro-text p {
  color: #49516f;
  line-height: 1.6;
}

.banner-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  min-width: 220px;
}

.refresh-button {
  background: linear-gradient(135deg, #4f75ff, #8259f5);
  color: #fff;
  border: none;
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 12px 30px rgba(79, 117, 255, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.refresh-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  box-shadow: none;
}

.refresh-button:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px rgba(79, 117, 255, 0.35);
}

.last-updated {
  font-size: 12px;
  color: #6c7ba1;
}

.main-layout {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1.3fr);
  gap: 24px;
}

.chat-area {
  flex: 1 1 400px;
}

.report-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-header h2 {
  font-size: 20px;
  color: #1d2547;
  margin-bottom: 6px;
}

.report-subtitle {
  color: #6c7ba1;
  font-size: 14px;
  line-height: 1.5;
}

.reports-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.report-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: #fff;
  border-radius: 16px;
  border: 1px dashed rgba(79, 117, 255, 0.3);
  gap: 12px;
  color: #4f75ff;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 4px solid rgba(79, 117, 255, 0.2);
  border-top-color: #4f75ff;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.report-error {
  background: rgba(255, 99, 132, 0.08);
  border: 1px solid rgba(255, 99, 132, 0.2);
  border-radius: 12px;
  padding: 16px;
  color: #c23632;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.report-error button {
  background: transparent;
  color: #c23632;
  border: 1px solid rgba(194, 54, 50, 0.4);
  border-radius: 8px;
  padding: 8px 16px;
  cursor: pointer;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 768px) {
  .header {
    padding: 12px 16px;
  }

  .title {
    font-size: 18px;
  }

  .content-wrapper {
    padding: 0 16px 16px;
    gap: 16px;
  }

  .main-layout {
    grid-template-columns: 1fr;
  }

  .intro-banner {
    flex-direction: column;
  }

  .banner-actions {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

@media (max-width: 480px) {
  .header {
    padding: 10px 12px;
  }

  .back-button {
    font-size: 14px;
  }

  .title {
    font-size: 16px;
  }

  .content-wrapper {
    padding: 0 12px 12px;
  }

  .refresh-button {
    width: 100%;
  }
}
</style>

