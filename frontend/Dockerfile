FROM node:24-alpine AS build
RUN corepack enable
WORKDIR /build/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
COPY frontend/apps/web/package.json ./apps/web/
COPY frontend/apps/admin/package.json ./apps/admin/
COPY frontend/apps/mobile/package.json ./apps/mobile/
COPY frontend/packages/ui/package.json ./packages/ui/
COPY frontend/packages/session/package.json ./packages/session/
COPY frontend/packages/drive-core/package.json ./packages/drive-core/
COPY frontend/packages/api-client/package.json ./packages/api-client/
COPY frontend/packages/eslint-config/package.json ./packages/eslint-config/
COPY frontend/packages/typescript-config/package.json ./packages/typescript-config/
RUN --mount=type=cache,id=cloudlane-pnpm,target=/pnpm/store pnpm install --frozen-lockfile --store-dir=/pnpm/store
COPY frontend/ .
COPY docs/api/openapi.json /build/docs/api/openapi.json
ARG NPM_REGISTRY=https://registry.npmjs.org
ENV npm_config_registry=$NPM_REGISTRY
RUN pnpm check:web && pnpm check:admin && pnpm check:mobile && pnpm test && pnpm build:web && pnpm build:admin && pnpm build:mobile
FROM nginx:1.28-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/frontend/apps/web/dist /usr/share/nginx/html
COPY --from=build /build/frontend/apps/admin/dist /usr/share/nginx/html/admin
COPY --from=build /build/frontend/apps/mobile/dist /usr/share/nginx/html/m
EXPOSE 80
