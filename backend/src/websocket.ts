import { WebSocketServer, WebSocket } from 'ws';
import { Server as HttpServer } from 'http';
import { v4 as uuidv4 } from 'uuid';
import type { ClientMessage, ServerMessage } from './types.js';
import { connections } from './connections.js';
import { handleClientMessage } from './handlers.js';
import { SessionManager } from './sessions.js';
import { WorkflowClient } from './workflow.js';
import { MockWorkflowClient } from './mock-workflow.js';

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
