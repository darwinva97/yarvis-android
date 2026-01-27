import "@dotenvx/dotenvx/config";
import { generateText, stepCountIs } from "ai";
import { google } from "@ai-sdk/google";
import { tavilySearch, tavilyExtract } from "@tavily/ai-sdk";

const main = async () => {
  console.log("Generating text...");
  const { text, steps } = await generateText({
    model: google("gemini-3-pro-preview"),
    system: "Eres un asistente útil. Cuando uses herramientas de búsqueda, usa los resultados para responder la pregunta del usuario de forma concisa. Solo haz UNA búsqueda y luego responde.",
    prompt: "¿Quién es el actual presidente del perú?",
    tools: {
      tavilySearch: tavilySearch({ maxResults: 3 }),
    },
    stopWhen: stepCountIs(3),
  });

  console.log("Steps:", steps.length);
  console.log("Response:", text);
};

main();
