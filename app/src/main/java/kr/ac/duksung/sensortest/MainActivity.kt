package kr.ac.duksung.sensortest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var stepCountSensor: Sensor? = null

    // 계산용 변수
    private var initialStepCount: Float = -1f
    private var currentSteps: Int = 0

    // 계산용 변수
    private var lastTimestamp: Long = 0
    private var stepCount: Int = 0

    private lateinit var stepCountText: TextView
    private lateinit var logText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 요소 초기화
        stepCountText = findViewById(R.id.stepCountText)
        logText = findViewById(R.id.logText)

        // 자이로스코프는 기기의 x, y, z축을 중심으로 회전 속도를 rad/s 단위로 측정합니다.
        // 기본 자이로스코프의 인스턴스를 가져오는 방법을 나타냅니다.
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//        stepCountSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        if (gyroSensor == null) {
            Log.e("Gyroseneor", "자이로센서 사용 불가")
            logText.text = "자이로센서 사용 불가"
        } else {
            logText.text = "자이로센서 초기화 성공!"
        }

        stepCountText.text = "아직 측정되지 않았습니다. "

        findViewById<Button>(R.id.button).setOnClickListener {
            stepCount = 0
            stepCountText.text = "Steps: $stepCount" // 화면에 초기화된 값 반영
            logText.text = "걸음 수 초기화됨."
            Log.d("Reset", "Step count reset to 0")
        }
    }



    // 얘네가 왜 있는가? 꼭 필요한 것인가? 이유를 찾자
    override fun onResume() {
        super.onResume()

        // 센서 등록
        gyroSensor.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // 얘네가 왜 있는가? 꼭 필요한 것인가? 이유를 찾자
    override fun onPause() {
        super.onPause()

        // 센서 해제
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val angularSpeedX = p0.values[0] // X축 회전 속도
            val angularSpeedY = p0.values[1] // Y축 회전 속도
            val angularSpeedZ = p0.values[2]; // Z축 회전 속도

            val currentTime = System.currentTimeMillis()

//            // 회전 속도를 기준으로 걸음 판단
//            if (Math.abs(angularSpeedZ) > 2.0) {
//                if (lastTimestamp == 0L || currentTime - lastTimestamp > 500) { // 500ms이상 간격
//                    stepCount++
//                    lastTimestamp = currentTime
//
//                    stepCountText.text = "Steps: $stepCount"
//                    logText.text = "걸음 감지 -> ${currentTime}ms"
//                }
//            }

            if (Math.abs(angularSpeedZ) > 2.0 &&
                Math.abs(angularSpeedX) < 2.0 &&
                Math.abs(angularSpeedY) < 2.0 ) {

                // 걸음 간격이 적절한지 확인 (0.5초 이상)
                if (lastTimestamp == 0L || currentTime - lastTimestamp > 400) {
                    stepCount++
                    lastTimestamp = currentTime

                    // UI 업데이트
                    stepCountText.text = "Steps: $stepCount"
                    logText.text = "걸음 감지 -> $currentTime ms"
                }
            }
        }


//        if (p0?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
//            val totalSteps = p0.values[0]
//
//            // 초기 값을 설정하여 보정
//            if (initialStepCount == -1f) {
//                initialStepCount = totalSteps
//            }
//
//            // 현재 걸음 수 계산
//            currentSteps = (totalSteps - initialStepCount).toInt()
//
//            // UI 업데이트
//            stepCountText.text = "Steps: 테스트입니다"
//            logText.text = "걸음 수 업데이트: $currentSteps"
//        }

    }
    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        Log.d("Sensor", "Accuracy changed")
    }
}

