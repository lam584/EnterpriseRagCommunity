package com.example.EnterpriseRagCommunity.entity.content.id;

import com.example.EnterpriseRagCommunity.entity.content.enums.PostTagSource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@Embeddable
public class PostTagsId implements Serializable {
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "tag_id")
    private Long tagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16)
    private PostTagSource source;
}
