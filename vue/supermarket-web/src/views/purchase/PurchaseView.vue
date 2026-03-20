<template>
  <div>
    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form :model="searchForm" inline>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width:130px">
            <el-option label="草稿" :value="0" />
            <el-option label="待审核" :value="1" />
            <el-option label="已审核" :value="2" />
            <el-option label="已入库" :value="3" />
            <el-option label="已取消" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <span>采购单列表</span>
          <el-button type="primary" @click="dialogVisible = true">
            <el-icon><Plus /></el-icon> 新建采购单
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="采购单号" width="180" />
        <el-table-column prop="storeId" label="门店ID" width="80" />
        <el-table-column prop="supplierId" label="供应商ID" width="90" />
        <el-table-column prop="totalAmount" label="总金额" width="100">
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expectedDate" label="预计到货" width="120" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small" type="primary"
              @click="handleSubmit(row.id)">提交审核</el-button>
            <el-button
              v-if="row.status === 1"
              size="small" type="success"
              @click="handleApprove(row.id)">审核通过</el-button>
            <el-button
              v-if="row.status === 2"
              size="small" type="warning"
              @click="handleReceive(row.id)">确认入库</el-button>
            <el-popconfirm
              v-if="row.status < 3"
              title="确定取消该采购单吗？"
              @confirm="handleCancel(row.id)">
              <template #reference>
                <el-button size="small" type="danger">取消</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top:16px; display:flex; justify-content:flex-end">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next"
          @change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新建采购单弹窗 -->
    <el-dialog v-model="dialogVisible" title="新建采购单" width="600px" @close="resetForm">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="供应商ID" prop="supplierId">
              <el-input-number v-model="form.supplierId" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计到货">
              <el-date-picker
                v-model="form.expectedDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width:100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 采购明细 -->
        <div style="margin-bottom:8px; font-weight:600">采购明细</div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="商品ID" width="100">
            <template #default="{ row }">
              <el-input-number v-model="row.productId" :min="1" size="small" style="width:80px" />
            </template>
          </el-table-column>
          <el-table-column label="采购价">
            <template #default="{ row }">
              <el-input-number v-model="row.purchasePrice" :min="0" :precision="2" size="small" style="width:100px" />
            </template>
          </el-table-column>
          <el-table-column label="数量">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0.001" :precision="3" size="small" style="width:100px" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button size="small" type="danger" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button style="margin-top:8px" @click="addItem">+ 添加商品</el-button>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useStoreStore } from '../../stores/store'
const storeStore = useStoreStore()

import { ref, reactive, onMounted ,watch} from 'vue'
import { ElMessage } from 'element-plus'
import {
  getPurchasePage, createPurchase,
  submitPurchase, approvePurchase,
  receivePurchase, cancelPurchase
} from '../../api/purchase'

const loading = ref(false)
const tableData = ref([])
const searchForm = reactive({ status: null })
const pagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

const statusName = s => ({ 0: '草稿', 1: '待审核', 2: '已审核', 3: '已入库', 4: '已取消' }[s] || '')
const statusTagType = s => ({ 0: 'info', 1: 'warning', 2: 'primary', 3: 'success', 4: 'danger' }[s] || '')

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getPurchasePage({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      storeId: storeStore.currentStoreId,
      ...searchForm
    })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.pageNum = 1; fetchData() }
const handleReset = () => { searchForm.status = null; handleSearch() }

// 流程操作
const handleSubmit = async id => {
  await submitPurchase(id)
  ElMessage.success('提交成功')
  fetchData()
}
const handleApprove = async id => {
  await approvePurchase(id)
  ElMessage.success('审核通过')
  fetchData()
}
const handleReceive = async id => {
  await receivePurchase(id)
  ElMessage.success('入库成功')
  fetchData()
}
const handleCancel = async id => {
  await cancelPurchase(id)
  ElMessage.success('已取消')
  fetchData()
}

// 新建采购单
const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const form = reactive({
  supplierId: 1,
  expectedDate: '',
  remark: '',
  items: [{ productId: 1, purchasePrice: 0, quantity: 1 }]
})

const addItem = () => form.items.push({ productId: 1, purchasePrice: 0, quantity: 1 })
const removeItem = index => form.items.splice(index, 1)
const resetForm = () => {
  form.supplierId = 1
  form.expectedDate = ''
  form.remark = ''
  form.items = [{ productId: 1, purchasePrice: 0, quantity: 1 }]
}

const handleCreate = async () => {
  submitLoading.value = true
  try {
    await createPurchase({ ...form, storeId: storeStore.currentStoreId })
    ElMessage.success('采购单创建成功')
    dialogVisible.value = false
    fetchData()
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => fetchData())

watch(() => storeStore.currentStoreId, () => {
  fetchData()
})
</script>
