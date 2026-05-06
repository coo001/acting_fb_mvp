# AI 연기 자기관찰 도구 MVP

영상을 올리면 **감정 강도 / 발화 속도 / 침묵 구간** 세 가지 메트릭만 추출해 시각화한다.
이전 결과와 오버레이 비교 가능.

> AI는 측정만 함. 평가/조언/점수 없음. 해석은 본인 몫.

## 사전 요구

- JDK 17 이상 (Gradle toolchain이 Java 21을 자동 provisioning)
- Gemini API key — https://aistudio.google.com/app/apikey

## 실행

1. API key 환경변수 설정 (PowerShell):

   ```powershell
   $env:GEMINI_API_KEY="발급받은_키"
   ```

   영구 설정하려면:
   ```powershell
   [System.Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "발급받은_키", "User")
   ```

2. 실행:

   ```powershell
   ./gradlew bootRun
   ```

   첫 실행 시 Gradle이 Java 21 toolchain을 자동 다운로드한다 (수 분 소요).

3. 브라우저: http://localhost:8080

## 사용 흐름

1. 영상 (mp4 / mov, ≤500MB) 업로드 + 라벨 입력 (예: `햄릿 1막 1차`)
2. 1~3분 대기 (Gemini 분석)
3. 결과 페이지에서 차트 3개 확인
   - 감정 강도 (0~10 시계열)
   - 발화 속도 (WPM 시계열, 5초 슬라이딩 윈도우)
   - 침묵 구간 (0.8s 이상)
4. 두 번째 영상부터는 드롭다운에서 이전 결과 선택해 오버레이 비교

## 메트릭 출처

- **emotionIntensity**: Gemini 2.5 Pro가 영상 보고 직접 0~10 척도로 추정
- **transcript**: Gemini가 한국어 트랜스크립션
- **speechRate / silences**: transcript를 받아 Java 측 `MetricsProcessor`가 후처리 계산

## 데이터 위치

- `./data/results/{id}.json`: 결과 본문
- `./data/index.json`: 결과 메타 인덱스
- 업로드 영상은 `${java.io.tmpdir}/acting_uploads/`에 잠시 저장 후 분석 끝나면 즉시 삭제
- Gemini Files API에 업로드된 영상도 분석 끝나면 즉시 삭제

## 스택

Java 21 + Spring Boot 3.3.5 + Thymeleaf SSR + Chart.js (CDN). DB 없음. 인증 없음.

## 제약

- 단일 사용자 가정. 동시 분석 ❌
- 분석은 동기. 1~3분 브라우저가 응답 대기
- mp4 / mov 만 지원
- 500MB 상한
