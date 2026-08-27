package com.m1racle.reader.ui.annotation

/**
 * R25 瞬时选择持有器：仅内存、不持久化（无 Room/DataStore 路径）。
 * 语义：翻页/滚动越界/组件离开组合时由宿主显式 [clear]。
 */
class TransientSelectionHolder {
    var current: TextSelection? = null
        private set

    fun set(selection: TextSelection) {
        current = selection
    }

    fun clear() {
        current = null
    }
}
