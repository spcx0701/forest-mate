# Android APK 배포

숲길동무 Android 배포는 Play Console용 AAB가 아니라 직접 배포용 signed APK를 기준으로 한다.

## 산출물

- 패키지 ID: `kr.forestmate.app`
- Wear OS 패키지 ID: `kr.forestmate.watch`
- 빌드 도구: Bubblewrap TWA
- 최종 파일: `dist/forestmate-android-v1.1.0.apk`
- 체크섬: `dist/forestmate-android-v1.1.0.apk.sha256`

Bubblewrap는 내부적으로 AAB도 만들지만, `npm run build:apk`는 빌드 후 AAB를 삭제하고 APK만 `dist/`에 남긴다.

## Galaxy Watch / Wear OS 연동

워치 앱은 별도 Wear OS 모듈(`:wear`)로 빌드한다. 제품 UX 기준은 폰앱과 워치앱이 companion 앱으로 자동 연결되는 흐름이며, 6자리 코드는 자동 연결이 실패했거나 워치 단독 네트워크로 복구해야 할 때 쓰는 백업 연결이다. 현재 구현된 런타임 경로는 백업 코드 연결이며, 기본 자동 연결을 완성하려면 Android 폰 래퍼와 Wear OS 앱 사이에 Wear OS Data Layer 토큰 전달을 추가해야 한다. 워치가 워치 전용 토큰을 받으면 전경 서비스가 심박·GPS·가속도·배터리 샘플을 서버 `/api/v1/watch/track`으로 전송한다.

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

완료 후 `dist/forestmate-android-v1.1.0.apk`만 배포한다.

## 3. Digital Asset Links

TWA에서 주소창을 숨기려면 APK 서명키 SHA-256을 웹 서버의 `/.well-known/assetlinks.json`에 넣어야 한다.

```bash
npm run fingerprint
```

출력된 SHA-256 값을 `/Users/dong9733/Documents/GitHub/forest-mate/app/.well-known/assetlinks.json`에 반영하고 다시 배포한다. 현재 배포 서명키 지문이 이 파일에 반영되어 있어야 한다.

## 4. 배포 방식

APK는 Play Store가 아니라 직접 링크, GitHub Release, 또는 행사 제출 패키지로 배포한다. 사용자는 Android 설정에서 "알 수 없는 앱 설치"를 허용해야 설치할 수 있다.

GitHub Release를 `published` 상태로 만들면 `.github/workflows/android-release.yml`이 signed APK와 `.sha256` 파일을 Release asset으로 업로드하고 GitHub artifact provenance를 생성한다. 이 workflow를 쓰려면 repository secrets에 다음 값을 먼저 넣는다.

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
