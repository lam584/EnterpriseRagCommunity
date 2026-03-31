package com.example.EnterpriseRagCommunity.dto.access;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Update request for current user's profile.
 *
 * Maps to:
 * - users.username
 * - users.metadata.profile.{avatarUrl,bio,location,website}
 */
@Data
public class UpdateMyProfileRequest {

    @Size(max = 64, message = "username长度不能超过64")
    private String username;

    @JsonIgnore
    private boolean avatarUrlPresent;

    @Size(max = 191, message = "avatarUrl长度不能超过191")
    private String avatarUrl;

    @JsonIgnore
    private boolean bioPresent;

    @Size(max = 500, message = "bio长度不能超过500")
    private String bio;

    @JsonIgnore
    private boolean locationPresent;

    @Size(max = 64, message = "location长度不能超过64")
    private String location;

    @JsonIgnore
    private boolean websitePresent;

    @Size(max = 191, message = "website长度不能超过191")
    private String website;

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrlPresent = true;
        this.avatarUrl = avatarUrl;
    }

    public void setBio(String bio) {
        this.bioPresent = true;
        this.bio = bio;
    }

    public void setLocation(String location) {
        this.locationPresent = true;
        this.location = location;
    }

    public void setWebsite(String website) {
        this.websitePresent = true;
        this.website = website;
    }

}
