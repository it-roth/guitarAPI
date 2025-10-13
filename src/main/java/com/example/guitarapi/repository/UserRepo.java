package com.example.guitarapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.guitarapi.models.Users;

public interface UserRepo extends JpaRepository<Users, Integer>    {
	Users findByEmailAndPassword(String email, String password);
	Users findByToken(String token);
}
