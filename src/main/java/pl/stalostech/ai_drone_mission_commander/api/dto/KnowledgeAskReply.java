package pl.stalostech.ai_drone_mission_commander.api.dto;

public record KnowledgeAskReply(ChatReply answer, RagContext retrieval) {}
