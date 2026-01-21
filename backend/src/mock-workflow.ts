import type { WorkflowResponse, ShowContent } from './types.js';

/**
 * Respuestas mock predefinidas para diferentes tipos de comandos
 */
interface MockResponse {
  patterns: RegExp[];
  response: WorkflowResponse;
}

const mockResponses: MockResponse[] = [
  // Saludos
  {
    patterns: [/hola/i, /buenos días/i, /buenas tardes/i, /buenas noches/i, /hi/i, /hello/i],
    response: {
      success: true,
      response: '¡Hola! Soy Yarvis, tu asistente. ¿En qué puedo ayudarte hoy? desde el modo mock.',
      show: {
        type: 'text',
        text: '¡Hola! ¿En qué puedo ayudarte? desde el modo mock.',
      },
    },
  },

  // Clima
  {
    patterns: [/clima/i, /tiempo.*hoy/i, /weather/i, /temperatura/i],
    response: {
      success: true,
      response: 'El clima hoy está soleado con una temperatura de 22 grados celsius. Se esperan cielos despejados durante todo el día.',
      show: {
        type: 'card',
        title: 'Clima Actual',
        text: '22°C - Soleado',
        imageUrl: 'https://example.com/weather/sunny.png',
      },
    },
  },

  // Hora
  {
    patterns: [/hora/i, /qué hora/i, /what time/i],
    response: {
      success: true,
      response: `Son las ${new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}.`,
      show: {
        type: 'text',
        text: new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }),
      },
    },
  },

  // Fecha
  {
    patterns: [/fecha/i, /qué día/i, /what day/i, /today/i],
    response: {
      success: true,
      response: `Hoy es ${new Date().toLocaleDateString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}.`,
      show: {
        type: 'text',
        text: new Date().toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'short' }),
      },
    },
  },

  // Recordatorio
  {
    patterns: [/recuérdame/i, /recordar/i, /reminder/i, /remind me/i],
    response: {
      success: true,
      response: 'De acuerdo, he creado un recordatorio para ti. Te avisaré cuando sea el momento.',
      action: 'SET_REMINDER',
      params: { time: Date.now() + 3600000 },
      show: {
        type: 'text',
        title: 'Recordatorio creado',
        text: 'Te avisaré pronto',
      },
    },
  },

  // Noticias
  {
    patterns: [/noticias/i, /news/i, /novedades/i],
    response: {
      success: true,
      response: 'Aquí tienes las últimas noticias: La tecnología sigue avanzando con nuevos desarrollos en inteligencia artificial. El mercado tecnológico muestra tendencias positivas.',
      show: {
        type: 'list',
        title: 'Últimas Noticias',
        items: [
          { title: 'Avances en IA', subtitle: 'Nuevos modelos de lenguaje' },
          { title: 'Mercado Tech', subtitle: 'Tendencia positiva' },
          { title: 'Innovación', subtitle: 'Startups en crecimiento' },
        ],
      },
    },
  },

  // Búsqueda
  {
    patterns: [/busca/i, /buscar/i, /search/i, /encuentra/i],
    response: {
      success: true,
      response: 'He encontrado varios resultados relacionados con tu búsqueda. Aquí tienes algunos enlaces relevantes.',
      show: {
        type: 'links',
        title: 'Resultados de búsqueda',
        links: [
          { title: 'Wikipedia', url: 'https://wikipedia.org', description: 'Enciclopedia libre' },
          { title: 'Documentación', url: 'https://docs.example.com', description: 'Guías y tutoriales' },
        ],
      },
    },
  },

  // Música
  {
    patterns: [/música/i, /canción/i, /music/i, /play/i, /reproduce/i, /pon/i],
    response: {
      success: true,
      response: 'Reproduciendo música ahora. Disfruta de la selección.',
      action: 'PLAY_MUSIC',
      params: { playlist: 'favorites' },
      show: {
        type: 'card',
        title: 'Reproduciendo',
        text: 'Tu música favorita',
        imageUrl: 'https://example.com/music/playlist.png',
      },
    },
  },

  // Alarma
  {
    patterns: [/alarma/i, /despiértame/i, /alarm/i, /wake me/i],
    response: {
      success: true,
      response: 'Alarma configurada. Te despertaré a la hora indicada.',
      action: 'SET_ALARM',
      show: {
        type: 'text',
        title: 'Alarma configurada',
        text: 'Lista para despertarte',
      },
    },
  },

  // Chiste
  {
    patterns: [/chiste/i, /joke/i, /algo gracioso/i, /hazme reír/i],
    response: {
      success: true,
      response: '¿Por qué los programadores prefieren el frío? Porque odian los bugs del calor. ¡Ba dum tss!',
      show: {
        type: 'text',
        text: '🤖 ¡Ba dum tss!',
      },
    },
  },

  // Ayuda
  {
    patterns: [/ayuda/i, /help/i, /qué puedes hacer/i, /comandos/i],
    response: {
      success: true,
      response: 'Puedo ayudarte con varias cosas: consultar el clima, la hora, crear recordatorios, buscar información, reproducir música, configurar alarmas, y mucho más. Solo pregúntame lo que necesites.',
      show: {
        type: 'list',
        title: 'Puedo ayudarte con',
        items: [
          { title: 'Clima', subtitle: 'Consultar el tiempo' },
          { title: 'Hora y fecha', subtitle: 'Información temporal' },
          { title: 'Recordatorios', subtitle: 'Crear avisos' },
          { title: 'Búsquedas', subtitle: 'Encontrar información' },
          { title: 'Música', subtitle: 'Reproducir canciones' },
        ],
      },
    },
  },

  // Despedida
  {
    patterns: [/adiós/i, /adios/i, /hasta luego/i, /bye/i, /chao/i],
    response: {
      success: true,
      response: '¡Hasta luego! Fue un placer ayudarte. Vuelve cuando quieras.',
      endConversation: true,
      farewell: '¡Hasta luego!',
      show: {
        type: 'text',
        text: '👋 ¡Hasta pronto!',
      },
    },
  },
];

/**
 * Respuesta genérica cuando no hay coincidencia
 */
const defaultResponse: WorkflowResponse = {
  success: true,
  response: 'Entendido. Estoy procesando tu solicitud. ¿Hay algo más en lo que pueda ayudarte?',
  show: {
    type: 'text',
    text: '¿Algo más en lo que pueda ayudarte?',
  },
};

/**
 * Cliente mock para el sistema de automatización.
 * Simula respuestas sin necesidad de un servidor externo.
 */
export class MockWorkflowClient {
  private delay: number;

  constructor(delay = 500) {
    this.delay = delay;
  }

  /**
   * Simula el envío de un comando de voz y retorna una respuesta mock.
   */
  async sendVoiceCommand(
    text: string,
    sessionId?: string,
    context?: Record<string, unknown>,
    production = false
  ): Promise<WorkflowResponse> {
    // Simular latencia de red
    await this.simulateDelay();

    console.log(`[Mock] Processing: "${text}" (session: ${sessionId || 'none'})`);

    // Buscar respuesta que coincida con el texto
    for (const mock of mockResponses) {
      for (const pattern of mock.patterns) {
        if (pattern.test(text)) {
          console.log(`[Mock] Matched pattern: ${pattern}`);
          return { ...mock.response };
        }
      }
    }

    // Respuesta por defecto
    console.log('[Mock] No pattern matched, using default response');
    return { ...defaultResponse };
  }

  /**
   * Simula el envío de una notificación.
   */
  async sendNotification(
    app: string,
    title: string,
    text: string,
    production = false
  ): Promise<WorkflowResponse> {
    await this.simulateDelay();

    console.log(`[Mock] Notification from ${app}: ${title}`);

    // Simular que algunas notificaciones activan conversación
    const importantApps = ['whatsapp', 'telegram', 'gmail', 'calendar'];
    const isImportant = importantApps.some((a) => app.toLowerCase().includes(a));

    if (isImportant) {
      return {
        success: true,
        response: `Tienes una nueva notificación de ${app}: ${title}`,
        action: 'START_CONVERSATION',
        show: {
          type: 'card',
          title: app,
          text: title,
        },
      };
    }

    return {
      success: true,
      // No response = no action needed
    };
  }

  /**
   * Health check siempre retorna true en modo mock.
   */
  async healthCheck(): Promise<boolean> {
    return true;
  }

  private simulateDelay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, this.delay));
  }
}
