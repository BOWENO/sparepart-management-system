import request from './request'

// 认证
export const loginApi = (data) => request.post('/auth/login', data)
export const logoutApi = () => request.post('/auth/logout')
export const meApi = () => request.get('/auth/me')
export const changePassword = (data) => request.post('/auth/change-password', data)
export const getUserAccounts = () => request.get('/user-accounts')
export const resetUserPassword = (id, data) => request.post(`/user-accounts/${id}/reset-password`, data)

// 备品
export const getParts = (params) => request.get('/parts', { params })
export const savePart = (data) => request.post('/parts', data)
export const deletePart = (id) => request.delete(`/parts/${id}`)
export const importParts = (formData) =>
  request.post('/parts/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const previewPartsImport = (formData) =>
  request.post('/parts/import/preview', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const rollbackPartsImport = (batchNo) => request.post(`/parts/import/${batchNo}/rollback`)
export const getPart = (id) => request.get(`/parts/${id}`)
export const getScrapRecords = (params) => request.get('/scrap-records', { params })
export const getInstalledParts = (params) => request.get('/installed-parts', { params })
export const getInstalledPartDevices = (params) => request.get('/installed-parts/devices', { params })
export const getInstalledPartDeviceHistory = (deviceId) => request.get(`/installed-parts/devices/${deviceId}/history`)
export const getTechnicians = (activeOnly = false) => request.get('/technicians', { params: { activeOnly } })
export const saveTechnician = (data) => request.post('/technicians', data)
export const disableTechnician = (id) => request.delete(`/technicians/${id}`)
export const previewTechniciansImport = (data) => request.post('/technicians/import/preview', data, { headers: { 'Content-Type': 'multipart/form-data' } })
export const importTechnicians = (data) => request.post('/technicians/import', data, { headers: { 'Content-Type': 'multipart/form-data' } })
export const rollbackTechniciansImport = (batchNo) => request.post(`/technicians/import/${batchNo}/rollback`)

// 设备
export const getDevices = (lineId) => request.get('/devices/list', { params: lineId ? { lineId } : {} })
export const getDeviceLedger = () => request.get('/devices')
export const saveDevice = (data) => request.post('/devices', data)
export const deleteDevice = (id) => request.delete(`/devices/${id}`)
export const importDevices = (formData) =>
  request.post('/devices/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const previewDevicesImport = (formData) =>
  request.post('/devices/import/preview', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const rollbackDevicesImport = (batchNo) => request.post(`/devices/import/${batchNo}/rollback`)
export const getProductionLines = (activeOnly = false) => request.get('/production-lines', { params: { activeOnly } })
export const saveProductionLine = (data) => request.post('/production-lines', data)

// 换新
export const replaceBatch = (data) => request.post('/flows/replace-batch', data)

// 出入库
export const stockIn = (params) => request.post('/flows/in', null, { params })
export const stockOut = (params) => request.post('/flows/out', null, { params })
export const scrapOut = (params) => request.post('/flows/scrap-out', null, { params })
export const goodToScrap = (params) => request.post('/flows/good-to-scrap', null, { params })
export const getFlows = (params) => request.get('/flows', { params })
export const reverseFlow = (id, reason) => request.post(`/flows/${id}/reverse`, null, { params: { reason } })
export const cancelReplacement = (flowNo, reason) => request.post(`/flows/replacements/${flowNo}/cancel`, null, { params: { reason } })

// 盘点
export const doCheck = (data) => request.post('/checks/do-check', data)
export const getChecks = (params) => request.get('/checks', { params })

// 分析
export const getDeviceCost = (params) => request.get('/analysis/device-cost', { params })
export const getUnitTotal = () => request.get('/analysis/unit-total')
export const getZombieParts = (params) => request.get('/analysis/zombie-parts', { params })
export const getConsumeTrend = (params) => request.get('/analysis/consume-trend', { params })
export const getDeviceTrend = (params) => request.get('/analysis/device-trend', { params })
export const getPartType = () => request.get('/analysis/part-type')
export const getScrapStats = () => request.get('/analysis/scrap-stats')
export const getLifeStats = () => request.get('/analysis/life-stats')
export const getStockForecast = () => request.get('/analysis/stock-forecast')
export const getDashboard = () => request.get('/dashboard/summary')

// 消息
export const getMessages = (params) => request.get('/messages', { params })
export const markRead = (id) => request.post(`/messages/${id}/read`)

// 日志
export const getLogs = (params) => request.get('/logs', { params })
export const getArchiveLogs = (params) => request.get('/logs/archive', { params })
