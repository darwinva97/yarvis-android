import type { WorkflowResponse } from './types.js';

/**
 * Payload enviado al sistema de automatización de flujos
 */
interface WorkflowPayload {
  type: 'voice_command' | 'notification';
  text?: string;
  timestamp?: number;
  sessionId?: string;
  context?: Record<string, unknown>;
  app?: string;
  title?: string;
}

/**
 * Cliente genérico para enviar mensajes a webhooks de sistemas de automatización.
 * Compatible con cualquier sistema que acepte webhooks HTTP (n8n, Make, Zapier, etc.)
 */
export class WorkflowClient {
  private webhookUrl: string;
  private timeout: number;

  constructor(webhookUrl: string, timeout = 30000) {
    this.webhookUrl = webhookUrl;
    this.timeout = timeout;
  }

  /**
   * Envía un comando de voz al sistema de automatización y espera la respuesta.
   * Incluye información de sesión si existe una conversación activa.
   */
  async sendVoiceCommand(
    text: string,
    sessionId?: string,
    context?: Record<string, unknown>
  ): Promise<WorkflowResponse> {
    return this.send({
      type: 'voice_command',
      text,
      timestamp: Date.now(),
      sessionId,
      context,
    });
  }

  /**
   * Envía una notificación al sistema de automatización (para procesamiento/filtrado)
   */
  async sendNotification(app: string, title: string, text: string): Promise<WorkflowResponse> {
    return this.send({
      type: 'notification',
      app,
      title,
      text,
    });
  }

  /**
   * Envía un mensaje genérico al webhook
   */
  private async send(payload: WorkflowPayload): Promise<WorkflowResponse> {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeout);

      const response = await fetch(this.webhookUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        return {
          success: false,
          error: `Workflow responded with status ${response.status}`,
        };
      }

      const data = await response.json();
      return {
        success: true,
        ...data,
      };
    } catch (error) {
      if (error instanceof Error) {
        if (error.name === 'AbortError') {
          return { success: false, error: 'Workflow request timed out' };
        }
        return { success: false, error: error.message };
      }
      return { success: false, error: 'Unknown error' };
    }
  }

  /**
   * Verifica si el webhook está disponible
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(this.webhookUrl, {
        method: 'HEAD',
      });
      return response.ok || response.status === 405; // 405 = Method not allowed (webhook exists)
    } catch {
      return false;
    }
  }
}
