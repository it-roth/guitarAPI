package com.example.guitarapi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users_tbl")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Users {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    int id;

    @Column(name = "first_name")
    String firstName;

    @Column(name = "last_name")
    String lastName;

    @Column(name = "gender")
    char gender;

    @Column(name = "email")
    String email;

    @Column(name = "password")
    String password;

    @Column(name = "images", nullable = true)
    String images;
    
    @Column(name = "token", nullable = true)
    String token;

    @Column(name = "role", nullable = true)
    String role;

    @Column(name = "created_at", nullable = true)
    String createdAt;

    @Column(name = "updated_at", nullable = true)
    String updatedAt;

}
