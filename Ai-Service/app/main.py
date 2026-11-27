
from fastapi import FastAPI

from app.api import health
from app.core.config import settings


app = FastAPI(
    title=settings.app_name,
    description="AI-powered resource search and assistant service",
    version="1.0.0",
    debug=settings.debug
)


@app.get("/")
def home():
    return {
        "message": f"{settings.app_name} is running!"
    }


app.include_router(health.router)
