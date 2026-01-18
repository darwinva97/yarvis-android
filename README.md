# Yarvis

Asistente de voz para Android con reconocimiento offline.

## Requisitos

- Android SDK 34 (Android 14)
- Java 11+
- Gradle 8.2+

### Dispositivo
- Android 8.0+ (API 26)

## Compilar

```bash
# Clonar repositorio
git clone https://github.com/tu-usuario/yarvis.git
cd yarvis

# Configurar SDK path
echo "sdk.dir=/ruta/a/tu/android/sdk" > local.properties

# Compilar APK debug
./gradlew assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`

## Instalar

```bash
# Conectar dispositivo con USB debugging habilitado
adb install -r app/build/outputs/apk/debug/app-debug.apk

# O directamente:
./gradlew installDebug
```

## Estructura

```
yarvis/
├── app/
│   ├── src/main/
│   │   ├── java/com/yarvis/assistant/
│   │   └── res/
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## Documentacion adicional

Ver [BUILD.md](BUILD.md) para instrucciones detalladas de configuracion del entorno.

## Licencia

MIT License - Ver [LICENSE](LICENSE)
