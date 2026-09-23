import { Injectable } from '@angular/core';

export type PromptType = 'GRID_UPDATE' | 'TOOL_CHAIN' | 'GENERAL';

export interface GridContext {
  gridSchema: string;
  gridState: string;
  selectedRow: string | null;
}

export interface ChatRequest {
  sessionId: string;
  message: string;
  type: PromptType;
  gridContext?: GridContext | null;
}

export interface ChatStreamHandlers {
  onMessage: (text: string) => void;
  onTool: (tool: string, status: 'start' | 'done') => void;
  onGrid: (command: Record<string, unknown>) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

/**
 * Talks to POST /chat/stream, which streams Server-Sent Events
 * (event: message | tool | grid | done | error). We use fetch + a
 * ReadableStream reader -- not EventSource -- because EventSource only
 * supports GET and this endpoint needs a JSON body.
 */
@Injectable({ providedIn: 'root' })
export class ChatApiService {

  private readonly endpoint = '/chat/stream';

  streamChat(req: ChatRequest, handlers: ChatStreamHandlers): AbortController {
    const controller = new AbortController();

    fetch(this.endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
      signal: controller.signal,
    })
      .then(async response => {
        if (!response.ok || !response.body) {
          handlers.onError(`Request failed: ${response.status}`);
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          // SSE frames are separated by a blank line
          const frames = buffer.split('\n\n');
          buffer = frames.pop() ?? '';

          for (const frame of frames) {
            this.dispatchFrame(frame, handlers);
          }
        }
      })
      .catch(err => {
        if (err?.name !== 'AbortError') {
          handlers.onError(err?.message ?? 'Stream failed');
        }
      });

    return controller; // caller can .abort() to cancel an in-flight stream
  }

  private dispatchFrame(frame: string, handlers: ChatStreamHandlers): void {
    const eventLine = frame.split('\n').find(l => l.startsWith('event:'));
    const dataLine = frame.split('\n').find(l => l.startsWith('data:'));
    if (!eventLine || !dataLine) return;

    const event = eventLine.replace('event:', '').trim();
    let data: any;
    try {
      data = JSON.parse(dataLine.replace('data:', '').trim());
    } catch {
      return;
    }

    switch (event) {
      case 'message': handlers.onMessage(data.text ?? ''); break;
      case 'tool': handlers.onTool(data.tool, data.status); break;
      case 'grid': handlers.onGrid(data); break;
      case 'done': handlers.onDone(); break;
      case 'error': handlers.onError(data.message ?? 'Unknown error'); break;
    }
  }
}
