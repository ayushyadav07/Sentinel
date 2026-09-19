-- ADDED for the agentic flow: stores the agent's full final response (reasoning + JSON summary)
-- for audit/debugging, regardless of whether the structured JSON block parsed cleanly.
ALTER TABLE scan_run ADD COLUMN agent_transcript TEXT;