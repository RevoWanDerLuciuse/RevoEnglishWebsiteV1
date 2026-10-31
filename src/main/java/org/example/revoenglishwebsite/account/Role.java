package org.example.revoenglishwebsite.account;

/**
 * Account type. Roles are assigned only on the server (student creation by an administrator,
 * administrator bootstrap); no endpoint accepts a role from the client.
 */
public enum Role {
    STUDENT,
    ADMIN;

    /** Spring Security authority name, e.g. "ROLE_STUDENT". */
    public String authority() {
        return "ROLE_" + name();
    }
}
