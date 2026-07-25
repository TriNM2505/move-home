FROM nginx:alpine

# Custom server block (routing + caching for a multi-page static site)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Static site files (index.html, css/, js/, pages/, assets/)
COPY . /usr/share/nginx/html

EXPOSE 80