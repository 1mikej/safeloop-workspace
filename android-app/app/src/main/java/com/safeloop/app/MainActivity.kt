package com.safeloop.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var sensorDetector: SensorDetector
    private val db = FirebaseFirestore.getInstance()
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var currentGState by mutableDoubleStateOf(1.00)
        var alertActive by mutableStateOf(false)
        var remainingSeconds by mutableIntStateOf(15)
        var lastDetectedShockG by mutableDoubleStateOf(0.0)
        var lastServerLog by mutableStateOf("대기 중 (출근 또는 충격 감지 시 서버로 전송됨)")

        fun uploadEventToFirestore(eventType: String, factType: String, shockVal: Double) {
            val evtId = "evt_" + UUID.randomUUID().toString().take(8)
            val now = System.currentTimeMillis()
            val (prevH, currH) = HashChainEngine.generateChainedHash(
                evtId, "user_park_01", eventType, shockVal, now
            )

            val event = SafetyEvent(
                eventId = evtId,
                incidentGroupId = "inc_20261026_01",
                eventType = eventType,
                factOrEstimate = factType,
                shockG = shockVal,
                deviceTimestamp = now,
                prevEventHash = prevH,
                currentEventHash = currH
            )

            db.collection("safety_events")
                .document(evtId)
                .set(event)
                .addOnSuccessListener {
                    // 줄바꿈 에러를 방지하도록 안전하게 1줄로 처리
                    lastServerLog = "✅ 전송 완료: [$eventType] (${shockVal}g) | 해시: " + currH.take(16) + "..."
                    Toast.makeText(this, "서버 기록 완료: $eventType", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    lastServerLog = "❌ 전송 실패: ${e.message}"
                }
        }

        fun triggerShockAlert(shockG: Double) {
            if (alertActive) return
            alertActive = true
            lastDetectedShockG = shockG
            remainingSeconds = 15

            // 1차 충격 감지 서버 기록
            uploadEventToFirestore("SHOCK_DETECTED", "SYSTEM_ESTIMATE", shockG)

            // 15초 본인 확인 타이머 작동
            countDownTimer?.cancel()
            countDownTimer = object : CountDownTimer(15000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingSeconds = (millisUntilFinished / 1000).toInt() + 1
                }
                override fun onFinish() {
                    alertActive = false
                    // 15초 무응답 시 전이 기록
                    uploadEventToFirestore("SELF_NO_RESPONSE", "SYSTEM_ESTIMATE", shockG)
                }
            }.start()
        }

        sensorDetector = SensorDetector(
            context = this,
            onRealtimeGChanged = { g -> currentGState = g },
            onShockDetected = { shockG -> triggerShockAlert(shockG) }
        )
        sensorDetector.start()

        setContent {
            SafeLoopWorkerApp(
                currentG = currentGState,
                alertActive = alertActive,
                remainingSec = remainingSeconds,
                lastLog = lastServerLog,
                onCheckInClick = {
                    uploadEventToFirestore("CHECK_IN", "HUMAN_FACT", 1.0)
                },
                onSimulateShockClick = {
                    triggerShockAlert(3.45)
                },
                onImOkayClick = {
                    countDownTimer?.cancel()
                    alertActive = false
                    uploadEventToFirestore("SELF_OK_FALSE_ALARM", "HUMAN_FACT", lastDetectedShockG)
                },
                onCall119Click = {
                    uploadEventToFirestore("EMERGENCY_119_CALLED", "HUMAN_FACT", lastDetectedShockG)
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:119"))
                    startActivity(intent)
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        sensorDetector.stop()
    }
}

@Composable
fun SafeLoopWorkerApp(
    currentG: Double,
    alertActive: Boolean,
    remainingSec: Int,
    lastLog: String,
    onCheckInClick: () -> Unit,
    onSimulateShockClick: () -> Unit,
    onImOkayClick: () -> Unit,
    onCall119Click: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("SafeLoop 작업자 안전노드", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("현장: SITE_2026_A (2층 설비구역) | 작업자: 박태규", color = Color(0xFF9CA3AF), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("실시간 가속도 합성 벡터 (에지 로컬 연산 중)", color = Color(0xFF93C5FD), fontSize = 13.sp)
                    Text("${currentG} G", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                    Text("※ 2.8G 이상 충격 시에만 서버 전송 (API 비용 0원 유지)", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lastLog,
                    color = Color(0xFFA7F3D0),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (alertActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("강한 충격이 감지되었습니다!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${remainingSec}초 후 동료(정민철)와 반장에게 자동 호출됩니다.", color = Color(0xFFFCA5A5), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onImOkayClick,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("괜찮습니다 (오탐 취소 - 본인 확인)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCheckInClick,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("출근 및 안전점검 3항목 완료 기록", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSimulateShockClick,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("모의 3.45G 충격 발생 테스트 (폰 흔들기 대체)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Button(
            onClick = onCall119Click,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
            Text("119 긴급 신고 전화 연결 (상시 노출)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}