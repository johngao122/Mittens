import type { NextRequest } from 'next/server';
import { addClient, removeClient } from '@/lib/sse';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(_req: NextRequest) {
  const encoder = new TextEncoder();
  let id = 0;
  let keepAlive: ReturnType<typeof setInterval> | undefined;

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const send = (msg: string) => controller.enqueue(encoder.encode(msg));

      // Send initial comment to establish the stream
      send(`: connected\n\n`);

      id = addClient((payload: string) => {
        send(payload);
      });

      // Keep-alive pings (some proxies/timeouts need this)
      keepAlive = setInterval(() => {
        send(`: ping\n\n`);
      }, 25000);
    },
    cancel() {
      if (keepAlive) clearInterval(keepAlive);
      if (id) removeClient(id);
    }
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      // CORS for local dev across hosts if needed
      // 'Access-Control-Allow-Origin': '*',
    },
  });
}
