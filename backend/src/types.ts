/**
 * Tipos compartidos para comunicación WebSocket entre Android y Backend
 */

// ==================== Sesiones de conversación ====================

export interface ConversationSession {
  id: string;
  clientId: string;
  startedAt: number;
  lastActivityAt: number;
  initiatedBy: 'user' | 'system';  // user = wake word, system = workflow push
  context?: Record<string, unknown>;
}

// ==================== Mensajes del cliente (Android) al servidor ====================

export type ClientMessage =
  | { type: 'voice_command'; text: string; timestamp: number; sessionId?: string }
  | { type: 'chat_message'; text: string; timestamp: number; sessionId?: string }  // Mensaje de chat escrito
  | { type: 'notification'; app: string; title: string; text: string }
  | { type: 'end_conversation'; sessionId: string; reason?: string }
  | { type: 'ping' }
  | { type: 'auth'; password: string; agentName?: string }
  | { type: 'change_password'; currentPassword: string; newPassword: string };

// ==================== Contenido enriquecido para mostrar ====================

/**
 * Tipos de contenido que se pueden mostrar en la UI
 */
export type ShowContentType = 'text' | 'image' | 'image_text' | 'link' | 'links' | 'video' | 'card' | 'list';

/**
 * Contenido para mostrar en la UI (vista previa)
 */
export interface ShowContent {
  type: ShowContentType;
  title?: string;           // Título corto para preview
  text?: string;            // Texto corto para preview
  imageUrl?: string;        // URL de imagen
  videoUrl?: string;        // URL de video
  thumbnailUrl?: string;    // Thumbnail para video/link
  links?: ShowLink[];       // Lista de enlaces
  items?: ShowListItem[];   // Items para tipo 'list'
}

export interface ShowLink {
  title: string;
  url: string;
  description?: string;
  thumbnailUrl?: string;
}

export interface ShowListItem {
  title: string;
  subtitle?: string;
  imageUrl?: string;
}

// ==================== Mensajes del servidor al cliente (Android) ====================

export type ServerMessage =
  | {
      type: 'response';
      text: string;              // Texto completo (para historial y TTS)
      speak: boolean;
      sessionId?: string;
      messageId?: string;        // ID único del mensaje
      show?: ShowContent;        // Contenido para mostrar en UI (preview)
    }
  | { type: 'action'; action: string; params?: Record<string, unknown> }
  | {
      type: 'start_conversation';
      sessionId: string;
      greeting?: string;
      context?: Record<string, unknown>;
      show?: ShowContent;
    }
  | { type: 'end_conversation'; sessionId: string; farewell?: string; reason: EndConversationReason }
  | { type: 'error'; message: string }
  | { type: 'pong' }
  | { type: 'auth_response'; success: boolean; message?: string }
  | { type: 'change_password_response'; success: boolean; message: string };

export type EndConversationReason =
  | 'user_request'      // Usuario dijo "termina", "adiós", etc.
  | 'agent_decision'    // El sistema de automatización decidió terminar
  | 'timeout'           // Inactividad
  | 'system';           // Sistema (error, desconexión, etc.)

// ==================== API REST para sistemas de automatización ====================

// POST /api/speak - Sistema de automatización envía mensaje para que Yarvis hable
export interface SpeakRequest {
  text: string;
  clientId?: string;           // Opcional: cliente específico, si no, broadcast
  startConversation?: boolean; // Iniciar sesión de conversación
  context?: Record<string, unknown>; // Contexto para la conversación
}

export interface SpeakResponse {
  success: boolean;
  sessionId?: string;
  clientsReached: number;
  error?: string;
}

// POST /api/end-conversation - Sistema de automatización termina una conversación
export interface EndConversationRequest {
  sessionId?: string;          // Terminar sesión específica
  clientId?: string;           // Terminar todas las sesiones de un cliente
  farewell?: string;           // Mensaje de despedida
  reason?: EndConversationReason;
}

// GET /api/sessions - Obtener sesiones activas
export interface SessionsResponse {
  sessions: ConversationSession[];
  totalClients: number;
}

// POST /api/broadcast - Enviar mensaje a todos los clientes
export interface BroadcastRequest {
  text: string;
  speak: boolean;
}

// ==================== Respuesta del sistema de automatización ====================

export interface WorkflowResponse {
  success: boolean;
  response?: string;           // Texto completo de la respuesta
  action?: string;
  params?: Record<string, unknown>;
  endConversation?: boolean;   // Sistema indica que quiere terminar
  farewell?: string;           // Mensaje de despedida si termina
  show?: ShowContent;          // Contenido enriquecido para mostrar
  error?: string;
}

// ==================== Autenticación ====================

export interface AuthMessage {
  type: 'auth';
  password: string;
  agentName?: string;
}

export interface AuthResponse {
  type: 'auth_response';
  success: boolean;
  message?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ChangePasswordResponse {
  success: boolean;
  message: string;
}

// ==================== Configuración del servidor ====================

export interface ServerConfig {
  port: number;
  workflowWebhookUrl: string;
  workflowTimeout: number;
  sessionTimeout: number;      // Timeout de inactividad en ms
  mockMode: boolean;           // Usar cliente mock en lugar de webhook real
  mockDelay: number;           // Delay simulado en modo mock (ms)
  password: string;            // Contraseña para autenticación de clientes
  passwordFilePath: string;    // Archivo para persistir la contraseña
}
