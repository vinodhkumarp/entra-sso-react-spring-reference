/** Log levels supported by the browser logger, ordered from most to least verbose. */
export type LogLevel = "debug" | "info" | "warn" | "error";

/** Safe scalar fields accepted by a structured browser log event. */
export type LogContext = Readonly<Record<string, string | number | boolean | null | undefined>>;

/** Machine-readable event shape that can later be forwarded to an observability provider. */
export interface BrowserLogEvent {
  readonly timestamp: string;
  readonly level: LogLevel;
  readonly event: string;
  readonly context: Readonly<Record<string, string | number | boolean | null>>;
}

const PRIORITY: Readonly<Record<LogLevel, number>> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

const configuredMinimumLevel = minimumLevel();

/**
 * Central browser logger used by authentication and API modules.
 *
 * <p>Do not put tokens, secrets, authorization headers, or sensitive personal data in its context.
 * Production applications can replace the console sink here with Application Insights, OpenTelemetry,
 * or another approved telemetry transport without changing callers.</p>
 */
export const logger = {
  /** Writes detailed diagnostic information when debug logging is enabled. */
  debug(event: string, context: LogContext = {}): void {
    write("debug", event, context);
  },

  /** Writes a normal application lifecycle or request event. */
  info(event: string, context: LogContext = {}): void {
    write("info", event, context);
  },

  /** Writes a recoverable or client-side failure event. */
  warn(event: string, context: LogContext = {}): void {
    write("warn", event, context);
  },

  /** Writes an unexpected failure event without serializing an Error or its stack. */
  error(event: string, context: LogContext = {}): void {
    write("error", event, context);
  },
} as const;

/** Creates one opaque request ID shared by frontend and backend logs for an API call. */
export function createCorrelationId(): string {
  return crypto.randomUUID();
}

/** Returns an exception class name that is safe to record without a message or stack trace. */
export function errorType(error: unknown): string {
  return error instanceof Error ? error.name : "UnknownError";
}

/** Writes a structured event through the one approved browser-console boundary. */
function write(level: LogLevel, event: string, context: LogContext): void {
  if (PRIORITY[level] < PRIORITY[configuredMinimumLevel]) {
    return;
  }

  const cleanContext = Object.fromEntries(
    Object.entries(context).filter(
      (entry): entry is [string, string | number | boolean | null] => entry[1] !== undefined,
    ),
  );
  const logEvent: BrowserLogEvent = {
    timestamp: new Date().toISOString(),
    level,
    event,
    context: cleanContext,
  };

  if (level === "error") {
    console.error(logEvent);
  } else if (level === "warn") {
    console.warn(logEvent);
  } else if (level === "info") {
    console.info(logEvent);
  } else {
    console.debug(logEvent);
  }
}

/** Resolves VITE_LOG_LEVEL and falls back to debug locally and info in production. */
function minimumLevel(): LogLevel {
  const configured = import.meta.env.VITE_LOG_LEVEL?.trim().toLowerCase();
  if (configured && configured in PRIORITY) {
    return configured as LogLevel;
  }
  return import.meta.env.DEV ? "debug" : "info";
}
