# The web server: Caddy serving the built web app and proxying /api to the backend, with automatic HTTPS.
# Built by deploy/compose.yaml with the repository root as context.

FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /src/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM caddy:2-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /src/frontend/dist /srv/www
