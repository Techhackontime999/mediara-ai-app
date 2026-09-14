FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

COPY backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

COPY backend/ /app/

EXPOSE 8000

# Run migrations, bootstrap the first superuser from DJANGO_SUPERUSER_* env
# vars (no-op when those are unset), then start the ASGI server (Daphne for
# Channels WebSockets).
CMD ["sh", "-c", "python manage.py migrate && python manage.py ensure_superuser && daphne -b 0.0.0.0 -p 8000 config.asgi:application"]