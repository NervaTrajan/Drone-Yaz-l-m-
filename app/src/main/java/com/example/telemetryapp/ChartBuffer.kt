package com.example.telemetryapp

class ChartBuffer(private val capacity: Int) {
    private val items = ArrayDeque<ChartPoint>(capacity)

    fun add(point: ChartPoint) {
        if (items.size >= capacity) {
            items.removeFirst()
        }
        items.addLast(point)
    }

    fun toList(): List<ChartPoint> = items.toList()

    fun clear() {
        items.clear()
    }
}
