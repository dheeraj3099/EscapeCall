from __future__ import annotations

import time
from typing import Any, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from .agora_client import AgoraClient
from .session_store import SessionRecord, SessionStore


class TokenRequest(BaseModel):
    persona_name: str = "Mom"
    relationship: str = "Family"
    demo_mode: bool = False


class AgentStartRequest(BaseModel):
    channel_name: str
    requester_rtc_uid: int
    requester_rtm_user_id: str = ""
    agent_rtc_uid: int
    persona_name: str
    relationship: str
    system_prompt: str
    demo_mode: bool = False


class AgentActionRequest(BaseModel):
    agent_id: str
    channel_name: str


class EmergencyAlertRequest(BaseModel):
    source: str = "agora-custom-tool"
    reason: str = ""
    transcript_text: str = ""
    persona_name: str = ""
    contact_name: str = ""
    codeword: str = ""
    delivery: str = "tool_received"
    message: str = ""
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    channel_name: Optional[str] = None
    metadata: dict[str, Any] = Field(default_factory=dict)


def create_router(settings: Any, agora: AgoraClient, store: SessionStore) -> APIRouter:
    router = APIRouter()

    @router.get("/health")
    async def health() -> dict[str, Any]:
        return {"status": "ok", "backend_version": settings.build_version, "active_sessions": store.count()}

    @router.post("/v1/agora/token")
    async def token(body: TokenRequest) -> dict[str, Any]:
        try:
            return agora.create_session_tokens(
                persona_name=body.persona_name,
                relationship=body.relationship,
                demo_mode=body.demo_mode,
            )
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc

    @router.post("/v1/agora/agent/start")
    async def start_agent(body: AgentStartRequest) -> dict[str, Any]:
        try:
            result = await agora.start_agent(
                channel_name=body.channel_name,
                requester_rtc_uid=body.requester_rtc_uid,
                agent_rtc_uid=body.agent_rtc_uid,
                system_prompt=body.system_prompt,
                persona_name=body.persona_name,
                relationship=body.relationship,
                demo_mode=body.demo_mode,
            )
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc
        agent_id = str(result.get("agent_id", "")).strip()
        if not agent_id:
            raise HTTPException(status_code=502, detail="Agora did not return an agent ID.")
        store.put(SessionRecord(
            channel_name=body.channel_name,
            requester_rtc_uid=body.requester_rtc_uid,
            requester_rtm_user_id=body.requester_rtm_user_id,
            agent_id=agent_id,
            created_at_unix=int(result.get("created_at_unix") or time.time()),
        ))
        return {"agent_id": agent_id, "status": result.get("status", "started"), "created_at_unix": int(result.get("created_at_unix") or time.time())}

    @router.post("/v1/agora/agent/stop")
    async def stop_agent(body: AgentActionRequest) -> dict[str, Any]:
        try:
            result = await agora.stop_agent(agent_id=body.agent_id, channel_name=body.channel_name)
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc
        store.remove(body.channel_name)
        return result

    @router.post("/v1/agora/agent/interrupt")
    async def interrupt_agent(body: AgentActionRequest) -> dict[str, Any]:
        try:
            return await agora.interrupt_agent(agent_id=body.agent_id, channel_name=body.channel_name)
        except (RuntimeError, ValueError) as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @router.post("/v1/emergency/alert-emergency-contact")
    async def alert_emergency_contact(body: EmergencyAlertRequest) -> dict[str, Any]:
        event = store.record_alert(body.model_dump())
        print(f"[EscapeCall] emergency alert received: {event}", flush=True)
        return {"success": True, "status": "received", "received_at": event["received_at"]}

    return router
