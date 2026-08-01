package cn.squ.coursetable

/**
 * 课表数据变化后通知平台桌面组件刷新。
 * Android：刷新 Glance 小组件；Desktop：无组件，空实现。
 */
expect suspend fun notifyCourseWidgetChanged()
