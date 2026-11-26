FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /build/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
COPY frontend/apps/web/package.json ./apps/web/
COPY frontend/apps/admin/package.json ./apps/admin/
COPY frontend/packages/ui/package.json ./packages/ui/
COPY frontend/packages/session/package.json ./packages/session/
COPY frontend/packages/api-client/package.json ./packages/api-client/
COPY frontend/packages/eslint-config/package.json ./packages/eslint-config/
COPY frontend/packages/typescript-config/package.json ./packages/typescript-config/
RUN --mount=type=cache,id=cloudlane-pnpm,target=/pnpm/store pnpm install --frozen-lockfile --store-dir=/pnpm/store
COPY frontend/ .
COPY docs/api/openapi.json /build/docs/api/openapi.json
ARG NPM_REGISTRY=https://registry.npmjs.org
ENV npm_config_registry=$NPM_REGISTRY
RUN pnpm check:web && pnpm check:admin && pnpm test && pnpm build:web && pnpm build:admin
FROM nginx:1.28-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/frontend/apps/web/dist /usr/share/nginx/html
COPY --from=build /build/frontend/apps/admin/dist /usr/share/nginx/html/admin
EXPOSE 80
