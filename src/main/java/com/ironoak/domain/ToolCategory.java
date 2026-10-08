package com.ironoak.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "tool_category")
public class ToolCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_label", nullable = false,
     unique = true, length = 100)
    private String modelLabel;

    @Column(name = "display_name", 
    nullable = false, length = 150)
    private String displayName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> synonyms;

    @Column(columnDefinition = "text")
    private String description;

    protected ToolCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getModelLabel() {
        return modelLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public String getDescription() {
        return description;
    }
}
