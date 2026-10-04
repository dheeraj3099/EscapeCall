from __future__ import annotations

import time
import uuid
import random
from typing import Any

import httpx

from .config import Settings


class AgoraClient:
    """Backend wrapper for Agora's published Conversational AI agent."""

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._sessions: dict[str, Any] = {}
        self._client = None

    def _ensure_client(self):
        if self._client is None:
            from agora_agent import Area, AsyncAgora

            self._client = AsyncAgora(
                area=Area.US,
                app_id=self.settings.agora_app_id,
                app_certificate=self.settings.agora_app_certificate,
            )
        return self._client

    def create_session_tokens(self, *, persona_name: str, relationship: str, demo_mode: bool) -> dict[str, Any]:
        self.settings.validate_for_tokens()
        from agora_agent.agentkit.token import generate_convo_ai_token

        channel_name = f"escape-{uuid.uuid4().hex[:16]}"
        requester_uid = int(str(int(time.time() * 1000))[-8:])
        user_token = generate_convo_ai_token(
            app_id=self.settings.agora_app_id,
            app_certificate=self.settings.agora_app_certificate,
            channel_name=channel_name,
            uid=requester_uid,
            token_expire=self.settings.token_expiry_seconds,
        )
        agent_uid = random.randint(10_000_000, 99_999_999)
        while agent_uid == requester_uid:
            agent_uid = random.randint(10_000_000, 99_999_999)
        return {
            "app_id": self.settings.agora_app_id,
            "channel_name": channel_name,
            "requester_rtc_uid": requester_uid,
            "requester_rtm_user_id": str(requester_uid),
            "agent_rtc_uid": agent_uid,
            "rtc_token": user_token,
            "rtm_token": user_token,
            "expires_at_unix": int(time.time()) + self.settings.token_expiry_seconds,
            "persona_name": persona_name,
            "relationship": relationship,
            "demo_mode": demo_mode,
        }

    async def start_agent(
        self,
        *,
        channel_name: str,
        requester_rtc_uid: int,
        agent_rtc_uid: int,
        system_prompt: str,
        persona_name: str,
        relationship: str,
        demo_mode: bool,
    ) -> dict[str, Any]:
        self.settings.validate_for_agent()
        if not self.settings.agent_name or not self.settings.agent_pipeline_id:
            raise ValueError("AGORA_AGENT_NAME and AGORA_AGENT_PIPELINE_ID are required.")
        if not self.settings.agora_api_key or not self.settings.agora_api_secret:
            raise ValueError("AGORA_API_KEY and AGORA_API_SECRET are required.")

        from agora_agent.agentkit.token import generate_convo_ai_token

        agent_token = generate_convo_ai_token(
            app_id=self.settings.agora_app_id,
            app_certificate=self.settings.agora_app_certificate,
            channel_name=channel_name,
            uid=agent_rtc_uid,
            token_expire=self.settings.token_expiry_seconds,
        )
        payload = {
            "name": self.settings.agent_name,
            "pipeline_id": self.settings.agent_pipeline_id,
            "properties": {
                "channel": channel_name,
                "agent_rtc_uid": str(agent_rtc_uid),
                # Each EscapeCall session uses a fresh channel with one user.
                # This matches Agora's Embed Agent payload and avoids a UID
                # type mismatch when a published pipeline is configured for all
                # remote participants.
                "remote_rtc_uids": ["*"],
                "token": agent_token,
            },
        }
        auth = (self.settings.agora_api_key, self.settings.agora_api_secret)
        url = f"{self.settings.agora_api_base_url}/projects/{self.settings.agora_app_id}/join"
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(url, json=payload, auth=auth)
        if response.is_error:
            raise RuntimeError(f"Agora agent start failed ({response.status_code}): {response.text[:500]}")
        result = response.json()
        agent_id = str(result.get("agent_id", "")).strip()
        if not agent_id:
            raise RuntimeError(f"Agora agent start returned no agent_id: {result}")
        print(f"[EscapeCall] Agora published agent started: agent_id={agent_id}, channel={channel_name}", flush=True)
        return {"agent_id": agent_id, "status": "started", "created_at_unix": int(time.time())}

    async def stop_agent(self, *, agent_id: str, channel_name: str) -> dict[str, Any]:
        url = f"{self.settings.agora_api_base_url}/projects/{self.settings.agora_app_id}/agents/{agent_id}/leave"
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(url, auth=(self.settings.agora_api_key, self.settings.agora_api_secret))
        if response.is_error:
            raise RuntimeError(f"Agora agent stop failed ({response.status_code}): {response.text[:500]}")
        return {"success": True}

    async def interrupt_agent(self, *, agent_id: str, channel_name: str) -> dict[str, Any]:
        # The published pipeline owns interruption and turn-taking behavior.
        return {"success": True}

    async def close(self) -> None:
        self._sessions.clear()
