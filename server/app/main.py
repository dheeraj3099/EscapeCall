from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .agora_client import AgoraClient
from .config import Settings
from .routes import create_router
from .session_store import SessionStore
from .tts import create_tts_router


settings = Settings.from_env()
store = SessionStore()
agora = AgoraClient(settings)

app = FastAPI(title="EscapeCall Backend", version=settings.build_version)
app.add_middleware(
    CORSMiddleware,
    allow_origins=list(settings.allowed_origins),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(create_router(settings, agora, store))
app.include_router(create_tts_router())


@app.on_event("shutdown")
async def shutdown() -> None:
    await agora.close()
