# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Next.js marketing site (App Router, standalone output).
#
#   docker build --build-arg NEXT_PUBLIC_SITE_URL=https://example.com -t nk-ethical/pharma-web:1.0.0 .
#
# The runtime image starts the standalone server bundle, so it needs neither the
# full `node_modules` tree nor the build toolchain.
# ---------------------------------------------------------------------------

# --- dependencies ----------------------------------------------------------
FROM node:24-alpine AS deps

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

COPY package.json package-lock.json* ./
# `npm ci` needs a lock file; it is committed to the repository. The fallback
# keeps the image buildable from a checkout that has one missing.
RUN if [ -f package-lock.json ]; then \
        npm ci --no-audit --no-fund; \
    else \
        npm install --no-audit --no-fund; \
    fi

# --- build -----------------------------------------------------------------
FROM node:24-alpine AS build

WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

# NEXT_PUBLIC_* variables are inlined into the client bundle at build time.
ARG NEXT_PUBLIC_SITE_URL=http://localhost:3000
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL}

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN npm run build

# --- runtime ---------------------------------------------------------------
FROM node:24-alpine AS runtime

WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 --ingroup nodejs --home /app nextjs

# `output: "standalone"` emits a self-contained server plus the static assets.
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nodejs /app/public ./public

USER nextjs

EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=15s --retries=6 \
    CMD node -e "fetch('http://127.0.0.1:3000/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
