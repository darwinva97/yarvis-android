# Yarvis - Build Instructions

## 1. Instalar Android SDK (Arch Linux)

```bash
# Instalar cmdline-tools desde AUR
yay -S android-sdk-cmdline-tools-latest

# O manualmente:
mkdir -p ~/Android/Sdk/cmdline-tools
cd ~/Android/Sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mv cmdline-tools latest

# Agregar al PATH (~/.bashrc o ~/.zshrc)
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## 2. Instalar componentes del SDK

```bash
# Aceptar licencias
yes | sdkmanager --licenses

# Instalar componentes necesarios
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## 3. Actualizar local.properties

```bash
cd ~/Code/android/yarvis
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## 4. Compilar el proyecto

```bash
cd ~/Code/android/yarvis

# Build debug APK
./gradlew assembleDebug

# El APK estará en:
# app/build/outputs/apk/debug/app-debug.apk
```

## 5. Instalar en dispositivo

```bash
# Conectar Honor X8c 5G por USB
# Habilitar "Depuración USB" en Opciones de desarrollador

# Verificar conexión
adb devices

# Instalar APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# O en un solo comando:
./gradlew installDebug
```

## 6. Ver logs del servicio

```bash
# Filtrar logs de Yarvis
adb logcat -s VoiceService:V MainActivity:V

# O todos los logs de la app
adb logcat | grep yarvis
```

## Comandos útiles

```bash
# Limpiar build
./gradlew clean

# Build release (firmado)
./gradlew assembleRelease

# Desinstalar app
adb uninstall com.yarvis.assistant

# Reiniciar adb si hay problemas
adb kill-server && adb start-server
```

## Estructura del proyecto

```
yarvis/
├── app/
│   ├── build.gradle          # Configuración del módulo
│   ├── proguard-rules.pro    # Reglas de ofuscación
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/yarvis/assistant/
│       │   ├── MainActivity.java    # UI mínima
│       │   └── VoiceService.java    # Foreground Service
│       └── res/
│           ├── drawable/            # Iconos vectoriales
│           ├── layout/              # Layouts XML
│           ├── mipmap-*/            # Launcher icons
│           └── values/              # Strings, colors, themes
├── build.gradle              # Build script raíz
├── settings.gradle           # Configuración del proyecto
├── gradle.properties         # Propiedades de Gradle
├── local.properties          # Ruta del SDK (local)
└── gradle/wrapper/           # Gradle wrapper
```

## Próximos pasos (Vosk)

Para agregar reconocimiento de voz offline:

1. Agregar dependencia Vosk en `app/build.gradle`:
   ```groovy
   implementation 'com.alphacephei:vosk-android:0.3.47'
   ```

2. Descargar modelo ligero de español:
   ```
   https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip
   ```

3. Colocar modelo en `app/src/main/assets/model-es/`

4. Implementar métodos placeholder en `VoiceService.java`
