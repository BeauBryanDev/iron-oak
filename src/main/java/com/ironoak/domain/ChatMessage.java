package com.ironoak.domain;

import com.ironoak.domain.enums.MessageRole;
import jakarta.persistence.*;
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
    private ChatSession chatSession;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false, columnDefinition = "message_role")
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // set only when role = TOOL
    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ChatMessage() {
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
}
