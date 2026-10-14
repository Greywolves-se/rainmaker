# syntax=docker/dockerfile:1
# Mission Control web image: the Vite build served by nginx as a non-root user.
# Build context: repository root, the same as the collector image.
#   docker build -f apps/mission-control/web/Dockerfile .

FROM node:22-alpine AS build
WORKDIR /src
COPY apps/mission-control/web/package.json apps/mission-control/web/package-lock.json ./
RUN npm ci
COPY apps/mission-control/web/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY apps/mission-control/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
