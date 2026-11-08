FROM python:3.12.10-alpine3.21
WORKDIR /app
COPY dev/fraud-stub/server.py server.py
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD python -c "from urllib.request import urlopen; urlopen('http://127.0.0.1:8080/health', timeout=3)"
ENTRYPOINT ["python", "-u", "/app/server.py"]
