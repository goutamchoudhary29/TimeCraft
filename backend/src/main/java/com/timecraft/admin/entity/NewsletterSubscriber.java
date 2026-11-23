package com.timecraft.admin.entity;

import com.timecraft.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "newsletter_subscribers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriber extends CreatedEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
