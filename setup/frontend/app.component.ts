import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatApiService, ChatRequest, PromptType } from './services/chat-api.service';

interface ToolStatus {
  tool: string;
  status: 'start' | 'done';
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h1>Chat</h1>

      <label>
        Prompt type
        <select [(ngModel)]="promptType">
          <option value="TOOL_CHAIN">Tool chain (REST APIs)</option>
          <option value="GRID_UPDATE">Grid update</option>
          <option value="GENERAL">General</option>
        </select>
      </label>

      <textarea [(ngModel)]="message" rows="3" placeholder="Type a message..."></textarea>
      <button (click)="send()" [disabled]="sending">{{ sending ? 'Sending…' : 'Send' }}</button>

      <div class="tools" *ngIf="toolStatuses.length">
        <div class="tool-chip" *ngFor="let t of toolStatuses">
          {{ t.tool }} — {{ t.status === 'start' ? 'calling…' : 'done' }}
        </div>
      </div>

      <div class="answer" *ngIf="answer">{{ answer }}</div>

      <div class="grid-commands" *ngIf="gridCommands.length">
        <h3>Grid commands received</h3>
        <pre *ngFor="let c of gridCommands">{{ c | json }}</pre>
      </div>

      <div class="error" *ngIf="error">{{ error }}</div>
    </div>
  `,
  styles: [`
    .container { max-width: 640px; margin: 40px auto; padding: 0 16px; }
    textarea { width: 100%; box-sizing: border-box; margin: 12px 0; }
    select { margin-left: 8px; }
    button { padding: 8px 16px; }
    .tools { margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
    .tool-chip { background: #e8e8ed; border-radius: 12px; padding: 4px 10px; font-size: 13px; }
    .answer { margin-top: 16px; white-space: pre-wrap; background: white; padding: 12px; border-radius: 8px; }
    .grid-commands pre { background: #1e1e1e; color: #d4d4d4; padding: 8px; border-radius: 6px; }
    .error { margin-top: 12px; color: #c0392b; }
  `]
})
export class AppComponent {
  promptType: PromptType = 'TOOL_CHAIN';
  message = '';
  sending = false;
  answer = '';
  error = '';
  toolStatuses: ToolStatus[] = [];
  gridCommands: Record<string, unknown>[] = [];

  private readonly sessionId = crypto.randomUUID();

  constructor(private chatApi: ChatApiService) {}

  send(): void {
    if (!this.message.trim() || this.sending) return;

    this.sending = true;
    this.answer = '';
    this.error = '';
    this.toolStatuses = [];
    this.gridCommands = [];

    const req: ChatRequest = {
      sessionId: this.sessionId,
      message: this.message,
      type: this.promptType,
      // Wire this up to your actual AG Grid instance (gridApi.getColumnDefs(),
      // gridApi.getFilterModel()/getSortModel(), the selected row, etc.)
      gridContext: this.promptType === 'GRID_UPDATE'
        ? { gridSchema: '[]', gridState: '{}', selectedRow: null }
        : null,
    };

    this.chatApi.streamChat(req, {
      onMessage: text => (this.answer += text),
      onTool: (tool, status) => this.toolStatuses.push({ tool, status }),
      onGrid: command => this.gridCommands.push(command),
      onDone: () => (this.sending = false),
      onError: message => {
        this.error = message;
        this.sending = false;
      },
    });
  }
}
