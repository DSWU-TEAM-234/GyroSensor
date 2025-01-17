package kr.ac.duksung.sensortest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {
    // 센서 매니저 및 센서 객체
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null

    // 걸음 수 및 시간 추적 변수
    private var stepCount: Int = 0
    private var lastTimestamp: Long = 0

    // 자이로스코프 필터 및 계산 변수
    private var filteredGyroX: Float = 0f
    private var filteredGyroY: Float = 0f
    private var filteredGyroZ: Float = 0f
    private val gyroAlpha = 0.8f // 로우패스 필터 계수
    private var estimatedAngle: Float = 0f // 상보 필터로 계산된 각도

    // 이동 평균 필터를 위한 데이터 리스트
    private val gyroDataX = mutableListOf<Float>()
    private val gyroDataY = mutableListOf<Float>()
    private val gyroDataZ = mutableListOf<Float>()
    private val movingAverageWindowSize = 5 // 이동 평균 윈도우 크기

    // 가속도계 필터 및 계산 변수
    private var lastAcceleration: Float = 0f
    private var filteredAcceleration: Float = 0f
    private val kalmanGain = 0.5f // 칼만 필터 계수
    private var kalmanEstimate: Float = 0f

    // 상보 필터 계수
    private val complementaryAlpha = 0.98f // 자이로스코프 가중치

    // UI 요소
    private lateinit var stepCountText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 초기화
        stepCountText = findViewById(R.id.stepCountText)
        logText = findViewById(R.id.logText)

        // 센서 매니저 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 센서 사용 가능 여부 확인 후 로그에 출력
        logText.text = if (gyroSensor != null) "자이로센서 초기화 성공!" else "자이로센서 사용 불가"
        logText.append("\n${if (accelerometerSensor != null) "가속도계 초기화 성공!" else "가속도계 센서 사용 불가"}")

        stepCountText.text = "아직 측정되지 않았습니다."

        // 리셋 버튼 클릭 리스너
        findViewById<Button>(R.id.button).setOnClickListener {
            stepCount = 0
            estimatedAngle = 0f
            stepCountText.text = "Steps: $stepCount"
            logText.text = "걸음 수 초기화됨."
        }
    }

    override fun onResume() {
        super.onResume()
        // 센서 리스너 등록
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val currentTime = System.currentTimeMillis()

        // 자이로스코프 데이터 처리
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // 자이로스코프 데이터 가져오기
            val rawGyroX = event.values[0]
            val rawGyroY = event.values[1]
            val rawGyroZ = event.values[2]

            // 이동 평균 필터 적용
            val smoothedGyroX = applyMovingAverageFilter(gyroDataX, rawGyroX, movingAverageWindowSize)
            val smoothedGyroY = applyMovingAverageFilter(gyroDataY, rawGyroY, movingAverageWindowSize)
            val smoothedGyroZ = applyMovingAverageFilter(gyroDataZ, rawGyroZ, movingAverageWindowSize)

            // 로우패스 필터 적용
            filteredGyroX = applyLowPassFilter(smoothedGyroX, filteredGyroX, gyroAlpha)
            filteredGyroY = applyLowPassFilter(smoothedGyroY, filteredGyroY, gyroAlpha)
            filteredGyroZ = applyLowPassFilter(smoothedGyroZ, filteredGyroZ, gyroAlpha)

            // 자이로스코프 데이터를 기반으로 각도 계산
            val deltaTime = (currentTime - lastTimestamp) / 1000.0f
            val gyroAngle = estimatedAngle + filteredGyroZ * deltaTime

            // 상보 필터로 최종 각도 계산
            estimatedAngle = applyComplementaryFilter(gyroAngle, calculateAccelAngle())

            // 걸음 감지 조건
            if (filteredGyroZ > 2.0f && filteredGyroX < 2.0f && filteredGyroY < 2.0f &&
                (lastTimestamp == 0L || currentTime - lastTimestamp > 500)) {
                stepCount++
                lastTimestamp = currentTime
                stepCountText.text = "Steps: $stepCount"
                logText.text = "자이로 기반 걸음 감지 -> $currentTime ms"

                vibrate() // 진동 발생
            }
        }

        // 가속도계 데이터 처리
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // 가속도계 데이터 가져오기
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 가속도의 크기 계산
            val rawAcceleration = sqrt(x * x + y * y + z * z)

            // 로우패스 필터 적용
            filteredAcceleration = applyLowPassFilter(rawAcceleration, filteredAcceleration, 0.8f)

            // 칼만 필터 적용
            val adaptiveFilteredAcceleration = applyKalmanFilter(rawAcceleration)

            // 걸음 감지 조건
            if (Math.abs(adaptiveFilteredAcceleration - lastAcceleration) > 2.5f &&
                (lastTimestamp == 0L || currentTime - lastTimestamp > 500)) {
                stepCount++
                lastTimestamp = currentTime
                stepCountText.text = "Steps: $stepCount"
                logText.text = "가속도 기반 걸음 감지 -> $currentTime ms"

                vibrate() // 진동 발생
            }

            lastAcceleration = adaptiveFilteredAcceleration
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 센서 정확도 변경 시 처리
    }

    // 로우패스 필터 적용
    private fun applyLowPassFilter(rawValue: Float, filteredValue: Float, alpha: Float): Float {
        return alpha * filteredValue + (1 - alpha) * rawValue
    }

    // 이동 평균 필터 적용
    private fun applyMovingAverageFilter(dataList: MutableList<Float>, newValue: Float, windowSize: Int): Float {
        dataList.add(newValue)
        if (dataList.size > windowSize) {
            dataList.removeAt(0)
        }
        return dataList.average().toFloat()
    }

    // 상보 필터 적용
    private fun applyComplementaryFilter(gyroAngle: Float, accelAngle: Float): Float {
        return complementaryAlpha * gyroAngle + (1 - complementaryAlpha) * accelAngle
    }

    // 가속도계를 이용한 각도 계산
    private fun calculateAccelAngle(): Float {
        return atan2(filteredAcceleration, 9.8f) * (180 / Math.PI).toFloat()
    }

    // 칼만 필터 적용
    private fun applyKalmanFilter(measuredValue: Float): Float {
        kalmanEstimate = kalmanEstimate + kalmanGain * (measuredValue - kalmanEstimate)
        return kalmanEstimate
    }

    // 진동 발생 함수
    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
    }
}
