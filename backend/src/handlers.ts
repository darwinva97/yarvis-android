import { v4 as uuidv4 } from 'uuid';
import type { ClientMessage, ServerMessage, WorkflowResponse } from './types.js';
import { SessionManager } from './sessions.js';
import { WorkflowClient } from './workflow.js';
import { MockWorkflowClient } from './mock-workflow.js';

type SendResponse = (msg: ServerMessage) => void;

/**
 * Detecta si el texto indica intención de terminar la conversación
 */
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

/**
 * Procesa la respuesta del workflow y envía los mensajes apropiados
 */
function processWorkflowResponse(
  result: WorkflowResponse,
  activeSession: { id: string } | undefined,
  speak: boolean,
  sendResponse: SendResponse,
  sessions: SessionManager
): boolean {
  if (!result.success) {
    sendResponse({
      type: 'error',
      message: result.error || 'Error procesando comando',
    });
    return false;
  }

  if (result.endConversation && activeSession) {
    sendResponse({
      type: 'response',
      text: result.farewell || result.response || '',
      speak,
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
    return true;
  }

  if (result.response) {
    sendResponse({
      type: 'response',
      text: result.response,
      speak,
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

  return true;
}

/**
 * Manejador de mensajes de voz y chat (lógica compartida)
 */
async function handleTextMessage(
  clientId: string,
  text: string,
  speak: boolean,
  production: boolean,
  sessions: SessionManager,
  workflow: WorkflowClient | MockWorkflowClient,
  sendResponse: SendResponse
): Promise<void> {
  const logType = speak ? 'Voice' : 'Chat';
  const envLabel = production ? 'PROD' : 'DEV';
  console.log(`[${logType}] [${envLabel}] Client ${clientId}: "${text}"`);

  const activeSession = sessions.getActiveSessionForClient(clientId);
  if (activeSession) {
    sessions.updateActivity(activeSession.id);
  }

  if (activeSession && detectEndConversationIntent(text)) {
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
    text,
    activeSession?.id,
    activeSession?.context,
    production
  );

  processWorkflowResponse(result, activeSession, speak, sendResponse, sessions);
}

/**
 * Manejador de notificaciones
 */
async function handleNotification(
  clientId: string,
  app: string,
  title: string,
  text: string,
  production: boolean,
  sessions: SessionManager,
  workflow: WorkflowClient | MockWorkflowClient,
  sendResponse: SendResponse
): Promise<void> {
  const envLabel = production ? 'PROD' : 'DEV';
  console.log(`[Notification] [${envLabel}] ${app}: ${title}`);

  const result = await workflow.sendNotification(app, title, text, production);

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
}

/**
 * Manejador principal de mensajes del cliente
 */
export async function handleClientMessage(
  clientId: string,
  message: ClientMessage,
  sendResponse: SendResponse,
  sessions: SessionManager,
  workflow: WorkflowClient | MockWorkflowClient
): Promise<void> {
  switch (message.type) {
    case 'ping':
      sendResponse({ type: 'pong' });
      break;

    case 'voice_command':
      await handleTextMessage(
        clientId,
        message.text,
        true,
        message.production ?? false,
        sessions,
        workflow,
        sendResponse
      );
      break;

    case 'chat_message':
      await handleTextMessage(
        clientId,
        message.text,
        false,
        message.production ?? false,
        sessions,
        workflow,
        sendResponse
      );
      break;

    case 'notification':
      await handleNotification(
        clientId,
        message.app,
        message.title,
        message.text,
        message.production ?? false,
        sessions,
        workflow,
        sendResponse
      );
      break;

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
