from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


def _load_dotenv() -> None:
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    root = Path(__file__).resolve().parents[1]
    load_dotenv(root / ".env.local")
    load_dotenv(root / ".env")


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def _env_int(name: str, default: int) -> int:
    raw = _env(name)
    if not raw:
        return default
    return int(raw)


@dataclass(frozen=True)
class Settings:
    agora_app_id: str
    agora_app_certificate: str
    agora_api_key: str
    agora_api_secret: str
    agora_api_base_url: str
    agent_uid: int
    token_expiry_seconds: int
    session_ttl_seconds: int
    public_base_url: str
    agent_name: str
    agent_pipeline_id: str
    llm_url: str
    llm_api_key: str
    llm_model: str
    asr_vendor: str
    asr_language: str
    tts_vendor: str
    tts_params: dict[str, Any]
    custom_tts_url: str
    custom_tts_api_key: str
    custom_tts_app_id: str
    custom_tts_model: str
    custom_tts_voice: str
    allowed_origins: tuple[str, ...]
    build_version: str = "0.1.0"

    @classmethod
    def from_env(cls) -> "Settings":
        _load_dotenv()
        allowed_origins = tuple(
            origin.strip()
            for origin in _env("ALLOWED_ORIGINS", "*").split(",")
            if origin.strip()
        )
        tts_params_raw = _env("TTS_PARAMS_JSON", "{}")
        try:
            tts_params = json.loads(tts_params_raw)
        except json.JSONDecodeError as exc:
            raise ValueError("TTS_PARAMS_JSON must be valid JSON.") from exc
        if not isinstance(tts_params, dict):
            raise ValueError("TTS_PARAMS_JSON must decode to a JSON object.")

        return cls(
            agora_app_id=_env("AGORA_APP_ID"),
            agora_app_certificate=_env("AGORA_APP_CERTIFICATE"),
            agora_api_key=_env("AGORA_API_KEY"),
            agora_api_secret=_env("AGORA_API_SECRET"),
            agora_api_base_url=_env(
                "AGORA_API_BASE_URL",
                "https://api.agora.io/api/conversational-ai-agent/v2",
            ).rstrip("/"),
            agent_uid=_env_int("AGORA_AGENT_UID", 1000),
            token_expiry_seconds=_env_int("TOKEN_EXPIRY_SECONDS", 3600),
            session_ttl_seconds=_env_int("SESSION_TTL_SECONDS", 7200),
            public_base_url=_env("PUBLIC_BASE_URL").rstrip("/"),
            agent_name=_env("AGORA_AGENT_NAME"),
            agent_pipeline_id=_env("AGORA_AGENT_PIPELINE_ID"),
            llm_url=_env("LLM_URL", "https://api.openai.com/v1/chat/completions"),
            llm_api_key=_env("LLM_API_KEY"),
            llm_model=_env("LLM_MODEL", "gpt-4o-mini"),
            asr_vendor=_env("ASR_VENDOR", "ares"),
            asr_language=_env("ASR_LANGUAGE", "en-US"),
            tts_vendor=_env("TTS_VENDOR", "minimax"),
            tts_params=tts_params,
            custom_tts_url=_env("CUSTOM_TTS_URL"),
            custom_tts_api_key=_env("CUSTOM_TTS_API_KEY"),
            custom_tts_app_id=_env("CUSTOM_TTS_APP_ID"),
            custom_tts_model=_env("CUSTOM_TTS_MODEL", "custom-tts"),
            custom_tts_voice=_env("CUSTOM_TTS_VOICE", "Rohan"),
            allowed_origins=allowed_origins or ("*",),
        )

    def validate_for_tokens(self) -> None:
        missing = [
            name
            for name, value in (
                ("AGORA_APP_ID", self.agora_app_id),
                ("AGORA_APP_CERTIFICATE", self.agora_app_certificate),
            )
            if not value
        ]
        if missing:
            raise ValueError(f"Missing required token settings: {', '.join(missing)}")

    def validate_for_agent(self) -> None:
        self.validate_for_tokens()
