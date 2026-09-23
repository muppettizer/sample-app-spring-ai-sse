package com.example.chat.tools;

import com.example.chat.web.SseEvents;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * Placeholder standing in for the real AG Grid AI toolkit's tool beans.
 * Swap this component out (and the constructor param type in
 * GridUpdateHandler) for whatever the actual toolkit exposes -- these three
 * methods just illustrate the shape: the model calls a tool, the tool builds
 * a structured grid-command object, and pushes it as a "grid" SSE event
 * (via the same ToolContext progress-sink pattern as ExternalApiTools) for
 * Angular to apply via the AG Grid API as soon as it resolves.
 */
@Component
public class GridTools {

    public static final String PROGRESS_SINK_KEY = "progressSink";

    @Tool(description = "Apply a column filter to the grid")
    public Map<String, Object> applyFilter(String column, String filterType, String filterValue, ToolContext toolContext) {
        Map<String, Object> command = Map.of(
                "action", "applyFilter",
                "column", column,
                "filterType", filterType,
                "filterValue", filterValue
        );
        emitGrid(toolContext, command);
        return command;
    }

    @Tool(description = "Sort the grid by a column")
    public Map<String, Object> applySort(String column, String direction, ToolContext toolContext) {
        Map<String, Object> command = Map.of("action", "applySort", "column", column, "direction", direction);
        emitGrid(toolContext, command);
        return command;
    }

    @Tool(description = "Update a cell value in the currently selected row")
    public Map<String, Object> updateSelectedRowCell(String column, String value, ToolContext toolContext) {
        Map<String, Object> command = Map.of("action", "updateCell", "column", column, "value", value);
        emitGrid(toolContext, command);
        return command;
    }

    @SuppressWarnings("unchecked")
    private void emitGrid(ToolContext toolContext, Map<String, Object> command) {
        Object raw = toolContext.getContext().get(PROGRESS_SINK_KEY);
        if (raw instanceof Sinks.Many<?> sink) {
            ((Sinks.Many<ServerSentEvent<Object>>) sink).tryEmitNext(SseEvents.grid(command));
        }
    }
}
