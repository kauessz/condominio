package com.example.condo.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilitário para gerar hashes BCrypt
 * Execute este main para gerar os hashes que serão usados no SQL
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String[] passwords = {
            "admin123",
            "super123",
            "sindico123",
            "zelador123",
            "portaria123",
            "morador123",
            "visita123"
        };
        
        String[] emails = {
            "admin@demo.com",
            "superadmin@demo.com",
            "sindico@demo.com",
            "zelador@demo.com",
            "portaria@demo.com",
            "morador@demo.com",
            "visita@demo.com"
        };
        
        System.out.println("-- SQL UPDATE para senhas BCrypt");
        System.out.println("-- Gerado em: " + java.time.LocalDateTime.now());
        System.out.println();
        
        for (int i = 0; i < passwords.length; i++) {
            String hash = encoder.encode(passwords[i]);
            System.out.println("-- " + emails[i] + " / " + passwords[i]);
            System.out.println("UPDATE users");
            System.out.println("SET password_hash = '" + hash + "'");
            System.out.println("WHERE email = '" + emails[i] + "' AND tenant_id = 'demo';");
            System.out.println();
        }
    }
}