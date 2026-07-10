# Build stage
FROM node:20-alpine AS build
WORKDIR /workspace

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate

# Copy package files
COPY package.json pnpm-lock.yaml* pnpm-workspace.yaml* ./
COPY frontend/package.json ./frontend/
COPY libs/api-contracts/package.json* ./libs/api-contracts/ 2>/dev/null || true

# Install dependencies
RUN pnpm install --frozen-lockfile

# Copy source
COPY frontend ./frontend
COPY libs ./libs 2>/dev/null || true

# Build
WORKDIR /workspace/frontend
RUN pnpm run build

# Runtime stage
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/dist /usr/share/nginx/html
COPY infra/nginx/frontend.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
