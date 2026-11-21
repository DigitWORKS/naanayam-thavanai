# syntax=docker/dockerfile:1
# Nginx serving the Angular build and proxying /v1 to the API (same origin, no CORS).
FROM node:22-alpine AS build
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY web/ ./
RUN npx ng build

FROM nginx:1.27-alpine
COPY --from=build /web/dist/naanayam-web/browser /usr/share/nginx/html
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
