"""Optional MongoDB client for auxiliary data (analytics, counters, documents).

Django's ORM does not run on MongoDB — core relational data (accounts,
mediations, agreements, audit) always lives in the configured ORM database
(PostgreSQL recommended in production). MongoDB is available as a *secondary*
store for unstructured high-volume data.

Enable by setting ``MONGO_URL`` (and optional ``MONGO_DB``); the helpers below
return ``None`` when disabled so callers degrade gracefully. See
docs/DATABASE.md for provider examples (Atlas, local, DigitalOcean).
"""

import logging

from django.conf import settings

logger = logging.getLogger("django")

_client = None
_dbname = None


def get_mongo_client():
    """Return a cached :class:`pymongo.MongoClient`, or None if disabled."""
    global _client
    url = getattr(settings, "MONGO_URL", "") or ""
    if not url:
        return None
    if _client is None:
        from pymongo import MongoClient

        _client = MongoClient(url, serverSelectionTimeoutMS=5000)
        logger.info("MongoDB client configured for %s", settings.MONGO_DB)
    return _client


def get_mongo_db():
    """Return the configured MongoDB database handle, or None if disabled."""
    global _dbname
    client = get_mongo_client()
    if client is None:
        return None
    if _dbname is None:
        _dbname = client[(getattr(settings, "MONGO_DB", "") or "mediara")]
    return _dbname


def mongo_available() -> bool:
    """True when MongoDB is configured *and* reachable."""
    db = get_mongo_db()
    if db is None:
        return False
    try:
        db.client.admin.command("ping", serverSelectionTimeoutMS=2000)
    except Exception:  # noqa: BLE001 - connectivity probe, degrade gracefully
        return False
    return True
