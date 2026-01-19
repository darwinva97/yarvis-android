import { WebSocket } from 'ws';
import type { ServerMessage } from './types.js';

/**
 * Representa una conexión WebSocket de un cliente
 */
export interface ClientConnection {
  ws: WebSocket;
  id: string;
}

/**
 * Gestor de conexiones WebSocket activas
 */
class ConnectionManager {
  private connections = new Map<string, ClientConnection>();

  /**
   * Registra una nueva conexión
   */
  add(clientId: string, ws: WebSocket): ClientConnection {
    const conn: ClientConnection = { ws, id: clientId };
    this.connections.set(clientId, conn);
    return conn;
  }

  /**
   * Elimina una conexión
   */
  remove(clientId: string): boolean {
    return this.connections.delete(clientId);
  }

  /**
   * Obtiene una conexión por ID
   */
  get(clientId: string): ClientConnection | undefined {
    return this.connections.get(clientId);
  }

  /**
   * Verifica si existe una conexión
   */
  has(clientId: string): boolean {
    return this.connections.has(clientId);
  }

  /**
   * Número de conexiones activas
   */
  get size(): number {
    return this.connections.size;
  }

  /**
   * Itera sobre todas las conexiones
   */
  forEach(callback: (conn: ClientConnection, clientId: string) => void): void {
    this.connections.forEach((conn, id) => callback(conn, id));
  }

  /**
   * Obtiene todos los IDs de clientes
   */
  getAllClientIds(): string[] {
    return Array.from(this.connections.keys());
  }

  /**
   * Envía un mensaje a un cliente específico
   */
  sendTo(clientId: string, message: ServerMessage): boolean {
    const conn = this.connections.get(clientId);
    if (conn && conn.ws.readyState === WebSocket.OPEN) {
      try {
        conn.ws.send(JSON.stringify(message));
        return true;
      } catch (error) {
        console.error(`[WS] Error sending to ${clientId}:`, error);
      }
    }
    return false;
  }

  /**
   * Envía un mensaje a todos los clientes conectados
   */
  broadcast(message: ServerMessage): number {
    let count = 0;
    this.connections.forEach((conn) => {
      if (conn.ws.readyState === WebSocket.OPEN) {
        try {
          conn.ws.send(JSON.stringify(message));
          count++;
        } catch (error) {
          console.error(`[WS] Error broadcasting to ${conn.id}:`, error);
        }
      }
    });
    return count;
  }
}

// Singleton para gestionar conexiones
export const connections = new ConnectionManager();
