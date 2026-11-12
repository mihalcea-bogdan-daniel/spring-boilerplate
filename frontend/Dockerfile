FROM node:22-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci --ignore-scripts
COPY . .
RUN npx nuxi prepare && npm run build

FROM node:22-alpine
WORKDIR /app
COPY --from=build /src/.output ./.output
ENV HOST=0.0.0.0 PORT=3000 NODE_ENV=production
USER node
EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
