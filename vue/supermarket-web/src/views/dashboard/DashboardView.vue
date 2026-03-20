<template>
  <div>
    <!-- 今日概览卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-icon" style="background: #e6f4ff">
              <el-icon color="#1890ff" size="24"><Money /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥ {{ stats.todaySaleAmount || 0 }}</div>
              <div class="stat-label">今日销售额</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-icon" style="background: #f6ffed">
              <el-icon color="#52c41a" size="24"><ShoppingBag /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayOrderCount || 0 }}</div>
              <div class="stat-label">今日订单数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-icon" style="background: #fff7e6">
              <el-icon color="#fa8c16" size="24"><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">¥ {{ stats.todayAvgAmount || 0 }}</div>
              <div class="stat-label">今日客单价</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-card">
            <div class="stat-icon" style="background: #fff0f6">
              <el-icon color="#eb2f96" size="24"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.todayMemberCount || 0 }}</div>
              <div class="stat-label">今日新增会员</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 时间范围选择 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form inline>
        <el-form-item label="时间范围">
          <el-radio-group v-model="timeRange" @change="handleTimeRangeChange">
            <el-radio-button value="7">近7天</el-radio-button>
            <el-radio-button value="30">近30天</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="timeRange === 'custom'">
          <el-date-picker
            v-model="customDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
            @change="handleCustomDateChange"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 图表区 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <!-- 折线图 -->
      <el-col :span="16">
        <el-card shadow="never" header="销售趋势">
          <div ref="lineChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>

      <!-- 饼图 -->
      <el-col :span="8">
        <el-card shadow="never" header="商品分类销售占比">
          <div v-if="stats.categoryStats && stats.categoryStats.length > 0"
               ref="pieChartRef" style="height: 300px"></div>
          <el-empty v-else description="暂无数据" style="height: 300px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 底部 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never" header="热销商品TOP5">
          <div v-if="stats.topProducts && stats.topProducts.length">
            <div
              v-for="(item, index) in stats.topProducts"
              :key="item.productId"
              class="rank-item"
            >
              <span class="rank-no" :class="'rank-' + (index + 1)">{{ index + 1 }}</span>
              <span class="rank-name">{{ item.productName }}</span>
              <span class="rank-amount">¥{{ item.totalAmount }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>

      <!-- AI运营报告 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center">
              <span>AI 运营月报</span>
              <div style="display:flex; gap:8px">
                <el-tag type="info">{{ lastMonthLabel }}</el-tag>
                <el-button type="primary" :loading="reportLoading" @click="generateReport">
                  {{ reportContent ? '重新生成' : '生成报告' }}
                </el-button>
                <el-button v-if="reportContent" @click="exportReport">导出 Word</el-button>
              </div>
            </div>
          </template>

          <div v-if="!reportContent && !reportLoading" class="report-empty">
            <el-empty description="点击「生成报告」自动分析上月经营数据" />
          </div>

          <div v-if="reportLoading && !reportContent" class="report-loading">
            <el-icon class="loading-icon"><Loading /></el-icon>
            <span style="margin-left:8px; color:#999">AI 正在分析数据，请稍候...</span>
          </div>

          <div v-if="reportContent" class="report-content" v-html="renderedReport"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch,computed  } from 'vue'
import * as echarts from 'echarts'
import request from '../../utils/request'
import { useStoreStore } from '../../stores/store'
import { ElMessage } from 'element-plus'

const storeStore = useStoreStore()

const stats = ref({})
const lineChartRef = ref(null)
const pieChartRef = ref(null)
let lineChart = null
let pieChart = null

// 时间范围
const timeRange = ref('7')
const customDateRange = ref(null)
const startDate = ref(null)
const endDate = ref(null)

// ---- AI 报告 ----
const reportContent = ref('')
const reportLoading = ref(false)

const lastMonthLabel = computed(() => {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
})

// 把 \n 转成换行，简单渲染 markdown 标题
const renderedReport = computed(() => {
  if (!reportContent.value) return ''
  return reportContent.value
    .replace(/\\n/g, '\n')
    .replace(/\n/g, '<br>')
    .replace(/^(#{1,3})\s+(.+)$/gm, '<strong>$2</strong>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
})

const generateReport = async () => {
  reportContent.value = ''
  reportLoading.value = true

  try {
    const token = localStorage.getItem('token')
    const storeId = storeStore.currentStoreId
    const url = `http://localhost:8080/stats/report${storeId ? '?storeId=' + storeId : ''}`

    const response = await fetch(url, {
      headers: { Authorization: 'Bearer ' + token }
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const text = line.slice(6)
          if (text === '[DONE]') {
            reportLoading.value = false
            return
          }
          reportContent.value += text
        }
      }
    }
  } catch (e) {
    ElMessage.error('生成失败，请重试')
  } finally {
    reportLoading.value = false
  }
}

// 导出 Word（用 HTML 转 blob 方式）
const exportReport = () => {
  const content = reportContent.value.replace(/\\n/g, '\n')
  const html = `
    <html xmlns:o='urn:schemas-microsoft-com:office:office'
          xmlns:w='urn:schemas-microsoft-com:office:word'
          xmlns='http://www.w3.org/TR/REC-html40'>
    <head><meta charset='utf-8'><title>运营月报</title></head>
    <body>
      <h2>${lastMonthLabel.value} 运营月报</h2>
      <pre style="font-family: 微软雅黑; font-size: 14px; line-height: 1.8">${content}</pre>
    </body>
    </html>
  `
  const blob = new Blob(['\ufeff' + html], { type: 'application/msword' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${lastMonthLabel.value}运营月报.doc`
  a.click()
  URL.revokeObjectURL(url)
}



















const handleTimeRangeChange = val => {
  if (val !== 'custom') {
    const days = parseInt(val)
    const now = new Date()
    const start = new Date(now)
    start.setDate(now.getDate() - days + 1)
    startDate.value = formatDate(start)
    endDate.value = formatDate(now)
    fetchStats()
  }
}

const handleCustomDateChange = val => {
  if (val && val.length === 2) {
    startDate.value = val[0]
    endDate.value = val[1]
    fetchStats()
  }
}

const formatDate = date => {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const fetchStats = async () => {
  const params = { storeId: storeStore.currentStoreId }
  if (startDate.value) params.startDate = startDate.value
  if (endDate.value) params.endDate = endDate.value

  const res = await request.get('/stats/dashboard', { params })
  stats.value = res.data

  await nextTick()
  await nextTick() // 等两次确保 v-if 的 DOM 已渲染
  renderLineChart(res.data.last7Days || [])
  renderPieChart(res.data.categoryStats || [])
}

const renderLineChart = data => {
  if (!lineChartRef.value) return
  if (!lineChart) lineChart = echarts.init(lineChartRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.map(d => d.date) },
    yAxis: { type: 'value', name: '销售额(元)' },
    series: [{
      name: '销售额',
      type: 'line',
      smooth: true,
      data: data.map(d => d.amount),
      itemStyle: { color: '#1890ff' },
      areaStyle: { color: 'rgba(24,144,255,0.1)' }
    }]
  })
}

const renderPieChart = data => {
  if (!pieChartRef.value || !data.length) return
  // 每次重新初始化，避免 DOM 重建后实例失效
  if (pieChart) {
    pieChart.dispose()
    pieChart = null
  }
  pieChart = echarts.init(pieChartRef.value)
  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: ¥{c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center'
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['40%', '50%'],
      avoidLabelOverlap: false,
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' }
      },
      data: data.map(d => ({
        name: d.categoryName,
        value: d.amount
      }))
    }]
  })
}

// 初始化默认近7天
const initDefaultRange = () => {
  const now = new Date()
  const start = new Date(now)
  start.setDate(now.getDate() - 6)
  startDate.value = formatDate(start)
  endDate.value = formatDate(now)
}

watch(() => storeStore.currentStoreId, () => fetchStats())

onMounted(() => {
  initDefaultRange()
  fetchStats()
})
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info .stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
}
.stat-info .stat-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}
.rank-item {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.rank-item:last-child { border-bottom: none; }
.rank-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0f0f0;
  color: #666;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  flex-shrink: 0;
}
.rank-1 { background: #ff4d4f; color: #fff; }
.rank-2 { background: #fa8c16; color: #fff; }
.rank-3 { background: #fadb14; color: #fff; }
.rank-name { flex: 1; margin-left: 12px; font-size: 14px; }
.rank-amount { color: #1890ff; font-weight: 600; font-size: 14px; }













.report-content {
  font-size: 14px;
  line-height: 1.9;
  color: #333;
  white-space: pre-wrap;
  min-height: 200px;
}
.report-empty, .report-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
.loading-icon {
  font-size: 20px;
  color: #1890ff;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
