import { WebSocketServer, WebSocket } from 'ws';
import { Server as HttpServer } from 'http';
import { v4 as uuidv4 } from 'uuid';
import type { ClientMessage, ServerMessage } from './types.js';
import { connections } from './connections.js';
import { handleClientMessage } from './handlers.js';
import { SessionManager } from './sessions.js';
import { WorkflowClient } from './workflow.js';
import { MockWorkflowClient } from './mock-workflow.js';
import { config, savePassword } from './config.js';

// Map para rastrear clientes autenticados
const authenticatedClients = new Map<string, { agentName?: string }>();

/**
 * Verifica si un cliente está autenticado
 */
export function isClientAuthenticated(clientId: string): boolean {
  return authenticatedClients.has(clientId);
}

/**
 * Obtiene el nombre del agente de un cliente
 */
export function getAgentName(clientId: string): string | undefined {
  return authenticatedClients.get(clientId)?.agentName;
}

/**
 * Configura el servidor WebSocket
 */
export function setupWebSocketServer(
  server: HttpServer,
  sessions: SessionManager,
  workflow: WorkflowClient | MockWorkflowClient
): WebSocketServer {
  const wss = new WebSocketServer({ server, path: '/ws' });

  wss.on('connection', (ws, req) => {
    const url = new URL(req.url || '', `http://${req.headers.host}`);
    const clientId = url.searchParams.get('clientId') || uuidv4();

    connections.add(clientId, ws);
    console.log(`[WS] Client connected: ${clientId} (total: ${connections.size})`);

    ws.on('message', async (data) => {
      try {
        const message = JSON.parse(data.toString()) as ClientMessage;
        
        // Manejar autenticación
        if (message.type === 'auth') {
          const isValid = message.password === config.password;
          if (isValid) {
            authenticatedClients.set(clientId, { agentName: message.agentName });
            console.log(`[WS] Client authenticated: ${clientId} (agent: ${message.agentName || 'unnamed'})`);
          } else {
            console.log(`[WS] Authentication failed for client: ${clientId}`);
          }
          ws.send(JSON.stringify({
            type: 'auth_response',
            success: isValid,
            message: isValid ? 'Autenticación exitosa' : 'Contraseña incorrecta',
          } as ServerMessage));
          return;
        }

        // Manejar cambio de contraseña
        if (message.type === 'change_password') {
          if (!isClientAuthenticated(clientId)) {
            ws.send(JSON.stringify({
              type: 'change_password_response',
              success: false,
              message: 'Debes autenticarte primero',
            } as ServerMessage));
            return;
          }

          if (message.currentPassword !== config.password) {
            ws.send(JSON.stringify({
              type: 'change_password_response',
              success: false,
              message: 'Contraseña actual incorrecta',
            } as ServerMessage));
            return;
          }

          const saved = savePassword(message.newPassword);
          ws.send(JSON.stringify({
            type: 'change_password_response',
            success: saved,
            message: saved ? 'Contraseña actualizada exitosamente' : 'Error al guardar la contraseña',
          } as ServerMessage));
          return;
        }

        // Para otros mensajes, verificar autenticación (excepto ping)
        if (message.type !== 'ping' && !isClientAuthenticated(clientId)) {
          ws.send(JSON.stringify({
            type: 'error',
            message: 'No autenticado. Envía un mensaje de tipo "auth" con la contraseña.',
          } as ServerMessage));
          return;
        }

        await handleClientMessage(
          clientId,
          message,
          (response: ServerMessage) => {
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify(response));
            }
          },
          sessions,
          workflow
        );
      } catch (error) {
        console.error('[WS] Error parsing message:', error);
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({
            type: 'error',
            message: 'Invalid message format',
          } as ServerMessage));
        }
      }
    });

    ws.on('close', () => {
      connections.remove(clientId);
      authenticatedClients.delete(clientId);
      sessions.endSessionsForClient(clientId, 'system');
      console.log(`[WS] Client disconnected: ${clientId} (total: ${connections.size})`);
    });

    ws.on('error', (error) => {
      console.error(`[WS] Error for client ${clientId}:`, error);
    });
  });

  return wss;
}

/**
 * Inicia el cleanup periódico de sesiones expiradas
 */
export function startSessionCleanup(sessions: SessionManager): NodeJS.Timeout {
  return setInterval(() => {
    const expired = sessions.cleanupExpiredSessions();
    for (const { sessionId, clientId } of expired) {
      connections.sendTo(clientId, {
        type: 'end_conversation',
        sessionId,
        farewell: 'La conversación se cerró por inactividad.',
        reason: 'timeout',
      });
    }
  }, 60 * 1000);
}
