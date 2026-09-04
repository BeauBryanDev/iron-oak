package com.ironoak.domain;

import jakarta.persistence.*;

/**
 * The 87 classes the vision model predicts. model_label must match the values in
 * ml/idx_to_class.json exactly, or a classification cannot be resolved to a catalog row.
 *
 * The synonyms TEXT[] column is deliberately not mapped yet - it needs an explicit
 * array type mapping, and nothing in the boot check depends on it.
 */
@Entity
@Table(name = "tool_category")
public class ToolCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_label", nullable = false, unique = true, length = 100)
    private String modelLabel;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

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
}
