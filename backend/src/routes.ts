import { Router } from 'express';
import type {
  SpeakRequest,
  SpeakResponse,
  EndConversationRequest,
  BroadcastRequest,
  SessionsResponse,
  ServerConfig,
} from './types.js';
import { connections } from './connections.js';
import { SessionManager } from './sessions.js';

/**
 * Crea el router con todos los endpoints REST API
 */
export function createRoutes(config: ServerConfig, sessions: SessionManager): Router {
  const router = Router();

  // ==================== Health & Info ====================

  router.get('/health', (req, res) => {
    res.json({
      status: 'ok',
      mockMode: config.mockMode,
      connections: connections.size,
      activeSessions: sessions.getAllSessions().length,
      workflowUrlDev: config.mockMode ? null : config.workflowDev.url,
      workflowUrlProd: config.mockMode ? null : config.workflowProd.url,
    });
  });

  router.get('/', (req, res) => {
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
  router.post('/api/speak', (req, res) => {
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
            connections.sendTo(clientId, {
              type: 'start_conversation',
              sessionId: session.id,
              greeting: text,
              context,
            });
          } else {
            connections.sendTo(clientId, {
              type: 'response',
              text,
              speak: true,
              sessionId: sessions.getActiveSessionForClient(clientId)?.id,
            });
          }
          clientsReached = 1;
        }
      } else {
        connections.forEach((conn, id) => {
          if (startConversation) {
            const session = sessions.createSession(id, 'system', context);
            if (!sessionId) sessionId = session.id;
            connections.sendTo(id, {
              type: 'start_conversation',
              sessionId: session.id,
              greeting: text,
              context,
            });
          } else {
            connections.sendTo(id, {
              type: 'response',
              text,
              speak: true,
            });
          }
          clientsReached++;
        });
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
  router.post('/api/end-conversation', (req, res) => {
    try {
      const body = req.body as EndConversationRequest;
      const { sessionId, clientId, farewell, reason = 'agent_decision' } = body;

      let endedCount = 0;

      if (sessionId) {
        const session = sessions.getSession(sessionId);
        if (session) {
          connections.sendTo(session.clientId, {
            type: 'end_conversation',
            sessionId,
            farewell,
            reason,
          });
          sessions.endSession(sessionId, reason);
          endedCount = 1;
        }
      } else if (clientId) {
        const endedSessions = sessions.endSessionsForClient(clientId, reason);

        for (const session of endedSessions) {
          connections.sendTo(clientId, {
            type: 'end_conversation',
            sessionId: session.id,
            farewell,
            reason,
          });
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
  router.post('/api/broadcast', (req, res) => {
    try {
      const body = req.body as BroadcastRequest;
      const { text, speak } = body;

      if (!text) {
        res.status(400).json({ success: false, error: 'text is required' });
        return;
      }

      const clientsReached = connections.broadcast({
        type: 'response',
        text,
        speak,
      });

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
  router.get('/api/sessions', (req, res) => {
    const response: SessionsResponse = {
      sessions: sessions.getAllSessions(),
      totalClients: connections.size,
    };
    res.json(response);
  });

  /**
   * GET /api/clients
   */
  router.get('/api/clients', (req, res) => {
    const clients = connections.getAllClientIds().map((id) => ({
      id,
      hasActiveSession: sessions.hasActiveSession(id),
      activeSession: sessions.getActiveSessionForClient(id),
    }));

    res.json({
      count: clients.length,
      clients,
    });
  });

  return router;
}
