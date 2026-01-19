# Yarvis Backend API Documentation

## Arquitectura General

```
┌─────────────────┐     WebSocket      ┌─────────────────┐      HTTP/Webhook     ┌─────────────────┐
│                 │◄──────────────────►│                 │◄────────────────────►│                 │
│  Android App    │                    │  Yarvis Backend │                      │  Agente/n8n     │
│                 │                    │                 │                      │                 │
└─────────────────┘                    └─────────────────┘                      └─────────────────┘
                                              │
                                              │ REST API
                                              ▼
                                       ┌─────────────────┐
                                       │ Sistemas        │
                                       │ Externos        │
                                       └─────────────────┘
```

---

## 1. WebSocket API (Android ↔ Backend)

### Conexión

```
URL: ws://{host}:{port}/ws?clientId={uuid}
```

| Parámetro | Tipo | Requerido | Descripción |
|-----------|------|-----------|-------------|
| `clientId` | string (UUID) | No | Identificador único del cliente. Si no se provee, el servidor genera uno. |

---

### 1.1 Mensajes del Cliente → Servidor

#### `voice_command`
Comando de voz reconocido por el usuario.

```json
{
  "type": "voice_command",
  "text": "¿Qué hora es?",
  "timestamp": 1705123456789,
  "sessionId": "uuid-session-id"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"voice_command"` | Sí | Tipo de mensaje |
| `text` | string | Sí | Texto reconocido por voz |
| `timestamp` | number | Sí | Unix timestamp en milisegundos |
| `sessionId` | string | No | ID de sesión activa (si existe) |

---

#### `chat_message`
Mensaje de texto escrito por el usuario.

```json
{
  "type": "chat_message",
  "text": "Hola, ¿cómo estás?",
  "timestamp": 1705123456789,
  "sessionId": "uuid-session-id"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"chat_message"` | Sí | Tipo de mensaje |
| `text` | string | Sí | Texto escrito por el usuario |
| `timestamp` | number | Sí | Unix timestamp en milisegundos |
| `sessionId` | string | No | ID de sesión activa (si existe) |

---

#### `notification`
Notificación del sistema Android interceptada.

```json
{
  "type": "notification",
  "app": "WhatsApp",
  "title": "Juan Pérez",
  "text": "Hola, ¿cómo estás?"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"notification"` | Sí | Tipo de mensaje |
| `app` | string | Sí | Nombre de la aplicación origen |
| `title` | string | Sí | Título de la notificación |
| `text` | string | Sí | Contenido de la notificación |

---

#### `end_conversation`
El usuario solicita terminar la conversación activa.

```json
{
  "type": "end_conversation",
  "sessionId": "uuid-session-id",
  "reason": "user_request"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"end_conversation"` | Sí | Tipo de mensaje |
| `sessionId` | string | Sí | ID de la sesión a terminar |
| `reason` | string | No | Razón del cierre |

---

#### `ping`
Verificación de conexión (heartbeat).

```json
{
  "type": "ping"
}
```

**Respuesta esperada:** `pong`

---

### 1.2 Mensajes del Servidor → Cliente

#### `response`
Respuesta del asistente al usuario.

```json
{
  "type": "response",
  "text": "Son las 3:45 de la tarde.",
  "speak": true,
  "sessionId": "uuid-session-id",
  "messageId": "uuid-message-id",
  "show": {
    "type": "text",
    "text": "15:45"
  }
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"response"` | Sí | Tipo de mensaje |
| `text` | string | Sí | Texto completo de la respuesta (para TTS e historial) |
| `speak` | boolean | Sí | `true` = reproducir con TTS, `false` = solo mostrar |
| `sessionId` | string | No | ID de sesión asociada |
| `messageId` | string | No | ID único del mensaje |
| `show` | ShowContent | No | Contenido enriquecido para UI |

---

#### `start_conversation`
El servidor inicia una nueva conversación (push del agente).

```json
{
  "type": "start_conversation",
  "sessionId": "uuid-session-id",
  "greeting": "Tienes un mensaje importante de WhatsApp.",
  "context": {
    "source": "notification",
    "app": "WhatsApp"
  },
  "show": {
    "type": "card",
    "title": "WhatsApp",
    "text": "Mensaje de Juan"
  }
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"start_conversation"` | Sí | Tipo de mensaje |
| `sessionId` | string | Sí | ID de la nueva sesión |
| `greeting` | string | No | Saludo inicial (se reproduce con TTS) |
| `context` | object | No | Contexto adicional para la conversación |
| `show` | ShowContent | No | Contenido visual |

---

#### `end_conversation`
El servidor termina una conversación.

```json
{
  "type": "end_conversation",
  "sessionId": "uuid-session-id",
  "farewell": "¡Hasta luego!",
  "reason": "agent_decision"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"end_conversation"` | Sí | Tipo de mensaje |
| `sessionId` | string | Sí | ID de la sesión terminada |
| `farewell` | string | No | Mensaje de despedida (se reproduce con TTS) |
| `reason` | EndConversationReason | Sí | Razón del cierre |

**EndConversationReason:**
- `user_request` - Usuario dijo "adiós", "termina", etc.
- `agent_decision` - El agente decidió terminar
- `timeout` - Inactividad
- `system` - Error del sistema o desconexión

---

#### `action`
Acción que el cliente Android debe ejecutar.

```json
{
  "type": "action",
  "action": "PLAY_MUSIC",
  "params": {
    "playlist": "favorites",
    "shuffle": true
  }
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `type` | `"action"` | Sí | Tipo de mensaje |
| `action` | string | Sí | Nombre de la acción |
| `params` | object | No | Parámetros de la acción |

---

#### `error`
Error en el procesamiento.

```json
{
  "type": "error",
  "message": "Error procesando comando"
}
```

---

#### `pong`
Respuesta a ping.

```json
{
  "type": "pong"
}
```

---

### 1.3 ShowContent (Contenido Enriquecido)

Estructura para mostrar contenido visual en la UI del cliente.

```typescript
interface ShowContent {
  type: 'text' | 'image' | 'image_text' | 'link' | 'links' | 'video' | 'card' | 'list';
  title?: string;
  text?: string;
  imageUrl?: string;
  videoUrl?: string;
  thumbnailUrl?: string;
  links?: ShowLink[];
  items?: ShowListItem[];
}

interface ShowLink {
  title: string;
  url: string;
  description?: string;
  thumbnailUrl?: string;
}

interface ShowListItem {
  title: string;
  subtitle?: string;
  imageUrl?: string;
}
```

**Ejemplos por tipo:**

```json
// type: "text"
{ "type": "text", "text": "15:45" }

// type: "card"
{ "type": "card", "title": "Clima", "text": "22°C Soleado", "imageUrl": "https://..." }

// type: "list"
{
  "type": "list",
  "title": "Noticias",
  "items": [
    { "title": "Noticia 1", "subtitle": "Descripción..." },
    { "title": "Noticia 2", "subtitle": "Descripción..." }
  ]
}

// type: "links"
{
  "type": "links",
  "title": "Resultados",
  "links": [
    { "title": "Wikipedia", "url": "https://...", "description": "Enciclopedia" }
  ]
}
```

---

## 2. Webhook API (Backend → Agente/n8n)

El backend envía peticiones HTTP POST al webhook configurado y espera una respuesta JSON.

### URL de Configuración

```
Variable de entorno: WORKFLOW_WEBHOOK_URL
Default: http://localhost:5678/webhook/yarvis
```

---

### 2.1 Payload enviado al Agente

#### Comando de voz/texto

```json
{
  "type": "voice_command",
  "text": "¿Qué hora es?",
  "timestamp": 1705123456789,
  "sessionId": "uuid-session-id",
  "context": {
    "previousTopic": "weather"
  }
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `type` | `"voice_command"` | Tipo de evento |
| `text` | string | Texto del comando |
| `timestamp` | number | Unix timestamp |
| `sessionId` | string \| undefined | ID de sesión activa |
| `context` | object \| undefined | Contexto de la conversación |

---

#### Notificación

```json
{
  "type": "notification",
  "app": "WhatsApp",
  "title": "Juan Pérez",
  "text": "Hola, ¿cómo estás?"
}
```

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `type` | `"notification"` | Tipo de evento |
| `app` | string | Aplicación origen |
| `title` | string | Título de la notificación |
| `text` | string | Contenido |

---

### 2.2 Respuesta esperada del Agente

```json
{
  "response": "Son las 3:45 de la tarde.",
  "action": "NONE",
  "params": {},
  "endConversation": false,
  "farewell": null,
  "show": {
    "type": "text",
    "text": "15:45"
  }
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `response` | string | No | Texto a responder. Si está vacío/null, no se envía nada al cliente. |
| `action` | string | No | Acción a ejecutar en el cliente (ej: `PLAY_MUSIC`, `SET_ALARM`) |
| `params` | object | No | Parámetros para la acción |
| `endConversation` | boolean | No | `true` = terminar la conversación después de responder |
| `farewell` | string | No | Mensaje de despedida si `endConversation=true` |
| `show` | ShowContent | No | Contenido enriquecido para la UI |

---

### 2.3 Casos de uso del Agente

#### Responder y continuar conversación
```json
{
  "response": "El clima está soleado, 22 grados.",
  "show": { "type": "card", "title": "Clima", "text": "22°C" }
}
```

#### Responder y terminar conversación
```json
{
  "response": "¡Listo! Tu alarma está configurada.",
  "endConversation": true,
  "farewell": "¡Que descanses!"
}
```

#### No responder (ignorar)
```json
{}
```
o
```json
{
  "response": null
}
```

#### Ejecutar acción en el dispositivo
```json
{
  "response": "Reproduciendo tu música favorita.",
  "action": "PLAY_MUSIC",
  "params": { "playlist": "favorites" }
}
```

#### Iniciar conversación desde notificación
```json
{
  "response": "Tienes un mensaje importante de WhatsApp.",
  "action": "START_CONVERSATION"
}
```

---

## 3. REST API (Sistemas externos → Backend)

API para que sistemas externos interactúen con los clientes conectados.

### Base URL
```
http://{host}:{port}
```

---

### `GET /health`

Estado del servidor.

**Response:**
```json
{
  "status": "ok",
  "mockMode": true,
  "connections": 2,
  "activeSessions": 1,
  "workflowUrl": "http://localhost:5678/webhook/yarvis"
}
```

---

### `GET /`

Información del servidor.

**Response:**
```json
{
  "name": "Yarvis Backend",
  "version": "1.2.0",
  "endpoints": {
    "websocket": "/ws",
    "health": "/health",
    "speak": "POST /api/speak",
    "endConversation": "POST /api/end-conversation",
    "broadcast": "POST /api/broadcast",
    "sessions": "GET /api/sessions"
  }
}
```

---

### `POST /api/speak`

Envía un mensaje para que Yarvis lo hable.

**Request:**
```json
{
  "text": "Tienes una reunión en 10 minutos.",
  "clientId": "uuid-client-id",
  "startConversation": true,
  "context": { "source": "calendar" }
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `text` | string | Sí | Texto a hablar |
| `clientId` | string | No | Cliente específico. Si no se provee, broadcast a todos. |
| `startConversation` | boolean | No | Iniciar una sesión de conversación |
| `context` | object | No | Contexto para la conversación |

**Response:**
```json
{
  "success": true,
  "sessionId": "uuid-session-id",
  "clientsReached": 1
}
```

---

### `POST /api/end-conversation`

Termina una conversación activa.

**Request:**
```json
{
  "sessionId": "uuid-session-id",
  "farewell": "¡Hasta luego!",
  "reason": "agent_decision"
}
```

| Campo | Tipo | Requerido | Descripción |
|-------|------|-----------|-------------|
| `sessionId` | string | No* | ID de sesión a terminar |
| `clientId` | string | No* | Terminar todas las sesiones de un cliente |
| `farewell` | string | No | Mensaje de despedida |
| `reason` | string | No | Razón del cierre |

*Se requiere `sessionId` o `clientId`.

**Response:**
```json
{
  "success": true,
  "endedCount": 1
}
```

---

### `POST /api/broadcast`

Envía un mensaje a todos los clientes conectados.

**Request:**
```json
{
  "text": "Actualización del sistema completada.",
  "speak": true
}
```

**Response:**
```json
{
  "success": true,
  "clientsReached": 3
}
```

---

### `GET /api/sessions`

Lista las sesiones de conversación activas.

**Response:**
```json
{
  "sessions": [
    {
      "id": "uuid-session-id",
      "clientId": "uuid-client-id",
      "startedAt": 1705123456789,
      "lastActivityAt": 1705123556789,
      "initiatedBy": "user",
      "context": {}
    }
  ],
  "totalClients": 2
}
```

---

### `GET /api/clients`

Lista los clientes WebSocket conectados.

**Response:**
```json
{
  "count": 2,
  "clients": [
    {
      "id": "uuid-client-id",
      "hasActiveSession": true,
      "activeSession": { "id": "uuid-session-id", "..." }
    }
  ]
}
```

---

## 4. Flujos de Comunicación

### 4.1 Usuario habla → Agente responde

```
┌────────┐         ┌─────────┐         ┌───────┐
│Android │         │ Backend │         │ Agente│
└───┬────┘         └────┬────┘         └───┬───┘
    │                   │                  │
    │ voice_command     │                  │
    │──────────────────►│                  │
    │                   │ POST /webhook    │
    │                   │─────────────────►│
    │                   │                  │
    │                   │ WorkflowResponse │
    │                   │◄─────────────────│
    │                   │                  │
    │ response          │                  │
    │◄──────────────────│                  │
    │                   │                  │
```

### 4.2 Notificación → Agente decide si responder

```
┌────────┐         ┌─────────┐         ┌───────┐
│Android │         │ Backend │         │ Agente│
└───┬────┘         └────┬────┘         └───┬───┘
    │                   │                  │
    │ notification      │                  │
    │──────────────────►│                  │
    │                   │ POST /webhook    │
    │                   │─────────────────►│
    │                   │                  │
    │                   │ {response: null} │ (ignorar)
    │                   │◄─────────────────│
    │                   │                  │
    │ (nada)            │                  │
    │                   │                  │
```

### 4.3 Agente inicia conversación

```
┌────────┐         ┌─────────┐         ┌───────┐
│Android │         │ Backend │         │ Agente│
└───┬────┘         └────┬────┘         └───┬───┘
    │                   │                  │
    │                   │ POST /api/speak  │
    │                   │◄─────────────────│
    │                   │ {startConv:true} │
    │                   │                  │
    │ start_conversation│                  │
    │◄──────────────────│                  │
    │                   │                  │
    │ (usuario responde)│                  │
    │──────────────────►│                  │
    │        ...        │       ...        │
```

---

## 5. Variables de Entorno

| Variable | Default | Descripción |
|----------|---------|-------------|
| `PORT` | `3000` | Puerto del servidor |
| `WORKFLOW_WEBHOOK_URL` | `http://localhost:5678/webhook/yarvis` | URL del webhook del agente |
| `WORKFLOW_TIMEOUT` | `30000` | Timeout de peticiones al agente (ms) |
| `SESSION_TIMEOUT` | `300000` | Timeout de inactividad de sesiones (ms) |
| `MOCK_MODE` | `false` | Usar respuestas mock en lugar del agente real |
| `MOCK_DELAY` | `500` | Delay simulado en modo mock (ms) |

---

## 6. Códigos de Error

| Código | Descripción |
|--------|-------------|
| `400` | Bad Request - Parámetros faltantes o inválidos |
| `404` | Not Found - Recurso no encontrado |
| `500` | Internal Server Error - Error del servidor |
| `503` | Service Unavailable - Agente no disponible |
