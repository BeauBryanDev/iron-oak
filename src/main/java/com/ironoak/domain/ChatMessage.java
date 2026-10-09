package com.ironoak.domain;

import com.ironoak.domain.enums.MessageRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    @NotNull
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "message_role")
    @NotNull
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    @NotNull
    private String content;

    // set only when role = TOOL
    @Column(name = "tool_name", length = 100)
    @Size(max = 100)
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(ChatSession chatSession,
            MessageRole role,
            String content,
            String toolName) {
        this.chatSession = chatSession;
        this.role = role;
        this.content = content;
        this.toolName = toolName;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getToolName() {
        return toolName;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setChatSession(ChatSession chatSession) {
        this.chatSession = chatSession;
    }

    public void setRole(MessageRole role) {
        this.role = role;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
