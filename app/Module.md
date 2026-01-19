# Module Yarvis Assistant

Aplicación de asistente de voz para Android.

## Arquitectura

El proyecto sigue una arquitectura modular con separación de responsabilidades:

- **MainActivity** - Coordinador principal de la UI
- **PermissionManager** - Gestión centralizada de permisos
- **VoiceServiceController** - Control del servicio de reconocimiento de voz
- **SpeechBroadcastReceiver** - Recepción de eventos de voz
- **MainUIManager** - Gestión de actualizaciones de la interfaz

## Componentes principales

### VoiceService
Servicio en primer plano que realiza reconocimiento de voz continuo.

### NotificationListenerService
Servicio para interceptar y leer notificaciones del sistema.
