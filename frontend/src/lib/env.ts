import { z } from "zod";

/**
 * Validated, server-only configuration.
 *
 * Parsing the environment once at module load means a typo in a deployment variable fails fast at
 * startup instead of surfacing as a mysterious 500 on the first submission.
 */
const serverEnvSchema = z.object({
  API_BASE_URL: z.string().url().default("http://localhost:8080"),
  CLIENT_IP_HASH_SECRET: z.string().min(16).default("development-only-insecure-secret"),
  INQUIRY_FORWARD_TIMEOUT_MS: z.coerce.number().int().min(500).max(60000).default(10000),
  RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(1).max(1000).default(5),
  RATE_LIMIT_BURST: z.coerce.number().int().min(1).max(1000).default(2),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development")
});

const parsed = serverEnvSchema.safeParse({
  API_BASE_URL: process.env.API_BASE_URL,
  CLIENT_IP_HASH_SECRET: process.env.CLIENT_IP_HASH_SECRET,
  INQUIRY_FORWARD_TIMEOUT_MS: process.env.INQUIRY_FORWARD_TIMEOUT_MS,
  RATE_LIMIT_PER_MINUTE: process.env.RATE_LIMIT_PER_MINUTE,
  RATE_LIMIT_BURST: process.env.RATE_LIMIT_BURST,
  NODE_ENV: process.env.NODE_ENV
});

if (!parsed.success) {
  const details = parsed.error.issues
    .map((issue) => `${issue.path.join(".") || "(root)"}: ${issue.message}`)
    .join("; ");
  throw new Error(`Invalid server configuration: ${details}`);
}

export const serverEnv = parsed.data;

export const isProduction = serverEnv.NODE_ENV === "production";
