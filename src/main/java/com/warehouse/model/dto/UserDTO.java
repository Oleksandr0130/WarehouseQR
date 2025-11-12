package com.warehouse.model.dto;

public class UserDTO {
    private Long id;            // 👈 добавлено для фронта (нужно для PUT/DELETE)
    private String username;
    private String email;
    private String companyName;
    private boolean admin;

    public UserDTO() {}

    public UserDTO(Long id, String username, String email, String companyName, boolean admin) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.companyName = companyName;
        this.admin = admin;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
