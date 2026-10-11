package com.m1racle.reader.data.anchor

enum class HighlightStyle(val argb: Long, val label: String) {
    Yellow(0x66FFD54F, "黄色"), Green(0x663DDC84, "绿色"), Blue(0x6653B8FF, "蓝色"), Pink(0x66F48FB1, "粉色"),
    Underline(0xFF3B82F6, "下划线"), Strike(0xFFE57373, "删除线");

    companion object { fun fromStorage(value: String) = entries.firstOrNull { it.name == value } ?: Yellow }
}
