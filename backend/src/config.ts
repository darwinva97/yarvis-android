import type { ServerConfig } from './types.js';

/**
 * Configuración del servidor desde variables de entorno
 */
export const config: ServerConfig = {
  port: Number(process.env.PORT) || 3000,
  workflowWebhookUrl: process.env.WORKFLOW_WEBHOOK_URL || 'http://localhost:5678/webhook/yarvis',
  workflowTimeout: Number(process.env.WORKFLOW_TIMEOUT) || 30000,
  sessionTimeout: Number(process.env.SESSION_TIMEOUT) || 5 * 60 * 1000, // 5 minutos
  mockMode: process.env.MOCK_MODE === 'true' || process.env.MOCK_MODE === '1',
  mockDelay: Number(process.env.MOCK_DELAY) || 500,
};
