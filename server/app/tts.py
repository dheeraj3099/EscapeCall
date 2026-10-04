from __future__ import annotations

import os
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel


class SpeechRequest(BaseModel):
    input: str
    api_key: Optional[str] = None
    model: Optional[str] = None
    voice: Optional[str] = None
    speed: Optional[float] = 1.0
    sample_rate: int = 24000
    response_format: str = "pcm"
    instruction: Optional[str] = None
    app_id: Optional[str] = None


def create_tts_router() -> APIRouter:
    router = APIRouter(prefix="/tts")

    @router.post("/v1/audio/speech")
    async def speech(request: SpeechRequest) -> Response:
        if not request.input.strip():
            raise HTTPException(status_code=400, detail="input must not be empty")
        if request.sample_rate != 24000 or request.response_format.lower() != "pcm":
            raise HTTPException(status_code=400, detail="Custom TTS requires PCM16 audio at 24 kHz")

        shared_key = os.getenv("CUSTOM_TTS_API_KEY", "").strip()
        if shared_key and request.api_key != shared_key:
            raise HTTPException(status_code=401, detail="Invalid custom TTS key")

        elevenlabs_key = os.getenv("ELEVENLABS_API_KEY", "").strip()
        if not elevenlabs_key:
            raise HTTPException(status_code=503, detail="ELEVENLABS_API_KEY is not configured")

        voice_id = request.voice or os.getenv("ELEVENLABS_VOICE_ID", "uuyFQegRan8rpIQCGu9K")
        model_id = request.model or os.getenv("ELEVENLABS_MODEL_ID", "eleven_multilingual_v2")
        payload = {
            "text": request.input,
            "model_id": model_id,
            "voice_settings": {
                "stability": float(os.getenv("ELEVENLABS_STABILITY", "0.5")),
                "similarity_boost": float(os.getenv("ELEVENLABS_SIMILARITY_BOOST", "0.75")),
                "style": float(os.getenv("ELEVENLABS_STYLE", "0")),
                "use_speaker_boost": os.getenv("ELEVENLABS_SPEAKER_BOOST", "true").lower() == "true",
                "speed": float(request.speed or 1.0),
            },
        }
        output_format = "pcm_24000"
        url = f"https://api.elevenlabs.io/v1/text-to-speech/{voice_id}?output_format={output_format}"
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0)) as client:
            upstream = await client.post(
                url,
                json=payload,
                headers={"xi-api-key": elevenlabs_key, "Accept": "audio/pcm"},
            )
        if upstream.status_code >= 400:
            raise HTTPException(status_code=502, detail=f"ElevenLabs TTS failed: {upstream.text[:300]}")
        return Response(
            content=upstream.content,
            media_type="application/octet-stream",
            headers={"X-Audio-Sample-Rate": "24000"},
        )

    @router.get("/health")
    async def health() -> dict[str, str]:
        return {"status": "ok", "service": "elevenlabs-custom-tts"}

    return router
