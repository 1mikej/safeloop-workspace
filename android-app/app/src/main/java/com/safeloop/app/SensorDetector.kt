package com.safeloop.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorDetector(
    context: Context,
    private val onRealtimeGChanged: (Double) -> Unit,
    private val onShockDetected: (Double) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // 중력 성분을 포함한 가속도 센서 호출
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastTriggerTime: Long = 0L

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // 3축 합성 가속도 벡터 계산 공식: sqrt(ax^2 + ay^2 + az^2)
        val totalAccel = sqrt((ax * ax + ay * ay + az * az).toDouble())
        // 중력가속도(9.80665 m/s^2)로 나누어 G 단위로 변환 (평상시 가만히 두면 약 1.0g)
        val gForce = (totalAccel / 9.80665 * 100).toInt() / 100.0

        // 화면에 실시간 G 수치 표시 (서버 전송 X -> API 비용 0원 유지!)
        onRealtimeGChanged(gForce)

        // [에지 필터링] 2.8g 이상 급격한 충격이 발생했을 때만 3초 쿨타임으로 이벤트 트리거!
        val now = System.currentTimeMillis()
        if (gForce >= 2.8 && (now - lastTriggerTime > 3000)) {
            lastTriggerTime = now
            onShockDetected(gForce)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
