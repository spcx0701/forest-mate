# Android APK 배포

숲길동무 Android 배포는 Play Console용 AAB가 아니라 직접 배포용 signed APK를 기준으로 한다.

## 산출물

- 패키지 ID: `kr.forestmate.app`
- Wear OS 패키지 ID: `kr.forestmate.watch`
- 빌드 도구: Bubblewrap TWA + native Wear OS module
- 휴대폰 APK: `dist/forestmate-android-v1.1.0.apk`
- 워치 APK: `dist/forestmate-wear-v1.1.0.apk`
- 체크섬: 각 APK 옆의 `.sha256`

Bubblewrap는 내부적으로 AAB도 만들지만, `npm run build:apk`는 빌드 후 AAB를 삭제하고 휴대폰 APK와 Wear OS APK만 `dist/`에 남긴다.

## Galaxy Watch / Wear OS 연동

워치 앱은 별도 Wear OS 모듈(`:wear`)로 빌드한다. 손목 화면은 지도형 산행 화면 위에 남은 거리, 방향, 심박, 나침반, GPS, 배터리, 조난 위험 상태를 오버레이한다. 워치가 워치 전용 토큰을 받으면 전경 서비스가 심박·GPS·고도·나침반·가속도·배터리 샘플을 서버 `/api/v1/watch/track`으로 전송하고, 서버가 돌려준 진행률과 위험 레벨을 다시 워치 화면에 반영한다.

6자리 코드는 기본 UX가 아니라 백업 연결이다. 현재 리포지토리는 Wear OS 네이티브 앱과 서버 토큰 연동을 포함하고, 워치 화면에서는 코드를 백업 버튼 아래로 숨긴다. 완전한 paired-phone 자동 핸드오프는 TWA 웹 레이어가 가진 익명 기기 토큰을 네이티브 Android 래퍼가 Wear OS Data Layer로 전달하는 별도 브리지에서 소유해야 한다.

```bash
cd "/Users/dong9733/Documents/GitHub/forest-mate/packaging/android"
./gradlew --no-daemon :wear:assembleDebug
```

예상 산출물은 `wear/build/outputs/apk/debug/wear-debug.apk`다. 기본 API 주소는 `wear/src/main/res/values/strings.xml`의 `default_api_base`이며, 로컬 서버와 붙여 테스트할 때는 해당 값을 같은 서버의 `/api/v1` 주소로 바꾼다.

## 1. 최초 1회 준비

이 단계는 JDK 17+, Android SDK, 서명키 생성 때문에 대화형으로 진행한다.

```bash
cd "/Users/dong9733/Documents/GitHub/forest-mate/packaging/android"
npm install
npm run init:project
```

프롬프트에서는 기존 설정값을 기준으로 입력한다.

- Application ID: `kr.forestmate.app`
- Host: `forestmate.onrender.com`
- Name: `숲길동무 ForestMate`
- Launcher name: `숲길동무`
- Signing key alias: `forestmate`

생성된 `android-signing.keystore`와 비밀번호는 유출되면 안 된다. 이 repo의 `packaging/.gitignore`는 keystore 커밋을 막는다.

## 2. APK 빌드

서명키 비밀번호를 환경변수로 넣고 빌드한다.

```bash
cd "/Users/dong9733/Documents/GitHub/forest-mate/packaging/android"
export BUBBLEWRAP_KEYSTORE_PASSWORD="..."
export BUBBLEWRAP_KEY_PASSWORD="..."
npm run build:apk
```

완료 후 `dist/forestmate-android-v1.1.0.apk`와 `dist/forestmate-wear-v1.1.0.apk`를 함께 배포한다.

## 3. Digital Asset Links

TWA에서 주소창을 숨기려면 APK 서명키 SHA-256을 웹 서버의 `/.well-known/assetlinks.json`에 넣어야 한다.

```bash
npm run fingerprint
```

출력된 SHA-256 값을 `/Users/dong9733/Documents/GitHub/forest-mate/app/.well-known/assetlinks.json`에 반영하고 다시 배포한다. 현재 배포 서명키 지문이 이 파일에 반영되어 있어야 한다.

## 4. 배포 방식

APK는 Play Store가 아니라 직접 링크, GitHub Release, 또는 행사 제출 패키지로 배포한다. 사용자는 Android/Wear OS 설정에서 "알 수 없는 앱 설치"를 허용해야 설치할 수 있다.

GitHub Release를 `published` 상태로 만들면 `.github/workflows/android-release.yml`이 휴대폰 signed APK, 워치 signed APK, 각 `.sha256` 파일을 Release asset으로 업로드하고 GitHub artifact provenance를 생성한다. 이 workflow를 쓰려면 repository secrets에 다음 값을 먼저 넣는다.

- `ANDROID_KEYSTORE_BASE64`: `android-signing.keystore`를 base64로 인코딩한 값
- `BUBBLEWRAP_KEYSTORE_PASSWORD`: keystore 비밀번호
- `BUBBLEWRAP_KEY_PASSWORD`: signing key 비밀번호

## 5. F-Droid 소스 빌드

F-Droid 제출은 GitHub Release APK 업로드가 아니라 소스에서 unsigned release APK를 빌드하는 방식이다. 루트의 `.fdroid.yml`와 `fastlane/metadata/android/`가 제출 메타데이터의 기준이며, 자세한 절차는 `packaging/fdroid/README.md`를 따른다.

F-Droid 빌드는 개인 서명키를 쓰지 않는다.

```bash
cd "/Users/dong9733/Documents/GitHub/forest-mate/packaging/android"
./gradlew --no-daemon assembleRelease
```

예상 산출물은 `app/build/outputs/apk/release/app-release-unsigned.apk`이며, F-Droid가 이 unsigned APK를 자체 키로 서명한다.
