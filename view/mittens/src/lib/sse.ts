// Simple Server-Sent Events (SSE) broadcaster for Next.js Route Handlers
// Keeps a registry of connected clients and allows broadcasting messages.

type Client = {
  id: number;
  send: (payload: string) => void;
};

const clients = new Set<Client>();
let clientIdSeq = 1;

export function addClient(send: (payload: string) => void): number {
  const id = clientIdSeq++;
  clients.add({ id, send });
  return id;
}

export function removeClient(id: number) {
  for (const c of clients) {
    if (c.id === id) {
      clients.delete(c);
      break;
    }
  }
}

export function broadcast(event: string, data?: any) {
  const payload = data !== undefined ? JSON.stringify(data) : "";
  const message = `event: ${event}\n` + (payload ? `data: ${payload}\n` : "") + "\n";
  for (const c of clients) {
    try {
      c.send(message);
    } catch (_) {
      // ignore individual client errors
    }
  }
}

