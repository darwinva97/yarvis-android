import { v4 as uuidv4 } from 'uuid';
import type { ConversationSession, EndConversationReason } from './types.js';

/**
 * Gestiona las sesiones de conversación activas.
 */
export class SessionManager {
  private sessions = new Map<string, ConversationSession>();
  private sessionsByClient = new Map<string, Set<string>>();
  private timeoutMs: number;
  private cleanupInterval: ReturnType<typeof setInterval> | null = null;

  constructor(timeoutMs = 5 * 60 * 1000) { // 5 minutos por defecto
    this.timeoutMs = timeoutMs;
    this.startCleanupTask();
  }

  /**
   * Crea una nueva sesión de conversación.
   */
  createSession(
    clientId: string,
    initiatedBy: 'user' | 'system',
    context?: Record<string, unknown>
  ): ConversationSession {
    const session: ConversationSession = {
      id: uuidv4(),
      clientId,
      startedAt: Date.now(),
      lastActivityAt: Date.now(),
      initiatedBy,
      context,
    };

    this.sessions.set(session.id, session);

    // Indexar por cliente
    if (!this.sessionsByClient.has(clientId)) {
      this.sessionsByClient.set(clientId, new Set());
    }
    this.sessionsByClient.get(clientId)!.add(session.id);

    console.log(`[Session] Created: ${session.id} for client ${clientId} (initiated by ${initiatedBy})`);
    return session;
  }

  /**
   * Obtiene una sesión por ID.
   */
  getSession(sessionId: string): ConversationSession | undefined {
    return this.sessions.get(sessionId);
  }

  /**
   * Obtiene la sesión activa de un cliente (la más reciente).
   */
  getActiveSessionForClient(clientId: string): ConversationSession | undefined {
    const sessionIds = this.sessionsByClient.get(clientId);
    if (!sessionIds || sessionIds.size === 0) return undefined;

    // Retornar la sesión más reciente
    let latestSession: ConversationSession | undefined;
    for (const sessionId of sessionIds) {
      const session = this.sessions.get(sessionId);
      if (session && (!latestSession || session.lastActivityAt > latestSession.lastActivityAt)) {
        latestSession = session;
      }
    }
    return latestSession;
  }

  /**
   * Actualiza la actividad de una sesión.
   */
  updateActivity(sessionId: string): void {
    const session = this.sessions.get(sessionId);
    if (session) {
      session.lastActivityAt = Date.now();
    }
  }

  /**
   * Termina una sesión.
   */
  endSession(sessionId: string, reason: EndConversationReason): ConversationSession | undefined {
    const session = this.sessions.get(sessionId);
    if (!session) return undefined;

    this.sessions.delete(sessionId);

    const clientSessions = this.sessionsByClient.get(session.clientId);
    if (clientSessions) {
      clientSessions.delete(sessionId);
      if (clientSessions.size === 0) {
        this.sessionsByClient.delete(session.clientId);
      }
    }

    console.log(`[Session] Ended: ${sessionId} (reason: ${reason})`);
    return session;
  }

  /**
   * Termina todas las sesiones de un cliente.
   */
  endSessionsForClient(clientId: string, reason: EndConversationReason): ConversationSession[] {
    const sessionIds = this.sessionsByClient.get(clientId);
    if (!sessionIds) return [];

    const endedSessions: ConversationSession[] = [];
    for (const sessionId of sessionIds) {
      const session = this.endSession(sessionId, reason);
      if (session) endedSessions.push(session);
    }
    return endedSessions;
  }

  /**
   * Obtiene todas las sesiones activas.
   */
  getAllSessions(): ConversationSession[] {
    return Array.from(this.sessions.values());
  }

  /**
   * Verifica si un cliente tiene una sesión activa.
   */
  hasActiveSession(clientId: string): boolean {
    return this.sessionsByClient.has(clientId) &&
           this.sessionsByClient.get(clientId)!.size > 0;
  }

  /**
   * Limpia sesiones expiradas por inactividad.
   */
  cleanupExpiredSessions(): { sessionId: string; clientId: string }[] {
    const now = Date.now();
    const expired: { sessionId: string; clientId: string }[] = [];

    for (const [sessionId, session] of this.sessions) {
      if (now - session.lastActivityAt > this.timeoutMs) {
        expired.push({ sessionId, clientId: session.clientId });
        this.endSession(sessionId, 'timeout');
      }
    }

    if (expired.length > 0) {
      console.log(`[Session] Cleaned up ${expired.length} expired sessions`);
    }

    return expired;
  }

  /**
   * Inicia la tarea de limpieza periódica.
   */
  private startCleanupTask(): void {
    // Ejecutar cada minuto
    this.cleanupInterval = setInterval(() => {
      this.cleanupExpiredSessions();
    }, 60 * 1000);
  }

  /**
   * Detiene la tarea de limpieza.
   */
  destroy(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
  }
}
