import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import type {
  ClientMessage,
  ServerMessage,
  ServerConfig,
  SpeakRequest,
  SpeakResponse,
  EndConversationRequest,
  BroadcastRequest,
  SessionsResponse,
} from './types.js';
import { WorkflowClient } from './workflow.js';
import { MockWorkflowClient } from './mock-workflow.js';
import { SessionManager } from './sessions.js';

// Configuración desde variables de entorno
const config: ServerConfig = {
  port: Number(process.env.PORT) || 3000,
  workflowWebhookUrl: process.env.WORKFLOW_WEBHOOK_URL || 'http://localhost:5678/webhook/yarvis',
  workflowTimeout: Number(process.env.WORKFLOW_TIMEOUT) || 30000,
  sessionTimeout: Number(process.env.SESSION_TIMEOUT) || 5 * 60 * 1000, // 5 minutos
  mockMode: process.env.MOCK_MODE === 'true' || process.env.MOCK_MODE === '1',
  mockDelay: Number(process.env.MOCK_DELAY) || 500,
};

// Usar cliente mock o real según configuración
const workflow = config.mockMode
  ? new MockWorkflowClient(config.mockDelay)
  : new WorkflowClient(config.workflowWebhookUrl, config.workflowTimeout);

const sessions = new SessionManager(config.sessionTimeout);

// Tipo para conexiones WebSocket
interface ClientConnection {
  ws: WebSocket;
  id: string;
}

// Conexiones WebSocket activas
const connections = new Map<string, ClientConnection>();

// Express app
const app = express();
app.use(cors());
app.use(express.json());

// ==================== Health & Info ====================

app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    mockMode: config.mockMode,
    connections: connections.size,
    activeSessions: sessions.getAllSessions().length,
    workflowUrl: config.mockMode ? null : config.workflowWebhookUrl,
  });
});

app.get('/', (req, res) => {
  res.json({
    name: 'Yarvis Backend',
    version: '1.2.0',
    endpoints: {
      websocket: '/ws',
      health: '/health',
      speak: 'POST /api/speak',
      endConversation: 'POST /api/end-conversation',
      broadcast: 'POST /api/broadcast',
      sessions: 'GET /api/sessions',
    },
  });
});

// ==================== API REST para sistemas de automatización ====================

/**
 * POST /api/speak
 * Sistema de automatización envía un mensaje para que Yarvis lo hable.
 */
app.post('/api/speak', (req, res) => {
  try {
    const body = req.body as SpeakRequest;
    const { text, clientId, startConversation, context } = body;

    if (!text) {
      res.status(400).json({
        success: false,
        clientsReached: 0,
        error: 'text is required',
      } as SpeakResponse);
      return;
    }

    let sessionId: string | undefined;
    let clientsReached = 0;

    if (clientId) {
      const conn = connections.get(clientId);
      if (conn) {
        if (startConversation) {
          const session = sessions.createSession(clientId, 'system', context);
          sessionId = session.id;
          sendToClient(conn, {
            type: 'start_conversation',
            sessionId: session.id,
            greeting: text,
            context,
          });
        } else {
          sendToClient(conn, {
            type: 'response',
            text,
            speak: true,
            sessionId: sessions.getActiveSessionForClient(clientId)?.id,
          });
        }
        clientsReached = 1;
      }
    } else {
      for (const [id, conn] of connections) {
        if (startConversation) {
          const session = sessions.createSession(id, 'system', context);
          if (!sessionId) sessionId = session.id;
          sendToClient(conn, {
            type: 'start_conversation',
            sessionId: session.id,
            greeting: text,
            context,
          });
        } else {
          sendToClient(conn, {
            type: 'response',
            text,
            speak: true,
          });
        }
        clientsReached++;
      }
    }

    console.log(`[API] /speak: "${text.substring(0, 50)}..." to ${clientsReached} client(s)`);

    res.json({
      success: clientsReached > 0,
      sessionId,
      clientsReached,
      error: clientsReached === 0 ? 'No clients connected' : undefined,
    } as SpeakResponse);
  } catch (error) {
    res.status(500).json({
      success: false,
      clientsReached: 0,
      error: error instanceof Error ? error.message : 'Unknown error',
    } as SpeakResponse);
  }
});

/**
 * POST /api/end-conversation
 */
app.post('/api/end-conversation', (req, res) => {
  try {
    const body = req.body as EndConversationRequest;
    const { sessionId, clientId, farewell, reason = 'agent_decision' } = body;

    let endedCount = 0;

    if (sessionId) {
      const session = sessions.getSession(sessionId);
      if (session) {
        const conn = connections.get(session.clientId);
        if (conn) {
          sendToClient(conn, {
            type: 'end_conversation',
            sessionId,
            farewell,
            reason,
          });
        }
        sessions.endSession(sessionId, reason);
        endedCount = 1;
      }
    } else if (clientId) {
      const endedSessions = sessions.endSessionsForClient(clientId, reason);
      const conn = connections.get(clientId);

      for (const session of endedSessions) {
        if (conn) {
          sendToClient(conn, {
            type: 'end_conversation',
            sessionId: session.id,
            farewell,
            reason,
          });
        }
      }
      endedCount = endedSessions.length;
    }

    console.log(`[API] /end-conversation: ended ${endedCount} session(s)`);

    res.json({
      success: endedCount > 0,
      endedCount,
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
});

/**
 * POST /api/broadcast
 */
app.post('/api/broadcast', (req, res) => {
  try {
    const body = req.body as BroadcastRequest;
    const { text, speak } = body;

    if (!text) {
      res.status(400).json({ success: false, error: 'text is required' });
      return;
    }

    let clientsReached = 0;
    for (const [, conn] of connections) {
      sendToClient(conn, {
        type: 'response',
        text,
        speak,
      });
      clientsReached++;
    }

    console.log(`[API] /broadcast: "${text.substring(0, 50)}..." to ${clientsReached} client(s)`);

    res.json({
      success: clientsReached > 0,
      clientsReached,
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
});

/**
 * GET /api/sessions
 */
app.get('/api/sessions', (req, res) => {
  const response: SessionsResponse = {
    sessions: sessions.getAllSessions(),
    totalClients: connections.size,
  };
  res.json(response);
});

/**
 * GET /api/clients
 */
app.get('/api/clients', (req, res) => {
  const clients = Array.from(connections.keys()).map((id) => ({
    id,
    hasActiveSession: sessions.hasActiveSession(id),
    activeSession: sessions.getActiveSessionForClient(id),
  }));

  res.json({
    count: clients.length,
    clients,
  });
});

// ==================== Helpers ====================

function sendToClient(conn: ClientConnection, message: ServerMessage): void {
  try {
    if (conn.ws.readyState === WebSocket.OPEN) {
      conn.ws.send(JSON.stringify(message));
    }
  } catch (error) {
    console.error(`[WS] Error sending to ${conn.id}:`, error);
  }
}

function detectEndConversationIntent(text: string): boolean {
  const lowerText = text.toLowerCase();
  const endPhrases = [
    'adiós', 'adios', 'hasta luego', 'chao', 'chau',
    'termina', 'terminamos', 'eso es todo', 'nada más',
    'gracias eso es todo', 'ya no necesito nada',
    'bye', 'goodbye', 'that\'s all',
  ];
  return endPhrases.some((phrase) => lowerText.includes(phrase));
}

async function handleClientMessage(
  clientId: string,
  message: ClientMessage,
  sendResponse: (msg: ServerMessage) => void
): Promise<void> {
  switch (message.type) {
    case 'ping':
      sendResponse({ type: 'pong' });
      break;

    case 'voice_command': {
      console.log(`[Voice] Client ${clientId}: "${message.text}"`);

      let activeSession = sessions.getActiveSessionForClient(clientId);
      if (activeSession) {
        sessions.updateActivity(activeSession.id);
      }

      if (activeSession && detectEndConversationIntent(message.text)) {
        sendResponse({
          type: 'end_conversation',
          sessionId: activeSession.id,
          farewell: 'Hasta luego, que tengas un buen día.',
          reason: 'user_request',
        });
        sessions.endSession(activeSession.id, 'user_request');
        return;
      }

      const result = await workflow.sendVoiceCommand(
        message.text,
        activeSession?.id,
        activeSession?.context
      );

      if (result.success) {
        if (result.endConversation && activeSession) {
          sendResponse({
            type: 'response',
            text: result.farewell || result.response || '',
            speak: true,
            sessionId: activeSession.id,
            messageId: uuidv4(),
            show: result.show,
          });
          sendResponse({
            type: 'end_conversation',
            sessionId: activeSession.id,
            farewell: result.farewell,
            reason: 'agent_decision',
          });
          sessions.endSession(activeSession.id, 'agent_decision');
          return;
        }

        if (result.response) {
          sendResponse({
            type: 'response',
            text: result.response,
            speak: true,
            sessionId: activeSession?.id,
            messageId: uuidv4(),
            show: result.show,
          });
        }
        if (result.action) {
          sendResponse({
            type: 'action',
            action: result.action,
            params: result.params,
          });
        }
      } else {
        sendResponse({
          type: 'error',
          message: result.error || 'Error procesando comando',
        });
      }
      break;
    }

    case 'chat_message': {
      console.log(`[Chat] Client ${clientId}: "${message.text}"`);

      let activeSession = sessions.getActiveSessionForClient(clientId);
      if (activeSession) {
        sessions.updateActivity(activeSession.id);
      }

      if (activeSession && detectEndConversationIntent(message.text)) {
        sendResponse({
          type: 'end_conversation',
          sessionId: activeSession.id,
          farewell: 'Hasta luego, que tengas un buen día.',
          reason: 'user_request',
        });
        sessions.endSession(activeSession.id, 'user_request');
        return;
      }

      const result = await workflow.sendVoiceCommand(
        message.text,
        activeSession?.id,
        activeSession?.context
      );

      if (result.success) {
        if (result.endConversation && activeSession) {
          sendResponse({
            type: 'response',
            text: result.farewell || result.response || '',
            speak: false,
            sessionId: activeSession.id,
            messageId: uuidv4(),
            show: result.show,
          });
          sendResponse({
            type: 'end_conversation',
            sessionId: activeSession.id,
            farewell: result.farewell,
            reason: 'agent_decision',
          });
          sessions.endSession(activeSession.id, 'agent_decision');
          return;
        }

        if (result.response) {
          sendResponse({
            type: 'response',
            text: result.response,
            speak: false,
            sessionId: activeSession?.id,
            messageId: uuidv4(),
            show: result.show,
          });
        }
        if (result.action) {
          sendResponse({
            type: 'action',
            action: result.action,
            params: result.params,
          });
        }
      } else {
        sendResponse({
          type: 'error',
          message: result.error || 'Error procesando mensaje',
        });
      }
      break;
    }

    case 'notification': {
      console.log(`[Notification] ${message.app}: ${message.title}`);

      const result = await workflow.sendNotification(
        message.app,
        message.title,
        message.text
      );

      if (result.success && result.response) {
        if (result.action === 'START_CONVERSATION') {
          const session = sessions.createSession(clientId, 'system');
          sendResponse({
            type: 'start_conversation',
            sessionId: session.id,
            greeting: result.response,
          });
        } else {
          sendResponse({
            type: 'response',
            text: result.response,
            speak: true,
          });
        }
      }
      break;
    }

    case 'end_conversation': {
      const session = sessions.getSession(message.sessionId);
      if (session) {
        sessions.endSession(message.sessionId, 'user_request');
        console.log(`[Session] Client ${clientId} ended session ${message.sessionId}`);
      }
      break;
    }
  }
}

// ==================== HTTP + WebSocket Server ====================

const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
  const url = new URL(req.url || '', `http://${req.headers.host}`);
  const clientId = url.searchParams.get('clientId') || uuidv4();

  connections.set(clientId, { ws, id: clientId });
  console.log(`[WS] Client connected: ${clientId} (total: ${connections.size})`);

  ws.on('message', async (data) => {
    try {
      const message = JSON.parse(data.toString()) as ClientMessage;
      await handleClientMessage(clientId, message, (response) => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify(response));
        }
      });
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
    connections.delete(clientId);
    sessions.endSessionsForClient(clientId, 'system');
    console.log(`[WS] Client disconnected: ${clientId} (total: ${connections.size})`);
  });

  ws.on('error', (error) => {
    console.error(`[WS] Error for client ${clientId}:`, error);
  });
});

// Cleanup sesiones expiradas
setInterval(() => {
  const expired = sessions.cleanupExpiredSessions();
  for (const { sessionId, clientId } of expired) {
    const conn = connections.get(clientId);
    if (conn) {
      sendToClient(conn, {
        type: 'end_conversation',
        sessionId,
        farewell: 'La conversación se cerró por inactividad.',
        reason: 'timeout',
      });
    }
  }
}, 60 * 1000);

// Start server
server.listen(config.port, () => {
  const modeLabel = config.mockMode ? '🧪 MOCK MODE' : '🔗 LIVE MODE';
  const workflowInfo = config.mockMode
    ? `Mock (delay: ${config.mockDelay}ms)`.padEnd(40)
    : config.workflowWebhookUrl.substring(0, 40).padEnd(40);

  console.log(`
╔══════════════════════════════════════════════════════╗
║              Yarvis Backend v1.2.0                   ║
║                  ${modeLabel.padEnd(34)}║
╠══════════════════════════════════════════════════════╣
║  HTTP:       http://localhost:${config.port}                    ║
║  WebSocket:  ws://localhost:${config.port}/ws                   ║
║  Workflow:   ${workflowInfo}  ║
╠══════════════════════════════════════════════════════╣
║  API Endpoints:                                      ║
║    POST /api/speak          - Hacer hablar a Yarvis  ║
║    POST /api/end-conversation - Terminar conversación║
║    POST /api/broadcast      - Mensaje a todos        ║
║    GET  /api/sessions       - Ver sesiones activas   ║
║    GET  /api/clients        - Ver clientes conectados║
╚══════════════════════════════════════════════════════╝
`);
});

export default server;
