proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $remote_addr;   # overwrite: clients must not be able to spoof throttle keys
proxy_set_header X-Forwarded-Proto https;
proxy_connect_timeout 10s; proxy_read_timeout 60s;
