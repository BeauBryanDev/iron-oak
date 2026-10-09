package com.ironoak.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "tool_category")
public class ToolCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_label", nullable = false, unique = true, length = 100)
    @NotBlank
    @Size(max = 100)
    private String modelLabel;

    @Column(name = "display_name", nullable = false, length = 150)
    @NotBlank
    @Size(max = 150)
    private String displayName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> synonyms;

    @Column(columnDefinition = "text")
    private String description;

    protected ToolCategory() {
    }

    public ToolCategory(String modelLabel,
            String displayName,
            List<String> synonyms,
            String description) {
        this.modelLabel = modelLabel;
        this.displayName = displayName;
        this.synonyms = synonyms;
        this.description = description;
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setModelLabel(String modelLabel) {
        this.modelLabel = modelLabel;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setSynonyms(List<String> synonyms) {
        this.synonyms = synonyms;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
