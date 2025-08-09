package com.warehouse.model.dto;

public class UserDTO {
    private String username;
    private String email;
    private String companyName;
    private boolean admin;

    public UserDTO() {}

    public UserDTO(String username, String email, String companyName, boolean admin) {
        this.username = username;
        this.email = email;
        this.companyName = companyName;
        this.admin = admin;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}