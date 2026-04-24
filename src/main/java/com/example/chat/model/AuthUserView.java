package com.example.chat.model;

import lombok.Data;

/**
 * Join result used when resolving a session token back to the current user.
 */
@Data
public class AuthUserView {

    private Long id;
    private String accountName;
    private Integer points;
}
