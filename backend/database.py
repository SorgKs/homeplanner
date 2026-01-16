"""Database configuration and session management."""

from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

from backend.config import get_settings

settings = get_settings()

engine = create_engine(
    settings.database_url,
    connect_args={"check_same_thread": False} if "sqlite" in settings.database_url else {},
    echo=settings.debug,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    """Dependency for getting database session."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db():
    """Initialize database tables."""
    import logging
    logger = logging.getLogger("homeplanner.database")
    logger.info("Initializing database tables...")
    logger.info(f"Database URL: {settings.database_url}")
    try:
        Base.metadata.create_all(bind=engine)
        logger.info("Database tables created successfully")
        # Log available tables
        from sqlalchemy import inspect
        inspector = inspect(engine)
        tables = inspector.get_table_names()
        logger.info(f"Available tables after init: {tables}")
    except Exception as e:
        logger.error(f"Error initializing database: {e}", exc_info=True)

