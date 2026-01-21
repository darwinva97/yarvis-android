import type { WorkflowResponse, WorkflowEndpointConfig } from './types.js';

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
 * Soporta dos entornos: desarrollo y producción, con autenticación BasicAuth.
 */
export class WorkflowClient {
  private devConfig: WorkflowEndpointConfig;
  private prodConfig: WorkflowEndpointConfig;
  private timeout: number;

  constructor(
    devConfig: WorkflowEndpointConfig,
    prodConfig: WorkflowEndpointConfig,
    timeout = 30000
  ) {
    this.devConfig = devConfig;
    this.prodConfig = prodConfig;
    this.timeout = timeout;
  }

  /**
   * Obtiene la configuración del endpoint según el entorno
   */
  private getEndpointConfig(production: boolean): WorkflowEndpointConfig {
    return production ? this.prodConfig : this.devConfig;
  }

  /**
   * Genera el header de autenticación BasicAuth
   */
  private getAuthHeader(config: WorkflowEndpointConfig): string | null {
    if (config.username && config.password) {
      const credentials = Buffer.from(`${config.username}:${config.password}`).toString('base64');
      return `Basic ${credentials}`;
    }
    return null;
  }

  /**
   * Envía un comando de voz al sistema de automatización y espera la respuesta.
   * Incluye información de sesión si existe una conversación activa.
   */
  async sendVoiceCommand(
    text: string,
    sessionId?: string,
    context?: Record<string, unknown>,
    production = false
  ): Promise<WorkflowResponse> {
    return this.send(
      {
        type: 'voice_command',
        text,
        timestamp: Date.now(),
        sessionId,
        context,
      },
      production
    );
  }

  /**
   * Envía una notificación al sistema de automatización (para procesamiento/filtrado)
   */
  async sendNotification(
    app: string,
    title: string,
    text: string,
    production = false
  ): Promise<WorkflowResponse> {
    return this.send(
      {
        type: 'notification',
        app,
        title,
        text,
      },
      production
    );
  }

  /**
   * Envía un mensaje genérico al webhook del entorno especificado
   */
  private async send(payload: WorkflowPayload, production: boolean): Promise<WorkflowResponse> {
    const endpointConfig = this.getEndpointConfig(production);
    const envLabel = production ? 'PROD' : 'DEV';

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), this.timeout);

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };

      const authHeader = this.getAuthHeader(endpointConfig);
      if (authHeader) {
        headers['Authorization'] = authHeader;
      }

      console.log(`[Workflow] Sending to ${envLabel}: ${endpointConfig.url}`);
      console.log(`[Workflow] Headers: ${JSON.stringify(headers)}`);
      console.log(`[Workflow] Payload: ${JSON.stringify(payload)}`);

      const response = await fetch(endpointConfig.url, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        return {
          success: false,
          error: `Workflow [${envLabel}] responded with status ${response.status}`,
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
          return { success: false, error: `Workflow [${envLabel}] request timed out` };
        }
        return { success: false, error: `Workflow [${envLabel}]: ${error.message}` };
      }
      return { success: false, error: `Workflow [${envLabel}]: Unknown error` };
    }
  }

  /**
   * Verifica si el webhook está disponible
   */
  async healthCheck(production = false): Promise<boolean> {
    const endpointConfig = this.getEndpointConfig(production);
    try {
      const headers: Record<string, string> = {};
      const authHeader = this.getAuthHeader(endpointConfig);
      if (authHeader) {
        headers['Authorization'] = authHeader;
      }

      const response = await fetch(endpointConfig.url, {
        method: 'HEAD',
        headers,
      });
      return response.ok || response.status === 405; // 405 = Method not allowed (webhook exists)
    } catch {
      return false;
    }
  }
}
