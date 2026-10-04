from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Optional


@dataclass
class SessionRecord:
    channel_name: str
    requester_rtc_uid: int
    requester_rtm_user_id: str
    agent_id: str = ""
    created_at_unix: int = 0


class SessionStore:
    """Process-local session state; no user data is persisted by the backend."""

    def __init__(self) -> None:
        self._sessions: dict[str, SessionRecord] = {}
        self.alert_events: list[dict[str, Any]] = []

    def put(self, record: SessionRecord) -> None:
        self._sessions[record.channel_name] = record

    def get(self, channel_name: str) -> Optional[SessionRecord]:
        return self._sessions.get(channel_name)

    def set_agent(self, channel_name: str, agent_id: str) -> None:
        record = self._sessions.get(channel_name)
        if record:
            record.agent_id = agent_id

    def remove(self, channel_name: str) -> None:
        self._sessions.pop(channel_name, None)

    def count(self) -> int:
        return len(self._sessions)

    def record_alert(self, payload: dict[str, Any]) -> dict[str, Any]:
        event = {
            "received_at": datetime.now(timezone.utc).isoformat(),
            **payload,
        }
        self.alert_events.append(event)
        self.alert_events[:] = self.alert_events[-100:]
        return event
