import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { config } from './config.js';
import { createRoutes } from './routes.js';
import { setupWebSocketServer, startSessionCleanup } from './websocket.js';
import { SessionManager } from './sessions.js';
import { WorkflowClient } from './workflow.js';
import { MockWorkflowClient } from './mock-workflow.js';

// Instanciar servicios
const workflow = config.mockMode
  ? new MockWorkflowClient(config.mockDelay)
  : new WorkflowClient(config.workflowWebhookUrl, config.workflowTimeout);

const sessions = new SessionManager(config.sessionTimeout);

// Configurar Express
const app = express();
app.use(cors());
app.use(express.json());
app.use(createRoutes(config, sessions));

// Crear servidor HTTP y WebSocket
const server = createServer(app);
setupWebSocketServer(server, sessions, workflow);
startSessionCleanup(sessions);

// Iniciar servidor
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
