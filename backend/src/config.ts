import type { ServerConfig } from './types.js';
import fs from 'fs';
import path from 'path';

const DEFAULT_PASSWORD = 'PasswordJarvis2026!';
const PASSWORD_FILE = process.env.PASSWORD_FILE || './data/password.txt';

/**
 * Lee la contraseña del archivo si existe, si no usa la por defecto
 */
function loadPassword(): string {
  try {
    if (fs.existsSync(PASSWORD_FILE)) {
      return fs.readFileSync(PASSWORD_FILE, 'utf-8').trim();
    }
  } catch (error) {
    console.warn('[Config] Could not read password file, using default');
  }
  return DEFAULT_PASSWORD;
}

/**
 * Guarda la contraseña en el archivo
 */
export function savePassword(password: string): boolean {
  try {
    const dir = path.dirname(PASSWORD_FILE);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    fs.writeFileSync(PASSWORD_FILE, password, 'utf-8');
    config.password = password;
    return true;
  } catch (error) {
    console.error('[Config] Could not save password:', error);
    return false;
  }
}

/**
 * Configuración del servidor desde variables de entorno
 */
export const config: ServerConfig = {
  port: Number(process.env.PORT) || 3000,
  workflowDev: {
    url: process.env.WORKFLOW_WEBHOOK_URL_DEV || 'http://localhost:5678/webhook/yarvis-dev',
    username: process.env.WORKFLOW_WEBHOOK_USERNAME_DEV || '',
    password: process.env.WORKFLOW_WEBHOOK_PASSWORD_DEV || '',
  },
  workflowProd: {
    url: process.env.WORKFLOW_WEBHOOK_URL_PROD || 'http://localhost:5678/webhook/yarvis-prod',
    username: process.env.WORKFLOW_WEBHOOK_USERNAME_PROD || '',
    password: process.env.WORKFLOW_WEBHOOK_PASSWORD_PROD || '',
  },
  workflowTimeout: Number(process.env.WORKFLOW_TIMEOUT) || 30000,
  sessionTimeout: Number(process.env.SESSION_TIMEOUT) || 5 * 60 * 1000, // 5 minutos
  mockMode: process.env.MOCK_MODE === 'true' || process.env.MOCK_MODE === '1',
  mockDelay: Number(process.env.MOCK_DELAY) || 500,
  password: loadPassword(),
  passwordFilePath: PASSWORD_FILE,
  apiAuth: {
    username: process.env.API_AUTH_USERNAME || '',
    password: process.env.API_AUTH_PASSWORD || '',
    enabled: !!(process.env.API_AUTH_USERNAME && process.env.API_AUTH_PASSWORD),
  },
};
