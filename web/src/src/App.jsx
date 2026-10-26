import React, { useEffect, useState, useRef } from "react";
import { initializeApp } from "firebase/app";
import { getFirestore, collection, onSnapshot, query, orderBy, limit } from "firebase/firestore";


const firebaseConfig = {
  apiKey: "AIzaSyBNnOTl_epS4dfDXSc_PSo0R_GLpPa8iFw",
  authDomain: "safeloop-core.firebaseapp.com",
  projectId: "safeloop-core",
  storageBucket: "safeloop-core.firebasestorage.app",
  messagingSenderId: "802066192774",
  appId: "1:802066192774:web:dbcd3ee14118f511b2b407"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

export default function App() {
  const [events, setEvents] = useState([]);
  // [실무 보간(Lerp) 엔진 상태] 현재 화면에 그려지는 위치(renderPos)와 목표 위치(targetPos) 분리
  const [renderPos, setRenderPos] = useState({ x: 120, y: 140 });
  const targetPosRef = useRef({ x: 120, y: 140 });

  // 1. Firestore 실시간 리스너 연결 (스마트폰에서 버튼 누르면 0.1초 만에 자동 수신)
  useEffect(() => {
    const q = query(collection(db, "safety_events"), orderBy("deviceTimestamp", "desc"), limit(15));
    const unsubscribe = onSnapshot(q, (snapshot) => {
      const list = snapshot.docs.map((doc) => doc.data());
      setEvents(list);
    });
    // ★중요: 화면이 꺼질 때 리스너를 반드시 해제(unsubscribe)해야 API 무한 과금을 막습니다!
    return () => unsubscribe();
  }, []);

  // 2. 60fps 선형 보간(Linear Interpolation) 애니메이션 루프 -> 순간이동 완전 해결!
  useEffect(() => {
    let animId;
    const smoothLoop = () => {
      setRenderPos((prev) => {
        const dx = targetPosRef.current.x - prev.x;
        const dy = targetPosRef.current.y - prev.y;
        // 목표 지점까지 매 프레임 8%씩 부드럽게 감속 접근 (순간이동 절대 없음)
        return {
          x: Math.abs(dx) < 0.2 ? targetPosRef.current.x : prev.x + dx * 0.08,
          y: Math.abs(dy) < 0.2 ? targetPosRef.current.y : prev.y + dy * 0.08
        };
      });
      animId = requestAnimationFrame(smoothLoop);
    };
    animId = requestAnimationFrame(smoothLoop);
    return () => cancelAnimationFrame(animId);
  }, []);

  const moveMarkerSmoothly = (newX, newY) => {
    targetPosRef.current = { x: newX, y: newY };
  };

  return (
    <div style={{ backgroundColor: "#0F172A", minHeight: "100vh", color: "#F8FAFC", padding: "24px", fontFamily: "sans-serif" }}>
      <h1 style={{ fontSize: "24px", fontWeight: "bold", marginBottom: "8px" }}>
        SafeLoop 실시간 현장 안전 관제 대시보드 (해시 무결성 & 60fps 위치 보간)
      </h1>
      <p style={{ color: "#94A3B8", fontSize: "14px", marginBottom: "20px" }}>
        스마트폰 앱에서 전송된 이벤트가 실시간으로 동기화되며, 사실(HUMAN_FACT)과 시스템 추정(SYSTEM_ESTIMATE)을 엄격히 구분합니다.
      </p>

      {/* 상단: 순간이동 없는 60fps 보간 관제 맵 데모 */}
      <div style={{ backgroundColor: "#1E293B", padding: "16px", borderRadius: "12px", marginBottom: "24px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
          <span style={{ fontWeight: "bold", color: "#38BDF8" }}>
            📍 실시간 작업자/차량 텔레메트리 위치 보간 (Linear Interpolation 60fps)
          </span>
          <div style={{ display: "flex", gap: "8px" }}>
            <button onClick={() => moveMarkerSmoothly(120, 140)} style={{ padding: "6px 12px", background: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}>1구역(자재창고) 이동</button>
            <button onClick={() => moveMarkerSmoothly(380, 90)} style={{ padding: "6px 12px", background: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}>2구역(2층 설비) 이동</button>
            <button onClick={() => moveMarkerSmoothly(620, 160)} style={{ padding: "6px 12px", background: "#DC2626", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}>3구역(개구부 위험지점) 이동</button>
          </div>
        </div>
        <div style={{ position: "relative", height: "220px", backgroundColor: "#0F172A", borderRadius: "8px", border: "1px solid #334155", overflow: "hidden" }}>
          <div style={{ position: "absolute", left: "100px", top: "120px", color: "#64748B", fontSize: "12px" }}>[1구역: 자재창고]</div>
          <div style={{ position: "absolute", left: "360px", top: "70px", color: "#64748B", fontSize: "12px" }}>[2구역: 2층 설비구역]</div>
          <div style={{ position: "absolute", left: "580px", top: "140px", color: "#EF4444", fontSize: "12px" }}>[3구역: 고위험 개구부]</div>
          {/* 부드럽게 움직이는 작업자 마커 */}
          <div style={{
            position: "absolute",
            left: `${renderPos.x}px`,
            top: `${renderPos.y}px`,
            transform: "translate(-50%, -50%)",
            backgroundColor: "#22C55E",
            color: "#052E16",
            padding: "6px 12px",
            borderRadius: "999px",
            fontWeight: "bold",
            fontSize: "12px",
            boxShadow: "0 0 15px rgba(34,197,94,0.6)"
          }}>
            👷 박태규 ({Math.round(renderPos.x)}, {Math.round(renderPos.y)})
          </div>
        </div>
      </div>

      {/* 하단: 실시간 safety_events 해시 체인 로그 테이블 */}
      <div style={{ backgroundColor: "#1E293B", padding: "16px", borderRadius: "12px" }}>
        <h2 style={{ fontSize: "18px", fontWeight: "bold", marginBottom: "12px" }}>
          🔗 실시간 안전 이벤트 & SHA-256 해시 체인 타임라인
        </h2>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
          <thead>
            <tr style={{ backgroundColor: "#0F172A", color: "#94A3B8", textAlign: "left" }}>
              <th style={{ padding: "10px" }}>발생 시각</th>
              <th style={{ padding: "10px" }}>작업자</th>
              <th style={{ padding: "10px" }}>이벤트 코드 (eventType)</th>
              <th style={{ padding: "10px" }}>성격 구분</th>
              <th style={{ padding: "10px" }}>충격량(G)</th>
              <th style={{ padding: "10px" }}>직전 해시 → 현재 SHA-256 해시</th>
            </tr>
          </thead>
          <tbody>
            {events.map((ev, i) => (
              <tr key={ev.eventId || i} style={{ borderBottom: "1px solid #334155" }}>
                <td style={{ padding: "10px" }}>{new Date(ev.deviceTimestamp).toLocaleTimeString()}</td>
                <td style={{ padding: "10px", fontWeight: "bold" }}>{ev.actorName}</td>
                <td style={{ padding: "10px", color: ev.eventType?.includes("SHOCK") || ev.eventType?.includes("119") ? "#F87171" : "#4ADE80", fontWeight: "bold" }}>
                  {ev.eventType}
                </td>
                <td style={{ padding: "10px" }}>
                  <span style={{
                    padding: "3px 8px",
                    borderRadius: "4px",
                    fontSize: "11px",
                    backgroundColor: ev.factOrEstimate === "HUMAN_FACT" ? "#065F46" : "#78350F",
                    color: "#fff"
                  }}>
                    {ev.factOrEstimate}
                  </span>
                </td>
                <td style={{ padding: "10px" }}>{ev.shockG} G</td>
                <td style={{ padding: "10px", fontFamily: "monospace", fontSize: "11px", color: "#94A3B8" }}>
                  {ev.prevEventHash?.slice(0, 8)}... → <strong style={{ color: "#E2E8F0" }}>{ev.currentEventHash?.slice(0, 14)}...</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
