# Build stage
FROM node:20-alpine AS build
WORKDIR /workspace

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate

# Copy package files. The images build the workspace, not just the frontend:
# the app depends on @zynema/api-contracts, so the lockfile install happens
# with both manifests present.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY frontend/package.json ./frontend/
COPY libs/api-contracts/package.json ./libs/api-contracts/

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source
COPY frontend ./frontend
COPY libs ./libs

# Build
WORKDIR /workspace/frontend
RUN pnpm run build

# Runtime stage
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
COPY infra/nginx/frontend.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# 127.0.0.1, not localhost: BusyBox wget resolves localhost to ::1 first and
# nginx listens on IPv4 only, which reads as a healthy server being down.
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
